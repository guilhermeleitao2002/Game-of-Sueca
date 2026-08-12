package pt.up.fe.asma.sueca.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import pt.up.fe.asma.sueca.data.AppSettings
import pt.up.fe.asma.sueca.engine.Card
import pt.up.fe.asma.sueca.ui.components.CameraSurface
import pt.up.fe.asma.sueca.ui.components.MiniCard
import pt.up.fe.asma.sueca.ui.components.Panel
import pt.up.fe.asma.sueca.ui.components.ScreenScaffold
import pt.up.fe.asma.sueca.ui.components.SectionLabel
import pt.up.fe.asma.sueca.ui.theme.Gold
import pt.up.fe.asma.sueca.ui.theme.Positive
import pt.up.fe.asma.sueca.vision.ScanFrame
import kotlin.math.max

@Composable
fun ScanScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onUse: (List<Card>) -> Unit,
    onTrainDeck: () -> Unit,
    viewModel: ScanViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(settings.deckProfileId) { viewModel.useProfile(settings.deckProfileId) }

    ScreenScaffold(
        title = "Scan cards",
        onBack = onBack,
        actions = {
            IconButton(onClick = onTrainDeck) {
                Icon(Icons.Default.School, contentDescription = "Train this deck")
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CameraSurface(
                scanner = viewModel.scanner,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black),
            ) {
                DetectionOverlay(state.frame, Modifier.fillMaxSize())
            }

            Panel(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Column(
                    Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel("Found ${state.collected.size}")
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = viewModel::clear) { Text("Clear") }
                    }

                    if (state.collected.isEmpty()) {
                        Text(
                            text = "Hold the phone over the cards so the index corners are visible. " +
                                "Each card has to be seen a few frames in a row before it counts.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            state.collected.forEach { card ->
                                MiniCard(card, width = 38.dp, onClick = { viewModel.remove(card) })
                            }
                        }
                        Text(
                            text = "Tap a card to throw it out. Reading your deck badly? " +
                                "Train it from the button up top.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Button(
                        onClick = { onUse(state.collected) },
                        enabled = state.collected.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Use these ${state.collected.size} cards")
                    }
                }
            }
        }
    }
}

/**
 * Boxes over whatever the last frame recognised.
 *
 * The analysis frame and the preview rarely have the same aspect ratio, and the preview is
 * cropped to fill, so the boxes are mapped through the same fill-centre transform to end up
 * over the right cards.
 */
@Composable
private fun DetectionOverlay(frame: ScanFrame, modifier: Modifier = Modifier) {
    if (frame.imageWidth == 0 || frame.imageHeight == 0) return

    Canvas(modifier) {
        val scale = max(size.width / frame.imageWidth, size.height / frame.imageHeight)
        val drawnWidth = frame.imageWidth * scale
        val drawnHeight = frame.imageHeight * scale
        val offsetX = (size.width - drawnWidth) / 2f
        val offsetY = (size.height - drawnHeight) / 2f

        for (detection in frame.detections) {
            val left = offsetX + detection.bounds.left * drawnWidth
            val top = offsetY + detection.bounds.top * drawnHeight
            val right = offsetX + detection.bounds.right * drawnWidth
            val bottom = offsetY + detection.bounds.bottom * drawnHeight

            drawRoundRect(
                color = if (detection.confidence > 0.7) Positive else Gold,
                topLeft = Offset(left - 6f, top - 6f),
                size = Size((right - left) + 12f, (bottom - top) + 12f),
                cornerRadius = CornerRadius(10f, 10f),
                style = Stroke(width = 3f),
            )
        }
    }
}
