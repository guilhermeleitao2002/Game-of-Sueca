package pt.up.fe.asma.sueca.vision

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import pt.up.fe.asma.sueca.engine.Card
import pt.up.fe.asma.sueca.engine.Rank
import pt.up.fe.asma.sueca.engine.Suit
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** A box in image space, expressed as fractions so the overlay can be drawn at any size. */
data class NormRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val area: Float get() = ((right - left) * (bottom - top)).coerceAtLeast(0f)
}

/**
 * One index corner the pipeline pulled out of a frame, whether or not it made sense of it.
 *
 * The failures are the interesting ones: a sample with a rank but no confident suit is exactly
 * what the deck trainer wants to be handed, so it can be told what it was looking at.
 */
data class PipSample(
    /** Raw text the recogniser returned, before any interpretation. */
    val token: String,
    val rank: Rank?,
    val rankConfidence: Double,
    val suit: Suit?,
    val suitConfidence: Double,
    val suitMargin: Double,
    /** True when the suit was decided by a card the user trained rather than a template. */
    val fromProfile: Boolean,
    /** The pip normalised into the template box, ready to be learned from. */
    val mask: BooleanArray,
    val redness: Double,
    val bounds: NormRect,
) {

    val card: Card? get() = rank?.let { r -> suit?.let { s -> Card(s, r) } }

    val confidence: Double get() = rankConfidence * suitConfidence
}

/** One card read confidently enough to offer to the user. */
data class DetectedCard(
    val card: Card,
    val confidence: Double,
    val bounds: NormRect,
    val suitMargin: Double,
    val fromProfile: Boolean,
)

/** Everything one frame produced, plus the cards that have been seen often enough to trust. */
data class ScanFrame(
    val detections: List<DetectedCard> = emptyList(),
    /** Every candidate, including the ones that were not confident enough to report. */
    val samples: List<PipSample> = emptyList(),
    val stable: List<ScannedCard> = emptyList(),
    val frames: Long = 0,
    /** Size of the analysed frame, so the overlay can line its boxes up with the preview. */
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
) {

    /** The most prominent candidate, which is the card being held up to the camera. */
    val focused: PipSample? get() = samples.maxByOrNull { it.bounds.area }
}

/**
 * Reads playing cards out of the camera, on device and offline.
 *
 * The pipeline is deliberately split in two, because the two halves of a card index are very
 * different problems:
 *
 *  1. **The rank** is a glyph, and a glyph is what a text recogniser is good at. ML Kit's
 *     bundled Latin model finds every candidate and [RankReader] filters its output down to the
 *     ten ranks a Sueca deck actually has.
 *  2. **The suit** is a silhouette, and no text model reads those. The strip just below each
 *     rank is thresholded, the pip is pulled out as a connected component, and [SuitClassifier]
 *     matches it against masks rendered from the same vector outlines the app draws its cards
 *     with — and, once the user has trained a deck, against pips cut out of that deck itself.
 *
 * A single frame is never trusted on its own: [ScanAccumulator] only surfaces a card once it has
 * turned up in several consecutive frames.
 */
class CardScanner(
    private val onFrame: (ScanFrame) -> Unit,
) : ImageAnalysis.Analyzer {

    /**
     * The scanner owns its thread and hands it to CameraX, which matters more than it looks:
     * ML Kit posts its callbacks to the main thread unless told otherwise, and thresholding a
     * frame there would jank the UI. Everything downstream of the recogniser runs here instead,
     * and because CameraX will not deliver another frame until this one is closed, the whole
     * pipeline is serialised onto this single thread.
     */
    val executor: ExecutorService =
        Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "sueca-vision") }

    /** What this deck's pips look like, when the user has trained one. */
    @Volatile
    var profile: DeckProfile? = null

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val accumulator = ScanAccumulator(requiredSightings = 3, memoryMillis = 2_500L)
    private val lock = Any()
    private var frameCount = 0L

    /** Reused between frames; a 720p frame is nearly four megabytes of ints to hand the collector. */
    private var pixels = IntArray(0)

    @Volatile
    private var running = true

    override fun analyze(image: ImageProxy) {
        if (!running) {
            image.close()
            return
        }

        val upright = image.toUprightBitmap()
        if (upright == null) {
            image.close()
            return
        }

        recognizer.process(InputImage.fromBitmap(upright, 0))
            .addOnSuccessListener(executor) { text ->
                publish(readSamples(text, upright), upright.width, upright.height)
            }
            .addOnCompleteListener(executor) { image.close() }
    }

    fun reject(card: Card) = synchronized(lock) { accumulator.reject(card) }

    fun accept(card: Card) = synchronized(lock) { accumulator.accept(card) }

    fun clear() = synchronized(lock) { accumulator.clear() }

    fun close() {
        running = false
        recognizer.close()
        executor.shutdown()
    }

    // ---------------------------------------------------------------------------------------

    private fun publish(samples: List<PipSample>, width: Int, height: Int) {
        val detections = samples.mapNotNull { sample ->
            val card = sample.card ?: return@mapNotNull null
            if (sample.suitMargin < MIN_MARGIN || sample.confidence < MIN_CONFIDENCE) return@mapNotNull null
            DetectedCard(card, sample.confidence, sample.bounds, sample.suitMargin, sample.fromProfile)
        }.distinctBy { it.card }

        val now = System.currentTimeMillis()
        val stable = synchronized(lock) {
            detections.forEach { accumulator.observe(it.card, it.confidence, now) }
            accumulator.stable(now)
        }
        frameCount++
        onFrame(ScanFrame(detections, samples, stable, frameCount, width, height))
    }

    private fun readSamples(text: Text, bitmap: Bitmap): List<PipSample> {
        if (text.textBlocks.isEmpty()) return emptyList()

        val deck = profile
        val width = bitmap.width
        val height = bitmap.height
        if (pixels.size < width * height) pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val samples = mutableListOf<PipSample>()

        for (block in text.textBlocks) {
            for (line in block.lines) {
                for (element in line.elements) {
                    val box = element.boundingBox ?: continue
                    if (!plausibleIndex(box, width, height)) continue

                    val pip = findPip(box, width, height, deck) ?: continue
                    val reading = RankReader.read(element.text, deck)

                    samples.add(
                        PipSample(
                            token = element.text,
                            rank = reading?.rank,
                            rankConfidence = reading?.confidence ?: 0.0,
                            suit = pip.match.suit,
                            suitConfidence = pip.match.confidence,
                            suitMargin = pip.match.margin,
                            fromProfile = pip.match.fromProfile,
                            mask = pip.mask,
                            redness = pip.redness,
                            bounds = NormRect(
                                left = box.left.toFloat() / width,
                                top = box.top.toFloat() / height,
                                right = pip.bounds.right.toFloat() / width,
                                bottom = pip.bounds.bottom.toFloat() / height,
                            ),
                        ),
                    )
                }
            }
        }

        return samples
    }

    /**
     * Looks for the pip under a rank at a few different depths and keeps the best answer.
     *
     * Decks disagree about how much white sits between the index and its pip, and a crop that
     * clips the pip or swallows the card edge below it classifies badly. Trying three is cheap:
     * the region is a few thousand pixels.
     */
    private fun findPip(box: Rect, width: Int, height: Int, deck: DeckProfile?): PipDetection? {
        var best: PipDetection? = null
        for ((top, bottom) in PIP_EXTENTS) {
            val region = pipRegion(box, width, height, top, bottom) ?: continue
            val found = PipFinder.find(pixels, width, height, region, deck) ?: continue
            if (best == null || found.match.confidence > best!!.match.confidence) best = found
        }
        return best
    }

    /** A rank index is small, roughly upright, and never spans the frame. */
    private fun plausibleIndex(box: Rect, width: Int, height: Int): Boolean {
        val w = box.width()
        val h = box.height()
        if (w < 6 || h < 8) return false
        if (h > height * 0.35f || w > width * 0.35f) return false
        return w <= h * 2.2f
    }

    /**
     * The strip of card directly under the rank, which is where the corner pip is printed.
     *
     * Widened sideways because a heart is wider than a "7".
     */
    private fun pipRegion(box: Rect, width: Int, height: Int, top: Float, bottom: Float): ImageRect? {
        val h = box.height()
        val pad = (box.width() * 0.45f).toInt()
        val region = ImageRect(
            left = box.left - pad,
            top = box.bottom + (h * top).toInt(),
            right = box.right + pad,
            bottom = box.bottom + (h * bottom).toInt(),
        ).clampTo(width, height)

        return if (region.width < 5 || region.height < 5) null else region
    }

    private companion object {
        /** (gap below the rank, depth of the crop), both as multiples of the rank's height. */
        val PIP_EXTENTS = listOf(0.02f to 1.55f, 0.02f to 1.10f, 0.12f to 2.10f)

        const val MIN_MARGIN = 0.02
        const val MIN_CONFIDENCE = 0.45
    }
}

/** Rotates the frame upright so the recogniser and the pip finder see the same picture. */
private fun ImageProxy.toUprightBitmap(): Bitmap? {
    val source = runCatching { toBitmap() }.getOrNull() ?: return null
    val degrees = imageInfo.rotationDegrees
    if (degrees == 0) return source

    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    return runCatching {
        Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }.getOrNull()
}
