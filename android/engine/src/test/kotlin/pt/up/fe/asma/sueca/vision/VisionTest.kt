package pt.up.fe.asma.sueca.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.up.fe.asma.sueca.engine.Card
import pt.up.fe.asma.sueca.engine.Rank
import pt.up.fe.asma.sueca.engine.Suit
import pt.up.fe.asma.sueca.shapes.ShapeRasterizer
import pt.up.fe.asma.sueca.shapes.SuitShapes
import kotlin.random.Random

class ShapeTest {

    @Test
    fun `every suit rasterises to a sane amount of ink`() {
        for (suit in Suit.entries) {
            val mask = ShapeRasterizer.rasterize(SuitShapes.of(suit), 64, 64)
            val ink = mask.count { it }
            val fill = ink.toDouble() / mask.size
            assertTrue("${suit.id} filled $fill of its box", fill in 0.25..0.85)
        }
    }

    @Test
    fun `the four outlines are actually different shapes`() {
        val masks = Suit.entries.associateWith { ShapeRasterizer.rasterize(SuitShapes.of(it), 32, 32) }
        for (a in Suit.entries) {
            for (b in Suit.entries) {
                if (a >= b) continue
                val shared = masks.getValue(a).indices.count { masks.getValue(a)[it] && masks.getValue(b)[it] }
                val union = masks.getValue(a).indices.count { masks.getValue(a)[it] || masks.getValue(b)[it] }
                val overlap = shared.toDouble() / union
                assertTrue("${a.id} and ${b.id} overlap $overlap", overlap < 0.9)
            }
        }
    }

    @Test
    fun `the features pick out what makes each suit recognisable`() {
        fun features(suit: Suit) = SuitClassifier.featuresOf(
            ShapeRasterizer.rasterize(SuitShapes.of(suit), 64, 64), 64, 64,
        )

        // Feature layout: [fill, (runs, width) per probe height at 8/25/50/75/92%, centroid].
        val heartRunsAtTop = features(Suit.HEARTS)[1]
        val diamondRunsAtTop = features(Suit.DIAMONDS)[1]
        assertTrue("a heart is the only suit with a notch at the top", heartRunsAtTop > diamondRunsAtTop)

        val clubRunsAtMiddle = features(Suit.CLUBS)[5]
        val spadeRunsAtMiddle = features(Suit.SPADES)[5]
        assertTrue("a club is two separate lobes across the middle", clubRunsAtMiddle > spadeRunsAtMiddle)
    }
}

class SuitClassifierTest {

    private fun render(suit: Suit, size: Int) = ShapeRasterizer.rasterize(SuitShapes.of(suit), size, size)

    @Test
    fun `each suit is recognised from its own outline at any size`() {
        for (suit in Suit.entries) {
            for (size in listOf(14, 20, 32, 48, 90)) {
                val match = SuitClassifier.classify(render(suit, size), size, size, suit.isRed)
                assertNotNull(match)
                assertEquals("$suit at ${size}px", suit, match!!.suit)
            }
        }
    }

    @Test
    fun `recognition survives being squashed, stretched and speckled`() {
        val random = Random(17)
        for (suit in Suit.entries) {
            val width = 30
            val height = 44                                     // the aspect a real pip is printed at
            val mask = ShapeRasterizer.rasterize(SuitShapes.of(suit), width, height)

            // Flip a few pixels, the way a threshold on a real photo does.
            repeat(60) {
                val index = random.nextInt(mask.size)
                mask[index] = !mask[index]
            }

            val match = SuitClassifier.classify(mask, width, height, suit.isRed)!!
            assertEquals(suit, match.suit)
            assertTrue("confidence was ${match.confidence}", match.confidence > 0.6)
        }
    }

    @Test
    fun `without the colour hint the suits are still told apart`() {
        for (suit in Suit.entries) {
            val match = SuitClassifier.classify(render(suit, 36), 36, 36, redInk = null)!!
            assertEquals(suit, match.suit)
        }
    }

    @Test
    fun `an empty mask classifies as nothing at all`() {
        assertNull(SuitClassifier.classify(BooleanArray(32 * 32), 32, 32, null))
    }
}

class PipFinderTest {

    private val white = 0xFFFFFFFF.toInt()

    /** Paints a suit pip onto a white card sized patch, the way a phone camera would see one. */
    private fun frame(
        suit: Suit,
        imageWidth: Int = 60,
        imageHeight: Int = 84,
        pip: ImageRect = ImageRect(14, 20, 46, 66),
        ink: Int = if (suit.isRed) 0xFFC81E1E.toInt() else 0xFF141414.toInt(),
        noise: Int = 0,
    ): IntArray {
        val pixels = IntArray(imageWidth * imageHeight) { white }
        val mask = ShapeRasterizer.rasterize(SuitShapes.of(suit), pip.width, pip.height)
        for (y in 0 until pip.height) {
            for (x in 0 until pip.width) {
                if (mask[y * pip.width + x]) pixels[(pip.top + y) * imageWidth + pip.left + x] = ink
            }
        }
        if (noise > 0) {
            val random = Random(5)
            repeat(noise) {
                val index = random.nextInt(pixels.size)
                val shade = 200 + random.nextInt(56)
                pixels[index] = (0xFF shl 24) or (shade shl 16) or (shade shl 8) or shade
            }
        }
        return pixels
    }

    @Test
    fun `a painted pip is found and classified`() {
        for (suit in Suit.entries) {
            val pixels = frame(suit)
            val detection = PipFinder.find(pixels, 60, 84, ImageRect(0, 0, 60, 84))
            assertNotNull("nothing found for ${suit.id}", detection)
            assertEquals(suit, detection!!.match.suit)
            assertEquals(suit.isRed, detection.red)
            assertTrue(detection.inkPixels > 100)
        }
    }

    @Test
    fun `the colour of the ink is read off the pixels`() {
        val hearts = PipFinder.find(frame(Suit.HEARTS), 60, 84, ImageRect(0, 0, 60, 84))!!
        val spades = PipFinder.find(frame(Suit.SPADES), 60, 84, ImageRect(0, 0, 60, 84))!!

        assertEquals(true, hearts.red)
        assertEquals(false, spades.red)
    }

    @Test
    fun `a grubby frame still resolves`() {
        for (suit in Suit.entries) {
            val pixels = frame(suit, noise = 120)
            val detection = PipFinder.find(pixels, 60, 84, ImageRect(0, 0, 60, 84))!!
            assertEquals(suit, detection.match.suit)
        }
    }

    @Test
    fun `a blank patch yields nothing`() {
        val pixels = IntArray(60 * 84) { white }
        assertNull(PipFinder.find(pixels, 60, 84, ImageRect(0, 0, 60, 84)))
    }

    @Test
    fun `the detected bounds are reported in image coordinates`() {
        val pixels = frame(Suit.SPADES)
        val detection = PipFinder.find(pixels, 60, 84, ImageRect(6, 10, 56, 78))!!

        assertTrue(detection.bounds.left >= 6)
        assertTrue(detection.bounds.top >= 10)
        assertTrue(detection.bounds.right <= 56)
        assertTrue(detection.bounds.bottom <= 78)
    }
}

class RankReaderTest {

    @Test
    fun `the ranks of a sueca deck are read`() {
        val expected = mapOf(
            "A" to Rank.ACE, "2" to Rank.TWO, "3" to Rank.THREE, "4" to Rank.FOUR,
            "5" to Rank.FIVE, "6" to Rank.SIX, "7" to Rank.SEVEN,
            "J" to Rank.JACK, "Q" to Rank.QUEEN, "K" to Rank.KING,
        )
        expected.forEach { (text, rank) ->
            assertEquals(rank, RankReader.read(text)?.rank)
            assertEquals(1.0, RankReader.read(text)!!.confidence, 1e-9)
        }
    }

    @Test
    fun `portuguese face cards are read too`() {
        assertEquals(Rank.KING, RankReader.read("R")?.rank)
        assertEquals(Rank.QUEEN, RankReader.read("D")?.rank)
        assertEquals(Rank.JACK, RankReader.read("V")?.rank)
        assertEquals(Rank.ACE, RankReader.read("as")?.rank)
    }

    @Test
    fun `cards that are not in a sueca deck are rejected rather than guessed`() {
        listOf("10", "8", "9", "0", "1", "", "  ", "%%").forEach {
            assertNull("$it should not read as a rank", RankReader.read(it))
        }
    }

    @Test
    fun `likely misreadings come back with lower confidence`() {
        val five = RankReader.read("S")!!
        assertEquals(Rank.FIVE, five.rank)
        assertTrue(five.confidence < 1.0)

        // Suit glyphs are dropped outright, so an index read whole is still a clean read.
        assertEquals(1.0, RankReader.read("K♠")!!.confidence, 1e-9)

        // A rank read together with the letters underneath it is a guess, and says so.
        val king = RankReader.read("KJQ")!!
        assertEquals(Rank.KING, king.rank)
        assertTrue(king.confidence < 1.0)
    }
}

class ScanAccumulatorTest {

    private val ace = Card(Suit.HEARTS, Rank.ACE)
    private val seven = Card(Suit.CLUBS, Rank.SEVEN)

    @Test
    fun `a card has to be seen a few times before it is offered`() {
        val accumulator = ScanAccumulator(requiredSightings = 3)

        accumulator.observe(ace, 0.9, now = 0)
        assertTrue(accumulator.stable(now = 0).isEmpty())

        accumulator.observe(ace, 0.9, now = 100)
        accumulator.observe(ace, 0.9, now = 200)
        assertEquals(listOf(ace), accumulator.stable(now = 200).map { it.card })
    }

    @Test
    fun `low confidence readings are ignored`() {
        val accumulator = ScanAccumulator(requiredSightings = 1, minConfidence = 0.7)
        accumulator.observe(ace, 0.4, now = 0)
        assertTrue(accumulator.stable(now = 0).isEmpty())
    }

    @Test
    fun `detections fade once the camera moves away`() {
        val accumulator = ScanAccumulator(requiredSightings = 2, memoryMillis = 1_000)
        repeat(3) { accumulator.observe(ace, 0.9, now = it.toLong()) }
        assertEquals(1, accumulator.stable(now = 10).size)
        assertTrue(accumulator.stable(now = 5_000).isEmpty())
    }

    @Test
    fun `a rejected card does not come back`() {
        val accumulator = ScanAccumulator(requiredSightings = 1)
        accumulator.observe(ace, 0.9, now = 0)
        accumulator.reject(ace)
        accumulator.observe(ace, 0.95, now = 10)
        assertTrue(accumulator.stable(now = 10).isEmpty())

        accumulator.accept(ace)
        accumulator.observe(ace, 0.95, now = 20)
        assertEquals(listOf(ace), accumulator.stable(now = 20).map { it.card })
    }

    @Test
    fun `the most confident readings come first`() {
        val accumulator = ScanAccumulator(requiredSightings = 1)
        accumulator.observe(ace, 0.7, now = 0)
        accumulator.observe(seven, 0.95, now = 0)
        assertEquals(listOf(seven, ace), accumulator.stable(now = 0).map { it.card })
    }
}
