package pt.up.fe.asma.sueca.data

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pt.up.fe.asma.sueca.engine.AgentKind
import pt.up.fe.asma.sueca.engine.Card
import pt.up.fe.asma.sueca.engine.EngineConfig

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore("sueca-settings")

/**
 * @param advisorAgent the agent that answers "what should I play?". The Deck Predictor by
 *   default, since it is the strongest of the six.
 * @param fairPlay when on, no agent is allowed to read anybody else's hand. Turning it off
 *   restores the behaviour of the Python simulator, where the Deck Predictor peeks.
 */
data class AppSettings(
    val advisorAgent: AgentKind = AgentKind.DEFAULT,
    val partnerAgent: AgentKind = AgentKind.DEFAULT,
    val opponentAgent: AgentKind = AgentKind.MAX_POINTS,
    val fairPlay: Boolean = true,
    val showHints: Boolean = true,
    val agentDelayMillis: Int = 650,
    /** Id of the trained deck the scanner should match against, or null for the built-in shapes. */
    val deckProfileId: String? = null,
) {

    /** The rules the agents play under in a game against a person. */
    val playConfig: EngineConfig
        get() = if (fairPlay) EngineConfig.FAIR else EngineConfig.SIMULATION
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val store = application.settingsStore

    val settings: StateFlow<AppSettings> = store.data
        .map { preferences ->
            AppSettings(
                advisorAgent = preferences.agent(ADVISOR_AGENT, AgentKind.DEFAULT),
                partnerAgent = preferences.agent(PARTNER_AGENT, AgentKind.DEFAULT),
                opponentAgent = preferences.agent(OPPONENT_AGENT, AgentKind.MAX_POINTS),
                fairPlay = preferences[FAIR_PLAY] ?: true,
                showHints = preferences[SHOW_HINTS] ?: true,
                agentDelayMillis = preferences[AGENT_DELAY] ?: 650,
                deckProfileId = preferences[DECK_PROFILE]?.takeIf { it.isNotEmpty() },
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    fun setAdvisorAgent(agent: AgentKind) = put(ADVISOR_AGENT, agent.cliName)

    fun setPartnerAgent(agent: AgentKind) = put(PARTNER_AGENT, agent.cliName)

    fun setOpponentAgent(agent: AgentKind) = put(OPPONENT_AGENT, agent.cliName)

    fun setFairPlay(enabled: Boolean) = put(FAIR_PLAY, enabled)

    fun setShowHints(enabled: Boolean) = put(SHOW_HINTS, enabled)

    fun setAgentDelay(millis: Int) = put(AGENT_DELAY, millis)

    /** Pass null to go back to the built-in suit shapes. */
    fun setDeckProfile(id: String?) = put(DECK_PROFILE, id.orEmpty())

    private fun <T> put(key: Preferences.Key<T>, value: T) {
        viewModelScope.launch { store.edit { it[key] = value } }
    }

    private fun Preferences.agent(key: Preferences.Key<String>, fallback: AgentKind): AgentKind =
        this[key]?.let { AgentKind.fromCliName(it) } ?: fallback

    private companion object {
        val ADVISOR_AGENT = stringPreferencesKey("advisor_agent")
        val PARTNER_AGENT = stringPreferencesKey("partner_agent")
        val OPPONENT_AGENT = stringPreferencesKey("opponent_agent")
        val FAIR_PLAY = booleanPreferencesKey("fair_play")
        val SHOW_HINTS = booleanPreferencesKey("show_hints")
        val AGENT_DELAY = intPreferencesKey("agent_delay")
        val DECK_PROFILE = stringPreferencesKey("deck_profile")
    }
}

/**
 * How the scanner hands its result back to whoever opened it.
 *
 * A one slot handover rather than a navigation argument, because a hand of ten cards is not a
 * route parameter.
 */
object ScanResultBus {

    private var pending: List<Card> = emptyList()

    fun offer(cards: List<Card>) {
        pending = cards
    }

    fun take(): List<Card> {
        val result = pending
        pending = emptyList()
        return result
    }
}
