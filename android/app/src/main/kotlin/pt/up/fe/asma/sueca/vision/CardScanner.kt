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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** A box in image space, expressed as fractions so the overlay can be drawn at any size. */
data class NormRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

/** One card read out of a single frame. */
data class DetectedCard(
    val card: Card,
    val confidence: Double,
    val bounds: NormRect,
    val suitMargin: Double,
)

/** Everything one frame produced, plus the cards that have been seen often enough to trust. */
data class ScanFrame(
    val detections: List<DetectedCard> = emptyList(),
    val stable: List<ScannedCard> = emptyList(),
    val frames: Long = 0,
    /** Size of the analysed frame, so the overlay can line its boxes up with the preview. */
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
)

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
 *     with.
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

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val accumulator = ScanAccumulator(requiredSightings = 3, memoryMillis = 2_500L)
    private val lock = Any()
    private var frameCount = 0L

    /** Reused between frames; a 640x480 frame is a megabyte of ints to hand the collector. */
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
                publish(readCards(text, upright), upright.width, upright.height)
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

    private fun publish(detections: List<DetectedCard>, width: Int, height: Int) {
        val now = System.currentTimeMillis()
        val stable = synchronized(lock) {
            detections.forEach { accumulator.observe(it.card, it.confidence, now) }
            accumulator.stable(now)
        }
        frameCount++
        onFrame(ScanFrame(detections, stable, frameCount, width, height))
    }

    private fun readCards(text: Text, bitmap: Bitmap): List<DetectedCard> {
        if (text.textBlocks.isEmpty()) return emptyList()

        val width = bitmap.width
        val height = bitmap.height
        if (pixels.size < width * height) pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val found = LinkedHashMap<Card, DetectedCard>()

        for (block in text.textBlocks) {
            for (line in block.lines) {
                for (element in line.elements) {
                    val box = element.boundingBox ?: continue
                    if (!plausibleIndex(box, width, height)) continue

                    val reading = RankReader.read(element.text) ?: continue
                    val region = pipRegion(box, width, height) ?: continue
                    val pip = PipFinder.find(pixels, width, height, region) ?: continue

                    // A pip that matched no better than the runner up is not worth reporting.
                    if (pip.match.margin < 0.02) continue

                    val card = Card(pip.match.suit, reading.rank)
                    val confidence = reading.confidence * pip.match.confidence
                    val detection = DetectedCard(
                        card = card,
                        confidence = confidence,
                        bounds = NormRect(
                            left = box.left.toFloat() / width,
                            top = box.top.toFloat() / height,
                            right = pip.bounds.right.toFloat() / width,
                            bottom = pip.bounds.bottom.toFloat() / height,
                        ),
                        suitMargin = pip.match.margin,
                    )

                    val existing = found[card]
                    if (existing == null || existing.confidence < confidence) found[card] = detection
                }
            }
        }

        return found.values.toList()
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
     * Widened sideways because a heart is wider than a "7", and taken a little over one rank
     * height tall, which covers the gap plus the pip itself.
     */
    private fun pipRegion(box: Rect, width: Int, height: Int): ImageRect? {
        val h = box.height()
        val pad = (box.width() * 0.45f).toInt()
        val region = ImageRect(
            left = box.left - pad,
            top = box.bottom + (h * 0.02f).toInt(),
            right = box.right + pad,
            bottom = box.bottom + (h * 1.55f).toInt(),
        ).clampTo(width, height)

        return if (region.width < 5 || region.height < 5) null else region
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
