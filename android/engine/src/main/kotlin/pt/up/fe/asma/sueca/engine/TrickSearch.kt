package pt.up.fe.asma.sueca.engine

/** Cards a player might answer with, and how likely they are to be holding each one. */
class Candidates(val cards: List<Card>, val weights: DoubleArray) {

    val size: Int get() = cards.size

    /**
     * Scales the weights so they sum to one.
     *
     * Ranking is untouched by this (it divides every combination by the same constant) but it
     * turns the sum the search produces into a genuine expected number of trick points, which
     * is what the eval bars in the app display.
     */
    fun normalised(): Candidates {
        val total = weights.sum()
        val scaled = if (total > 0.0) {
            DoubleArray(weights.size) { weights[it] / total }
        } else {
            DoubleArray(weights.size) { 1.0 / weights.size }     // nothing known: treat as uniform
        }
        return Candidates(cards, scaled)
    }

    /**
     * Keeps at most [k] cards, chosen to preserve what actually changes a trick: the strongest
     * and the weakest card of each suit, then whatever carries points.
     *
     * Only ever kicks in with the belief oracle, where a player's candidate set can be most of
     * the deck. With perfect information a hand is at most ten cards, so nothing is dropped and
     * the search stays identical to the Python one.
     */
    fun reduceTo(k: Int): Candidates {
        if (size <= k) return this

        val bySuit = cards.indices.groupBy { cards[it].suit }
        val queues = bySuit.values.map { indices ->
            val sorted = indices.sortedBy { cards[it].order }
            val ordered = LinkedHashSet<Int>()
            ordered.add(sorted.last())                                          // strongest
            ordered.add(sorted.first())                                         // weakest
            sorted.sortedByDescending { cards[it].points }.forEach(ordered::add) // then the points
            ordered.toList()
        }

        val kept = LinkedHashSet<Int>()
        var depth = 0
        while (kept.size < k && queues.any { depth < it.size }) {
            for (queue in queues) {
                if (depth < queue.size && kept.size < k) kept.add(queue[depth])
            }
            depth++
        }

        val indices = kept.sorted()
        return Candidates(indices.map { cards[it] }, DoubleArray(indices.size) { weights[indices[it]] })
    }
}

/** Where a strategy gets "what could the others still be holding?" from. */
interface HandOracle {

    /** Whether [target] might still be able to follow [suit]. */
    fun couldFollow(asker: Player, target: Player, suit: Suit): Boolean

    /** Cards [target] could answer with, restricted to [suit] when it is not null. */
    fun candidates(asker: Player, target: Player, suit: Suit?): Candidates
}

/**
 * Reads the other players' actual hands, exactly like `PredictorPlayer.get_player_possible_cards`
 * does in the Python simulator: the cards are real, only their weights come from the beliefs.
 *
 * Reproduces the published numbers, but it is peeking, so the app only uses it for
 * simulations and for agent versus agent play.
 */
object PerfectInfoOracle : HandOracle {

    override fun couldFollow(asker: Player, target: Player, suit: Suit): Boolean =
        target.cardsOfSuit(suit).isNotEmpty()

    override fun candidates(asker: Player, target: Player, suit: Suit?): Candidates {
        var cards = if (suit == null) target.hand.toList() else target.cardsOfSuit(suit)
        if (cards.isEmpty()) cards = target.hand.toList()       // void in the round suit, anything goes
        val weights = DoubleArray(cards.size) { asker.beliefs.probability(target.id, cards[it]) }
        return Candidates(cards, weights)
    }
}

/**
 * Reads only what the asking player is entitled to know: every card not yet seen, not in their
 * own hand, and not ruled out by someone failing to follow suit.
 *
 * This is the honest oracle, and the one the advisor uses at a real table, where nobody gets to
 * look at the other three hands.
 */
object BeliefOracle : HandOracle {

    override fun couldFollow(asker: Player, target: Player, suit: Suit): Boolean =
        asker.beliefs.holdsAnyOf(target.id, suit)

    override fun candidates(asker: Player, target: Player, suit: Suit?): Candidates {
        var cards = asker.beliefs.possibleCards(target.id, suit)
        if (cards.isEmpty()) cards = asker.beliefs.possibleCards(target.id, null)
        val weights = DoubleArray(cards.size) { asker.beliefs.probability(target.id, cards[it]) }
        return Candidates(cards, weights)
    }
}

/** One legal card, scored. */
data class MoveEval(
    val card: Card,
    /** Expected trick points, signed: positive means the points end up on the player's side. */
    val expectedPoints: Double,
    /** What the agent actually ranks on, i.e. [expectedPoints] after any heuristic penalty. */
    val score: Double,
    /** True when the early game trump penalty pushed this card down the ranking. */
    val penalised: Boolean,
    /** How many answers from the rest of the table were weighed to get here. */
    val combinations: Long,
)

/**
 * The expected trick search behind the Deck Predictor, and behind every eval bar in the app.
 *
 * Ported from `PredictorPlayer.choose_card`: for each legal card, walk the cartesian product of
 * everything the players still to act might answer with, weigh each combination by how likely it
 * is, and add up the trick points signed by whoever would take it.
 */
object TrickSearch {

    /** Utility penalty used to push a card to the bottom of the ranking. */
    const val AVOID = 1000.0

    fun evaluate(
        self: Player,
        turn: Turn,
        candidates: List<Card> = self.legalCards(turn.roundSuit),
    ): List<MoveEval> {
        val game = turn.game
        val config = game.config
        val oracle = config.oracle
        val order = game.playersOrder
        val trump = game.trumpSuit

        val stillToPlay = order.drop(turn.position + 1)
        val raw = stillToPlay.map { target ->
            val suit = when {
                turn.roundSuit == null -> null
                !oracle.couldFollow(self, target, turn.roundSuit) -> null
                else -> turn.roundSuit
            }
            oracle.candidates(self, target, suit)
        }

        // Keep the search inside its budget before expanding anything.
        var branch = config.maxBranch
        while (branch > 1 && combinationsWith(raw, branch) > config.searchBudget) branch--
        val groups = raw.map { it.reduceTo(branch).normalised() }
        val combinations = groups.fold(1L) { acc, group -> acc * group.size }

        // The cards already on the table decide the starting point of every simulated trick.
        var prefixPoints = 0
        var prefixBest: Card? = null
        var prefixBestSeat = 0
        turn.cardsPlayed.forEachIndexed { seat, card ->
            prefixPoints += card.points
            if (prefixBest == null || card.beats(prefixBest!!, trump)) {
                prefixBest = card
                prefixBestSeat = seat
            }
        }

        fun walk(depth: Int, bestSeat: Int, best: Card, points: Int, probability: Double): Double {
            if (depth == groups.size) {
                val ours = order[bestSeat].team === self.team
                return (if (ours) 1 else -1) * points * probability
            }

            val group = groups[depth]
            val seat = turn.position + 1 + depth
            var total = 0.0
            for (i in 0 until group.size) {
                val weight = group.weights[i]
                if (weight <= 0.0) continue
                val card = group.cards[i]
                val takesIt = card.beats(best, trump)
                total += walk(
                    depth = depth + 1,
                    bestSeat = if (takesIt) seat else bestSeat,
                    best = if (takesIt) card else best,
                    points = points + card.points,
                    probability = probability * weight,
                )
            }
            return total
        }

        val evaluations = candidates.map { card ->
            val takesIt = prefixBest == null || card.beats(prefixBest!!, trump)
            val expected = walk(
                depth = 0,
                bestSeat = if (takesIt) turn.position else prefixBestSeat,
                best = if (takesIt) card else prefixBest!!,
                points = prefixPoints + card.points,
                probability = 1.0,
            )

            // Hold on to the trumps early on, by pushing their utility down.
            val penalised = config.earlyTrumpAvoidance && game.currentRound < 2 && card.suit == trump
            MoveEval(
                card = card,
                expectedPoints = expected,
                score = if (penalised) expected - AVOID else expected,
                penalised = penalised,
                combinations = combinations,
            )
        }

        // The sort is stable and the hand is ordered weakest first, so ties go to the weakest card,
        // exactly like the Python version.
        return evaluations.sortedByDescending { it.score }
    }

    private fun combinationsWith(groups: List<Candidates>, branch: Int): Long =
        groups.fold(1L) { acc, group -> acc * minOf(group.size, branch) }
}
