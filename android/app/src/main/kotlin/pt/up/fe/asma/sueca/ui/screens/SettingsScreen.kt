package pt.up.fe.asma.sueca.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pt.up.fe.asma.sueca.data.AppSettings
import pt.up.fe.asma.sueca.data.SettingsViewModel
import pt.up.fe.asma.sueca.ui.components.AgentSelector
import pt.up.fe.asma.sueca.ui.components.Panel
import pt.up.fe.asma.sueca.ui.components.ScreenScaffold
import pt.up.fe.asma.sueca.ui.components.SectionLabel

@Composable
fun SettingsScreen(
    settings: AppSettings,
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    ScreenScaffold(title = "Settings", onBack = onBack) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                AgentSelector(
                    selected = settings.advisorAgent,
                    onSelect = viewModel::setAdvisorAgent,
                    label = "Agent that advises you",
                )
            }

            item {
                AgentSelector(
                    selected = settings.partnerAgent,
                    onSelect = viewModel::setPartnerAgent,
                    label = "Your partner",
                )
            }

            item {
                AgentSelector(
                    selected = settings.opponentAgent,
                    onSelect = viewModel::setOpponentAgent,
                    label = "Both opponents",
                )
            }

            item {
                Toggle(
                    title = "Fair play",
                    description = "Agents reason only from what they have seen. Turn it off to " +
                        "restore the simulator's behaviour, where the Deck Predictor reads the other hands.",
                    checked = settings.fairPlay,
                    onCheckedChange = viewModel::setFairPlay,
                )
            }

            item {
                Toggle(
                    title = "Show hints while playing",
                    description = "Highlights the recommended card and keeps the engine strip on screen.",
                    checked = settings.showHints,
                    onCheckedChange = viewModel::setShowHints,
                )
            }

            item {
                Panel(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionLabel("Agent pace")
                        Text(
                            text = "${settings.agentDelayMillis} ms between plays",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Slider(
                            value = settings.agentDelayMillis.toFloat(),
                            onValueChange = { viewModel.setAgentDelay(it.toInt()) },
                            valueRange = 100f..2_000f,
                            steps = 18,
                        )
                    }
                }
            }

            item {
                Text(
                    text = "Rules, agents and belief tables are a port of the Python simulator in " +
                        "this repository. Card recognition runs entirely on the device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Toggle(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Panel(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.padding(horizontal = 6.dp))
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
