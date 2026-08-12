package pt.up.fe.asma.sueca.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pt.up.fe.asma.sueca.data.DeckProfileStore
import pt.up.fe.asma.sueca.data.DeckProfileSummary
import pt.up.fe.asma.sueca.engine.Card
import pt.up.fe.asma.sueca.engine.Suit
import pt.up.fe.asma.sueca.vision.CardScanner
import pt.up.fe.asma.sueca.vision.DeckProfile
import pt.up.fe.asma.sueca.vision.ScanFrame

data class DeckTrainerUiState(
    val frame: ScanFrame = ScanFrame(),
    val profiles: List<DeckProfileSummary> = emptyList(),
    val profileId: String? = null,
    val profileName: String = "",
    val coverage: Map<Suit, Int> = Suit.entries.associateWith { 0 },
    val tokensLearned: Int = 0,
    val lastLearned: Card? = null,
    val message: String? = null,
) {

    val hasProfile: Boolean get() = profileId != null

    val ready: Boolean get() = coverage.values.all { it > 0 }

    /** What the scanner currently makes of whatever is in front of the camera. */
    val reading: String
        get() {
            val sample = frame.focused ?: return "Nothing in view"
            val card = sample.card
            return when {
                card != null -> "Reads ${card.label} · ${(sample.confidence * 100).toInt()}%"
                sample.rank != null -> "Reads ${sample.rank.label}, but the suit is unclear"
                sample.suit != null -> "Sees a ${sample.suit.id} pip, but no rank"
                else -> "Sees a pip it cannot place"
            }
        }
}

/**
 * Teaches the scanner one physical deck.
 *
 * Every confirmation is a labelled example: the pip mask goes into the profile under the suit
 * the user tapped, and whatever the text recogniser made of the rank goes in as a rule for that
 * glyph. The profile is live on the scanner while training, so the reading on screen improves
 * as the examples go in — which is also the quickest way to see whether it is working.
 */
class DeckTrainerViewModel(application: Application) : AndroidViewModel(application) {

    private val store = DeckProfileStore(application)

    private val _state = MutableStateFlow(DeckTrainerUiState())
    val state: StateFlow<DeckTrainerUiState> = _state.asStateFlow()

    val scanner = CardScanner { frame -> _state.update { it.copy(frame = frame) } }

    private var profile: DeckProfile? = null

    /** Opens the deck the app is currently set to, or the first one there is. */
    fun open(preferredId: String?) {
        viewModelScope.launch {
            val summaries = withContext(Dispatchers.IO) { store.list() }
            val id = preferredId?.takeIf { candidate -> summaries.any { it.id == candidate } }
                ?: summaries.firstOrNull()?.id

            val loaded = if (id == null) null else withContext(Dispatchers.IO) { store.load(id) }
            apply(loaded, summaries, message = null)
        }
    }

    fun createProfile(name: String, onCreated: (String) -> Unit = {}) {
        viewModelScope.launch {
            val fresh = withContext(Dispatchers.IO) { store.create(name.ifBlank { "My deck" }) }
            val summaries = withContext(Dispatchers.IO) { store.list() }
            apply(fresh, summaries, "Show ${fresh.name} to the camera, one card at a time")
            onCreated(fresh.id)
        }
    }

    fun selectProfile(id: String, onSelected: (String) -> Unit = {}) {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) { store.load(id) } ?: return@launch
            apply(loaded, _state.value.profiles, null)
            onSelected(id)
        }
    }

    /**
     * The user is holding [card] up to the camera: file what the pipeline is seeing under it.
     */
    fun confirm(card: Card) {
        val current = profile ?: run {
            message("Make a deck first")
            return
        }
        val sample = _state.value.frame.focused ?: run {
            message("Hold a card up to the camera, so its corner is in view")
            return
        }

        current.learnPip(card.suit, sample.mask, sample.redness)
        if (sample.token.isNotBlank()) current.learnRankToken(sample.token, card.rank)

        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.save(current) }
            _state.update {
                it.copy(
                    coverage = current.coverage(),
                    tokensLearned = current.ranksLearned(),
                    lastLearned = card,
                    message = "Learned ${card.label} · ${current.pipsLearned(card.suit)} " +
                        "${card.suit.symbol} example${if (current.pipsLearned(card.suit) == 1) "" else "s"}",
                )
            }
        }
    }

    fun forgetSuit(suit: Suit) {
        val current = profile ?: return
        current.forget(suit)
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.save(current) }
            _state.update {
                it.copy(coverage = current.coverage(), message = "Forgot every ${suit.id} example")
            }
        }
    }

    fun deleteProfile(onDeleted: () -> Unit = {}) {
        val current = profile ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.delete(current.id) }
            val summaries = withContext(Dispatchers.IO) { store.list() }
            val next = summaries.firstOrNull()?.id?.let { withContext(Dispatchers.IO) { store.load(it) } }
            apply(next, summaries, "Deck deleted")
            onDeleted()
        }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    override fun onCleared() {
        scanner.close()
    }

    private fun apply(loaded: DeckProfile?, summaries: List<DeckProfileSummary>, message: String?) {
        profile = loaded
        scanner.profile = loaded
        _state.update {
            it.copy(
                profiles = summaries,
                profileId = loaded?.id,
                profileName = loaded?.name.orEmpty(),
                coverage = loaded?.coverage() ?: Suit.entries.associateWith { 0 },
                tokensLearned = loaded?.ranksLearned() ?: 0,
                message = message,
            )
        }
    }

    private fun message(text: String) = _state.update { it.copy(message = text) }
}
