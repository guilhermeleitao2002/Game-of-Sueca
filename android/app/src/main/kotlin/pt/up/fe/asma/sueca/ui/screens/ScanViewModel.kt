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
import pt.up.fe.asma.sueca.engine.CARDS_PER_HAND
import pt.up.fe.asma.sueca.engine.Card
import pt.up.fe.asma.sueca.vision.CameraCapture
import pt.up.fe.asma.sueca.vision.CardScanner
import pt.up.fe.asma.sueca.vision.ClaudeCardReader
import pt.up.fe.asma.sueca.vision.ScanFrame

data class ScanUiState(
    val frame: ScanFrame = ScanFrame(),
    val collected: List<Card> = emptyList(),
    val deckName: String? = null,
    /** True while a photo is being taken and sent to Claude. */
    val cloudBusy: Boolean = false,
    val message: String? = null,
)

/**
 * Owns the camera analyser and the basket of cards it has filled.
 *
 * Cards collect themselves once the scanner has seen them in enough consecutive frames; the
 * user's job is only to throw out anything wrong, which also tells the scanner to stop
 * offering it. When a deck defeats the on-device pipeline entirely, [readWithClaude] takes one
 * photo and asks a model to read the lot — but only if the person supplied an API key.
 */
class ScanViewModel(application: Application) : AndroidViewModel(application) {

    private val store = DeckProfileStore(application)

    private val _state = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    val scanner = CardScanner { frame ->
        _state.update { current ->
            val collected = (current.collected + frame.stable.map { it.card })
                .distinct()
                .take(CARDS_PER_HAND)
            current.copy(frame = frame, collected = collected)
        }
    }

    val capture = CameraCapture()

    @Volatile
    private var apiKey: String? = null
    private var reader: ClaudeCardReader? = null

    /** Points the classifier at a trained deck, or back at the built-in shapes when null. */
    fun useProfile(id: String?) {
        viewModelScope.launch {
            val profile = if (id == null) null else withContext(Dispatchers.IO) { store.load(id) }
            scanner.profile = profile
            _state.update { it.copy(deckName = profile?.name) }
        }
    }

    /** null turns the cloud reader off; the button disappears with it. */
    fun useApiKey(key: String?) {
        if (key == apiKey) return
        apiKey = key
        reader = key?.let { ClaudeCardReader(it) }
    }

    /**
     * Takes one photo and asks Claude to read every card in it.
     *
     * Deliberately a button rather than something automatic: it costs the user money, however
     * little, and it leaves the device — neither should ever happen without them asking.
     */
    fun readWithClaude() {
        val current = reader ?: return
        if (_state.value.cloudBusy) return

        _state.update { it.copy(cloudBusy = true, message = "Reading the photo…") }
        viewModelScope.launch {
            val photo = capture.takeJpeg(scanner.executor)
            if (photo == null) {
                _state.update { it.copy(cloudBusy = false, message = "The camera could not take that photo.") }
                return@launch
            }

            when (val outcome = current.read(photo)) {
                is ClaudeCardReader.Outcome.Read -> _state.update { state ->
                    val merged = (state.collected + outcome.cards).distinct().take(CARDS_PER_HAND)
                    val added = merged.size - state.collected.size
                    state.copy(
                        collected = merged,
                        cloudBusy = false,
                        message = outcome.note?.takeIf { it.isNotBlank() }
                            ?: "Read ${outcome.cards.size} cards, $added new.",
                    )
                }

                is ClaudeCardReader.Outcome.Failed -> _state.update {
                    it.copy(cloudBusy = false, message = outcome.message)
                }
            }
        }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    fun remove(card: Card) {
        scanner.reject(card)
        _state.update { it.copy(collected = it.collected - card) }
    }

    fun clear() {
        scanner.clear()
        _state.update { it.copy(collected = emptyList(), message = null) }
    }

    override fun onCleared() {
        scanner.close()
    }
}
