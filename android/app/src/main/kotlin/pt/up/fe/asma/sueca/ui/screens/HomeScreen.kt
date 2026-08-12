package pt.up.fe.asma.sueca.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pt.up.fe.asma.sueca.Routes
import pt.up.fe.asma.sueca.data.AppSettings
import pt.up.fe.asma.sueca.engine.Card
import pt.up.fe.asma.sueca.engine.Rank
import pt.up.fe.asma.sueca.engine.Suit
import pt.up.fe.asma.sueca.ui.components.Panel
import pt.up.fe.asma.sueca.ui.components.PlayingCardFace
import pt.up.fe.asma.sueca.ui.theme.Gold
import pt.up.fe.asma.sueca.ui.theme.tableBackground

private data class Destination(
    val route: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val featured: Boolean = false,
)

@Composable
fun HomeScreen(settings: AppSettings, onNavigate: (String) -> Unit) {
    val destinations = listOf(
        Destination(
            route = Routes.ADVISOR,
            title = "Table advisor",
            description = "Playing with real cards? Scan your hand, tap in what gets played, " +
                "and the engine tells you what to play next.",
            icon = Icons.Default.AutoAwesome,
            featured = true,
        ),
        Destination(
            route = Routes.PLAY,
            title = "Play a game",
            description = "Four seats, three agents and you. The engine advises while you play.",
            icon = Icons.Default.PlayArrow,
        ),
        Destination(
            route = Routes.SCAN,
            title = "Scan cards",
            description = "Point the camera at a card and watch it get read, rank and suit.",
            icon = Icons.Default.PhotoCamera,
        ),
        Destination(
            route = Routes.SIMULATOR,
            title = "Simulator",
            description = "Run agent against agent for thousands of games, like the report does.",
            icon = Icons.Default.QueryStats,
        ),
        Destination(
            route = Routes.AGENTS,
            title = "The agents",
            description = "What each of the six strategies actually does.",
            icon = Icons.Default.Groups,
        ),
        Destination(
            route = Routes.SETTINGS,
            title = "Settings",
            description = "Advisor agent, opponents, fair play.",
            icon = Icons.Default.Settings,
        ),
    )

    Box(Modifier.fillMaxSize().tableBackground()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 48.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Header(settings) }
            items(destinations) { destination ->
                DestinationRow(destination) { onNavigate(destination.route) }
            }
            item {
                Text(
                    text = "Sueca engine and agents ported from the ASMA simulator. " +
                        "Everything runs on the phone: no account, no network.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun Header(settings: AppSettings) {
    Column(Modifier.padding(bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Sueca",
                    style = MaterialTheme.typography.displaySmall,
                    color = Gold,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "engine",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Box(Modifier.size(width = 96.dp, height = 104.dp)) {
                PlayingCardFace(
                    card = Card(Suit.SPADES, Rank.SEVEN),
                    width = 58.dp,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(top = 8.dp),
                )
                PlayingCardFace(
                    card = Card(Suit.HEARTS, Rank.ACE),
                    width = 58.dp,
                    modifier = Modifier.align(Alignment.CenterEnd),
                    highlighted = true,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Advising with the ${settings.advisorAgent.displayName}",
            style = MaterialTheme.typography.labelLarge,
            color = Gold.copy(alpha = 0.85f),
        )
    }
}

@Composable
private fun DestinationRow(destination: Destination, onClick: () -> Unit) {
    Panel(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        highlighted = destination.featured,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = destination.icon,
                contentDescription = null,
                tint = if (destination.featured) Gold else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = destination.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = destination.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
