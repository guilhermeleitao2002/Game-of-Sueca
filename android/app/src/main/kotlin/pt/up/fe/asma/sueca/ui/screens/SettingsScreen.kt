package pt.up.fe.asma.sueca.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import pt.up.fe.asma.sueca.data.AppSettings
import pt.up.fe.asma.sueca.data.DeckProfileStore
import pt.up.fe.asma.sueca.data.SettingsViewModel
import pt.up.fe.asma.sueca.ui.components.AgentSelector
import pt.up.fe.asma.sueca.ui.components.Panel
import pt.up.fe.asma.sueca.ui.components.ScreenScaffold
import pt.up.fe.asma.sueca.ui.components.SectionLabel
import pt.up.fe.asma.sueca.ui.theme.Positive

@Composable
fun SettingsScreen(
    settings: AppSettings,
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onTrainDeck: () -> Unit = {},
) {
    val context = LocalContext.current
    val decks = remember { DeckProfileStore(context).list() }

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
                    title = "Confirm before playing",
                    description = "Asks before a card leaves your hand. Cards on the table cannot " +
                        "be taken back, and a fan of ten is easy to mis-tap.",
                    checked = settings.confirmPlays,
                    onCheckedChange = viewModel::setConfirmPlays,
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
                Panel(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionLabel("Card scanner")
                        Text(
                            text = "Which trained deck the camera matches against. Without one it " +
                                "uses the suit shapes the app ships with.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(
                                selected = settings.deckProfileId == null,
                                onClick = { viewModel.setDeckProfile(null) },
                                label = { Text("Built-in shapes") },
                            )
                            decks.forEach { deck ->
                                FilterChip(
                                    selected = deck.id == settings.deckProfileId,
                                    onClick = { viewModel.setDeckProfile(deck.id) },
                                    label = { Text("${deck.name} · ${deck.pips}") },
                                )
                            }
                        }
                        TextButton(onClick = onTrainDeck) { Text("Train a deck") }
                    }
                }
            }

            item { CloudReaderPanel(settings, viewModel) }

            item {
                Text(
                    text = "Rules, agents and belief tables are a port of the Python simulator in " +
                        "this repository. Card recognition runs on the device unless you turn on " +
                        "the cloud reader above.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The optional cloud reader.
 *
 * Everything here is framed around one fact: the key is the user's, not the app's. This app is
 * distributed as a public APK, so a bundled key would be extractable by anyone who downloaded
 * it and charged to whoever owned it — which is why there is a text field here instead.
 */
@Composable
private fun CloudReaderPanel(settings: AppSettings, viewModel: SettingsViewModel) {
    var typed by remember(settings.claudeApiKey) { mutableStateOf(settings.claudeApiKey.orEmpty()) }
    var visible by remember { mutableStateOf(false) }

    Panel(Modifier.fillMaxWidth(), highlighted = settings.cloudReaderEnabled) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("Cloud card reader (optional)")
            Text(
                text = "Stuck on a deck the phone cannot read? With an Anthropic API key, the " +
                    "scanner grows a button that photographs your hand and has Claude read every " +
                    "card at once.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "It is not free: each photo costs roughly one to two US cents, billed to " +
                    "your key. The photo leaves the phone; nothing else in the app ever does. " +
                    "Your key is stored only on this device and no key is shipped with the app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it },
                label = { Text("sk-ant-…") },
                singleLine = true,
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    TextButton(onClick = { visible = !visible }) {
                        Text(if (visible) "Hide" else "Show", style = MaterialTheme.typography.labelSmall)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.setClaudeApiKey(typed) },
                    enabled = typed.isNotBlank() && typed != settings.claudeApiKey,
                ) {
                    Text("Save key")
                }
                if (settings.cloudReaderEnabled) {
                    TextButton(onClick = {
                        typed = ""
                        viewModel.setClaudeApiKey("")
                    }) {
                        Text("Remove")
                    }
                }
            }

            Text(
                text = if (settings.cloudReaderEnabled) {
                    "On. Get keys at console.anthropic.com."
                } else {
                    "Off — the scanner stays fully on-device. Keys come from console.anthropic.com."
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (settings.cloudReaderEnabled) Positive else MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
