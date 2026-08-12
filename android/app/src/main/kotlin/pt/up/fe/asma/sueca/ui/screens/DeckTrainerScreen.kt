package pt.up.fe.asma.sueca.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import pt.up.fe.asma.sueca.data.AppSettings
import pt.up.fe.asma.sueca.engine.Suit
import pt.up.fe.asma.sueca.ui.components.CameraSurface
import pt.up.fe.asma.sueca.ui.components.CardPickerGrid
import pt.up.fe.asma.sueca.ui.components.MiniCard
import pt.up.fe.asma.sueca.ui.components.Panel
import pt.up.fe.asma.sueca.ui.components.ScreenScaffold
import pt.up.fe.asma.sueca.ui.components.SectionLabel
import pt.up.fe.asma.sueca.ui.components.SuitGlyph
import pt.up.fe.asma.sueca.ui.theme.Gold
import pt.up.fe.asma.sueca.ui.theme.Positive
import pt.up.fe.asma.sueca.ui.theme.color
import pt.up.fe.asma.sueca.vision.ScanFrame
import kotlin.math.max

/**
 * Teaches the scanner what one particular deck looks like.
 *
 * Hold a card up, tap which card it is, repeat. Each tap files the pip shape under that suit and
 * the recogniser's reading of the index under that rank, and the live reading at the top of the
 * screen starts using them immediately.
 */
@Composable
fun DeckTrainerScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onProfileSelected: (String?) -> Unit,
    viewModel: DeckTrainerViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    var naming by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.open(settings.deckProfileId) }

    ScreenScaffold(
        title = "Train a deck",
        onBack = onBack,
        actions = {
            if (state.hasProfile) {
                IconButton(onClick = { viewModel.deleteProfile { onProfileSelected(null) } }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete this deck")
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(
                    text = "Every deck prints its pips differently, and the shapes the app ships " +
                        "with are one guess at each. Show it a few cards from yours and it will " +
                        "match against those instead.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    state.profiles.forEach { summary ->
                        FilterChip(
                            selected = summary.id == state.profileId,
                            onClick = {
                                viewModel.selectProfile(summary.id) { onProfileSelected(it) }
                            },
                            label = { Text("${summary.name} · ${summary.pips}") },
                        )
                    }
                    AssistChip(
                        onClick = { naming = true },
                        label = { Text("New deck") },
                        leadingIcon = { Icon(Icons.Default.Add, null, Modifier.size(16.dp)) },
                    )
                }
            }

            if (!state.hasProfile) {
                item {
                    Panel(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("No deck yet", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Make one, then show it four cards — one of each suit is " +
                                    "already enough to help.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(onClick = { naming = true }) { Text("Make a deck") }
                        }
                    }
                }
                return@LazyColumn
            }

            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black),
                ) {
                    CameraSurface(scanner = viewModel.scanner, modifier = Modifier.fillMaxSize()) {
                        FocusOverlay(state.frame, Modifier.fillMaxSize())
                    }
                    Text(
                        text = state.reading,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (state.frame.focused?.card != null) Positive else Gold,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(10.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black.copy(alpha = 0.45f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }

            item { CoverageRow(state, viewModel::forgetSuit) }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Tap the card you are holding")
                    Spacer(Modifier.weight(1f))
                    state.lastLearned?.let { MiniCard(it, width = 26.dp) }
                }
            }

            item {
                CardPickerGrid(
                    onPick = viewModel::confirm,
                    suggested = setOfNotNull(state.frame.focused?.card),
                )
            }

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

            item {
                Text(
                    text = if (state.ready) {
                        "All four suits trained. The scanner is matching against this deck now."
                    } else {
                        "Once every suit has at least one example, the scanner starts matching " +
                            "against this deck instead of the built-in shapes."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.ready) Positive else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (naming) {
        NameDeckDialog(
            onDismiss = { naming = false },
            onConfirm = { name ->
                naming = false
                viewModel.createProfile(name) { onProfileSelected(it) }
            },
        )
    }
}

@Composable
private fun CoverageRow(state: DeckTrainerUiState, onForget: (Suit) -> Unit) {
    Panel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Suit.entries.forEach { suit ->
                    val learned = state.coverage[suit] ?: 0
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        SuitGlyph(
                            suit = suit,
                            size = 20.dp,
                            color = if (learned > 0) suit.color() else Color.White.copy(alpha = 0.25f),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "$learned",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (learned > 0) Positive else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                        )
                        if (learned > 0) {
                            TextButton(onClick = { onForget(suit) }) {
                                Text("clear", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
            Text(
                text = "${state.tokensLearned} rank glyph${if (state.tokensLearned == 1) "" else "s"} learned",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A box around whatever the trainer would learn from if you tapped a card right now. */
@Composable
private fun FocusOverlay(frame: ScanFrame, modifier: Modifier = Modifier) {
    val sample = frame.focused ?: return
    if (frame.imageWidth == 0 || frame.imageHeight == 0) return

    Canvas(modifier) {
        val scale = max(size.width / frame.imageWidth, size.height / frame.imageHeight)
        val drawnWidth = frame.imageWidth * scale
        val drawnHeight = frame.imageHeight * scale
        val offsetX = (size.width - drawnWidth) / 2f
        val offsetY = (size.height - drawnHeight) / 2f

        val left = offsetX + sample.bounds.left * drawnWidth
        val top = offsetY + sample.bounds.top * drawnHeight
        val right = offsetX + sample.bounds.right * drawnWidth
        val bottom = offsetY + sample.bounds.bottom * drawnHeight

        drawRoundRect(
            color = Gold,
            topLeft = Offset(left - 8f, top - 8f),
            size = Size((right - left) + 16f, (bottom - top) + 16f),
            cornerRadius = CornerRadius(12f, 12f),
            style = Stroke(width = 4f),
        )
    }
}

@Composable
private fun NameDeckDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Name this deck") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Something you will recognise later, like the brand on the box.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Deck") },
                    modifier = Modifier.width(240.dp),
                )
            }
        },
        confirmButton = { Button(onClick = { onConfirm(name) }) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
