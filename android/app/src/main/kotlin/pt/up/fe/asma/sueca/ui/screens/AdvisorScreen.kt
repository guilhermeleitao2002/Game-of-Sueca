package pt.up.fe.asma.sueca.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import pt.up.fe.asma.sueca.data.AppSettings
import pt.up.fe.asma.sueca.data.ScanResultBus
import pt.up.fe.asma.sueca.engine.CARDS_PER_HAND
import pt.up.fe.asma.sueca.engine.Seat
import pt.up.fe.asma.sueca.engine.TeamId
import pt.up.fe.asma.sueca.ui.components.AgentSelector
import pt.up.fe.asma.sueca.ui.components.CardPickerGrid
import pt.up.fe.asma.sueca.ui.components.EnginePanel
import pt.up.fe.asma.sueca.ui.components.HandFan
import pt.up.fe.asma.sueca.ui.components.MiniCard
import pt.up.fe.asma.sueca.ui.components.Panel
import pt.up.fe.asma.sueca.ui.components.PlayingCardFace
import pt.up.fe.asma.sueca.ui.components.ScoreBar
import pt.up.fe.asma.sueca.ui.components.ScreenScaffold
import pt.up.fe.asma.sueca.ui.components.SectionLabel
import pt.up.fe.asma.sueca.ui.components.TrumpBadge
import pt.up.fe.asma.sueca.ui.theme.Gold
import pt.up.fe.asma.sueca.ui.theme.color

@Composable
fun AdvisorScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onScanHand: () -> Unit,
    viewModel: AdvisorViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(settings.advisorAgent) { viewModel.setAgent(settings.advisorAgent) }

    // Cards coming back from the scanner.
    LaunchedEffect(Unit) {
        val scanned = ScanResultBus.take()
        if (scanned.isNotEmpty()) viewModel.setHand(scanned)
    }

    ScreenScaffold(
        title = when (state.step) {
            AdvisorStep.HAND -> "Your hand"
            AdvisorStep.TRUMP -> "The trump"
            AdvisorStep.LEAD -> "Who leads"
            AdvisorStep.PLAY -> "Trick ${state.trickNumber} of 10"
        },
        onBack = onBack,
        actions = {
            if (state.step == AdvisorStep.PLAY) {
                IconButton(onClick = viewModel::undo, enabled = state.canUndo) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                }
                IconButton(onClick = viewModel::reset) {
                    Icon(Icons.Default.RestartAlt, contentDescription = "Start over")
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            state.message?.let { message ->
                item {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.labelLarge,
                        color = Gold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.3f))
                            .padding(10.dp),
                    )
                }
            }

            when (state.step) {
                AdvisorStep.HAND -> handStep(state, viewModel, onScanHand)
                AdvisorStep.TRUMP -> trumpStep(state, viewModel)
                AdvisorStep.LEAD -> leadStep(state, viewModel)
                AdvisorStep.PLAY -> playStep(state, viewModel)
            }
        }
    }
}

// -------------------------------------------------------------------------------------------
// Setting up
// -------------------------------------------------------------------------------------------

private fun androidx.compose.foundation.lazy.LazyListScope.handStep(
    state: AdvisorUiState,
    viewModel: AdvisorViewModel,
    onScanHand: () -> Unit,
) {
    item {
        Text(
            text = "Tap the ten cards you were dealt, or point the camera at them.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    item {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onScanHand) {
                Icon(Icons.Default.PhotoCamera, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Scan")
            }
            TextButton(onClick = viewModel::clearHand) { Text("Clear") }
            Spacer(Modifier.weight(1f))
            Text(
                text = "${state.hand.size} / $CARDS_PER_HAND",
                style = MaterialTheme.typography.labelLarge,
                color = if (state.handComplete) Gold else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (state.hand.isNotEmpty()) {
        item {
            Panel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    SectionLabel("Your hand")
                    Spacer(Modifier.height(8.dp))
                    HandFan(
                        cards = state.hand,
                        modifier = Modifier.fillMaxWidth(),
                        cardWidth = 54.dp,
                        onCardClick = viewModel::toggleHandCard,
                    )
                }
            }
        }
    }

    item {
        CardPickerGrid(
            onPick = viewModel::toggleHandCard,
            selected = state.hand.toSet(),
        )
    }

    item {
        Button(
            onClick = { viewModel.goTo(AdvisorStep.TRUMP) },
            enabled = state.hand.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.handComplete) "Next" else "Next (${state.hand.size} cards)")
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.trumpStep(
    state: AdvisorUiState,
    viewModel: AdvisorViewModel,
) {
    item {
        Text(
            text = "Which card was turned up as the trump, and who is holding it?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    item {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            state.trump?.let { PlayingCardFace(it, width = 56.dp, highlighted = true) }
            Column {
                Text(
                    text = state.trump?.let { "${it.rank.label} of ${it.suit.id}" } ?: "No trump chosen yet",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Everything of this suit beats everything of any other.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    item {
        CardPickerGrid(
            onPick = viewModel::setTrump,
            selected = setOfNotNull(state.trump),
            suggested = state.hand.toSet(),
        )
    }

    item {
        SeatSelector(
            label = "Turned up by",
            options = state.trumpHolderChoices,
            selected = state.trumpHolder,
            onSelect = viewModel::setTrumpHolder,
        )
    }

    item {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { viewModel.goTo(AdvisorStep.HAND) }) { Text("Back") }
            Button(
                onClick = { viewModel.goTo(AdvisorStep.LEAD) },
                enabled = state.trump != null,
                modifier = Modifier.weight(1f),
            ) {
                Text("Next")
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.leadStep(
    state: AdvisorUiState,
    viewModel: AdvisorViewModel,
) {
    item {
        Text(
            text = "Who plays the first card? Seats are named by the order they play in, going " +
                "around the table from you.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    item {
        SeatSelector(
            label = "Leads the first trick",
            options = Seat.entries,
            selected = state.leader,
            onSelect = viewModel::setLeader,
        )
    }

    item {
        AgentSelector(
            selected = state.agent,
            onSelect = viewModel::setAgent,
            label = "Advising agent",
        )
    }

    item {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { viewModel.goTo(AdvisorStep.TRUMP) }) { Text("Back") }
            Button(
                onClick = viewModel::begin,
                enabled = state.handComplete,
                modifier = Modifier.weight(1f),
            ) {
                Text(if (state.handComplete) "Start tracking" else "Ten cards needed")
            }
        }
    }
}

// -------------------------------------------------------------------------------------------
// Playing
// -------------------------------------------------------------------------------------------

private fun androidx.compose.foundation.lazy.LazyListScope.playStep(
    state: AdvisorUiState,
    viewModel: AdvisorViewModel,
) {
    item {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScoreBar(
                sporting = state.scores[TeamId.SPORTING] ?: 0,
                benfica = state.scores[TeamId.BENFICA] ?: 0,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            TrumpBadge(state.trump)
        }
    }

    item { TrickStrip(state) }

    if (state.finished) {
        item {
            val us = state.scores[TeamId.SPORTING] ?: 0
            val them = state.scores[TeamId.BENFICA] ?: 0
            Panel(Modifier.fillMaxWidth(), highlighted = true) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = when {
                            us > them -> "Your side wins, $us to $them"
                            them > us -> "Your side loses, $us to $them"
                            else -> "A tie at $us all"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Button(onClick = viewModel::reset) { Text("Track another game") }
                }
            }
        }
        return
    }

    if (state.advice != null || state.thinking) {
        item {
            EnginePanel(
                advice = state.advice,
                modifier = Modifier.fillMaxWidth(),
                onCardClick = viewModel::record,
            )
        }
    }

    item {
        val seat = state.currentSeat
        SectionLabel(
            when {
                seat == null -> "Waiting"
                seat == Seat.ME -> "Your turn — tap the card you play"
                else -> "What did ${seat.label} play?"
            },
        )
    }

    if (state.currentSeat == Seat.ME) {
        item {
            HandFan(
                cards = state.hand,
                modifier = Modifier.fillMaxWidth(),
                legal = state.playable.toSet(),
                recommended = state.advice?.recommended,
                onCardClick = viewModel::record,
            )
        }
    } else {
        item {
            CardPickerGrid(
                onPick = viewModel::record,
                disabled = state.impossibleCards(),
            )
        }
    }
}

/** The current trick, one slot per seat, in playing order. */
@Composable
private fun TrickStrip(state: AdvisorUiState) {
    Panel(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Seat.entries.forEach { seat ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.height(64.dp), contentAlignment = Alignment.Center) {
                        val card = state.table[seat]
                        if (card != null) {
                            MiniCard(card, width = 42.dp)
                        } else {
                            Box(
                                Modifier
                                    .size(width = 42.dp, height = 60.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.Black.copy(alpha = 0.18f)),
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (seat.isOpponent) TeamId.BENFICA.color() else TeamId.SPORTING.color(),
                                ),
                        )
                        Text(
                            text = seat.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (seat == state.currentSeat) Gold else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (seat == state.currentSeat) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SeatSelector(
    label: String,
    options: List<Seat>,
    selected: Seat,
    onSelect: (Seat) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(label)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { seat ->
                FilterChip(
                    selected = seat == selected,
                    onClick = { onSelect(seat) },
                    label = { Text(seat.label) },
                )
            }
        }
        Text(
            text = "You and Partner are one side; Right and Left are the other.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
