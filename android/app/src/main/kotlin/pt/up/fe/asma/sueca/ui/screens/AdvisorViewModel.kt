package pt.up.fe.asma.sueca.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pt.up.fe.asma.sueca.data.engineDispatcher
import pt.up.fe.asma.sueca.engine.Advice
import pt.up.fe.asma.sueca.engine.AdvisorSession
import pt.up.fe.asma.sueca.engine.AgentKind
import pt.up.fe.asma.sueca.engine.CARDS_PER_HAND
import pt.up.fe.asma.sueca.engine.Card
import pt.up.fe.asma.sueca.engine.FULL_DECK
import pt.up.fe.asma.sueca.engine.ROUNDS_PER_GAME
import pt.up.fe.asma.sueca.engine.Seat
import pt.up.fe.asma.sueca.engine.TeamId

enum class AdvisorStep { HAND, TRUMP, LEAD, PLAY }

data class AdvisorUiState(
    val step: AdvisorStep = AdvisorStep.HAND,
    val hand: List<Card> = emptyList(),
    val trump: Card? = null,
    val trumpHolder: Seat = Seat.RIGHT,
    val leader: Seat = Seat.ME,
    val agent: AgentKind = AgentKind.DEFAULT,
    val advice: Advice? = null,
    val thinking: Boolean = false,
    val currentSeat: Seat? = null,
    val table: Map<Seat, Card> = emptyMap(),
    val scores: Map<TeamId, Int> = TeamId.entries.associateWith { 0 },
    val trickNumber: Int = 1,
    val playable: List<Card> = emptyList(),
    val finished: Boolean = false,
    val canUndo: Boolean = false,
    val message: String? = null,
) {

    val handComplete: Boolean get() = hand.size == CARDS_PER_HAND

    /** The trump card is only ever in your hand when you were the one to turn it up. */
    val trumpHolderChoices: List<Seat>
        get() = if (trump != null && trump in hand) listOf(Seat.ME) else Seat.entries
}

/**
 * Tracks a game being played with real cards on a real table.
 *
 * Only one hand is ever known. Everything else the engine knows it worked out from the cards
 * that have been recorded, which is exactly the position a player is in.
 *
 * As in [PlayViewModel], every touch of the session happens on one thread, because the search
 * and the taps would otherwise be in the same mutable game at once.
 */
class AdvisorViewModel : ViewModel() {

    private val _state = MutableStateFlow(AdvisorUiState())
    val state: StateFlow<AdvisorUiState> = _state.asStateFlow()

    private val engine = engineDispatcher()

    private var session = AdvisorSession()
    private var adviceJob: Job? = null

    fun setAgent(agent: AgentKind) {
        _state.update { it.copy(agent = agent) }
        onEngine {
            session.agent = agent
            if (_state.value.step == AdvisorStep.PLAY) refresh()
        }
    }

    // --- setting up -------------------------------------------------------------------------

    fun toggleHandCard(card: Card) {
        _state.update { current ->
            val hand = when {
                card in current.hand -> current.hand - card
                current.hand.size >= CARDS_PER_HAND -> return@update current.copy(
                    message = "A Sueca hand is ten cards",
                )

                else -> current.hand + card
            }
            current.copy(
                hand = hand.sortedBy { it.order },
                message = null,
                // A trump taken back out of your own hand is no longer a trump you can claim.
                trump = current.trump?.takeIf { it !in current.hand || it in hand },
            )
        }
    }

    fun setHand(cards: List<Card>) {
        _state.update {
            it.copy(hand = cards.distinct().take(CARDS_PER_HAND).sortedBy { card -> card.order })
        }
    }

    fun clearHand() = _state.update { it.copy(hand = emptyList(), trump = null) }

    fun setTrump(card: Card) = _state.update {
        it.copy(trump = card, trumpHolder = if (card in it.hand) Seat.ME else it.trumpHolder)
    }

    fun setTrumpHolder(seat: Seat) = _state.update { it.copy(trumpHolder = seat) }

    fun setLeader(seat: Seat) = _state.update { it.copy(leader = seat) }

    fun goTo(step: AdvisorStep) = _state.update { it.copy(step = step, message = null) }

    fun begin() {
        val current = _state.value
        val trump = current.trump ?: return
        onEngine {
            session = AdvisorSession(current.agent)
            session.start(current.hand, trump, current.trumpHolder, current.leader)
            _state.update { it.copy(step = AdvisorStep.PLAY, message = null) }
            refresh()
        }
    }

    fun reset() {
        adviceJob?.cancel()
        onEngine {
            session = AdvisorSession(_state.value.agent)
            _state.value = AdvisorUiState(agent = _state.value.agent)
        }
    }

    // --- playing ----------------------------------------------------------------------------

    /** Records the card the seat to act just put on the table. */
    fun record(card: Card) = onEngine {
        val result = runCatching { session.record(card) }
        val error = result.exceptionOrNull()
        if (error != null) {
            _state.update { it.copy(message = error.message ?: "That card cannot be played here") }
            return@onEngine
        }

        val trick = result.getOrNull()
        val notice = trick?.let {
            val who = if (it.winner === session.me) "You take the trick" else "${Seat.ofPlayer(it.winner).label} takes it"
            "$who${if (it.points > 0) " +${it.points}" else ""}"
        }
        refresh(notice)
    }

    fun playRecommended() {
        _state.value.advice?.let { record(it.recommended) }
    }

    fun undo() = onEngine {
        if (session.undo()) refresh(null)
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    override fun onCleared() {
        adviceJob?.cancel()
        engine.close()
    }

    // ---------------------------------------------------------------------------------------

    private fun onEngine(block: suspend () -> Unit) {
        viewModelScope.launch(engine) { block() }
    }

    /** Only ever called on [engine]. */
    private fun refresh(notice: String? = null) {
        val game = session.game
        val table = game.playersOrder
            .take(game.cardsPlayedInRound.size)
            .mapIndexed { index, player -> Seat.ofPlayer(player) to game.cardsPlayedInRound[index] }
            .toMap()
        val myTurn = session.isMyTurn

        _state.update { current ->
            current.copy(
                hand = session.me.hand.toList(),
                currentSeat = session.currentSeat,
                table = table,
                scores = game.teams.associate { it.id to it.score },
                trickNumber = (game.currentRound + 1).coerceAtMost(ROUNDS_PER_GAME),
                playable = session.playableNow,
                finished = game.isFinished,
                canUndo = game.tricks.isNotEmpty() || game.cardsPlayedInRound.isNotEmpty(),
                advice = null,
                thinking = myTurn,
                message = notice,
            )
        }

        adviceJob?.cancel()
        if (myTurn) {
            adviceJob = viewModelScope.launch(engine) {
                val advice = runCatching { session.advise() }.getOrNull()
                if (session.isMyTurn) _state.update { it.copy(advice = advice, thinking = false) }
            }
        }
    }
}

/** Cards nobody could have played yet, for the "what did they play?" picker. */
fun AdvisorUiState.impossibleCards(): Set<Card> {
    val playableSet = playable.toSet()
    return FULL_DECK.filterNot { it in playableSet }.toSet()
}
