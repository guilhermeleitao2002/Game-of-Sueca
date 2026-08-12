package pt.up.fe.asma.sueca.engine

/**
 * What to do with the belief row that describes the agent's *own* hand.
 *
 * The Python simulator never updates a player's beliefs about the card that player just
 * played (`Game.update_beliefs` skips them), so an agent's own row keeps describing the hand
 * it was *dealt* rather than the hand it still holds. [LEGACY] reproduces that exactly, which
 * matters when replaying the numbers in `results/`; [TRACK_OWN_PLAYS] fixes it, which matters
 * when the table is real and the advice has to be right.
 */
enum class SelfBeliefPolicy { LEGACY, TRACK_OWN_PLAYS }

/**
 * `beliefs[player, suit, order]`: the probability that a player still holds a card.
 *
 * Ported from `BeliefPlayer` in `Player.py`, where it is a 4x4x10 numpy array. Cards the
 * owner was dealt sit at 1, cards already seen at 0, and everything else is split evenly
 * between the players that could still be holding it.
 */
class BeliefTable(val ownerId: Int, private val selfPolicy: SelfBeliefPolicy = SelfBeliefPolicy.LEGACY) {

    private val values = DoubleArray(PLAYER_COUNT * SUIT_COUNT * RANK_COUNT) { 1.0 / 3.0 }

    init {
        // We know exactly what we hold; our own row is filled in by observeDealt.
        for (i in 0 until SUIT_COUNT * RANK_COUNT) {
            values[ownerIndex + i] = 0.0
        }
    }

    private val ownerIndex: Int get() = (ownerId - 1) * SUIT_COUNT * RANK_COUNT

    private fun offset(playerId: Int, suit: Suit, order: Int): Int =
        ((playerId - 1) * SUIT_COUNT + suit.ordinal) * RANK_COUNT + order

    operator fun get(playerId: Int, suit: Suit, order: Int): Double = values[offset(playerId, suit, order)]

    operator fun set(playerId: Int, suit: Suit, order: Int, probability: Double) {
        values[offset(playerId, suit, order)] = probability
    }

    fun probability(playerId: Int, card: Card): Double = this[playerId, card.suit, card.order]

    /** Whether [playerId] might still hold anything of [suit]. */
    fun holdsAnyOf(playerId: Int, suit: Suit): Boolean =
        (0 until RANK_COUNT).any { this[playerId, suit, it] > 0.0 }

    /** Total points [playerId] is expected to be holding in [suit]. */
    fun expectedPointsIn(playerId: Int, suit: Suit): Double =
        (0 until RANK_COUNT).sumOf { this[playerId, suit, it] * Rank.entries[it].points }

    /** Every card [playerId] could still hold, optionally restricted to one suit. */
    fun possibleCards(playerId: Int, suit: Suit? = null): List<Card> {
        val suits = if (suit == null) Suit.entries else listOf(suit)
        return buildList {
            for (s in suits) {
                for (order in 0 until RANK_COUNT) {
                    if (this@BeliefTable[playerId, s, order] > 0.0) add(Card(s, Rank.entries[order]))
                }
            }
        }
    }

    /**
     * A card known to sit in one hand: nobody else can be holding it.
     *
     * Defaults to the owner of this table, which is the "cards I was dealt" case of
     * `update_beliefs_initial`; passing [holderId] covers the trump card, which everyone at a
     * real table sees being turned face up.
     */
    fun observeDealt(card: Card, holderId: Int = ownerId) {
        for (playerId in 1..PLAYER_COUNT) this[playerId, card.suit, card.order] = 0.0
        this[holderId, card.suit, card.order] = 1.0
    }

    /**
     * A card has hit the table.
     *
     * Once it is played nobody holds it any more, and a player that failed to follow suit has
     * just proved they are void in the round suit. Whatever is left gets split evenly between
     * the players that could still hold it.
     */
    fun observePlayed(card: Card, roundSuit: Suit, playerId: Int) {
        if (playerId == ownerId && selfPolicy == SelfBeliefPolicy.LEGACY) return

        for (id in 1..PLAYER_COUNT) this[id, card.suit, card.order] = 0.0

        if (card.suit != roundSuit) {
            for (order in 0 until RANK_COUNT) this[playerId, roundSuit, order] = 0.0
        }

        renormalise()
    }

    /** Split every unseen card evenly between the players that may still hold it. */
    private fun renormalise() {
        for (suit in Suit.entries) {
            for (order in 0 until RANK_COUNT) {
                var holders = 0
                for (id in 1..PLAYER_COUNT) if (this[id, suit, order] > 0.0) holders++
                if (holders == 0) continue
                val share = 1.0 / holders
                for (id in 1..PLAYER_COUNT) {
                    if (this[id, suit, order] > 0.0) this[id, suit, order] = share
                }
            }
        }
    }

    /** Debug/UI helper: a copy of one player's 4x10 slice. */
    fun rowOf(playerId: Int): Array<DoubleArray> =
        Array(SUIT_COUNT) { suit ->
            DoubleArray(RANK_COUNT) { order -> this[playerId, Suit.entries[suit], order] }
        }
}
