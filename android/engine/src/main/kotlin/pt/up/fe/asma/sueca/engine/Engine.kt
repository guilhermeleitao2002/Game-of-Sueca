package pt.up.fe.asma.sueca.engine

import kotlin.random.Random

/** Where somebody sits relative to you, in the order they play. */
enum class Seat(val label: String, val playerId: Int) {
    ME("You", 1),
    RIGHT("Right", 3),
    PARTNER("Partner", 2),
    LEFT("Left", 4);

    val isOpponent: Boolean get() = this == RIGHT || this == LEFT

    companion object {
        fun ofPlayer(player: Player): Seat = entries.first { it.playerId == player.id }
    }
}

/** What one agent would play here, and why. */
data class AgentPick(val agent: AgentKind, val card: Card, val reason: String)

/**
 * The engine's answer for one position: the card it recommends, every legal card scored, and
 * what each of the other agents would have done instead.
 */
data class Advice(
    val agent: AgentKind,
    val recommended: Card,
    val reason: String,
    val evaluations: List<MoveEval>,
    val alternatives: List<AgentPick>,
) {

    /** The card the expected trick search likes most, which is not always [recommended]. */
    val searchBest: MoveEval get() = evaluations.first()

    val agreesWithSearch: Boolean get() = recommended == searchBest.card

    fun evaluationOf(card: Card): MoveEval? = evaluations.firstOrNull { it.card == card }

    /** Highest and lowest expected points among the legal cards, for scaling the eval bars. */
    val range: Pair<Double, Double>
        get() {
            val points = evaluations.map { it.expectedPoints }
            return (points.minOrNull() ?: 0.0) to (points.maxOrNull() ?: 0.0)
        }
}

/**
 * The chess engine style front end: ask it about a position, get a ranked answer back.
 *
 * The recommendation always comes from the agent the user picked, while the eval bars always
 * come from the expected trick search, so every agent is judged on the same yardstick — which
 * is what makes "the Greedy Player would throw away 12 points here" visible at a glance.
 */
object SuecaEngine {

    fun advise(
        game: Game,
        player: Player = game.currentPlayer,
        agent: AgentKind = AgentKind.DEFAULT,
        includeAlternatives: Boolean = true,
    ): Advice {
        require(player === game.currentPlayer) { "${player.name} is not the player to act" }

        val turn = game.turn
        val evaluations = TrickSearch.evaluate(player, turn)

        val decision = player.withStrategy(agent.strategy) { it.decide(turn) }
        val alternatives = if (!includeAlternatives) emptyList() else {
            AgentKind.entries.map { kind ->
                val other = if (kind == agent) decision else player.withStrategy(kind.strategy) { it.decide(turn) }
                AgentPick(kind, other.card, other.reason)
            }
        }

        return Advice(
            agent = agent,
            recommended = decision.card,
            reason = decision.reason,
            evaluations = evaluations,
            alternatives = alternatives,
        )
    }

    /** Just the card, for callers that do not want the whole analysis. */
    fun bestCard(game: Game, agent: AgentKind = AgentKind.DEFAULT): Card =
        game.currentPlayer.withStrategy(agent.strategy) { it.chooseCard(game.turn) }

    private inline fun <T> Player.withStrategy(strategy: Strategy, block: (Player) -> T): T {
        val previous = this.strategy
        this.strategy = strategy
        try {
            return block(this)
        } finally {
            this.strategy = previous
        }
    }
}

/**
 * Tracks a game happening on a real table, where the app only ever sees one hand.
 *
 * Every card that hits the table is recorded, so the beliefs sharpen as the game goes on and
 * the advice improves with them. The whole session is replayable, which is how [undo] works:
 * a misrecorded card is a certainty at a real table, and rebuilding from the log is both
 * simpler and safer than trying to unwind the belief updates.
 */
class AdvisorSession(
    agent: AgentKind = AgentKind.DEFAULT,
    val config: EngineConfig = EngineConfig.ADVISOR,
) {

    data class Setup(
        val hand: List<Card>,
        val trump: Card,
        val trumpHolder: Seat,
        val leader: Seat,
    )

    var agent: AgentKind = agent
        set(value) {
            field = value
            me.strategy = value.strategy
        }

    private var setup: Setup? = null
    private val log: MutableList<Card> = mutableListOf()

    var game: Game = newGame(agent)
        private set

    val me: Player get() = game.playerWithId(Seat.ME.playerId)

    val isStarted: Boolean get() = setup != null

    val isMyTurn: Boolean get() = isStarted && !game.isFinished && game.currentPlayer === me

    val currentSeat: Seat? get() = if (!isStarted || game.isFinished) null else Seat.ofPlayer(game.currentPlayer)

    /** Every card nobody has seen yet: not on the table, not in the known hand. */
    val unseenCards: List<Card>
        get() {
            val seen = HashSet<Card>()
            game.tricks.forEach { seen.addAll(it.cards) }
            seen.addAll(game.cardsPlayedInRound)
            seen.addAll(me.hand)
            return FULL_DECK.filterNot { it in seen }
        }

    /** Cards the seat to act could legally have played, for the "what did they play?" picker. */
    val playableNow: List<Card>
        get() {
            if (!isStarted || game.isFinished) return emptyList()
            if (isMyTurn) return game.legalCards().ifEmpty { unseenCards }
            val roundSuit = game.roundSuit ?: return unseenCards
            val player = game.currentPlayer
            val following = unseenCards.filter {
                it.suit == roundSuit && me.beliefs.probability(player.id, it) > 0.0
            }
            return if (following.isEmpty()) unseenCards else following + unseenCards.filterNot { it.suit == roundSuit }
        }

    fun start(hand: List<Card>, trump: Card, trumpHolder: Seat, leader: Seat) {
        setup = Setup(hand.sortedBy { it.order }, trump, trumpHolder, leader)
        log.clear()
        rebuild()
    }

    /** Records the card the seat to act just played. Returns the trick when it completes one. */
    fun record(card: Card): Trick? {
        checkNotNull(setup) { "the session has not been set up yet" }
        val trick = game.play(card)
        log.add(card)
        return trick
    }

    /** Takes the last recorded card back by replaying the session without it. */
    fun undo(): Boolean {
        if (log.isEmpty()) return false
        log.removeAt(log.lastIndex)
        val replay = log.toList()
        rebuild()
        replay.forEach { game.play(it) }
        return true
    }

    /**
     * Null when there is nothing to advise on: somebody else is to act, or the tracked hand has
     * run out, which happens when a session was started from fewer than ten known cards.
     */
    fun advise(): Advice? {
        if (!isMyTurn || me.hand.isEmpty()) return null
        return SuecaEngine.advise(game, me, agent)
    }

    fun reset() {
        setup = null
        log.clear()
        game = newGame(agent)
    }

    private fun newGame(agent: AgentKind) =
        Game(agent, agent, config, Random.Default, humanPlayerName = TeamId.SPORTING.playerNames.first())

    private fun rebuild() {
        val current = checkNotNull(setup)
        game = newGame(agent)
        game.startFromKnownHand(
            seat = me,
            hand = current.hand,
            trumpCard = current.trump,
            trumpHolder = game.playerWithId(current.trumpHolder.playerId),
            leader = game.playerWithId(current.leader.playerId),
        )
    }
}
