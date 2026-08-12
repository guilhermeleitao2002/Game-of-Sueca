package pt.up.fe.asma.sueca.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GameTest {

    private fun dealtGame(seed: Long = 7L, agent: AgentKind = AgentKind.GREEDY): Game =
        Game(agent, agent, EngineConfig.SIMULATION, Random(seed)).apply { deal() }

    @Test
    fun `dealing gives four hands of ten and names a trump`() {
        val game = dealtGame()

        assertEquals(4, game.players.size)
        game.players.forEach { assertEquals(CARDS_PER_HAND, it.hand.size) }
        assertNotNull(game.trump)

        val all = game.players.flatMap { it.hand }
        assertEquals("every card is dealt exactly once", 40, all.toSet().size)
        assertEquals(TOTAL_POINTS, game.teams.sumOf { it.initialPoints })
    }

    @Test
    fun `hands stay sorted weakest first`() {
        val game = dealtGame()
        game.players.forEach { player ->
            assertEquals(player.hand.sortedBy { it.order }, player.hand)
        }
    }

    @Test
    fun `seating alternates between the teams`() {
        repeat(20) { seed ->
            val game = dealtGame(seed.toLong())
            val teams = game.playersOrder.map { it.team.id }
            assertEquals(teams[0], teams[2])
            assertEquals(teams[1], teams[3])
            assertTrue(teams[0] != teams[1])
        }
    }

    @Test
    fun `a full game always distributes exactly one hundred and twenty points`() {
        for (seed in 1L..30L) {
            val game = dealtGame(seed, AgentKind.MAX_POINTS)
            game.playToCompletion()

            assertTrue(game.isFinished)
            assertEquals(ROUNDS_PER_GAME, game.tricks.size)
            assertEquals(TOTAL_POINTS, game.teams.sumOf { it.score })
        }
    }

    @Test
    fun `following suit is enforced`() {
        val game = dealtGame(3L)
        while (!game.isFinished) {
            val roundSuit = game.roundSuit
            val legal = game.legalCards()
            if (roundSuit != null && game.currentPlayer.cardsOfSuit(roundSuit).isNotEmpty()) {
                assertTrue("must follow suit", legal.all { it.suit == roundSuit })
            }
            game.playAgentTurn()
        }
    }

    @Test
    fun `the winner of a trick leads the next one`() {
        val game = dealtGame(11L)
        while (!game.isFinished) {
            val (_, trick) = game.playAgentTurn()
            if (trick != null && !game.isFinished) {
                assertSame(trick.winner, game.currentPlayer)
            }
        }
    }

    @Test
    fun `rotating to the winner never reseats anybody`() {
        // The app works out where to draw each seat once, from the opening order, and relies on
        // the table never being reshuffled underneath it.
        val game = dealtGame(21L)
        val opening = game.playersOrder.map { it.id }

        while (!game.isFinished) {
            game.playAgentTurn()
            val current = game.playersOrder.map { it.id }
            val rotation = opening.indexOf(current.first())
            assertEquals(
                "the order stopped being a rotation of the opening one",
                opening.drop(rotation) + opening.take(rotation),
                current,
            )
        }
    }

    @Test
    fun `evaluate round scores partial tricks and indexes into the cards given`() {
        val game = dealtGame()
        val trump = game.trumpSuit
        val other = Suit.entries.first { it != trump }

        val partial = listOf(Card(other, Rank.KING), Card(other, Rank.ACE))
        val result = game.evaluateRound(partial)
        assertEquals(15, result.points)
        assertEquals(1, result.winner)
        assertEquals(Card(other, Rank.ACE), result.card)

        val cut = game.evaluateRound(partial + Card(trump, Rank.TWO))
        assertEquals("the cut takes it", 2, cut.winner)
    }

    @Test
    fun `the game result names the higher score`() {
        val game = dealtGame(5L)
        game.playToCompletion()
        val result = game.result()
        val sporting = result.scores.getValue(TeamId.SPORTING)
        val benfica = result.scores.getValue(TeamId.BENFICA)

        when {
            sporting > benfica -> assertEquals(TeamId.SPORTING, result.winner)
            benfica > sporting -> assertEquals(TeamId.BENFICA, result.winner)
            else -> assertNull(result.winner)
        }
    }
}
