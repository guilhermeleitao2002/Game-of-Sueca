package pt.up.fe.asma.sueca.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pt.up.fe.asma.sueca.data.AppSettings
import pt.up.fe.asma.sueca.data.engineDispatcher
import pt.up.fe.asma.sueca.engine.Advice
import pt.up.fe.asma.sueca.engine.AgentKind
import pt.up.fe.asma.sueca.engine.Card
import pt.up.fe.asma.sueca.engine.Game
import pt.up.fe.asma.sueca.engine.GameResult
import pt.up.fe.asma.sueca.engine.Player
import pt.up.fe.asma.sueca.engine.ROUNDS_PER_GAME
import pt.up.fe.asma.sueca.engine.SuecaEngine
import pt.up.fe.asma.sueca.engine.TeamId
import pt.up.fe.asma.sueca.engine.Trick

/** Where a seat is drawn, from the point of view of the person holding the phone. */
enum class TableSpot { BOTTOM, RIGHT, TOP, LEFT }

data class SeatView(
    val spot: TableSpot,
    val name: String,
    val agent: AgentKind,
    val team: TeamId,
    val cardsLeft: Int,
    val isCurrent: Boolean,
    val isHuman: Boolean,
)

data class PlayUiState(
    val started: Boolean = false,
    val hand: List<Card> = emptyList(),
    val legal: Set<Card> = emptySet(),
    val trump: Card? = null,
    val seats: List<SeatView> = emptyList(),
    val table: Map<TableSpot, Card> = emptyMap(),
    val scores: Map<TeamId, Int> = TeamId.entries.associateWith { 0 },
    val trickNumber: Int = 1,
    val advice: Advice? = null,
    val yourTurn: Boolean = false,
    val notice: String? = null,
    val result: GameResult? = null,
)

/**
 * A game against three agents, driven one card at a time.
 *
 * Everything that touches the [Game] runs on [engine], a single thread, so the search that
 * produces the advice and the tap that plays a card can never be inside the same mutable hand
 * at the same time. The UI only ever reads [state].
 */
class PlayViewModel : ViewModel() {

    private val _state = MutableStateFlow(PlayUiState())
    val state: StateFlow<PlayUiState> = _state.asStateFlow()

    private val engine = engineDispatcher()

    @Volatile
    private var game: Game? = null
    private var settings: AppSettings = AppSettings()
    private var spots: Map<Int, TableSpot> = emptyMap()
    private val trickCards = LinkedHashMap<Int, Card>()
    private var job: Job? = null
    private var adviceJob: Job? = null

    val humanName: String get() = TeamId.SPORTING.playerNames.first()

    fun startIfNeeded(settings: AppSettings) {
        if (game == null && job == null) newGame(settings)
    }

    fun newGame(settings: AppSettings) {
        job?.cancel()
        adviceJob?.cancel()
        _state.value = PlayUiState(started = true)

        job = viewModelScope.launch(engine) {
            this@PlayViewModel.settings = settings

            val fresh = Game(
                sportingAgent = settings.partnerAgent,
                benficaAgent = settings.opponentAgent,
                config = settings.playConfig,
                humanPlayerName = humanName,
            )
            fresh.deal()
            game = fresh

            val human = fresh.playerNamed(humanName)!!
            val order = fresh.playersOrder
            val humanIndex = order.indexOf(human)
            spots = order.indices.associate { offset ->
                order[(humanIndex + offset) % order.size].id to TableSpot.entries[offset]
            }

            trickCards.clear()
            refresh(notice = null)
            runAgents()
        }
    }

    /** The person tapped a card in their hand. */
    fun playCard(card: Card) {
        if (!_state.value.yourTurn) return

        job?.cancel()
        job = viewModelScope.launch(engine) {
            val current = game ?: return@launch
            if (current.isFinished || !current.currentPlayer.isHuman) return@launch

            if (card !in current.legalCards()) {
                val suit = current.roundSuit?.symbol ?: return@launch
                _state.update { it.copy(notice = "You have to follow $suit") }
                return@launch
            }

            val player = current.currentPlayer
            val trick = current.play(card)
            trickCards[player.id] = card
            refresh(notice = null)
            if (trick != null) showTrick(trick)
            runAgents()
        }
    }

    fun playRecommended() {
        _state.value.advice?.let { playCard(it.recommended) }
    }

    fun dismissNotice() = _state.update { it.copy(notice = null) }

    override fun onCleared() {
        job?.cancel()
        adviceJob?.cancel()
        engine.close()
    }

    // ---------------------------------------------------------------------------------------

    private suspend fun runAgents() {
        val current = game ?: return

        while (!current.isFinished && !current.currentPlayer.isHuman) {
            delay(settings.agentDelayMillis.toLong())
            val player = current.currentPlayer
            val (decision, trick) = current.playAgentTurn()
            trickCards[player.id] = decision.card
            refresh(notice = "${player.name} played ${decision.card.label}")
            if (trick != null) showTrick(trick)
        }

        refresh(notice = null)
        if (current.isFinished) _state.update { it.copy(result = current.result()) }
    }

    /** Leaves a finished trick on the table long enough to be read, then sweeps it away. */
    private suspend fun showTrick(trick: Trick) {
        val points = if (trick.points > 0) " +${trick.points}" else ""
        val who = if (trick.winner.isHuman) "You take the trick" else "${trick.winner.name} takes the trick"
        refresh(notice = "$who$points")
        delay(1_200)
        trickCards.clear()
        refresh(notice = null)
    }

    /** Rebuilds the immutable snapshot the UI renders. Only ever called on [engine]. */
    private fun refresh(notice: String? = _state.value.notice) {
        val current = game ?: return
        val human = current.playerNamed(humanName)!!
        val yourTurn = !current.isFinished && current.currentPlayer === human

        _state.update { previous ->
            previous.copy(
                started = true,
                hand = human.hand.toList(),
                legal = if (yourTurn) current.legalCards().toSet() else emptySet(),
                trump = current.trump,
                seats = current.players.map { it.toSeatView(current) },
                table = trickCards.entries.associate { (id, card) -> spots.getValue(id) to card },
                scores = current.teams.associate { it.id to it.score },
                trickNumber = (current.currentRound + 1).coerceAtMost(ROUNDS_PER_GAME),
                yourTurn = yourTurn,
                notice = notice,
                // Anything computed for an earlier turn is about a position that no longer exists.
                advice = if (yourTurn && previous.yourTurn) previous.advice else null,
            )
        }

        adviceJob?.cancel()
        if (yourTurn && settings.showHints) {
            adviceJob = viewModelScope.launch(engine) {
                val advice = runCatching { SuecaEngine.advise(current, human, settings.advisorAgent) }.getOrNull()
                // The position may have moved on while the search was queued.
                if (game === current && !current.isFinished && current.currentPlayer === human) {
                    _state.update { it.copy(advice = advice) }
                }
            }
        }
    }

    private fun Player.toSeatView(current: Game) = SeatView(
        spot = spots.getValue(id),
        name = if (isHuman) "You" else name,
        agent = strategy.kind,
        team = team.id,
        cardsLeft = hand.size,
        isCurrent = !current.isFinished && current.currentPlayer === this,
        isHuman = isHuman,
    )
}
