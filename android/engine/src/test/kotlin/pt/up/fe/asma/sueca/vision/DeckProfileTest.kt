package pt.up.fe.asma.sueca.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.up.fe.asma.sueca.engine.Rank
import pt.up.fe.asma.sueca.engine.Suit
import pt.up.fe.asma.sueca.shapes.ShapeRasterizer
import pt.up.fe.asma.sueca.shapes.SuitShapes

class DeckProfileTest {

    private fun pip(suit: Suit, size: Int = 40): BooleanArray {
        val raw = ShapeRasterizer.rasterize(SuitShapes.of(suit), size, size)
        return SuitClassifier.normalise(raw, size, size)!!
    }

    @Test
    fun `a trained pip overrides what the built in template would have said`() {
        // The awkward case a real deck creates: this deck's hearts are printed as something the
        // shipped outlines read as a diamond.
        val odd = pip(Suit.DIAMONDS)

        val untrained = SuitClassifier.classifyNormalised(odd, redInk = true)!!
        assertEquals(Suit.DIAMONDS, untrained.suit)
        assertFalse(untrained.fromProfile)

        val profile = DeckProfile("deck", "Copag")
        profile.learnPip(Suit.HEARTS, odd)

        val trained = SuitClassifier.classifyNormalised(odd, redInk = true, profile = profile)!!
        assertEquals(Suit.HEARTS, trained.suit)
        assertTrue("the match should be credited to the profile", trained.fromProfile)
    }

    @Test
    fun `training one suit leaves the others on the built in templates`() {
        val profile = DeckProfile("deck", "Copag")
        profile.learnPip(Suit.HEARTS, pip(Suit.HEARTS))

        for (suit in Suit.entries) {
            val match = SuitClassifier.classifyNormalised(pip(suit), suit.isRed, profile)!!
            assertEquals("$suit went wrong once hearts were trained", suit, match.suit)
        }
    }

    @Test
    fun `exemplars are capped and stay varied`() {
        val profile = DeckProfile("deck", "Copag")
        repeat(20) { profile.learnPip(Suit.SPADES, pip(Suit.SPADES, 20 + it)) }

        assertEquals(DeckProfile.MAX_EXEMPLARS, profile.pipsLearned(Suit.SPADES))
        assertEquals(0, profile.pipsLearned(Suit.CLUBS))
        assertFalse(profile.isUsable)
    }

    @Test
    fun `a profile becomes usable once every suit has been seen`() {
        val profile = DeckProfile("deck", "Copag")
        Suit.entries.forEach { profile.learnPip(it, pip(it)) }

        assertTrue(profile.isUsable)
        assertEquals(4, profile.totalPips)
        assertEquals(Suit.entries.associateWith { 1 }, profile.coverage())
    }

    @Test
    fun `a learned token beats even the built in rejections`() {
        // "O" normally means "not a Sueca card"; on a deck whose queen is drawn like one, it does not.
        assertNull(RankReader.read("O"))

        val profile = DeckProfile("deck", "Copag")
        profile.learnRankToken("O", Rank.QUEEN)
        assertEquals(Rank.QUEEN, RankReader.read("O", profile)?.rank)

        profile.learnRankToken("O", Rank.QUEEN)
        assertTrue("a twice confirmed token is trusted more", RankReader.read("O", profile)!!.confidence > 0.9)
    }

    @Test
    fun `token learning is case and punctuation insensitive`() {
        val profile = DeckProfile("deck", "Copag")
        profile.learnRankToken("q*", Rank.QUEEN)

        assertEquals(Rank.QUEEN, RankReader.read("Q*", profile)?.rank)
        assertEquals(Rank.QUEEN, RankReader.read(" q ", profile)?.rank)
    }

    @Test
    fun `the ink boundary is only learned once both colours have been seen a few times`() {
        val profile = DeckProfile("deck", "Copag")
        assertNull(profile.rednessThreshold())

        profile.learnPip(Suit.HEARTS, pip(Suit.HEARTS), redness = 60.0)
        profile.learnPip(Suit.DIAMONDS, pip(Suit.DIAMONDS), redness = 70.0)
        assertNull("still nothing black to compare against", profile.rednessThreshold())

        profile.learnPip(Suit.SPADES, pip(Suit.SPADES), redness = 4.0)
        profile.learnPip(Suit.CLUBS, pip(Suit.CLUBS), redness = 6.0)

        assertNotNull(profile.rednessThreshold())
        assertEquals("halfway between this deck's red and its black", 35.0, profile.rednessThreshold()!!, 0.01)
    }

    @Test
    fun `an unseparable deck keeps the built in ink thresholds`() {
        val profile = DeckProfile("deck", "Copag")
        listOf(Suit.HEARTS, Suit.DIAMONDS).forEach { profile.learnPip(it, pip(it), redness = 20.0) }
        listOf(Suit.SPADES, Suit.CLUBS).forEach { profile.learnPip(it, pip(it), redness = 19.0) }

        assertNull(profile.rednessThreshold())
    }

    @Test
    fun `a profile survives being written out and read back`() {
        val profile = DeckProfile("deck-7", "Fournier 25")
        Suit.entries.forEach { suit ->
            profile.learnPip(suit, pip(suit), redness = if (suit.isRed) 55.0 else 5.0)
        }
        profile.learnRankToken("O", Rank.QUEEN)
        profile.learnRankToken("O", Rank.QUEEN)
        profile.learnRankToken("l", Rank.JACK)

        val restored = DeckProfile.decode(profile.encode())!!

        assertEquals("deck-7", restored.id)
        assertEquals("Fournier 25", restored.name)
        assertEquals(profile.coverage(), restored.coverage())
        assertEquals(profile.rednessThreshold()!!, restored.rednessThreshold()!!, 1e-9)
        assertEquals(Rank.QUEEN to 2, restored.rankFor("O"))
        assertEquals(Rank.JACK to 1, restored.rankFor("L"))

        // And it still classifies the same way.
        for (suit in Suit.entries) {
            assertEquals(suit, SuitClassifier.classifyNormalised(pip(suit), suit.isRed, restored)!!.suit)
        }
    }

    @Test
    fun `nonsense does not decode into a profile`() {
        assertNull(DeckProfile.decode(""))
        assertNull(DeckProfile.decode("hello"))
        assertNull(DeckProfile.decode("{\"json\": true}"))
    }

    @Test
    fun `masks round trip through the hex encoding`() {
        val mask = pip(Suit.CLUBS)
        val encoded = DeckProfile.encodeMask(mask)

        assertEquals(SuitClassifier.TEMPLATE_SIZE * SuitClassifier.TEMPLATE_SIZE / 4, encoded.length)
        assertTrue(DeckProfile.decodeMask(encoded)!!.contentEquals(mask))
        assertNull(DeckProfile.decodeMask("too short"))
    }

    @Test
    fun `what the finder cuts out of a frame is what the profile can learn from`() {
        // The end to end shape of training: segment a pip from a photo, file it under the suit the
        // user says it is, and have the next frame come back with that answer.
        val width = 60
        val height = 84
        val white = 0xFFFFFFFF.toInt()
        val ink = 0xFFC81E1E.toInt()
        val pipBox = ImageRect(14, 20, 46, 66)

        val pixels = IntArray(width * height) { white }
        val shape = ShapeRasterizer.rasterize(SuitShapes.of(Suit.DIAMONDS), pipBox.width, pipBox.height)
        for (y in 0 until pipBox.height) {
            for (x in 0 until pipBox.width) {
                if (shape[y * pipBox.width + x]) pixels[(pipBox.top + y) * width + pipBox.left + x] = ink
            }
        }

        val whole = ImageRect(0, 0, width, height)
        val before = PipFinder.find(pixels, width, height, whole)!!
        assertEquals(Suit.DIAMONDS, before.match.suit)

        // "No, on my deck that is a heart."
        val profile = DeckProfile("deck", "Odd one")
        profile.learnPip(Suit.HEARTS, before.mask, before.redness)

        val after = PipFinder.find(pixels, width, height, whole, profile)!!
        assertEquals(Suit.HEARTS, after.match.suit)
        assertTrue(after.match.fromProfile)
        assertEquals("and the colour reading is unchanged", true, after.red)
    }

    @Test
    fun `a deck whose red ink is dull is still read as red once trained`() {
        val width = 60
        val height = 84
        val white = 0xFFFFFFFF.toInt()
        // A washed out red: 20 points of redness, inside the band where the built-in thresholds
        // refuse to call it either colour.
        val dull = 0xFF8A7673.toInt()
        val pipBox = ImageRect(14, 20, 46, 66)

        val pixels = IntArray(width * height) { white }
        val shape = ShapeRasterizer.rasterize(SuitShapes.of(Suit.HEARTS), pipBox.width, pipBox.height)
        for (y in 0 until pipBox.height) {
            for (x in 0 until pipBox.width) {
                if (shape[y * pipBox.width + x]) pixels[(pipBox.top + y) * width + pipBox.left + x] = dull
            }
        }

        val whole = ImageRect(0, 0, width, height)
        val before = PipFinder.find(pixels, width, height, whole)!!
        assertNull("neither red nor black by the built-in thresholds", before.red)

        val profile = DeckProfile("deck", "Faded")
        repeat(2) {
            profile.learnPip(Suit.HEARTS, before.mask, before.redness)
            profile.learnPip(Suit.SPADES, before.mask, 2.0)
        }

        val after = PipFinder.find(pixels, width, height, whole, profile)!!
        assertEquals("the learned boundary puts this deck's red on the red side", true, after.red)
    }

    @Test
    fun `a pip that is not the right size is refused`() {
        val profile = DeckProfile("deck", "Copag")
        val wrong = BooleanArray(10)
        val error = runCatching { profile.learnPip(Suit.HEARTS, wrong) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }
}
