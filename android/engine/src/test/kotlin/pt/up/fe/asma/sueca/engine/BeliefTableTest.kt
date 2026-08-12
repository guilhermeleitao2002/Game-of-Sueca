package pt.up.fe.asma.sueca.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BeliefTableTest {

    private val aceOfHearts = Card(Suit.HEARTS, Rank.ACE)
    private val twoOfClubs = Card(Suit.CLUBS, Rank.TWO)

    @Test
    fun `an unseen card is split evenly between the other three players`() {
        val beliefs = BeliefTable(ownerId = 1)
        assertEquals(0.0, beliefs.probability(1, aceOfHearts), 1e-9)
        for (id in 2..4) assertEquals(1.0 / 3.0, beliefs.probability(id, aceOfHearts), 1e-9)
    }

    @Test
    fun `a dealt card belongs to its owner alone`() {
        val beliefs = BeliefTable(ownerId = 2)
        beliefs.observeDealt(aceOfHearts)

        assertEquals(1.0, beliefs.probability(2, aceOfHearts), 1e-9)
        for (id in listOf(1, 3, 4)) assertEquals(0.0, beliefs.probability(id, aceOfHearts), 1e-9)
    }

    @Test
    fun `a played card leaves every hand and the rest is renormalised`() {
        val beliefs = BeliefTable(ownerId = 1)
        beliefs.observePlayed(aceOfHearts, Suit.HEARTS, playerId = 3)

        for (id in 1..4) assertEquals(0.0, beliefs.probability(id, aceOfHearts), 1e-9)
        for (id in 2..4) assertEquals(1.0 / 3.0, beliefs.probability(id, twoOfClubs), 1e-9)
    }

    @Test
    fun `failing to follow suit empties that suit for that player and reshares it`() {
        val beliefs = BeliefTable(ownerId = 1)
        beliefs.observePlayed(twoOfClubs, roundSuit = Suit.HEARTS, playerId = 3)

        assertFalse("player 3 proved they hold no hearts", beliefs.holdsAnyOf(3, Suit.HEARTS))
        assertEquals("so the other two split the hearts", 0.5, beliefs.probability(2, aceOfHearts), 1e-9)
        assertEquals(0.5, beliefs.probability(4, aceOfHearts), 1e-9)
    }

    @Test
    fun `the legacy policy keeps the owner's own plays in their row, tracking does not`() {
        val legacy = BeliefTable(ownerId = 1, selfPolicy = SelfBeliefPolicy.LEGACY)
        legacy.observeDealt(aceOfHearts)
        legacy.observePlayed(aceOfHearts, Suit.HEARTS, playerId = 1)
        assertEquals("Player.py never updates the player that just played", 1.0, legacy.probability(1, aceOfHearts), 1e-9)

        val tracking = BeliefTable(ownerId = 1, selfPolicy = SelfBeliefPolicy.TRACK_OWN_PLAYS)
        tracking.observeDealt(aceOfHearts)
        tracking.observePlayed(aceOfHearts, Suit.HEARTS, playerId = 1)
        assertEquals(0.0, tracking.probability(1, aceOfHearts), 1e-9)
    }

    @Test
    fun `beliefs converge on the truth as a game is played out`() {
        val game = Game(AgentKind.MAX_ROUNDS, AgentKind.MAX_ROUNDS, EngineConfig.SIMULATION, Random(42))
        game.deal()

        val watcher = game.players.first()
        while (!game.isFinished) {
            for (other in game.players) {
                if (other === watcher) continue
                for (card in other.hand) {
                    assertTrue(
                        "a card still held must stay possible: $card in ${other.name}",
                        watcher.beliefs.probability(other.id, card) > 0.0,
                    )
                }
            }
            game.playAgentTurn()
        }

        // Everything has been seen by the end, so nothing can still be held.
        for (card in FULL_DECK) {
            for (id in 1..PLAYER_COUNT) {
                if (id == watcher.id) continue
                assertEquals(0.0, watcher.beliefs.probability(id, card), 1e-9)
            }
        }
    }
}
