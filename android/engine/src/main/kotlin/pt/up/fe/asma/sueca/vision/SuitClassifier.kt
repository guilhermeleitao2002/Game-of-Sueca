package pt.up.fe.asma.sueca.vision

import pt.up.fe.asma.sueca.engine.Suit
import pt.up.fe.asma.sueca.shapes.ShapeRasterizer
import pt.up.fe.asma.sueca.shapes.SuitShapes
import kotlin.math.abs

data class SuitScore(val suit: Suit, val score: Double)

data class SuitMatch(val suit: Suit, val confidence: Double, val ranked: List<SuitScore>) {

    /** How far ahead of the runner up the winner is. Low margin means "do not trust this". */
    val margin: Double get() = if (ranked.size < 2) confidence else ranked[0].score - ranked[1].score
}

/**
 * Decides which suit a segmented pip is, by matching it against masks rendered from the very
 * same outlines the app draws its cards with.
 *
 * Two signals are combined:
 *  - Jaccard overlap after stretching the pip into a 32x32 box, which makes it invariant to
 *    size, aspect and the mild perspective of a phone held over a table.
 *  - A small feature vector (fill ratio, how many separate runs of ink a scanline crosses at
 *    several heights, the width profile, the centroid). The notch at the top of a heart and the
 *    two lobes of a club show up here even when the overlap is mediocre.
 *
 * Both are measured against the templates rather than against hardcoded constants, so changing
 * a suit outline in [SuitShapes] keeps the classifier honest automatically.
 */
object SuitClassifier {

    const val TEMPLATE_SIZE = 32

    /** Scanline heights the feature vector probes, as a fraction of the mask height. */
    private val PROBES = doubleArrayOf(0.08, 0.25, 0.50, 0.75, 0.92)

    private const val OVERLAP_WEIGHT = 0.65
    private const val FEATURE_WEIGHT = 0.35

    /**
     * Sizes each template is rendered at before being normalised.
     *
     * A pip filling 14 pixels of a camera frame comes out visibly fatter than the same outline
     * filling 60, because every edge rounds outwards. Rendering the templates across the range
     * of sizes a pip actually turns up at, and keeping the best match, folds that in instead of
     * fighting it: a small blobby diamond is matched against a small blobby diamond.
     */
    private val TEMPLATE_SCALES = intArrayOf(12, 16, 22, 32, 48)

    private class Template(val mask: BooleanArray, val features: DoubleArray)

    private val templates: Map<Suit, List<Template>> by lazy {
        Suit.entries.associateWith { suit ->
            TEMPLATE_SCALES.map { scale ->
                val raw = ShapeRasterizer.rasterize(SuitShapes.of(suit), scale, scale)
                val mask = normalise(raw, scale, scale) ?: raw
                Template(mask, featuresOf(mask, TEMPLATE_SIZE, TEMPLATE_SIZE))
            }
        }
    }

    /** The canonical 32x32 mask of a suit, for previews and debugging. */
    fun template(suit: Suit): BooleanArray {
        val raw = ShapeRasterizer.rasterize(SuitShapes.of(suit), TEMPLATE_SIZE, TEMPLATE_SIZE)
        return normalise(raw, TEMPLATE_SIZE, TEMPLATE_SIZE) ?: raw
    }

    /**
     * @param redInk true when the pip is red ink, false when black, null when the colour could
     *   not be decided. Knowing it halves the candidate set and roughly doubles accuracy.
     */
    fun classify(mask: BooleanArray, width: Int, height: Int, redInk: Boolean?): SuitMatch? {
        val normalised = normalise(mask, width, height) ?: return null
        val features = featuresOf(normalised, TEMPLATE_SIZE, TEMPLATE_SIZE)

        val candidates = when (redInk) {
            true -> Suit.entries.filter { it.isRed }
            false -> Suit.entries.filter { !it.isRed }
            null -> Suit.entries
        }

        val ranked = candidates.map { suit ->
            val best = templates.getValue(suit).maxOf { template ->
                val overlap = jaccard(normalised, template.mask)
                val similarity = featureSimilarity(features, template.features)
                OVERLAP_WEIGHT * overlap + FEATURE_WEIGHT * similarity
            }
            SuitScore(suit, best)
        }.sortedByDescending { it.score }

        val best = ranked.firstOrNull() ?: return null
        return SuitMatch(best.suit, best.score, ranked)
    }

    /** Crops to the ink, then stretches it into the template box. */
    fun normalise(mask: BooleanArray, width: Int, height: Int): BooleanArray? {
        var left = width
        var top = height
        var right = -1
        var bottom = -1
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (!mask[y * width + x]) continue
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
        if (right < left || bottom < top) return null

        val boxWidth = right - left + 1
        val boxHeight = bottom - top + 1
        val out = BooleanArray(TEMPLATE_SIZE * TEMPLATE_SIZE)

        // Box filter: a target pixel is ink when most of the source cell it covers is ink.
        for (ty in 0 until TEMPLATE_SIZE) {
            val y0 = top + ty * boxHeight / TEMPLATE_SIZE
            val y1 = (top + (ty + 1) * boxHeight / TEMPLATE_SIZE).coerceAtLeast(y0 + 1)
            for (tx in 0 until TEMPLATE_SIZE) {
                val x0 = left + tx * boxWidth / TEMPLATE_SIZE
                val x1 = (left + (tx + 1) * boxWidth / TEMPLATE_SIZE).coerceAtLeast(x0 + 1)
                var ink = 0
                var total = 0
                for (y in y0 until y1.coerceAtMost(height)) {
                    for (x in x0 until x1.coerceAtMost(width)) {
                        total++
                        if (mask[y * width + x]) ink++
                    }
                }
                out[ty * TEMPLATE_SIZE + tx] = total > 0 && ink * 2 >= total
            }
        }
        return out
    }

    private fun jaccard(a: BooleanArray, b: BooleanArray): Double {
        var intersection = 0
        var union = 0
        for (i in a.indices) {
            val inA = a[i]
            val inB = b[i]
            if (inA && inB) intersection++
            if (inA || inB) union++
        }
        return if (union == 0) 0.0 else intersection.toDouble() / union
    }

    /**
     * [fill ratio, then per probe height: number of ink runs and the ink width].
     *
     * Runs are the giveaway: a heart is the only suit crossed twice near the top, a club the
     * only one crossed twice across the middle.
     */
    fun featuresOf(mask: BooleanArray, width: Int, height: Int): DoubleArray {
        val out = DoubleArray(1 + PROBES.size * 2 + 1)
        var ink = 0
        var centroid = 0.0
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (mask[y * width + x]) {
                    ink++
                    centroid += y
                }
            }
        }
        out[0] = ink.toDouble() / (width * height)

        PROBES.forEachIndexed { index, fraction ->
            val row = (fraction * height).toInt().coerceIn(0, height - 1)
            var runs = 0
            var covered = 0
            var previous = false
            for (x in 0 until width) {
                val here = mask[row * width + x]
                if (here) {
                    covered++
                    if (!previous) runs++
                }
                previous = here
            }
            out[1 + index * 2] = (runs.coerceAtMost(3)).toDouble() / 3.0
            out[2 + index * 2] = covered.toDouble() / width
        }

        out[out.size - 1] = if (ink == 0) 0.5 else centroid / ink / height
        return out
    }

    private fun featureSimilarity(a: DoubleArray, b: DoubleArray): Double {
        var distance = 0.0
        for (i in a.indices) distance += abs(a[i] - b[i])
        return (1.0 - distance / a.size).coerceIn(0.0, 1.0)
    }
}
