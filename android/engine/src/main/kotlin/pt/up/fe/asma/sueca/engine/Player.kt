package pt.up.fe.asma.sueca.engine

/**
 * Everything a strategy is allowed to look at when it is asked to play.
 *
 * @param position seat within the trick, 0 for the player leading it.
 * @param roundSuit suit that must be followed, null while the trick is still being led.
 * @param cardsPlayed cards already on the table, in seat order.
 */
data class Turn(
    val position: Int,
    val roundSuit: Suit?,
    val cardsPlayed: List<Card>,
    val game: Game,
)

/** A strategy's pick, together with the one line explanation the UI shows underneath it. */
data class Decision(val card: Card, val reason: String)

/**
 * A way of picking a card.
 *
 * Unlike the Python simulator, where each strategy is a `Player` subclass, strategies here are
 * stateless objects that receive the player they are deciding for. That is what lets the app
 * ask *every* agent what it would do in the current position, and lets the human seat swap its
 * advisor without rebuilding the game.
 */
interface Strategy {
    val kind: AgentKind

    fun decide(self: Player, turn: Turn): Decision

    fun choose(self: Player, turn: Turn): Card = decide(self, turn).card
}

/**
 * The six agents of the simulator, in the order the report presents them.
 *
 * [cliName] is the value `sueca.py` accepts for `-s`/`-b` and [displayName] is exactly what
 * `Player.get_strategy()` returns, so logs from either side line up.
 */
enum class AgentKind(
    val cliName: String,
    val displayName: String,
    val tagline: String,
    val description: String,
) {
    RANDOM(
        cliName = "random",
        displayName = "Random Agent",
        tagline = "Plays anything legal",
        description = "Picks uniformly at random among the cards it is allowed to play. " +
            "The baseline every other agent is measured against.",
    ),
    GREEDY(
        cliName = "greedy",
        displayName = "Greedy Player",
        tagline = "Always the strongest card",
        description = "Plays its highest ranked legal card, every single time. Wins early " +
            "tricks and then has nothing left to win the ones that carry points.",
    ),
    MAX_POINTS(
        cliName = "maxpointswon",
        displayName = "Maximize Points Won",
        tagline = "Chases the points on the table",
        description = "Looks at what the trick is already worth. Piles points on when its own " +
            "side is winning, takes the trick when it can, and throws its cheapest card when it cannot.",
    ),
    MAX_ROUNDS(
        cliName = "maxroundswon",
        displayName = "Maximize Rounds Won",
        tagline = "Wins tricks as cheaply as possible",
        description = "Takes the trick with the weakest card that still takes it, and saves " +
            "everything strong for later. Ignores how many points are actually at stake.",
    ),
    COOPERATIVE(
        cliName = "cooperative",
        displayName = "Cooperative Player",
        tagline = "Plays for the partnership",
        description = "Tracks what its partner can still be holding and plays to set them up: " +
            "leads suits they can cut, feeds them points when they are taking the trick.",
    ),
    PREDICTOR(
        cliName = "predictor",
        displayName = "Deck Predictor",
        tagline = "Searches every answer the table can give",
        description = "Enumerates the cards every player still to act might answer with, weighs " +
            "each by how likely they are to hold it, and plays the card with the best expected trick.",
    );

    val strategy: Strategy
        get() = when (this) {
            RANDOM -> RandomStrategy
            GREEDY -> GreedyStrategy
            MAX_POINTS -> MaxPointsStrategy
            MAX_ROUNDS -> MaxRoundsStrategy
            COOPERATIVE -> CooperativeStrategy
            PREDICTOR -> PredictorStrategy
        }

    companion object {
        val DEFAULT = PREDICTOR

        fun fromCliName(name: String): AgentKind? = entries.firstOrNull { it.cliName == name }
    }
}

/**
 * One of the four seats. Ported from `Player.py`, minus the strategy, which now lives in
 * [strategy] instead of in a subclass.
 *
 * @param id 1..4, also the row this player occupies in every belief table.
 */
class Player(
    val id: Int,
    val name: String,
    val team: Team,
    strategy: Strategy,
    selfBeliefPolicy: SelfBeliefPolicy = SelfBeliefPolicy.LEGACY,
) {

    /** Swappable at any time, which is how the advisor screen changes agents mid game. */
    var strategy: Strategy = strategy

    /**
     * Kept sorted by [Card.order] on every insert, so `hand.first()` is always the weakest card
     * and `hand.last()` the strongest. Every strategy leans on that.
     */
    val hand: MutableList<Card> = mutableListOf()

    /**
     * Maintained for every seat, not just the belief agents, so the advisor can reason from any
     * seat regardless of which strategy is driving it.
     */
    val beliefs: BeliefTable = BeliefTable(id, selfBeliefPolicy)

    /** True for the seat a person is playing in the app. */
    var isHuman: Boolean = false

    /**
     * False for the three hands the advisor cannot see at a real table. Those seats still take
     * part in every trick, they just have no cards to remove and nothing to check legality
     * against — what they could be holding lives in everybody's [beliefs] instead.
     */
    var handKnown: Boolean = true

    /** Set once the hand is dealt: how many cards this seat started with that we can still see. */
    val cardsLeft: Int get() = hand.size

    fun addCard(card: Card) {
        hand.add(card)
        hand.sortBy { it.order }
    }

    fun removeCard(card: Card): Boolean = hand.remove(card)

    /** All cards of a suit, weakest first. */
    fun cardsOfSuit(suit: Suit): List<Card> = hand.filter { it.suit == suit }

    /**
     * Following suit is mandatory whenever the player can, so this is the whole hand when
     * leading or void, and only the round suit otherwise.
     */
    fun legalCards(roundSuit: Suit?): List<Card> {
        if (roundSuit == null) return hand.toList()
        val following = cardsOfSuit(roundSuit)
        return following.ifEmpty { hand.toList() }
    }

    fun partner(): Player? = team.partnerOf(this)

    fun decide(turn: Turn): Decision = strategy.decide(this, turn)

    fun chooseCard(turn: Turn): Card = strategy.choose(this, turn)

    override fun toString(): String = "$name(#$id, ${team.name}, ${strategy.kind.displayName})"
}
