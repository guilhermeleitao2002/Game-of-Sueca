package pt.up.fe.asma.sueca.vision

import pt.up.fe.asma.sueca.engine.Card
import pt.up.fe.asma.sueca.engine.Rank

/**
 * Turns whatever the text recogniser reports in a card's index corner into a [Rank].
 *
 * The Sueca deck has no 8, 9 or 10, which helps: a token that reads "10" or "0" is either not a
 * card or not a card of this game, and is thrown away rather than guessed at. Portuguese decks
 * that print R/D/V (Rei, Dama, Valete) instead of K/Q/J are accepted too.
 */
object RankReader {

    data class Reading(val rank: Rank, val confidence: Double)

    private val EXACT: Map<String, Rank> = buildMap {
        Rank.entries.forEach { put(it.label, it) }
        put("R", Rank.KING)         // Rei
        put("D", Rank.QUEEN)        // Dama
        put("V", Rank.JACK)         // Valete
        put("AS", Rank.ACE)
        put("REI", Rank.KING)
        put("DAMA", Rank.QUEEN)
        put("VALETE", Rank.JACK)
    }

    /** Glyphs the recogniser reliably confuses on a playing card face. */
    private val CONFUSABLE: Map<String, Rank> = mapOf(
        "S" to Rank.FIVE,
        "Z" to Rank.TWO,
        "G" to Rank.SIX,
        "B" to Rank.SIX,
        "T" to Rank.SEVEN,
        "/" to Rank.SEVEN,
        "?" to Rank.SEVEN,
        "H" to Rank.KING,
        "X" to Rank.KING,
    )

    /** Tokens that mean "this is not a Sueca card", so no rank should be guessed. */
    private val REJECTED = setOf("10", "1", "0", "8", "9", "O", "W", "M")

    /**
     * @param profile when the user has confirmed what this deck's recogniser output means, that
     *   beats every general rule below — including the rejections, since the whole point of
     *   training is to rescue a deck whose queen reads as "O".
     */
    fun read(text: String, profile: DeckProfile? = null): Reading? {
        val token = text.trim().uppercase().filter { it.isLetterOrDigit() || it == '/' || it == '?' }
        if (token.isEmpty()) return null

        profile?.rankFor(token)?.let { (rank, confirmations) ->
            return Reading(rank, if (confirmations >= 2) 0.95 else 0.8)
        }

        if (token in REJECTED) return null

        EXACT[token]?.let { return Reading(it, 1.0) }
        CONFUSABLE[token]?.let { return Reading(it, 0.6) }

        // Corner indices are sometimes read together with the pip, e.g. "K*" or "7v".
        val head = token.take(1)
        if (head in REJECTED) return null
        EXACT[head]?.let { return Reading(it, 0.75) }
        CONFUSABLE[head]?.let { return Reading(it, 0.45) }

        return null
    }
}

data class ScannedCard(val card: Card, val confidence: Double, val sightings: Int)

/**
 * Turns a stream of noisy per frame detections into a stable set of cards.
 *
 * A single frame is never trusted: a card has to be seen [requiredSightings] times before it is
 * offered to the user, and detections fade out again after [memoryMillis] so pointing the phone
 * somewhere else clears the board instead of accumulating rubbish.
 */
class ScanAccumulator(
    private val requiredSightings: Int = 3,
    private val memoryMillis: Long = 2_500L,
    private val minConfidence: Double = 0.55,
) {

    private class Track(var sightings: Int, var confidence: Double, var lastSeen: Long)

    private val tracks = LinkedHashMap<Card, Track>()

    /** Cards the user explicitly removed, so a stale detection cannot bring them straight back. */
    private val rejected = HashSet<Card>()

    fun observe(card: Card, confidence: Double, now: Long) {
        if (confidence < minConfidence || card in rejected) return
        val track = tracks.getOrPut(card) { Track(0, confidence, now) }
        track.sightings++
        track.confidence = track.confidence * 0.6 + confidence * 0.4
        track.lastSeen = now
    }

    /** Everything seen often enough and recently enough, best first. */
    fun stable(now: Long): List<ScannedCard> {
        expire(now)
        return tracks.entries
            .filter { it.value.sightings >= requiredSightings }
            .map { ScannedCard(it.key, it.value.confidence, it.value.sightings) }
            .sortedByDescending { it.confidence }
    }

    /** Everything currently being tracked, including candidates not yet confirmed. */
    fun pending(now: Long): List<ScannedCard> {
        expire(now)
        return tracks.map { ScannedCard(it.key, it.value.confidence, it.value.sightings) }
    }

    fun reject(card: Card) {
        rejected.add(card)
        tracks.remove(card)
    }

    fun accept(card: Card) {
        rejected.remove(card)
    }

    fun clear() {
        tracks.clear()
        rejected.clear()
    }

    private fun expire(now: Long) {
        val iterator = tracks.entries.iterator()
        while (iterator.hasNext()) {
            if (now - iterator.next().value.lastSeen > memoryMillis) iterator.remove()
        }
    }
}
