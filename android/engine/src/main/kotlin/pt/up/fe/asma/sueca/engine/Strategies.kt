package pt.up.fe.asma.sueca.engine

import java.util.Locale
import kotlin.math.roundToInt

/**
 * The six agents, ported one for one from the `Player.py` subclasses.
 *
 * Each one only decides *which* card to play; removing it from the hand, fixing the round suit
 * and updating everybody's beliefs is [Game]'s job, exactly as in the Python simulator.
 */

// ---------------------------------------------------------------------------------------------
// Random
// ---------------------------------------------------------------------------------------------

object RandomStrategy : Strategy {

    override val kind = AgentKind.RANDOM

    override fun decide(self: Player, turn: Turn): Decision {
        val random = turn.game.random
        if (turn.position == 0) {
            val card = self.hand[random.nextInt(self.hand.size)]
            return Decision(card, "Opens with a card picked at random.")
        }

        val following = self.cardsOfSuit(turn.roundSuit!!)
        if (following.isNotEmpty()) {
            val card = following[random.nextInt(following.size)]
            return Decision(card, "Random ${turn.roundSuit.symbol} out of ${following.size} it could follow with.")
        }

        val card = self.hand[random.nextInt(self.hand.size)]
        return Decision(card, "Void in ${turn.roundSuit.symbol} — discards at random.")
    }
}

// ---------------------------------------------------------------------------------------------
// Greedy
// ---------------------------------------------------------------------------------------------

object GreedyStrategy : Strategy {

    override val kind = AgentKind.GREEDY

    override fun decide(self: Player, turn: Turn): Decision {
        if (turn.position == 0) {
            return Decision(self.hand.last(), "Leads its strongest card.")
        }

        val following = self.cardsOfSuit(turn.roundSuit!!)
        return if (following.isNotEmpty()) {
            Decision(following.last(), "Strongest ${turn.roundSuit.symbol} in hand.")
        } else {
            Decision(self.hand.last(), "Void in ${turn.roundSuit.symbol} — throws its strongest card anyway.")
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Maximize points won
// ---------------------------------------------------------------------------------------------

object MaxPointsStrategy : Strategy {

    override val kind = AgentKind.MAX_POINTS

    override fun decide(self: Player, turn: Turn): Decision {
        if (turn.position == 0) return Decision(self.hand.last(), "Opens with its strongest card.")

        val game = turn.game
        val roundSuit = turn.roundSuit!!
        val following = self.cardsOfSuit(roundSuit)
        val soFar = game.evaluateRound(turn.cardsPlayed)

        if (game.playersOrder[soFar.winner].team === self.team) {
            val card = following.lastOrNull() ?: self.hand.last()
            return Decision(card, "${self.team.name} is taking the trick — piling ${card.points} more points onto it.")
        }

        if (following.isNotEmpty()) {
            val strongest = following.last()
            if (strongest.beats(soFar.card, game.trumpSuit)) {
                return Decision(strongest, "Takes the ${soFar.points} points on the table with ${strongest.label}.")
            }
            return Decision(following.first(), "Cannot beat ${soFar.card.label} — gives away as little as possible.")
        }

        val trumps = self.cardsOfSuit(game.trumpSuit)
        return if (trumps.isNotEmpty()) {
            Decision(trumps.last(), "Void in ${roundSuit.symbol} — cuts with its highest trump to take ${soFar.points}.")
        } else {
            Decision(self.hand.first(), "Void in ${roundSuit.symbol} with no trumps — discards its cheapest card.")
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Maximize rounds won
// ---------------------------------------------------------------------------------------------

object MaxRoundsStrategy : Strategy {

    override val kind = AgentKind.MAX_ROUNDS

    override fun decide(self: Player, turn: Turn): Decision {
        if (turn.position == 0) return Decision(self.hand.last(), "Opens with its strongest card.")

        val game = turn.game
        val roundSuit = turn.roundSuit!!
        val following = self.cardsOfSuit(roundSuit)
        val soFar = game.evaluateRound(turn.cardsPlayed)

        if (game.playersOrder[soFar.winner].team === self.team) {
            val card = following.firstOrNull() ?: self.hand.first()
            return Decision(card, "${self.team.name} already has the trick — saves every strong card.")
        }

        if (following.isNotEmpty()) {
            val cheapestWinner = following.firstOrNull { it.beats(soFar.card, game.trumpSuit) }
            if (cheapestWinner != null) {
                return Decision(cheapestWinner, "Cheapest ${roundSuit.symbol} that still beats ${soFar.card.label}.")
            }
            return Decision(following.first(), "Cannot beat ${soFar.card.label} — throws its lowest ${roundSuit.symbol}.")
        }

        val trumps = self.cardsOfSuit(game.trumpSuit)
        return if (trumps.isNotEmpty()) {
            Decision(trumps.first(), "Void in ${roundSuit.symbol} — cuts with its lowest trump.")
        } else {
            Decision(self.hand.first(), "Void in ${roundSuit.symbol} with no trumps — discards its cheapest card.")
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Cooperative
// ---------------------------------------------------------------------------------------------

object CooperativeStrategy : Strategy {

    override val kind = AgentKind.COOPERATIVE

    override fun decide(self: Player, turn: Turn): Decision = when {
        turn.position == 0 -> lead(self, turn)
        turn.position == 1 -> playSecond(self, turn)
        else -> playLate(self, turn)
    }

    /** Open the round, ideally with a suit the partner can profit from. */
    private fun lead(self: Player, turn: Turn): Decision {
        val game = turn.game
        val partner = self.partner() ?: return Decision(self.hand.last(), "No partner — leads its strongest card.")
        val beliefs = self.beliefs
        val trump = game.trumpSuit

        // A suit the partner is void in, while still holding trumps, is a suit they can cut.
        for (suit in Suit.entries) {
            val cards = self.cardsOfSuit(suit)
            if (cards.isNotEmpty() &&
                !beliefs.holdsAnyOf(partner.id, suit) &&
                beliefs.holdsAnyOf(partner.id, trump)
            ) {
                return Decision(
                    cards.last(),
                    "${partner.name} looks void in ${suit.symbol} and still holds trumps — leading it to be cut.",
                )
            }
        }

        // Otherwise lead the suit in which the partnership holds the most points.
        var bestSuit: Suit? = null
        var bestPoints = 0.0
        for (suit in Suit.entries) {
            if (self.cardsOfSuit(suit).isEmpty()) continue
            var total = 0.0
            for (order in 0 until RANK_COUNT) {
                total += (beliefs[partner.id, suit, order] + beliefs[self.id, suit, order]) * ORDER_POINTS[order]
            }
            if (total > bestPoints) {
                bestPoints = total
                bestSuit = suit
            }
        }

        if (bestSuit != null) {
            val card = self.cardsOfSuit(bestSuit).last()
            return Decision(
                card,
                "${bestSuit.symbol} is where the partnership holds the most points " +
                    "(about ${bestPoints.roundToInt()}) — leading it.",
            )
        }

        return Decision(self.hand.last(), "No suit stands out — leads its strongest card.")
    }

    /** Play right after the opening, with two opponents still to come. */
    private fun playSecond(self: Player, turn: Turn): Decision {
        val game = turn.game
        val roundSuit = turn.roundSuit!!
        val partner = self.partner()
        val beliefs = self.beliefs
        val following = self.cardsOfSuit(roundSuit)

        val partnerCanCut = partner != null &&
            beliefs.holdsAnyOf(partner.id, game.trumpSuit) &&
            !beliefs.holdsAnyOf(partner.id, roundSuit)

        if (partnerCanCut && following.isNotEmpty()) {
            return Decision(
                following.last(),
                "${partner!!.name} can cut this trick — feeding them the best ${roundSuit.symbol} in hand.",
            )
        }

        if (following.isEmpty()) {
            val trumps = self.cardsOfSuit(game.trumpSuit)
            if (trumps.isNotEmpty()) {
                return Decision(trumps.first(), "Void in ${roundSuit.symbol} — cuts with its lowest trump.")
            }
            return if (partnerCanCut) {
                Decision(self.hand.last(), "Partner is cutting — discarding the card worth the most to them.")
            } else {
                Decision(self.hand.first(), "Void in ${roundSuit.symbol} with no trumps — discards its cheapest card.")
            }
        }

        val soFar = game.evaluateRound(turn.cardsPlayed)
        if (soFar.card.suit == roundSuit) {
            // Only worth fighting for if the round has not been cut already.
            for (order in (soFar.card.order + 1) until RANK_COUNT) {
                val ownPoints = beliefs[self.id, roundSuit, order] * ORDER_POINTS[order]
                val partnerPoints = if (partner == null) 0.0 else beliefs[partner.id, roundSuit, order] * ORDER_POINTS[order]
                if (ownPoints > 0.0 || partnerPoints > 0.0) {
                    return Decision(
                        following.last(),
                        "There are still points above ${soFar.card.label} in ${roundSuit.symbol} — contesting the trick.",
                    )
                }
            }
        }

        return Decision(following.first(), "Nothing worth fighting for here — plays its lowest ${roundSuit.symbol}.")
    }

    /** Play third or fourth, when the round is nearly decided. */
    private fun playLate(self: Player, turn: Turn): Decision {
        val game = turn.game
        val roundSuit = turn.roundSuit!!
        val following = self.cardsOfSuit(roundSuit)
        val soFar = game.evaluateRound(turn.cardsPlayed)
        val partner = self.partner()

        if (partner != null && game.playersOrder[soFar.winner] === partner) {
            val card = following.lastOrNull() ?: self.hand.last()
            return Decision(card, "${partner.name} is winning the trick — handing them ${card.points} points.")
        }

        if (following.isNotEmpty()) {
            val strongest = following.last()
            if (strongest.beats(soFar.card, game.trumpSuit)) {
                return Decision(strongest, "Takes the ${soFar.points} points on the table with ${strongest.label}.")
            }
            return Decision(following.first(), "Cannot beat ${soFar.card.label} — throws its lowest ${roundSuit.symbol}.")
        }

        val trumps = self.cardsOfSuit(game.trumpSuit)
        return if (trumps.isNotEmpty()) {
            Decision(trumps.last(), "Void in ${roundSuit.symbol} — cuts with its highest trump.")
        } else {
            Decision(self.hand.first(), "Void in ${roundSuit.symbol} with no trumps — discards its cheapest card.")
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Deck Predictor
// ---------------------------------------------------------------------------------------------

object PredictorStrategy : Strategy {

    override val kind = AgentKind.PREDICTOR

    override fun decide(self: Player, turn: Turn): Decision {
        val ranked = TrickSearch.evaluate(self, turn)
        val best = ranked.first()

        val expected = String.format(Locale.US, "%+.2f", best.expectedPoints)
        val reason = buildString {
            append("Best expected trick of the ${ranked.size} legal cards: $expected points")
            append(" over ${best.combinations} possible answers")
            if (best.penalised) append(", trumps held back this early")
            append('.')
        }

        return Decision(best.card, reason)
    }
}
