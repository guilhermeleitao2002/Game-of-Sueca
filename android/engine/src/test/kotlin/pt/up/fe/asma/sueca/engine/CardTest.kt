package pt.up.fe.asma.sueca.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardTest {

    @Test
    fun `deck has forty distinct cards worth one hundred and twenty points`() {
        assertEquals(40, FULL_DECK.size)
        assertEquals(40, FULL_DECK.toSet().size)
        assertEquals(TOTAL_POINTS, FULL_DECK.sumOf { it.points })
    }

    @Test
    fun `trick order is the sueca one, not the point order`() {
        val expected = listOf("2", "3", "4", "5", "6", "Q", "J", "K", "7", "A")
        assertEquals(expected, Rank.entries.map { it.label })

        val seven = Card(Suit.HEARTS, Rank.SEVEN)
        val king = Card(Suit.HEARTS, Rank.KING)
        val ace = Card(Suit.HEARTS, Rank.ACE)

        assertTrue("a 7 beats a king", seven.beats(king, Suit.SPADES))
        assertTrue("an ace beats a 7", ace.beats(seven, Suit.SPADES))
        assertTrue("and the 7 is also worth more points than the king", seven.points > king.points)
    }

    @Test
    fun `a card of the leading suit never beats a round that was cut`() {
        val trump = Suit.SPADES
        val cut = Card(Suit.SPADES, Rank.TWO)
        val ace = Card(Suit.HEARTS, Rank.ACE)

        assertFalse("the ace of the led suit loses to the smallest trump", ace.beats(cut, trump))
        assertTrue("but a bigger trump takes it", Card(Suit.SPADES, Rank.THREE).beats(cut, trump))
        assertFalse("and an off suit card takes nothing", Card(Suit.CLUBS, Rank.ACE).beats(ace, trump))
    }

    @Test
    fun `card indices round trip`() {
        for (card in FULL_DECK) {
            assertEquals(card, Card.fromIndex(card.index))
            assertEquals(card, Card.parse(card.id))
            assertEquals(card, Card.parse(card.label))
        }
        assertNull(Card.parse("Z of nothing"))
    }

    @Test
    fun `points table matches the rules`() {
        assertEquals(11, Rank.ACE.points)
        assertEquals(10, Rank.SEVEN.points)
        assertEquals(4, Rank.KING.points)
        assertEquals(3, Rank.JACK.points)
        assertEquals(2, Rank.QUEEN.points)
        assertEquals(0, Rank.TWO.points + Rank.THREE.points + Rank.FOUR.points + Rank.FIVE.points + Rank.SIX.points)
    }
}
