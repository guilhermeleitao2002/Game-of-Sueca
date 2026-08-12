package pt.up.fe.asma.sueca.engine

import kotlin.random.Random

/**
 * Knobs that separate "reproduce the simulator exactly" from "give good advice at a real table".
 *
 * @param selfBeliefPolicy see [SelfBeliefPolicy].
 * @param perfectInformation when true the Deck Predictor reads the other hands, as the Python
 *   version does; when false it reasons only from its beliefs.
 * @param earlyTrumpAvoidance the `current_round < 2` trump penalty of `PredictorPlayer`.
 * @param revealTrump whether every seat is told who holds the trump card. True at a real table,
 *   where the trump is turned face up, false in the simulator, which never models it.
 * @param maxBranch cap on how many answers per opponent the search expands.
 * @param searchBudget cap on the total number of answers expanded per decision.
 */
data class EngineConfig(
    val selfBeliefPolicy: SelfBeliefPolicy = SelfBeliefPolicy.LEGACY,
    val perfectInformation: Boolean = true,
    val earlyTrumpAvoidance: Boolean = true,
    val revealTrump: Boolean = false,
    val maxBranch: Int = 12,
    val searchBudget: Long = 20_000L,
) {

    val oracle: HandOracle get() = if (perfectInformation) PerfectInfoOracle else BeliefOracle

    companion object {
        /** Bit for bit the behaviour of `sueca.py`, for reproducing the report's numbers. */
        val SIMULATION = EngineConfig()

        /** Agents that do not peek at anyone's hand. Used when a person is at the table. */
        val FAIR = EngineConfig(perfectInformation = false, selfBeliefPolicy = SelfBeliefPolicy.TRACK_OWN_PLAYS)

        /** Tracking a real game: nothing is known beyond what has been seen. */
        val ADVISOR = EngineConfig(
            selfBeliefPolicy = SelfBeliefPolicy.TRACK_OWN_PLAYS,
            perfectInformation = false,
            revealTrump = true,
        )
    }
}

/**
 * @param points total value of the cards on the table.
 * @param card the card winning the round so far.
 * @param winner index of that card in the list of cards played.
 */
data class RoundResult(val points: Int, val card: Card, val winner: Int)

data class Play(val player: Player, val card: Card)

data class Trick(val number: Int, val plays: List<Play>, val winner: Player, val points: Int) {
    val cards: List<Card> get() = plays.map { it.card }
}

data class GameResult(val winner: TeamId?, val scores: Map<TeamId, Int>, val dealt: Map<TeamId, Int>) {
    val isTie: Boolean get() = winner == null
}

/**
 * A game of Sueca, driven one card at a time.
 *
 * Where `Game.py` runs the whole thing in a blocking loop, this exposes the same rules as a
 * state machine ([currentPlayer], [legalCards], [play]) so a UI can wait for a tap, animate a
 * trick, or let an agent take over any seat at any moment. [playToCompletion] recovers the
 * original loop for simulations.
 */
class Game(
    val sportingAgent: AgentKind,
    val benficaAgent: AgentKind,
    val config: EngineConfig = EngineConfig.SIMULATION,
    val random: Random = Random.Default,
    humanPlayerName: String? = null,
) {

    val teams: List<Team> = TeamId.entries.map { id ->
        Team(id, if (id == TeamId.SPORTING) sportingAgent else benficaAgent)
    }

    val sporting: Team get() = teams[TeamId.SPORTING.ordinal]
    val benfica: Team get() = teams[TeamId.BENFICA.ordinal]

    /** All four seats, in id order (1..4), independent of who is playing first. */
    val players: List<Player>

    /** The four seats sorted by who plays next; rotated to the winner after every trick. */
    var playersOrder: List<Player> = emptyList()
        private set

    private val deck: MutableList<Card> = newDeck()

    /** Set by [deal] to the last card handed out, or by [startFromKnownHand]. */
    var trump: Card? = null
        private set

    val trumpSuit: Suit get() = requireNotNull(trump) { "the trump card is not known yet" }.suit

    /** Index of the trick being played, 0..9. */
    var currentRound: Int = 0
        private set

    /** The suit that has to be followed, null while the trick is still being led. */
    var roundSuit: Suit? = null
        private set

    private val _cardsPlayedInRound: MutableList<Card> = mutableListOf()
    val cardsPlayedInRound: List<Card> get() = _cardsPlayedInRound

    private val _tricks: MutableList<Trick> = mutableListOf()
    val tricks: List<Trick> get() = _tricks

    init {
        val built = mutableListOf<Player>()
        for (team in teams) {
            team.id.playerNames.forEachIndexed { offset, name ->
                val player = Player(
                    id = team.id.firstPlayerId + offset,
                    name = name,
                    team = team,
                    strategy = team.agent.strategy,
                    selfBeliefPolicy = config.selfBeliefPolicy,
                )
                player.isHuman = name == humanPlayerName
                team.addPlayer(player)
                built.add(player)
            }
        }
        players = built.sortedBy { it.id }

        // Randomise the seating, alternating between the teams, exactly like Game.__init__.
        for (team in teams) team.players.shuffle(random)
        val first = teams[random.nextInt(teams.size)]
        val second = if (first === teams[0]) teams[1] else teams[0]
        playersOrder = first.players.zip(second.players).flatMap { (a, b) -> listOf(a, b) }
    }

    fun playerNamed(name: String): Player? = players.firstOrNull { it.name == name }

    fun playerWithId(id: Int): Player = players[id - 1]

    // -----------------------------------------------------------------------------------------
    // Setting up
    // -----------------------------------------------------------------------------------------

    /**
     * Distributes the cards between the players. The last card dealt is the trump.
     *
     * Ported from `Game.hand_cards`, including the detail that the trump card simply stays in
     * the hand of whoever was dealt it last.
     */
    fun deal() {
        var last: Card? = null
        for (player in playersOrder) {
            repeat(CARDS_PER_HAND) {
                val card = deck.removeAt(random.nextInt(deck.size))
                player.addCard(card)
                player.team.initialPoints += card.points
                player.beliefs.observeDealt(card)
                last = card
            }
        }
        trump = last
        if (config.revealTrump) revealTrumpHolder(playersOrder.last())
    }

    /**
     * Sets the table up from the outside: one known hand, the trump card, and who sits where.
     *
     * This is how the advisor tracks a real game. The three other hands stay unknown
     * ([Player.handKnown] false) and the agents reason about them purely from beliefs.
     */
    fun startFromKnownHand(
        seat: Player,
        hand: List<Card>,
        trumpCard: Card,
        trumpHolder: Player?,
        leader: Player,
    ) {
        require(hand.size <= CARDS_PER_HAND) { "a Sueca hand holds at most $CARDS_PER_HAND cards" }

        for (player in players) {
            player.hand.clear()
            player.handKnown = player === seat
        }

        for (card in hand) {
            seat.addCard(card)
            seat.team.initialPoints += card.points
            for (player in players) player.beliefs.observeDealt(card, seat.id)
        }

        trump = trumpCard
        if (trumpHolder != null) revealTrumpHolder(trumpHolder)

        playersOrder = rotateToLeader(players.sortedBy { seatOrderIndex(it) }, leader)
        currentRound = 0
        roundSuit = null
        _cardsPlayedInRound.clear()
        _tricks.clear()
    }

    /**
     * Deterministic setup with all four hands known.
     *
     * Used to replay a game logged by the Python simulator, and to put the engine in front of a
     * specific position.
     */
    fun startFromHands(hands: Map<Int, List<Card>>, trumpCard: Card, leaderId: Int) {
        require(hands.keys.toSet() == (1..PLAYER_COUNT).toSet()) { "every seat needs a hand" }
        require(hands.values.flatten().toSet().size == hands.values.sumOf { it.size }) { "duplicate cards" }

        for (team in teams) {
            team.score = 0
            team.initialPoints = 0
        }

        for (player in players) {
            player.hand.clear()
            player.handKnown = true
            val hand = hands.getValue(player.id)
            for (card in hand) {
                player.addCard(card)
                player.team.initialPoints += card.points
                player.beliefs.observeDealt(card)
            }
        }

        trump = trumpCard
        if (config.revealTrump) {
            players.firstOrNull { trumpCard in it.hand }?.let { revealTrumpHolder(it) }
        }

        playersOrder = rotateToLeader(players.sortedBy { seatOrderIndex(it) }, playerWithId(leaderId))
        currentRound = 0
        roundSuit = null
        _cardsPlayedInRound.clear()
        _tricks.clear()
    }

    /** Playing order around the table: me, opponent, partner, opponent. */
    private fun seatOrderIndex(player: Player): Int = when (player.id) {
        1 -> 0      // Sporting first player
        3 -> 1      // Benfica first player
        2 -> 2      // Sporting second player
        else -> 3   // Benfica second player
    }

    private fun rotateToLeader(order: List<Player>, leader: Player): List<Player> {
        val index = order.indexOf(leader)
        require(index >= 0) { "$leader is not sitting at this table" }
        return order.drop(index) + order.take(index)
    }

    private fun revealTrumpHolder(holder: Player) {
        val card = trump ?: return
        for (player in players) player.beliefs.observeDealt(card, holder.id)
    }

    // -----------------------------------------------------------------------------------------
    // Playing
    // -----------------------------------------------------------------------------------------

    val currentPosition: Int get() = _cardsPlayedInRound.size

    val currentPlayer: Player get() = playersOrder[currentPosition]

    val isFinished: Boolean get() = currentRound >= ROUNDS_PER_GAME

    /** Everything the strategy of the player to act is allowed to see. */
    val turn: Turn get() = Turn(currentPosition, roundSuit, _cardsPlayedInRound.toList(), this)

    /** The cards the player to act is allowed to play. */
    fun legalCards(): List<Card> = currentPlayer.legalCards(roundSuit)

    /** What the current player's own strategy would do, without playing it. */
    fun suggestion(): Decision = currentPlayer.decide(turn)

    /**
     * Plays [card] for whoever is to act.
     *
     * Returns the finished trick when this was the fourth card, null otherwise.
     */
    fun play(card: Card): Trick? {
        check(!isFinished) { "the game is over" }
        val player = currentPlayer

        // A known hand that has run out is treated like an unknown one: it happens when a
        // session was started from fewer than ten cards, and refusing every card from then on
        // would strand the game rather than degrade it.
        if (player.handKnown && player.hand.isNotEmpty()) {
            require(card in legalCards()) { "${card.label} is not a legal card for ${player.name}" }
            player.removeCard(card)
        }

        if (_cardsPlayedInRound.isEmpty()) roundSuit = card.suit
        _cardsPlayedInRound.add(card)

        // Everyone updates their beliefs; the table each player owns decides what it does with it.
        val suit = roundSuit!!
        for (other in players) other.beliefs.observePlayed(card, suit, player.id)

        if (_cardsPlayedInRound.size < PLAYER_COUNT) return null

        val result = evaluateRound(_cardsPlayedInRound)
        val winner = playersOrder[result.winner]
        winner.team.score += result.points

        val trick = Trick(
            number = currentRound,
            plays = playersOrder.mapIndexed { index, seat -> Play(seat, _cardsPlayedInRound[index]) },
            winner = winner,
            points = result.points,
        )
        _tricks.add(trick)

        // Rotate the players order to the winner of the round.
        playersOrder = rotateToLeader(playersOrder, winner)
        _cardsPlayedInRound.clear()
        roundSuit = null
        currentRound++

        return trick
    }

    /** Lets the current player's agent choose, then plays it. */
    fun playAgentTurn(): Pair<Decision, Trick?> {
        val decision = suggestion()
        return decision to play(decision.card)
    }

    fun playToCompletion() {
        while (!isFinished) playAgentTurn()
    }

    /**
     * Calculates the points on the table and which of the cards is winning them.
     *
     * Also used by the strategies to score rounds that are still incomplete, and hypothetical
     * ones, which is why it accepts a partial list. The returned [RoundResult.winner] indexes
     * into [cards], which lines up with [playersOrder] because the cards played so far always
     * belong to the first N players in order.
     */
    fun evaluateRound(cards: List<Card>): RoundResult {
        require(cards.isNotEmpty()) { "cannot evaluate an empty round" }
        var winner = 0
        for (i in 1 until cards.size) {
            if (cards[i].beats(cards[winner], trumpSuit)) winner = i
        }
        return RoundResult(cards.sumOf { it.points }, cards[winner], winner)
    }

    fun result(): GameResult {
        val scores = teams.associate { it.id to it.score }
        val dealt = teams.associate { it.id to it.initialPoints }
        val winner = when {
            sporting.score > benfica.score -> TeamId.SPORTING
            benfica.score > sporting.score -> TeamId.BENFICA
            else -> null
        }
        return GameResult(winner, scores, dealt)
    }
}
