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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import pt.up.fe.asma.sueca.data.AppSettings
import pt.up.fe.asma.sueca.engine.TeamId
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
                    onPlayBest = viewModel::playRecommended,
                )
            }

            HandFan(
                cards = state.hand,
                modifier = Modifier.fillMaxWidth(),
                legal = if (state.yourTurn) state.legal else emptySet(),
                recommended = if (settings.showHints) state.advice?.recommended else null,
                onCardClick = viewModel::playCard,
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
                        viewModel.playCard(card)
                        showAnalysis = false
                    },
                )
            }
        }
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

/** The four seats around the felt, with whatever each of them has played this trick. */
@Composable
private fun TableLayout(state: PlayUiState) {
    val seats = state.seats.associateBy { it.spot }

    Box(Modifier.fillMaxSize()) {
        // Partner, across the table.
        seats[TableSpot.TOP]?.let { seat ->
            Column(
                Modifier.align(Alignment.TopCenter),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SeatChip(seat)
                Spacer(Modifier.height(4.dp))
                HiddenHand(seat.cardsLeft)
            }
        }

        seats[TableSpot.LEFT]?.let { seat ->
            SeatChip(seat, Modifier.align(Alignment.CenterStart))
        }

        seats[TableSpot.RIGHT]?.let { seat ->
            SeatChip(seat, Modifier.align(Alignment.CenterEnd))
        }

        // The trick itself, one card per side of a diamond.
        Box(Modifier.align(Alignment.Center).size(width = 210.dp, height = 190.dp)) {
            PlayedCard(state, TableSpot.TOP, Modifier.align(Alignment.TopCenter))
            PlayedCard(state, TableSpot.LEFT, Modifier.align(Alignment.CenterStart))
            PlayedCard(state, TableSpot.RIGHT, Modifier.align(Alignment.CenterEnd))
            PlayedCard(state, TableSpot.BOTTOM, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun PlayedCard(state: PlayUiState, spot: TableSpot, modifier: Modifier = Modifier) {
    val card = state.table[spot]
    AnimatedVisibility(
        visible = card != null,
        enter = fadeIn() + scaleIn(initialScale = 0.7f),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        card?.let { PlayingCardFace(card = it, width = 58.dp) }
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
        Column {
            Text(
                text = seat.name,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (seat.isHuman) "${seat.cardsLeft} cards" else "${seat.agent.displayName} · ${seat.cardsLeft}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
