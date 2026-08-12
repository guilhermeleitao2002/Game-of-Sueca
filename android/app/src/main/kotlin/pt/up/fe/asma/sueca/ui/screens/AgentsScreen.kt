package pt.up.fe.asma.sueca.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pt.up.fe.asma.sueca.engine.AgentKind
import pt.up.fe.asma.sueca.ui.components.Panel
import pt.up.fe.asma.sueca.ui.components.ScreenScaffold
import pt.up.fe.asma.sueca.ui.theme.Gold

/** What each agent believes about the table. */
private fun AgentKind.knowledge(): String = when (this) {
    AgentKind.RANDOM, AgentKind.GREEDY -> "Looks at nothing but its own hand."
    AgentKind.MAX_POINTS, AgentKind.MAX_ROUNDS -> "Looks at its hand and at the cards already on the table."
    AgentKind.COOPERATIVE -> "Keeps a probability for every card in every hand, and uses its partner's."
    AgentKind.PREDICTOR -> "Keeps the same probabilities and searches every answer the table could give."
}

@Composable
fun AgentsScreen(onBack: () -> Unit) {
    ScreenScaffold(title = "The agents", onBack = onBack) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = "Six strategies, ported from the ASMA simulator. Any of them can drive " +
                        "the opponents, and any of them can be the one advising you.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            items(AgentKind.entries) { agent ->
                Panel(Modifier.fillMaxWidth(), highlighted = agent == AgentKind.DEFAULT) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (agent == AgentKind.PREDICTOR) {
                                    Icons.Default.AutoAwesome
                                } else {
                                    Icons.Default.Groups
                                },
                                contentDescription = null,
                                tint = if (agent == AgentKind.DEFAULT) Gold else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(agent.displayName, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = agent.tagline,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Gold.copy(alpha = 0.85f),
                                )
                            }
                        }

                        Text(
                            text = agent.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = agent.knowledge(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "sueca.py -s ${agent.cliName}",
                            style = pt.up.fe.asma.sueca.ui.theme.NumberStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
