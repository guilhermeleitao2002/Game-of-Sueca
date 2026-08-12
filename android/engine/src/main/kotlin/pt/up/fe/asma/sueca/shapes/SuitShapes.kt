package pt.up.fe.asma.sueca.shapes

import pt.up.fe.asma.sueca.engine.Suit
import kotlin.math.max
import kotlin.math.min

/**
 * The four suit symbols as vector outlines in a unit box, y pointing down.
 *
 * There is exactly one definition of what a heart looks like in this project, and both users of
 * it read from here: the app turns these into `androidx.compose.ui.graphics.Path` to draw the
 * cards, and [ShapeRasterizer] turns them into binary masks that the camera pipeline matches
 * detected pips against. A card the app draws and a card the app recognises therefore agree by
 * construction.
 */
sealed interface PathCommand {
    data class MoveTo(val x: Float, val y: Float) : PathCommand
    data class LineTo(val x: Float, val y: Float) : PathCommand
    data class CubicTo(
        val x1: Float, val y1: Float,
        val x2: Float, val y2: Float,
        val x3: Float, val y3: Float,
    ) : PathCommand

    data object Close : PathCommand
}

/**
 * One closed outline. Shapes are kept as separate sub paths that are unioned rather than as one
 * wound path, so the rasterizer never has to care about winding direction.
 */
data class SubPath(val commands: List<PathCommand>)

data class ShapeSpec(val subPaths: List<SubPath>)

object SuitShapes {

    /** Bezier circle constant: how far the control points sit from the axis crossings. */
    private const val KAPPA = 0.5522848f

    fun of(suit: Suit): ShapeSpec = when (suit) {
        Suit.HEARTS -> HEART
        Suit.DIAMONDS -> DIAMOND
        Suit.CLUBS -> CLUB
        Suit.SPADES -> SPADE
    }

    private fun circle(cx: Float, cy: Float, r: Float): SubPath {
        val o = r * KAPPA
        return SubPath(
            listOf(
                PathCommand.MoveTo(cx, cy - r),
                PathCommand.CubicTo(cx + o, cy - r, cx + r, cy - o, cx + r, cy),
                PathCommand.CubicTo(cx + r, cy + o, cx + o, cy + r, cx, cy + r),
                PathCommand.CubicTo(cx - o, cy + r, cx - r, cy + o, cx - r, cy),
                PathCommand.CubicTo(cx - r, cy - o, cx - o, cy - r, cx, cy - r),
                PathCommand.Close,
            ),
        )
    }

    private fun polygon(vararg points: Float): SubPath {
        val commands = mutableListOf<PathCommand>()
        commands.add(PathCommand.MoveTo(points[0], points[1]))
        var i = 2
        while (i < points.size) {
            commands.add(PathCommand.LineTo(points[i], points[i + 1]))
            i += 2
        }
        commands.add(PathCommand.Close)
        return SubPath(commands)
    }

    private val HEART = ShapeSpec(
        listOf(
            SubPath(
                listOf(
                    PathCommand.MoveTo(0.50f, 0.98f),
                    PathCommand.CubicTo(0.50f, 0.98f, 0.02f, 0.62f, 0.02f, 0.33f),
                    PathCommand.CubicTo(0.02f, 0.12f, 0.18f, 0.02f, 0.31f, 0.02f),
                    PathCommand.CubicTo(0.41f, 0.02f, 0.47f, 0.09f, 0.50f, 0.15f),
                    PathCommand.CubicTo(0.53f, 0.09f, 0.59f, 0.02f, 0.69f, 0.02f),
                    PathCommand.CubicTo(0.82f, 0.02f, 0.98f, 0.12f, 0.98f, 0.33f),
                    PathCommand.CubicTo(0.98f, 0.62f, 0.50f, 0.98f, 0.50f, 0.98f),
                    PathCommand.Close,
                ),
            ),
        ),
    )

    private val DIAMOND = ShapeSpec(
        listOf(
            SubPath(
                listOf(
                    PathCommand.MoveTo(0.50f, 0.01f),
                    PathCommand.CubicTo(0.62f, 0.22f, 0.78f, 0.40f, 0.93f, 0.50f),
                    PathCommand.CubicTo(0.78f, 0.60f, 0.62f, 0.78f, 0.50f, 0.99f),
                    PathCommand.CubicTo(0.38f, 0.78f, 0.22f, 0.60f, 0.07f, 0.50f),
                    PathCommand.CubicTo(0.22f, 0.40f, 0.38f, 0.22f, 0.50f, 0.01f),
                    PathCommand.Close,
                ),
            ),
        ),
    )

    private val CLUB = ShapeSpec(
        listOf(
            circle(0.50f, 0.24f, 0.22f),
            circle(0.24f, 0.60f, 0.22f),
            circle(0.76f, 0.60f, 0.22f),
            polygon(0.44f, 0.56f, 0.38f, 0.99f, 0.62f, 0.99f, 0.56f, 0.56f),
        ),
    )

    private val SPADE = ShapeSpec(
        listOf(
            SubPath(
                listOf(
                    PathCommand.MoveTo(0.50f, 0.02f),
                    PathCommand.CubicTo(0.30f, 0.26f, 0.04f, 0.40f, 0.04f, 0.61f),
                    PathCommand.CubicTo(0.04f, 0.79f, 0.19f, 0.89f, 0.33f, 0.89f),
                    PathCommand.CubicTo(0.42f, 0.89f, 0.49f, 0.85f, 0.50f, 0.78f),
                    PathCommand.CubicTo(0.51f, 0.85f, 0.58f, 0.89f, 0.67f, 0.89f),
                    PathCommand.CubicTo(0.81f, 0.89f, 0.96f, 0.79f, 0.96f, 0.61f),
                    PathCommand.CubicTo(0.96f, 0.40f, 0.70f, 0.26f, 0.50f, 0.02f),
                    PathCommand.Close,
                ),
            ),
            polygon(0.44f, 0.74f, 0.37f, 0.99f, 0.63f, 0.99f, 0.56f, 0.74f),
        ),
    )
}

/**
 * Fills [ShapeSpec]s into binary masks.
 *
 * Deliberately tiny: cubics are flattened into short segments and each sub path is scanline
 * filled on its own, then unioned. That is enough for the 32x32 templates the suit classifier
 * compares against, and it keeps the whole thing dependency free and unit testable.
 */
object ShapeRasterizer {

    private const val FLATTEN_STEPS = 20

    fun rasterize(shape: ShapeSpec, width: Int, height: Int): BooleanArray {
        val mask = BooleanArray(width * height)
        for (subPath in shape.subPaths) {
            fill(flatten(subPath, width, height), width, height, mask)
        }
        return mask
    }

    /** Turns a sub path into a closed polygon in pixel space. */
    private fun flatten(subPath: SubPath, width: Int, height: Int): FloatArray {
        val points = ArrayList<Float>(64)
        var cursorX = 0f
        var cursorY = 0f
        var startX = 0f
        var startY = 0f

        fun push(x: Float, y: Float) {
            points.add(x * width)
            points.add(y * height)
        }

        for (command in subPath.commands) {
            when (command) {
                is PathCommand.MoveTo -> {
                    cursorX = command.x
                    cursorY = command.y
                    startX = cursorX
                    startY = cursorY
                    push(cursorX, cursorY)
                }

                is PathCommand.LineTo -> {
                    cursorX = command.x
                    cursorY = command.y
                    push(cursorX, cursorY)
                }

                is PathCommand.CubicTo -> {
                    val x0 = cursorX
                    val y0 = cursorY
                    for (step in 1..FLATTEN_STEPS) {
                        val t = step.toFloat() / FLATTEN_STEPS
                        val u = 1f - t
                        val x = u * u * u * x0 + 3f * u * u * t * command.x1 +
                            3f * u * t * t * command.x2 + t * t * t * command.x3
                        val y = u * u * u * y0 + 3f * u * u * t * command.y1 +
                            3f * u * t * t * command.y2 + t * t * t * command.y3
                        push(x, y)
                    }
                    cursorX = command.x3
                    cursorY = command.y3
                }

                PathCommand.Close -> {
                    push(startX, startY)
                    cursorX = startX
                    cursorY = startY
                }
            }
        }

        return points.toFloatArray()
    }

    /** Even-odd scanline fill of a single closed polygon. */
    private fun fill(polygon: FloatArray, width: Int, height: Int, mask: BooleanArray) {
        val count = polygon.size / 2
        if (count < 3) return

        val crossings = FloatArray(count)
        for (row in 0 until height) {
            val y = row + 0.5f
            var found = 0

            var i = 0
            var j = count - 1
            while (i < count) {
                val yi = polygon[i * 2 + 1]
                val yj = polygon[j * 2 + 1]
                if ((yi > y) != (yj > y)) {
                    val xi = polygon[i * 2]
                    val xj = polygon[j * 2]
                    crossings[found++] = xi + (y - yi) / (yj - yi) * (xj - xi)
                }
                j = i
                i++
            }
            if (found < 2) continue

            java.util.Arrays.sort(crossings, 0, found)
            var k = 0
            while (k + 1 < found) {
                val from = max(0, kotlin.math.ceil(crossings[k] - 0.5f).toInt())
                val to = min(width - 1, kotlin.math.floor(crossings[k + 1] - 0.5f).toInt())
                for (column in from..to) mask[row * width + column] = true
                k += 2
            }
        }
    }
}
