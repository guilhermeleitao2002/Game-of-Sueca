package pt.up.fe.asma.sueca.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import pt.up.fe.asma.sueca.data.AppSettings
import pt.up.fe.asma.sueca.engine.Advice
import pt.up.fe.asma.sueca.engine.Card
import pt.up.fe.asma.sueca.engine.TeamId
import pt.up.fe.asma.sueca.ui.components.signedPoints
import pt.up.fe.asma.sueca.ui.components.CARD_ASPECT
import pt.up.fe.asma.sueca.ui.components.CardSlot
import pt.up.fe.asma.sueca.ui.components.EnginePanel
import pt.up.fe.asma.sueca.ui.components.HandFan
import pt.up.fe.asma.sueca.ui.components.HiddenHand
import pt.up.fe.asma.sueca.ui.components.MiniCard
import pt.up.fe.asma.sueca.ui.components.Panel
import pt.up.fe.asma.sueca.ui.components.PlayingCardFace
import pt.up.fe.asma.sueca.ui.components.ScoreBar
import pt.up.fe.asma.sueca.ui.components.ScreenScaffold
import pt.up.fe.asma.sueca.ui.components.TrumpBadge
import pt.up.fe.asma.sueca.ui.theme.Gold
import pt.up.fe.asma.sueca.ui.theme.color

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: PlayViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showAnalysis by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.startIfNeeded(settings) }

    ScreenScaffold(
        title = "Trick ${state.trickNumber} of 10",
        onBack = onBack,
        actions = {
            IconButton(onClick = { viewModel.newGame(settings) }) {
                Icon(Icons.Default.Refresh, contentDescription = "New game")
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ScoreBar(
                sporting = state.scores[TeamId.SPORTING] ?: 0,
                benfica = state.scores[TeamId.BENFICA] ?: 0,
            )

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                TableLayout(state)

                TrumpBadge(state.trump, Modifier.align(Alignment.TopEnd))

                NoticeBanner(state.notice, Modifier.align(Alignment.BottomCenter))
            }

            if (settings.showHints) {
                HintStrip(
                    state = state,
                    onOpenAnalysis = { showAnalysis = true },
                    onPlayBest = { viewModel.playRecommended(settings.confirmPlays) },
                )
            }

            HandFan(
                cards = state.hand,
                modifier = Modifier.fillMaxWidth(),
                // Dimming means "you may not play this", not "wait your turn": between your
                // turns nothing is illegal yet, and fading the whole hand out and back in three
                // times a trick would only flicker.
                legal = if (state.yourTurn) state.legal else state.hand.toSet(),
                recommended = if (settings.showHints) state.advice?.recommended else null,
                selected = state.pending,
                onCardClick = { viewModel.requestPlay(it, settings.confirmPlays) },
            )
        }
    }

    if (showAnalysis) {
        ModalBottomSheet(
            onDismissRequest = { showAnalysis = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
            ) {
                EnginePanel(
                    advice = state.advice,
                    modifier = Modifier.fillMaxWidth(),
                    onCardClick = { card ->
                        viewModel.requestPlay(card, settings.confirmPlays)
                        showAnalysis = false
                    },
                )
            }
        }
    }

    state.pending?.let { card ->
        ConfirmPlayDialog(
            card = card,
            advice = state.advice,
            onConfirm = viewModel::confirmPending,
            onDismiss = viewModel::cancelPending,
        )
    }

    state.result?.let { result ->
        AlertDialog(
            onDismissRequest = {},
            title = {
                Text(
                    when (result.winner) {
                        TeamId.SPORTING -> "You win"
                        TeamId.BENFICA -> "You lose"
                        null -> "A tie"
                    },
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TeamId.entries.forEach { team ->
                        val score = result.scores[team] ?: 0
                        val dealt = result.dealt[team] ?: 0
                        Text(
                            text = "${team.displayName}: $score points " +
                                "(dealt $dealt, converted ${signed(score - dealt)})",
                            color = team.color(),
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.newGame(settings) }) { Text("Play again") }
            },
            dismissButton = {
                TextButton(onClick = onBack) { Text("Leave") }
            },
        )
    }
}

private fun signed(value: Int) = if (value >= 0) "+$value" else "$value"

/**
 * The last chance to change your mind.
 *
 * A card on the table cannot be taken back, and a fan of ten overlapping cards is easy to
 * mis-tap, so committing one takes a second, deliberate tap. When the card is not the engine's
 * pick, the dialog says so rather than quietly letting it go.
 */
@Composable
private fun ConfirmPlayDialog(
    card: Card,
    advice: Advice?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val evaluation = advice?.evaluationOf(card)
    val disagrees = advice != null && advice.recommended != card

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { PlayingCardFace(card, width = 54.dp) },
        title = { Text("Play ${card.label}?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (evaluation != null && !evaluation.penalised) {
                    Text(
                        text = "Expected trick: ${evaluation.expectedPoints.signedPoints()} points.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (disagrees) {
                    Text(
                        text = "${advice.agent.displayName} would play ${advice.recommended.label}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Gold,
                    )
                }
                Text(
                    text = "Once it is on the table it cannot be taken back.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { Button(onClick = onConfirm) { Text("Play it") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** "Fred takes the trick +12", floating over the felt for as long as it is relevant. */
@Composable
private fun NoticeBanner(notice: String?, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = notice != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Text(
            text = notice.orEmpty(),
            style = MaterialTheme.typography.labelLarge,
            color = Gold,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.35f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/**
 * The four seats around the felt, with whatever each of them has played this trick.
 *
 * Three stacked bands rather than four independently aligned corners. The seats and the trick
 * used to be placed against the edges of the same box, so on any phone narrower than the sum of
 * a 210dp diamond and two seat labels — which is every phone — the played cards landed on top of
 * the labels. Laying them out in the same row instead makes the overlap impossible rather than
 * unlikely.
 */
@Composable
private fun TableLayout(state: PlayUiState) {
    val seats = state.seats.associateBy { it.spot }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // The trick notice floats over the bottom of this box, so the table keeps a strip clear
        // for it rather than trusting there to be slack left over.
        val noticeRoom = 30.dp
        // The table gets whatever the score bar, the hint strip and your hand leave behind, which
        // on a small phone is not much. Three rows of cards and the partner's label have to fit
        // inside it, so the cards are sized from the height rather than fixed and hoped for.
        val cardWidth = ((maxHeight - 96.dp - noticeRoom) / 3 / CARD_ASPECT).coerceIn(34.dp, 58.dp)

        Column(
            Modifier
                .fillMaxSize()
                .padding(bottom = noticeRoom),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Partner, across the table.
            seats[TableSpot.TOP]?.let { seat ->
                SeatChip(seat)
                Spacer(Modifier.height(4.dp))
                HiddenHand(seat.cardsLeft)
            }
            Spacer(Modifier.height(6.dp))
            PlayedCard(state, TableSpot.TOP, cardWidth)

            // The two opponents, either side of the middle of the table. Each label takes a share
            // of what is left over once the two cards are placed, and truncates inside it.
            Row(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    seats[TableSpot.LEFT]?.let { SeatChip(it) }
                }
                PlayedCard(state, TableSpot.LEFT, cardWidth)
                Spacer(Modifier.weight(0.5f))
                PlayedCard(state, TableSpot.RIGHT, cardWidth)
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    seats[TableSpot.RIGHT]?.let { SeatChip(it) }
                }
            }

            PlayedCard(state, TableSpot.BOTTOM, cardWidth)
        }
    }
}

@Composable
private fun PlayedCard(state: PlayUiState, spot: TableSpot, width: Dp, modifier: Modifier = Modifier) {
    val card = state.table[spot]
    // The slot keeps its size whether or not there is a card in it, so the table does not shuffle
    // itself about as the trick fills up and empties.
    Box(
        modifier.size(width, width * CARD_ASPECT),
        contentAlignment = Alignment.Center,
    ) {
        CardSlot(width)
        AnimatedVisibility(
            visible = card != null,
            enter = fadeIn() + scaleIn(initialScale = 0.7f),
            exit = fadeOut(),
        ) {
            card?.let { PlayingCardFace(card = it, width = width) }
        }
    }
}

@Composable
private fun SeatChip(seat: SeatView, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.28f))
            .border(
                width = if (seat.isCurrent) 1.5.dp else 1.dp,
                color = if (seat.isCurrent) Gold else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(seat.team.color()),
        )
        // A seat at the edge of the table has room for a name and little else, so the agent goes
        // in by its short name and both lines cut off rather than push the chip over a card.
        Column {
            Text(
                text = seat.name,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (seat.isHuman) "${seat.cardsLeft} cards" else "${seat.agent.shortName} · ${seat.cardsLeft}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The one line version of the engine, always on screen while it is your turn. */
@Composable
private fun HintStrip(state: PlayUiState, onOpenAnalysis: () -> Unit, onPlayBest: () -> Unit) {
    val advice = state.advice

    Panel(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(enabled = advice != null, onClick = onOpenAnalysis),
        highlighted = advice != null,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (advice == null) {
                Icon(Icons.Default.AutoAwesome, null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
                Text(
                    text = if (state.yourTurn) "Thinking…" else "Waiting for the others",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Row
            }

            MiniCard(advice.recommended, width = 30.dp, selected = true)
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Play ${advice.recommended.label}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = advice.reason,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            TextButton(onClick = onPlayBest) { Text("Play") }
        }
    }
}
