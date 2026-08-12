package pt.up.fe.asma.sueca.ui.screens

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import pt.up.fe.asma.sueca.engine.CARDS_PER_HAND
import pt.up.fe.asma.sueca.engine.Card
import pt.up.fe.asma.sueca.vision.CardScanner
import pt.up.fe.asma.sueca.vision.ScanFrame

data class ScanUiState(
    val frame: ScanFrame = ScanFrame(),
    val collected: List<Card> = emptyList(),
)

/**
 * Owns the camera analyser and the basket of cards it has filled.
 *
 * Cards collect themselves once the scanner has seen them in enough consecutive frames; the
 * user's job is only to throw out anything wrong, which also tells the scanner to stop
 * offering it.
 */
class ScanViewModel : ViewModel() {

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

    fun remove(card: Card) {
        scanner.reject(card)
        _state.update { it.copy(collected = it.collected - card) }
    }

    fun clear() {
        scanner.clear()
        _state.update { it.copy(collected = emptyList()) }
    }

    override fun onCleared() {
        scanner.close()
    }
}
