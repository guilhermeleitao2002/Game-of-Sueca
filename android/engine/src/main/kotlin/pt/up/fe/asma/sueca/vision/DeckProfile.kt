package pt.up.fe.asma.sueca.vision

import pt.up.fe.asma.sueca.engine.Rank
import pt.up.fe.asma.sueca.engine.Suit

/**
 * What the app has learned about one physical deck.
 *
 * Every deck prints its pips differently — rounder hearts, thinner spades, a club whose lobes
 * almost touch — and the built-in templates are one idealised drawing of each. A profile
 * replaces that guess with examples cut out of the user's own cards.
 *
 * The learning is nearest neighbour over prototypes, not reinforcement learning: a correction
 * carries the answer, not a reward, and there is no sequence of actions to credit. One confirmed
 * card is enough to start helping, which is the property that matters when the training set is
 * whatever the user can be bothered to show the camera.
 *
 * Three things are learned:
 *  - **Pip shapes**, as normalised masks, matched against directly.
 *  - **Rank tokens**, so "this deck's queen reads as O" becomes a rule instead of a failure.
 *  - **Ink colour**, since "red" is vermilion on some decks and nearly brown on others.
 */
class DeckProfile(
    val id: String,
    name: String,
) {

    var name: String = name
        @Synchronized set

    private val suitExemplars: MutableMap<Suit, MutableList<Exemplar>> = mutableMapOf()

    /** token -> (rank -> how many times the user confirmed it). */
    private val rankTokens: MutableMap<String, MutableMap<Rank, Int>> = mutableMapOf()

    private var redSum = 0.0
    private var redCount = 0
    private var blackSum = 0.0
    private var blackCount = 0

    private class Exemplar(val mask: BooleanArray) {
        val features: DoubleArray by lazy {
            SuitClassifier.featuresOf(mask, SuitClassifier.TEMPLATE_SIZE, SuitClassifier.TEMPLATE_SIZE)
        }
    }

    // -----------------------------------------------------------------------------------------
    // Learning
    // -----------------------------------------------------------------------------------------

    /**
     * Files a confirmed pip under its suit.
     *
     * @param mask the pip already normalised to [SuitClassifier.TEMPLATE_SIZE] square.
     * @param redness mean redness of its ink, or null when it was not measured.
     */
    @Synchronized
    fun learnPip(suit: Suit, mask: BooleanArray, redness: Double? = null) {
        require(mask.size == SuitClassifier.TEMPLATE_SIZE * SuitClassifier.TEMPLATE_SIZE) {
            "a pip exemplar must be ${SuitClassifier.TEMPLATE_SIZE} square"
        }

        val exemplars = suitExemplars.getOrPut(suit) { mutableListOf() }
        val fresh = Exemplar(mask.copyOf())

        if (exemplars.size >= MAX_EXEMPLARS) {
            // Full: drop whichever stored example the new one duplicates most closely, so the set
            // drifts towards covering the deck's variation rather than one lucky angle.
            val nearest = exemplars.indices.maxBy { SuitClassifier.similarity(fresh.mask, exemplars[it].mask) }
            exemplars[nearest] = fresh
        } else {
            exemplars.add(fresh)
        }

        if (redness != null) {
            if (suit.isRed) {
                redSum += redness
                redCount++
            } else {
                blackSum += redness
                blackCount++
            }
        }
    }

    /** Records that this deck's text recogniser reads [token] when the card is a [rank]. */
    @Synchronized
    fun learnRankToken(token: String, rank: Rank) {
        val key = normaliseToken(token)
        if (key.isEmpty()) return
        val counts = rankTokens.getOrPut(key) { mutableMapOf() }
        counts[rank] = (counts[rank] ?: 0) + 1
    }

    @Synchronized
    fun forget(suit: Suit) {
        suitExemplars.remove(suit)
    }

    @Synchronized
    fun clear() {
        suitExemplars.clear()
        rankTokens.clear()
        redSum = 0.0
        redCount = 0
        blackSum = 0.0
        blackCount = 0
    }

    // -----------------------------------------------------------------------------------------
    // Using what was learned
    // -----------------------------------------------------------------------------------------

    /** Best similarity between [mask] and anything filed under [suit], or null if nothing is. */
    @Synchronized
    fun similarityTo(suit: Suit, mask: BooleanArray, features: DoubleArray): Double? {
        val exemplars = suitExemplars[suit] ?: return null
        if (exemplars.isEmpty()) return null
        return exemplars.maxOf { SuitClassifier.similarity(mask, features, it.mask, it.features) }
    }

    /** The rank this deck means by [token], when the user has confirmed it before. */
    @Synchronized
    fun rankFor(token: String): Pair<Rank, Int>? {
        val counts = rankTokens[normaliseToken(token)] ?: return null
        val best = counts.maxByOrNull { it.value } ?: return null
        return best.key to best.value
    }

    /**
     * Where to put the boundary between red and black ink for this deck, or null while there is
     * not enough of both to place one.
     */
    @Synchronized
    fun rednessThreshold(): Double? {
        if (redCount < 2 || blackCount < 2) return null
        val red = redSum / redCount
        val black = blackSum / blackCount
        if (red - black < 8.0) return null          // the two are not separable, keep the defaults
        return (red + black) / 2.0
    }

    // -----------------------------------------------------------------------------------------
    // Reporting
    // -----------------------------------------------------------------------------------------

    @Synchronized
    fun pipsLearned(suit: Suit): Int = suitExemplars[suit]?.size ?: 0

    @Synchronized
    fun coverage(): Map<Suit, Int> = Suit.entries.associateWith { pipsLearned(it) }

    @Synchronized
    fun ranksLearned(): Int = rankTokens.size

    val totalPips: Int
        @Synchronized get() = Suit.entries.sumOf { pipsLearned(it) }

    /** Every suit has at least one example, which is when matching can stand on its own. */
    val isUsable: Boolean
        @Synchronized get() = Suit.entries.all { pipsLearned(it) > 0 }

    // -----------------------------------------------------------------------------------------
    // Storage
    // -----------------------------------------------------------------------------------------

    /**
     * A small line based format rather than a serialisation library: it is a handful of lines,
     * it survives being looked at in a text editor, and the engine module stays dependency free.
     */
    @Synchronized
    fun encode(): String = buildString {
        appendLine("$MAGIC $VERSION")
        appendLine("id\t$id")
        appendLine("name\t${name.replace('\t', ' ').replace('\n', ' ')}")
        appendLine("ink\t$redSum\t$redCount\t$blackSum\t$blackCount")
        for ((suit, exemplars) in suitExemplars) {
            for (exemplar in exemplars) appendLine("pip\t${suit.id}\t${encodeMask(exemplar.mask)}")
        }
        for ((token, counts) in rankTokens) {
            for ((rank, count) in counts) appendLine("tok\t$token\t${rank.label}\t$count")
        }
    }

    companion object {

        /** How many examples of one suit are worth keeping. */
        const val MAX_EXEMPLARS = 8

        private const val MAGIC = "sueca-deck-profile"
        private const val VERSION = 1

        fun decode(text: String): DeckProfile? {
            val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
            if (lines.isEmpty() || !lines.first().startsWith(MAGIC)) return null

            var id = "deck"
            var name = "Deck"
            val pips = mutableListOf<Pair<Suit, BooleanArray>>()
            val tokens = mutableListOf<Triple<String, Rank, Int>>()
            var ink = DoubleArray(4)

            for (line in lines.drop(1)) {
                val parts = line.split('\t')
                when (parts.firstOrNull()) {
                    "id" -> id = parts.getOrNull(1) ?: id
                    "name" -> name = parts.getOrNull(1) ?: name
                    "ink" -> if (parts.size >= 5) {
                        ink = DoubleArray(4) { parts[it + 1].toDoubleOrNull() ?: 0.0 }
                    }

                    "pip" -> {
                        val suit = Suit.fromId(parts.getOrNull(1) ?: "") ?: continue
                        val mask = decodeMask(parts.getOrNull(2) ?: "") ?: continue
                        pips.add(suit to mask)
                    }

                    "tok" -> {
                        val token = parts.getOrNull(1) ?: continue
                        val rank = Rank.fromLabel(parts.getOrNull(2) ?: "") ?: continue
                        val count = parts.getOrNull(3)?.toIntOrNull() ?: 1
                        tokens.add(Triple(token, rank, count))
                    }
                }
            }

            val profile = DeckProfile(id, name)
            pips.forEach { (suit, mask) -> profile.learnPip(suit, mask) }
            tokens.forEach { (token, rank, count) -> repeat(count) { profile.learnRankToken(token, rank) } }
            profile.redSum = ink[0]
            profile.redCount = ink[1].toInt()
            profile.blackSum = ink[2]
            profile.blackCount = ink[3].toInt()
            return profile
        }

        internal fun normaliseToken(token: String): String =
            token.trim().uppercase().filter { it.isLetterOrDigit() }

        /** 1024 bits of mask as hex, which is 256 characters and reads fine in a file. */
        internal fun encodeMask(mask: BooleanArray): String {
            val out = StringBuilder(mask.size / 4)
            var index = 0
            while (index < mask.size) {
                var nibble = 0
                for (bit in 0 until 4) {
                    if (index + bit < mask.size && mask[index + bit]) nibble = nibble or (1 shl bit)
                }
                out.append(nibble.toString(16))
                index += 4
            }
            return out.toString()
        }

        internal fun decodeMask(hex: String): BooleanArray? {
            val bits = SuitClassifier.TEMPLATE_SIZE * SuitClassifier.TEMPLATE_SIZE
            if (hex.length != bits / 4) return null
            val mask = BooleanArray(bits)
            hex.forEachIndexed { position, character ->
                val nibble = character.digitToIntOrNull(16) ?: return null
                for (bit in 0 until 4) {
                    mask[position * 4 + bit] = (nibble shr bit) and 1 == 1
                }
            }
            return mask
        }
    }
}
