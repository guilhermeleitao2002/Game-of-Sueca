package pt.up.fe.asma.sueca.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class AdvisorTest {

    private val trump = Card(Suit.SPADES, Rank.KING)

    private val hand = listOf(
        Card(Suit.HEARTS, Rank.ACE),
        Card(Suit.HEARTS, Rank.THREE),
        Card(Suit.DIAMONDS, Rank.SEVEN),
        Card(Suit.DIAMONDS, Rank.TWO),
        Card(Suit.CLUBS, Rank.KING),
        Card(Suit.CLUBS, Rank.FOUR),
        Card(Suit.CLUBS, Rank.FIVE),
        Card(Suit.SPADES, Rank.ACE),
        Card(Suit.SPADES, Rank.SIX),
        Card(Suit.SPADES, Rank.TWO),
    )

    private fun session(agent: AgentKind = AgentKind.DEFAULT): AdvisorSession =
        AdvisorSession(agent).apply { start(hand, trump, Seat.RIGHT, Seat.ME) }

    @Test
    fun `the advisor only knows its own hand`() {
        val session = session()

        assertEquals(10, session.me.hand.size)
        assertTrue(session.me.handKnown)
        session.game.players.filter { it !== session.me }.forEach {
            assertFalse("the other three hands are hidden", it.handKnown)
            assertTrue(it.hand.isEmpty())
        }
        assertEquals(30, session.unseenCards.size)
    }

    @Test
    fun `the trump card is known to be in the hand that turned it up`() {
        val session = session()
        assertEquals(1.0, session.me.beliefs.probability(Seat.RIGHT.playerId, trump), 1e-9)
        assertEquals(0.0, session.me.beliefs.probability(Seat.LEFT.playerId, trump), 1e-9)
    }

    @Test
    fun `seats sit in playing order around the table`() {
        val session = session()
        val order = session.game.playersOrder.map { Seat.ofPlayer(it) }

        assertEquals(listOf(Seat.ME, Seat.RIGHT, Seat.PARTNER, Seat.LEFT), order)
        assertEquals(Seat.PARTNER, Seat.ofPlayer(session.me.partner()!!))
        assertTrue(Seat.RIGHT.isOpponent && Seat.LEFT.isOpponent)
        assertEquals(
            "you and your partner are one team",
            session.me.team.id,
            session.game.playerWithId(Seat.PARTNER.playerId).team.id,
        )
    }

    @Test
    fun `a different leader rotates the table without changing the seating`() {
        val session = AdvisorSession().apply { start(hand, trump, Seat.RIGHT, Seat.PARTNER) }
        val order = session.game.playersOrder.map { Seat.ofPlayer(it) }

        assertEquals(listOf(Seat.PARTNER, Seat.LEFT, Seat.ME, Seat.RIGHT), order)
        assertEquals(Seat.PARTNER, session.currentSeat)
    }

    @Test
    fun `advice is legal, ranked and explained`() {
        val session = session()
        val advice = session.advise()!!

        assertTrue(advice.recommended in session.game.legalCards())
        assertEquals(session.me.hand.size, advice.evaluations.size)
        assertTrue(advice.reason.isNotBlank())
        assertEquals(AgentKind.PREDICTOR, advice.agent)

        val scores = advice.evaluations.map { it.score }
        assertEquals("evaluations come back best first", scores.sortedDescending(), scores)
    }

    @Test
    fun `every agent can advise from the same seat`() {
        for (agent in AgentKind.entries) {
            val session = session(agent)
            val advice = session.advise()!!
            assertEquals(agent, advice.agent)
            assertTrue("${agent.displayName} advised an illegal card", advice.recommended in session.me.hand)
        }
    }

    @Test
    fun `recording cards drives the table around and narrows the beliefs`() {
        val session = session()

        val myCard = session.advise()!!.recommended
        assertNull(session.record(myCard))
        assertEquals(Seat.RIGHT, session.currentSeat)
        assertFalse(session.isMyTurn)
        assertNull("no advice while it is somebody else's turn", session.advise())

        val roundSuit = session.game.roundSuit!!
        val theirCard = session.unseenCards.first { it.suit != roundSuit }
        session.record(theirCard)

        assertFalse(
            "failing to follow suit proves they are void",
            session.me.beliefs.holdsAnyOf(Seat.RIGHT.playerId, roundSuit),
        )

        session.record(session.unseenCards.first())
        val trick = session.record(session.unseenCards.first())
        assertNotNull("the fourth card closes the trick", trick)
        assertEquals(1, session.game.tricks.size)
        assertEquals(9, session.me.hand.size)
    }

    @Test
    fun `a session started from a partial hand runs out quietly instead of crashing`() {
        val short = hand.take(2)
        val session = AdvisorSession().apply { start(short, trump, Seat.RIGHT, Seat.ME) }

        var guard = 0
        while (!session.game.isFinished && guard++ < 100) {
            val card = if (session.isMyTurn) {
                session.advise()?.recommended ?: session.playableNow.firstOrNull() ?: session.unseenCards.first()
            } else {
                session.playableNow.first()
            }
            session.record(card)
        }

        assertTrue(session.game.isFinished)
        assertTrue(session.me.hand.isEmpty())
        assertNull("nothing left to advise on", session.advise())
    }

    @Test
    fun `undo replays the session without the last card`() {
        val session = session()
        val first = session.advise()!!.recommended
        session.record(first)
        session.record(session.unseenCards.first())

        assertTrue(session.undo())
        assertEquals(9, session.me.hand.size)
        assertEquals(1, session.game.cardsPlayedInRound.size)
        assertEquals(Seat.RIGHT, session.currentSeat)

        assertTrue(session.undo())
        assertEquals(10, session.me.hand.size)
        assertTrue(session.isMyTurn)
        assertFalse("nothing left to undo", session.undo())
    }

    @Test
    fun `a whole game can be tracked from one seat`() {
        val session = session()
        var guard = 0

        while (!session.game.isFinished && guard++ < 100) {
            val card = if (session.isMyTurn) {
                session.advise()!!.recommended
            } else {
                session.playableNow.first()
            }
            session.record(card)
        }

        assertTrue(session.game.isFinished)
        assertEquals(ROUNDS_PER_GAME, session.game.tricks.size)
        assertEquals(TOTAL_POINTS, session.game.teams.sumOf { it.score })
        assertTrue(session.me.hand.isEmpty())
    }

    @Test
    fun `the engine never peeks when it is advising a real table`() {
        val session = session()
        assertFalse(session.config.perfectInformation)

        // With no hands to read, the search still has candidates to weigh, which is exactly what
        // the belief oracle is for.
        val evaluations = TrickSearch.evaluate(session.me, session.game.turn)
        assertTrue(evaluations.all { it.combinations > 1 })
    }

    @Test
    fun `the search budget keeps a blind position affordable`() {
        val session = AdvisorSession(AgentKind.PREDICTOR).apply { start(hand, trump, Seat.RIGHT, Seat.ME) }
        val started = System.currentTimeMillis()
        session.advise()
        val elapsed = System.currentTimeMillis() - started
        assertTrue("advice took ${elapsed}ms", elapsed < 2_000)
    }

    @Test
    fun `simulation is reproducible from a seed`() {
        val first = Simulation.run(AgentKind.GREEDY, AgentKind.RANDOM, games = 25, seed = 99L)
        val second = Simulation.run(AgentKind.GREEDY, AgentKind.RANDOM, games = 25, seed = 99L)

        assertEquals(first.wins, second.wins)
        assertEquals(first.points, second.points)
        assertEquals(first.dealt, second.dealt)
    }

    @Test
    fun `simulation totals add up`() {
        val summary = Simulation.run(AgentKind.MAX_POINTS, AgentKind.MAX_ROUNDS, games = 20, seed = 3L)

        assertEquals(20, summary.winsOf(TeamId.SPORTING) + summary.winsOf(TeamId.BENFICA) + summary.ties)
        assertEquals(
            (TOTAL_POINTS * 20).toLong(),
            summary.points.values.sum(),
        )
        assertEquals(
            "converted points are zero sum across the two teams",
            0.0,
            summary.convertedPoints(TeamId.SPORTING) + summary.convertedPoints(TeamId.BENFICA),
            1e-9,
        )
    }

    @Test
    fun `a rigged position can be handed straight to the engine`() {
        val game = Game(AgentKind.PREDICTOR, AgentKind.PREDICTOR, EngineConfig.SIMULATION, Random(1))
        val deck = FULL_DECK.shuffled(Random(8))
        game.startFromHands(
            hands = (1..4).associateWith { deck.subList((it - 1) * 10, it * 10).toList() },
            trumpCard = deck[39],
            leaderId = 1,
        )

        assertEquals(TOTAL_POINTS, game.teams.sumOf { it.initialPoints })
        game.playToCompletion()
        assertEquals(TOTAL_POINTS, game.teams.sumOf { it.score })
    }
}
