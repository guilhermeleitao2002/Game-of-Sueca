package pt.up.fe.asma.sueca.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.sp
import pt.up.fe.asma.sueca.engine.Advice
import pt.up.fe.asma.sueca.engine.AgentKind
import pt.up.fe.asma.sueca.engine.Card
import pt.up.fe.asma.sueca.engine.MoveEval
import pt.up.fe.asma.sueca.engine.TeamId
import pt.up.fe.asma.sueca.ui.theme.Gold
import pt.up.fe.asma.sueca.ui.theme.NumberStyle
import pt.up.fe.asma.sueca.ui.theme.Negative
import pt.up.fe.asma.sueca.ui.theme.Positive
import pt.up.fe.asma.sueca.ui.theme.color
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

fun Double.signedPoints(): String = String.format(Locale.US, "%+.2f", this)

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(Locale.US),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** A softly raised panel; the app's one container style. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = Color.Black.copy(alpha = 0.22f),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (highlighted) Gold.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.08f),
        ),
        content = content,
    )
}

/** One agent, as a chip. */
@Composable
fun AgentChip(
    agent: AgentKind,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        label = { Text(agent.displayName) },
        leadingIcon = if (agent == AgentKind.PREDICTOR) {
            { Icon(Icons.Default.AutoAwesome, contentDescription = null, Modifier.size(16.dp)) }
        } else {
            null
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Gold.copy(alpha = 0.22f),
            selectedLabelColor = Gold,
            selectedLeadingIconColor = Gold,
        ),
    )
}

/** The full agent picker, with the tagline of whichever one is selected. */
@Composable
fun AgentSelector(
    selected: AgentKind,
    onSelect: (AgentKind) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (label != null) SectionLabel(label)
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AgentKind.entries.forEach { agent ->
                AgentChip(agent, agent == selected, { onSelect(agent) })
            }
        }
        Text(
            text = selected.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Score line for the two teams, with the bar showing who is ahead of the 60 point line. */
@Composable
fun ScoreBar(sporting: Int, benfica: Int, modifier: Modifier = Modifier) {
    val total = (sporting + benfica).coerceAtLeast(1)
    val fraction by animateFloatAsState(sporting.toFloat() / total, label = "score")

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TeamScore(TeamId.SPORTING, sporting)
            Text(
                text = "60 to win",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TeamScore(TeamId.BENFICA, benfica)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .background(TeamId.BENFICA.color().copy(alpha = 0.55f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(8.dp)
                    .background(TeamId.SPORTING.color()),
            )
        }
    }
}

@Composable
private fun TeamScore(team: TeamId, score: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(team.color()),
        )
        Text(
            text = "${team.displayName} $score",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * One legal card, scored: the card, a bar running either side of zero, and the number.
 *
 * The bar is what makes an agent's judgement legible at a glance, the same way an evaluation
 * bar does next to a chess board.
 */
@Composable
fun EvalRow(
    eval: MoveEval,
    scale: Double,
    modifier: Modifier = Modifier,
    recommended: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val fraction = (abs(eval.expectedPoints) / scale).coerceIn(0.0, 1.0).toFloat()
    val positive = eval.expectedPoints >= 0

    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (recommended) Modifier.background(Gold.copy(alpha = 0.10f)) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MiniCard(eval.card, width = 30.dp)

        Box(
            Modifier
                .weight(1f)
                .height(10.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.07f)),
        ) {
            // Zero sits in the middle, so a card that hands points to the other side reads red
            // and to the left, and one that wins them reads green and to the right.
            Box(
                // The half the bar lives in, anchored so that it always grows away from the
                // middle: right and green when the points come to you, left and red when they go.
                Modifier
                    .align(if (positive) Alignment.CenterEnd else Alignment.CenterStart)
                    .fillMaxWidth(0.5f)
                    .height(10.dp),
                contentAlignment = if (positive) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(10.dp)
                        .clip(CircleShape)
                        .background(if (positive) Positive else Negative),
                )
            }
            Box(
                Modifier
                    .align(Alignment.Center)
                    .width(1.dp)
                    .height(10.dp)
                    .background(Color.White.copy(alpha = 0.25f)),
            )
        }

        Text(
            text = if (eval.penalised) "held" else eval.expectedPoints.signedPoints(),
            style = NumberStyle,
            color = when {
                eval.penalised -> MaterialTheme.colorScheme.onSurfaceVariant
                positive -> Positive
                else -> Negative
            },
            modifier = Modifier.width(56.dp),
        )
    }
}

/**
 * The engine's answer for the current position.
 *
 * Deliberately shows two things at once: what the chosen agent would play, and what the
 * expected trick search thinks of every legal card. When those disagree, that disagreement is
 * the interesting part.
 */
@Composable
fun EnginePanel(
    advice: Advice?,
    modifier: Modifier = Modifier,
    onCardClick: ((Card) -> Unit)? = null,
    onChangeAgent: (() -> Unit)? = null,
) {
    Panel(modifier, highlighted = true) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (advice == null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.AutoAwesome, null, tint = Gold, modifier = Modifier.size(18.dp))
                    Text("Waiting for your turn", style = MaterialTheme.typography.titleSmall)
                }
                return@Column
            }

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.AutoAwesome, null, tint = Gold, modifier = Modifier.size(18.dp))
                    Text(advice.agent.displayName, style = MaterialTheme.typography.titleSmall, color = Gold)
                }
                if (onChangeAgent != null) {
                    TextButton(onClick = onChangeAgent) { Text("Change") }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiniCard(advice.recommended, width = 44.dp, selected = true)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Play ${advice.recommended.label}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = advice.reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (!advice.agreesWithSearch) {
                Text(
                    text = "The search prefers ${advice.searchBest.card.label} " +
                        "(${advice.searchBest.expectedPoints.signedPoints()} expected points).",
                    style = MaterialTheme.typography.bodySmall,
                    color = Gold.copy(alpha = 0.9f),
                )
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

            val scale = max(1.0, advice.evaluations.maxOfOrNull { abs(it.expectedPoints) } ?: 1.0)
            advice.evaluations.forEach { eval ->
                EvalRow(
                    eval = eval,
                    scale = scale,
                    recommended = eval.card == advice.recommended,
                    onClick = onCardClick?.let { click -> { click(eval.card) } },
                )
            }

            if (advice.alternatives.isNotEmpty()) {
                ExpandableAgentComparison(advice)
            }
        }
    }
}

@Composable
private fun ExpandableAgentComparison(advice: Advice) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "Hide the other agents" else "What would the other agents play?")
            Spacer(Modifier.width(6.dp))
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
        }
        AnimatedVisibility(expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                advice.alternatives.forEach { pick ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        MiniCard(pick.card, width = 26.dp)
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = pick.agent.displayName,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (pick.agent == advice.agent) Gold else MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = pick.reason,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The trump card, pinned in a corner of the table. */
@Composable
fun TrumpBadge(card: Card?, modifier: Modifier = Modifier) {
    if (card == null) return
    Row(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.3f))
            .border(1.dp, Gold.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column {
            Text("TRUMP", style = MaterialTheme.typography.labelSmall, color = Gold, fontSize = 9.sp)
            Text(
                text = card.rank.label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        SuitGlyph(card.suit, 18.dp, color = if (card.suit.isRed) Negative else Color.White)
    }
}

