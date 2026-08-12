package pt.up.fe.asma.sueca.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import pt.up.fe.asma.sueca.engine.SimulationSummary
import pt.up.fe.asma.sueca.engine.TeamId
import pt.up.fe.asma.sueca.ui.components.AgentSelector
import pt.up.fe.asma.sueca.ui.components.Panel
import pt.up.fe.asma.sueca.ui.components.ScreenScaffold
import pt.up.fe.asma.sueca.ui.components.SectionLabel
import pt.up.fe.asma.sueca.ui.theme.Gold
import pt.up.fe.asma.sueca.ui.theme.NumberStyle
import pt.up.fe.asma.sueca.ui.theme.color
import java.util.Locale

private val GAME_COUNTS = listOf(25, 100, 500, 2_000)

@Composable
fun SimulatorScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    viewModel: SimulatorViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()

    ScreenScaffold(title = "Simulator", onBack = onBack) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    text = "The same experiment as the report: two strategies, N games, and the " +
                        "points each side converted out of the hands it was dealt.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                AgentSelector(
                    selected = state.sporting,
                    onSelect = viewModel::setSporting,
                    label = "Sporting",
                )
            }

            item {
                AgentSelector(
                    selected = state.benfica,
                    onSelect = viewModel::setBenfica,
                    label = "Benfica",
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("Games")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GAME_COUNTS.forEach { count ->
                            FilterChip(
                                selected = state.games == count,
                                onClick = { viewModel.setGames(count) },
                                label = { Text("$count") },
                            )
                        }
                    }
                }
            }

            item {
                if (state.running) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = "${(state.progress * state.games).toInt()} of ${state.games} games",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(onClick = viewModel::cancel) { Text("Stop") }
                    }
                } else {
                    Button(onClick = viewModel::run, modifier = Modifier.fillMaxWidth()) {
                        Text("Run ${state.games} games")
                    }
                }
            }

            state.summary?.let { summary ->
                item { ResultsPanel(summary) }
            }
        }
    }
}

@Composable
private fun ResultsPanel(summary: SimulationSummary) {
    Panel(Modifier.fillMaxWidth(), highlighted = true) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = "${summary.sportingAgent.displayName} vs ${summary.benficaAgent.displayName}",
                style = MaterialTheme.typography.titleMedium,
                color = Gold,
            )

            val sporting = summary.winsOf(TeamId.SPORTING)
            val benfica = summary.winsOf(TeamId.BENFICA)
            val total = (sporting + benfica + summary.ties).coerceAtLeast(1)

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f)),
            ) {
                Row(Modifier.fillMaxWidth()) {
                    Box(
                        Modifier
                            .weight(sporting.toFloat().coerceAtLeast(0.001f) / total)
                            .height(14.dp)
                            .background(TeamId.SPORTING.color()),
                    )
                    Box(
                        Modifier
                            .weight(summary.ties.toFloat().coerceAtLeast(0.001f) / total)
                            .height(14.dp)
                            .background(Color.Gray),
                    )
                    Box(
                        Modifier
                            .weight(benfica.toFloat().coerceAtLeast(0.001f) / total)
                            .height(14.dp)
                            .background(TeamId.BENFICA.color()),
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("$sporting wins", style = MaterialTheme.typography.labelLarge, color = TeamId.SPORTING.color())
                Text("${summary.ties} ties", style = MaterialTheme.typography.labelLarge, color = Color.Gray)
                Text("$benfica wins", style = MaterialTheme.typography.labelLarge, color = TeamId.BENFICA.color())
            }

            TeamId.entries.forEach { team ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "${team.displayName} · ${summary.agentOf(team).displayName}",
                        style = MaterialTheme.typography.labelLarge,
                        color = team.color(),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Stat("win rate", percent(summary.winRate(team)))
                    Stat("average points per game", format(summary.averagePoints(team)))
                    Stat("converted points", format(summary.convertedPoints(team), signed = true))
                }
            }

            Text(
                text = "${summary.games} games in ${summary.elapsedMillis} ms. Converted points is what " +
                    "a side captured minus what it was dealt, so it measures the strategy and not the luck.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Text(text = value, style = NumberStyle, color = MaterialTheme.colorScheme.onSurface)
    }
}

private fun percent(value: Double) = String.format(Locale.US, "%.1f%%", value * 100)

private fun format(value: Double, signed: Boolean = false) =
    String.format(Locale.US, if (signed) "%+.2f" else "%.2f", value)
