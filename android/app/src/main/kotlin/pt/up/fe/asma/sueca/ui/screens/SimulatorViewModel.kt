package pt.up.fe.asma.sueca.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import pt.up.fe.asma.sueca.engine.AgentKind
import pt.up.fe.asma.sueca.engine.EngineConfig
import pt.up.fe.asma.sueca.engine.Simulation
import pt.up.fe.asma.sueca.engine.SimulationSummary

data class SimulatorUiState(
    val sporting: AgentKind = AgentKind.PREDICTOR,
    val benfica: AgentKind = AgentKind.GREEDY,
    val games: Int = 200,
    val running: Boolean = false,
    val progress: Float = 0f,
    val summary: SimulationSummary? = null,
)

/**
 * Agent versus agent, the same experiment the report runs, but on the phone.
 *
 * Uses [EngineConfig.SIMULATION] rather than the fair play config, so the numbers are
 * comparable with the ones in `results/`.
 */
class SimulatorViewModel : ViewModel() {

    private val _state = MutableStateFlow(SimulatorUiState())
    val state: StateFlow<SimulatorUiState> = _state.asStateFlow()

    private var job: Job? = null

    fun setSporting(agent: AgentKind) = _state.update { it.copy(sporting = agent, summary = null) }

    fun setBenfica(agent: AgentKind) = _state.update { it.copy(benfica = agent, summary = null) }

    fun setGames(count: Int) = _state.update { it.copy(games = count, summary = null) }

    fun run() {
        job?.cancel()
        val current = _state.value
        _state.update { it.copy(running = true, progress = 0f, summary = null) }

        job = viewModelScope.launch(Dispatchers.Default) {
            val summary = runCatching {
                Simulation.run(
                    sporting = current.sporting,
                    benfica = current.benfica,
                    games = current.games,
                    config = EngineConfig.SIMULATION,
                ) { played ->
                    coroutineContext.ensureActive()
                    if (played % 5 == 0 || played == current.games) {
                        _state.update { it.copy(progress = played.toFloat() / current.games) }
                    }
                }
            }.getOrNull()

            _state.update { it.copy(running = false, summary = summary, progress = if (summary != null) 1f else 0f) }
        }
    }

    fun cancel() {
        job?.cancel()
        _state.update { it.copy(running = false) }
    }

    override fun onCleared() {
        job?.cancel()
    }
}
