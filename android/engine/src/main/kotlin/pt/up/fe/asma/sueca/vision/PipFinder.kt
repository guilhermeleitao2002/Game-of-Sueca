package pt.up.fe.asma.sueca.vision

import kotlin.math.max
import kotlin.math.min

data class ImageRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {

    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val isEmpty: Boolean get() = width <= 0 || height <= 0

    fun clampTo(imageWidth: Int, imageHeight: Int) = ImageRect(
        left = left.coerceIn(0, imageWidth),
        top = top.coerceIn(0, imageHeight),
        right = right.coerceIn(0, imageWidth),
        bottom = bottom.coerceIn(0, imageHeight),
    )

    fun expand(dx: Int, dy: Int) = ImageRect(left - dx, top - dy, right + dx, bottom + dy)

    fun translate(dx: Int, dy: Int) = ImageRect(left + dx, top + dy, right + dx, bottom + dy)
}

/** A pip that was segmented out of a frame, together with what the classifier made of it. */
data class PipDetection(
    val match: SuitMatch,
    /** true for red ink, false for black, null when the colour was inconclusive. */
    val red: Boolean?,
    val bounds: ImageRect,
    val inkPixels: Int,
)

/**
 * Finds the suit pip inside a region of a frame.
 *
 * Classical computer vision on purpose: the region handed in is small (the index corner of one
 * card, located by the text recogniser), the ink is high contrast against the card, and a
 * threshold plus a connected component is both far faster and far more predictable than a
 * neural network would be on a device holding a live camera.
 */
object PipFinder {

    /** Ink is anything meaningfully darker than the card it sits on. */
    private const val DARK_RATIO = 0.72

    /** Red ink is bright, so it needs its own, gentler, threshold. */
    private const val RED_RATIO = 0.94
    private const val RED_DOMINANCE = 38

    private const val MIN_INK_PIXELS = 12
    private const val MAX_INK_FRACTION = 0.80

    fun find(pixels: IntArray, imageWidth: Int, imageHeight: Int, region: ImageRect): PipDetection? {
        val box = region.clampTo(imageWidth, imageHeight)
        if (box.width < 5 || box.height < 5) return null

        val width = box.width
        val height = box.height
        val luma = IntArray(width * height)
        val redness = IntArray(width * height)

        for (y in 0 until height) {
            val sourceRow = (box.top + y) * imageWidth
            for (x in 0 until width) {
                val argb = pixels[sourceRow + box.left + x]
                val r = (argb shr 16) and 0xFF
                val g = (argb shr 8) and 0xFF
                val b = argb and 0xFF
                luma[y * width + x] = (299 * r + 587 * g + 114 * b) / 1000
                redness[y * width + x] = r - max(g, b)
            }
        }

        val background = borderMedian(luma, width, height)
        if (background <= 0) return null

        val darkLimit = (background * DARK_RATIO).toInt()
        val redLimit = (background * RED_RATIO).toInt()
        val ink = BooleanArray(width * height) { index ->
            luma[index] < darkLimit || (redness[index] >= RED_DOMINANCE && luma[index] < redLimit)
        }

        val component = largestComponent(ink, width, height) ?: return null
        if (component.pixels < MIN_INK_PIXELS) return null
        if (component.pixels > MAX_INK_FRACTION * width * height) return null

        val bounds = component.bounds
        val maskWidth = bounds.width
        val maskHeight = bounds.height
        if (maskWidth < 3 || maskHeight < 3) return null

        val mask = BooleanArray(maskWidth * maskHeight)
        var rednessSum = 0L
        for (y in 0 until maskHeight) {
            for (x in 0 until maskWidth) {
                val index = (bounds.top + y) * width + (bounds.left + x)
                if (component.labels[index] == component.id) {
                    mask[y * maskWidth + x] = true
                    rednessSum += redness[index]
                }
            }
        }

        val meanRedness = rednessSum.toDouble() / component.pixels
        val red = when {
            meanRedness >= 30 -> true
            meanRedness <= 12 -> false
            else -> null
        }

        val match = SuitClassifier.classify(mask, maskWidth, maskHeight, red) ?: return null

        return PipDetection(
            match = match,
            red = red,
            bounds = bounds.translate(box.left, box.top),
            inkPixels = component.pixels,
        )
    }

    private class Component(val labels: IntArray, val id: Int, val pixels: Int, val bounds: ImageRect)

    /** Iterative 4-connected flood fill; the biggest blob that is not glued to the border wins. */
    private fun largestComponent(ink: BooleanArray, width: Int, height: Int): Component? {
        val labels = IntArray(ink.size) { -1 }
        val stack = IntArray(ink.size)
        var nextId = 0
        var bestId = -1
        var bestScore = 0.0
        var bestPixels = 0
        var bestBounds = ImageRect(0, 0, 0, 0)

        for (start in ink.indices) {
            if (!ink[start] || labels[start] >= 0) continue

            val id = nextId++
            var top = 0
            stack[top++] = start
            labels[start] = id

            var count = 0
            var left = width
            var right = -1
            var upper = height
            var lower = -1
            var touchesBorder = 0

            while (top > 0) {
                val index = stack[--top]
                count++

                val x = index % width
                val y = index / width
                if (x < left) left = x
                if (x > right) right = x
                if (y < upper) upper = y
                if (y > lower) lower = y
                if (x == 0 || y == 0 || x == width - 1 || y == height - 1) touchesBorder++

                if (x > 0 && ink[index - 1] && labels[index - 1] < 0) {
                    labels[index - 1] = id; stack[top++] = index - 1
                }
                if (x < width - 1 && ink[index + 1] && labels[index + 1] < 0) {
                    labels[index + 1] = id; stack[top++] = index + 1
                }
                if (y > 0 && ink[index - width] && labels[index - width] < 0) {
                    labels[index - width] = id; stack[top++] = index - width
                }
                if (y < height - 1 && ink[index + width] && labels[index + width] < 0) {
                    labels[index + width] = id; stack[top++] = index + width
                }
            }

            // A blob running along the edge of the crop is usually the card border, not a pip.
            val perimeter = 2 * (width + height)
            val penalty = 1.0 - min(0.9, touchesBorder.toDouble() / perimeter * 2.0)
            val score = count * penalty
            if (score > bestScore) {
                bestScore = score
                bestId = id
                bestPixels = count
                bestBounds = ImageRect(left, upper, right + 1, lower + 1)
            }
        }

        if (bestId < 0) return null
        return Component(labels, bestId, bestPixels, bestBounds)
    }

    private fun borderMedian(luma: IntArray, width: Int, height: Int): Int {
        val samples = ArrayList<Int>((width + height) * 2)
        for (x in 0 until width) {
            samples.add(luma[x])
            samples.add(luma[(height - 1) * width + x])
        }
        for (y in 0 until height) {
            samples.add(luma[y * width])
            samples.add(luma[y * width + width - 1])
        }
        if (samples.isEmpty()) return 0
        samples.sort()
        return samples[samples.size / 2]
    }
}
