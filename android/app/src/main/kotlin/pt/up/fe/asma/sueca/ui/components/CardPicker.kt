package pt.up.fe.asma.sueca.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pt.up.fe.asma.sueca.engine.Card
import pt.up.fe.asma.sueca.engine.Rank
import pt.up.fe.asma.sueca.engine.Suit

/**
 * The whole deck, one row per suit, weakest card first.
 *
 * Rows follow trick strength rather than the usual A-2-3 ordering, so the picker reads in the
 * same direction as everything else in the app: left is weak, right is strong.
 */
@Composable
fun CardPickerGrid(
    onPick: (Card) -> Unit,
    modifier: Modifier = Modifier,
    selected: Set<Card> = emptySet(),
    disabled: Set<Card> = emptySet(),
    suggested: Set<Card> = emptySet(),
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val spacing: Dp = 3.dp
        val cardWidth = (maxWidth - spacing * (Rank.entries.size - 1)) / Rank.entries.size

        Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
            for (suit in Suit.entries) {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                    for (rank in Rank.entries) {
                        val card = Card(suit, rank)
                        val isDisabled = card in disabled
                        MiniCard(
                            card = card,
                            width = cardWidth,
                            selected = card in selected || card in suggested,
                            dimmed = isDisabled,
                            onClick = if (isDisabled) null else ({ onPick(card) }),
                        )
                    }
                }
            }
        }
    }
}
