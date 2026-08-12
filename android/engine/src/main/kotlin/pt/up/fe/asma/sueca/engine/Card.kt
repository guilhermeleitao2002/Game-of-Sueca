package pt.up.fe.asma.sueca.engine

/**
 * Card model, ported from `Card.py`.
 *
 * Two number systems live on a card and are easy to confuse:
 *  - [Rank.points] is what the card *scores* for whoever captures it (A=11, 7=10, K=4, J=3, Q=2).
 *  - [Rank.order] is its *trick strength* (0..9) over the sequence 2 3 4 5 6 Q J K 7 A.
 *
 * Round winners are decided by order, scores by points. This file is the single source of
 * truth for both, exactly like `Card.py` is on the Python side.
 */
enum class Suit(val id: String, val symbol: String, val isRed: Boolean) {
    HEARTS("hearts", "♥", true),
    DIAMONDS("diamonds", "♦", true),
    CLUBS("clubs", "♣", false),
    SPADES("spades", "♠", false);

    companion object {
        fun fromId(id: String): Suit? = entries.firstOrNull { it.id.equals(id, ignoreCase = true) }
    }
}

/** Ranks in ascending trick taking strength: the ordinal *is* [order]. */
enum class Rank(val label: String, val points: Int) {
    TWO("2", 0),
    THREE("3", 0),
    FOUR("4", 0),
    FIVE("5", 0),
    SIX("6", 0),
    QUEEN("Q", 2),
    JACK("J", 3),
    KING("K", 4),
    SEVEN("7", 10),
    ACE("A", 11);

    /** Trick taking strength, 0 (weakest) to 9 (strongest). */
    val order: Int get() = ordinal

    companion object {
        fun fromLabel(label: String): Rank? = entries.firstOrNull { it.label.equals(label, ignoreCase = true) }
    }
}

data class Card(val suit: Suit, val rank: Rank) {

    /** Trick taking strength (0..9). Never compare this directly to decide a round, use [beats]. */
    val order: Int get() = rank.order

    /** Points this card is worth to whoever captures it. */
    val points: Int get() = rank.points

    /** `A_of_hearts`, the same identifier the Python simulator writes into its logs. */
    val id: String get() = "${rank.label}_of_${suit.id}"

    /** Compact label for the UI, e.g. `Q♠`. */
    val label: String get() = "${rank.label}${suit.symbol}"

    /** Dense index 0..39, used to address the belief tables. */
    val index: Int get() = suit.ordinal * RANK_COUNT + rank.order

    /**
     * Whether this card would take a round currently being won by [currentBest].
     *
     * Only a higher card of the same suit, or a trump played on a non trump, wins. A card of
     * the leading suit therefore never beats a round that has already been cut, which is why
     * comparing [order] on its own is always wrong.
     */
    fun beats(currentBest: Card, trump: Suit): Boolean =
        if (suit == currentBest.suit) order > currentBest.order else suit == trump

    override fun toString(): String = id

    companion object {
        fun of(suit: Suit, rank: Rank) = Card(suit, rank)

        /** Parses `A_of_hearts` or `AH` / `7♦` style labels. Returns null when unparseable. */
        fun parse(text: String): Card? {
            val trimmed = text.trim()
            if (trimmed.contains("_of_")) {
                val (rankPart, suitPart) = trimmed.split("_of_", limit = 2)
                val rank = Rank.fromLabel(rankPart) ?: return null
                val suit = Suit.fromId(suitPart) ?: return null
                return Card(suit, rank)
            }
            if (trimmed.length < 2) return null
            val rank = Rank.fromLabel(trimmed.dropLast(1)) ?: return null
            val suitChar = trimmed.last()
            val suit = Suit.entries.firstOrNull {
                it.symbol[0] == suitChar || it.id[0].equals(suitChar, ignoreCase = true)
            } ?: return null
            return Card(suit, rank)
        }

        fun fromIndex(index: Int): Card = Card(Suit.entries[index / RANK_COUNT], Rank.entries[index % RANK_COUNT])
    }
}

const val SUIT_COUNT = 4
const val RANK_COUNT = 10
const val CARDS_PER_HAND = 10
const val ROUNDS_PER_GAME = 10
const val PLAYER_COUNT = 4

/** Every point in the deck: 4 x (11 + 10 + 4 + 3 + 2) = 120. */
const val TOTAL_POINTS = 120

/** Points per [Card.order], the equivalent of `ORDER_VALUES` in `Card.py`. */
val ORDER_POINTS: IntArray = IntArray(RANK_COUNT) { Rank.entries[it].points }

/**
 * The 40 card Sueca deck, in the same order `Card.new_deck()` builds it (rank major), so a
 * seeded deal here matches a seeded deal there.
 */
val FULL_DECK: List<Card> = buildList {
    for (rank in Rank.entries) {
        for (suit in Suit.entries) {
            add(Card(suit, rank))
        }
    }
}

fun newDeck(): MutableList<Card> = FULL_DECK.toMutableList()
