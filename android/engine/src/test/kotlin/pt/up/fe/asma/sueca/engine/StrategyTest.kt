package pt.up.fe.asma.sueca.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class StrategyTest {

    /** Puts four known hands on the table so a strategy can be asked about a fixed position. */
    private fun position(
        agent: AgentKind,
        trump: Card,
        leaderId: Int,
        hands: Map<Int, List<Card>>,
        config: EngineConfig = EngineConfig.SIMULATION,
    ): Game = Game(agent, agent, config, Random(1)).apply { startFromHands(hands, trump, leaderId) }

    private fun cards(suit: Suit, vararg ranks: Rank) = ranks.map { Card(suit, it) }

    @Test
    fun `every agent plays legal cards for a whole game`() {
        for (agent in AgentKind.entries) {
            for (seed in 1L..5L) {
                val game = Game(agent, agent, EngineConfig.SIMULATION, Random(seed))
                game.deal()
                while (!game.isFinished) {
                    val legal = game.legalCards()
                    val decision = game.suggestion()
                    assertTrue(
                        "${agent.displayName} picked ${decision.card} which is not legal",
                        decision.card in legal,
                    )
                    assertTrue("${agent.displayName} gave no reason", decision.reason.isNotBlank())
                    game.play(decision.card)
                }
                assertEquals(TOTAL_POINTS, game.teams.sumOf { it.score })
            }
        }
    }

    @Test
    fun `every agent also plays legally without peeking at the other hands`() {
        for (agent in AgentKind.entries) {
            val game = Game(agent, agent, EngineConfig.FAIR, Random(9))
            game.deal()
            while (!game.isFinished) {
                val legal = game.legalCards()
                val (decision, _) = game.playAgentTurn()
                assertTrue("${agent.displayName} played illegally", decision.card in legal)
            }
        }
    }

    @Test
    fun `greedy leads its strongest card and follows with its strongest of the suit`() {
        val game = position(
            agent = AgentKind.GREEDY,
            trump = Card(Suit.SPADES, Rank.TWO),
            leaderId = 1,
            hands = mapOf(
                1 to cards(Suit.HEARTS, Rank.THREE, Rank.KING) + cards(Suit.CLUBS, Rank.FOUR),
                2 to cards(Suit.HEARTS, Rank.FOUR) + cards(Suit.CLUBS, Rank.FIVE, Rank.SIX),
                3 to cards(Suit.HEARTS, Rank.FIVE, Rank.ACE) + cards(Suit.CLUBS, Rank.SEVEN),
                4 to cards(Suit.HEARTS, Rank.SIX) + cards(Suit.CLUBS, Rank.TWO, Rank.THREE),
            ),
        )

        assertEquals(Card(Suit.HEARTS, Rank.KING), game.suggestion().card)
        game.play(Card(Suit.HEARTS, Rank.KING))

        // Seat 3 plays next and must follow hearts with its best one.
        assertEquals(Card(Suit.HEARTS, Rank.ACE), game.suggestion().card)
    }

    @Test
    fun `maximize rounds won takes the trick with the cheapest card that wins it`() {
        val game = position(
            agent = AgentKind.MAX_ROUNDS,
            trump = Card(Suit.SPADES, Rank.TWO),
            leaderId = 1,
            hands = mapOf(
                1 to cards(Suit.HEARTS, Rank.QUEEN) + cards(Suit.CLUBS, Rank.TWO, Rank.THREE),
                2 to cards(Suit.HEARTS, Rank.TWO) + cards(Suit.CLUBS, Rank.FOUR, Rank.FIVE),
                3 to cards(Suit.HEARTS, Rank.JACK, Rank.KING, Rank.ACE),
                4 to cards(Suit.HEARTS, Rank.THREE) + cards(Suit.CLUBS, Rank.SIX, Rank.SEVEN),
            ),
        )

        game.play(Card(Suit.HEARTS, Rank.QUEEN))
        assertEquals(
            "the jack already beats a queen, so the king and ace stay in hand",
            Card(Suit.HEARTS, Rank.JACK),
            game.suggestion().card,
        )
    }

    @Test
    fun `maximize points won cuts a valuable trick it cannot follow`() {
        val trump = Card(Suit.SPADES, Rank.TWO)
        val game = position(
            agent = AgentKind.MAX_POINTS,
            trump = trump,
            leaderId = 1,
            hands = mapOf(
                1 to cards(Suit.HEARTS, Rank.ACE) + cards(Suit.CLUBS, Rank.TWO, Rank.THREE),
                2 to cards(Suit.HEARTS, Rank.TWO) + cards(Suit.CLUBS, Rank.FOUR, Rank.FIVE),
                3 to cards(Suit.CLUBS, Rank.SIX, Rank.SEVEN) + cards(Suit.SPADES, Rank.THREE),
                4 to cards(Suit.HEARTS, Rank.THREE, Rank.FOUR, Rank.FIVE),
            ),
        )

        game.play(Card(Suit.HEARTS, Rank.ACE))
        assertEquals(
            "eleven points on the table and no hearts left, so it trumps",
            Card(Suit.SPADES, Rank.THREE),
            game.suggestion().card,
        )
    }

    @Test
    fun `the cooperative agent leads a suit its partner can cut`() {
        val trump = Card(Suit.SPADES, Rank.TWO)
        val game = position(
            agent = AgentKind.COOPERATIVE,
            trump = trump,
            leaderId = 1,
            hands = mapOf(
                1 to cards(Suit.HEARTS, Rank.FOUR, Rank.FIVE) + cards(Suit.DIAMONDS, Rank.SIX),
                2 to cards(Suit.SPADES, Rank.THREE) + cards(Suit.DIAMONDS, Rank.SEVEN, Rank.ACE),
                3 to cards(Suit.HEARTS, Rank.TWO, Rank.THREE, Rank.SIX),
                4 to cards(Suit.HEARTS, Rank.SEVEN, Rank.ACE) + cards(Suit.CLUBS, Rank.TWO),
            ),
        )

        // Teach seat 1 what it would have learned from the partner failing to follow hearts
        // earlier: they are void there, and they still hold a trump.
        val me = game.playerWithId(1)
        val partner = me.partner()!!
        for (order in 0 until RANK_COUNT) me.beliefs[partner.id, Suit.HEARTS, order] = 0.0
        me.beliefs[partner.id, trump.suit, Rank.THREE.order] = 1.0

        val decision = game.suggestion()
        assertEquals(Suit.HEARTS, decision.card.suit)
        assertEquals("and it leads the highest one it has", Rank.FIVE, decision.card.rank)
    }

    @Test
    fun `the predictor holds its trumps back in the first two tricks`() {
        val trump = Card(Suit.SPADES, Rank.TWO)
        val game = position(
            agent = AgentKind.PREDICTOR,
            trump = trump,
            leaderId = 1,
            hands = mapOf(
                1 to cards(Suit.SPADES, Rank.ACE, Rank.SEVEN) + cards(Suit.HEARTS, Rank.THREE),
                2 to cards(Suit.HEARTS, Rank.FOUR, Rank.FIVE, Rank.SIX),
                3 to cards(Suit.DIAMONDS, Rank.TWO, Rank.THREE, Rank.FOUR),
                4 to cards(Suit.CLUBS, Rank.TWO, Rank.THREE, Rank.FOUR),
            ),
        )

        val decision = game.suggestion()
        assertEquals("trumps are penalised while current_round < 2", Suit.HEARTS, decision.card.suit)

        val evaluations = TrickSearch.evaluate(game.currentPlayer, game.turn)
        assertTrue("the penalty is reported", evaluations.filter { it.card.suit == Suit.SPADES }.all { it.penalised })
    }

    @Test
    fun `agents disagree, which is the whole point of comparing them`() {
        val game = Game(AgentKind.PREDICTOR, AgentKind.RANDOM, EngineConfig.SIMULATION, Random(4))
        game.deal()
        game.play(game.suggestion().card)

        val advice = SuecaEngine.advise(game, agent = AgentKind.PREDICTOR)
        assertEquals(AgentKind.entries.size, advice.alternatives.size)
        assertTrue(advice.evaluations.isNotEmpty())
        assertTrue(advice.recommended in game.legalCards())
        advice.alternatives.forEach {
            assertTrue("${it.agent} suggested an illegal card", it.card in game.legalCards())
        }
    }
}
