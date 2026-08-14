package pt.up.fe.asma.sueca.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import pt.up.fe.asma.sueca.engine.Card
import pt.up.fe.asma.sueca.engine.Rank
import pt.up.fe.asma.sueca.engine.Suit
import pt.up.fe.asma.sueca.shapes.PathCommand
import pt.up.fe.asma.sueca.shapes.ShapeSpec
import pt.up.fe.asma.sueca.shapes.SuitShapes
import pt.up.fe.asma.sueca.ui.theme.CardEdge
import pt.up.fe.asma.sueca.ui.theme.CardFace
import pt.up.fe.asma.sueca.ui.theme.CardFaceShade
import pt.up.fe.asma.sueca.ui.theme.Felt
import pt.up.fe.asma.sueca.ui.theme.FeltLight
import pt.up.fe.asma.sueca.ui.theme.Gold
import pt.up.fe.asma.sueca.ui.theme.color
import pt.up.fe.asma.sueca.ui.theme.colorOnDark
import kotlin.math.abs

/** Cards are drawn, never bitmapped: the outlines come straight out of the shared shape module. */
const val CARD_ASPECT = 1.45f

/** Converts a [ShapeSpec] from the engine into something Compose can fill. */
fun ShapeSpec.toPath(width: Float, height: Float): Path {
    val path = Path()
    for (subPath in subPaths) {
        for (command in subPath.commands) {
            when (command) {
                is PathCommand.MoveTo -> path.moveTo(command.x * width, command.y * height)
                is PathCommand.LineTo -> path.lineTo(command.x * width, command.y * height)
                is PathCommand.CubicTo -> path.cubicTo(
                    command.x1 * width, command.y1 * height,
                    command.x2 * width, command.y2 * height,
                    command.x3 * width, command.y3 * height,
                )

                PathCommand.Close -> path.close()
            }
        }
    }
    return path
}

fun DrawScope.drawSuit(suit: Suit, topLeft: Offset, size: Size, color: Color, flipped: Boolean = false) {
    val path = SuitShapes.of(suit).toPath(size.width, size.height)
    withTransform({
        translate(topLeft.x, topLeft.y)
        if (flipped) {
            rotate(180f, Offset(size.width / 2f, size.height / 2f))
        }
    }) {
        drawPath(path, color)
    }
}

/**
 * A suit symbol on its own, for chips, legends and the trump badge.
 *
 * Every one of those sits on the felt rather than on a card, hence [colorOnDark] by default.
 */
@Composable
fun SuitGlyph(suit: Suit, size: Dp, modifier: Modifier = Modifier, color: Color = suit.colorOnDark()) {
    Canvas(modifier.size(size)) {
        drawSuit(suit, Offset.Zero, Size(this.size.width, this.size.height), color)
    }
}

/** Where the pips go in the middle of the card, as fractions of the inner area. */
private fun pipLayout(rank: Rank): List<Triple<Float, Float, Boolean>> = when (rank) {
    Rank.TWO -> listOf(t(0.5f, 0.06f), t(0.5f, 0.94f, true))
    Rank.THREE -> listOf(t(0.5f, 0.06f), t(0.5f, 0.5f), t(0.5f, 0.94f, true))
    Rank.FOUR -> listOf(t(0.25f, 0.06f), t(0.75f, 0.06f), t(0.25f, 0.94f, true), t(0.75f, 0.94f, true))
    Rank.FIVE -> listOf(
        t(0.25f, 0.06f), t(0.75f, 0.06f), t(0.5f, 0.5f),
        t(0.25f, 0.94f, true), t(0.75f, 0.94f, true),
    )

    Rank.SIX -> listOf(
        t(0.25f, 0.06f), t(0.75f, 0.06f), t(0.25f, 0.5f), t(0.75f, 0.5f),
        t(0.25f, 0.94f, true), t(0.75f, 0.94f, true),
    )

    Rank.SEVEN -> listOf(
        t(0.25f, 0.06f), t(0.75f, 0.06f), t(0.5f, 0.28f), t(0.25f, 0.5f), t(0.75f, 0.5f),
        t(0.25f, 0.94f, true), t(0.75f, 0.94f, true),
    )

    else -> emptyList()
}

private fun t(x: Float, y: Float, flipped: Boolean = false) = Triple(x, y, flipped)

/**
 * A single card, drawn from vectors at whatever size it is asked for.
 *
 * @param highlighted the engine's pick, which gets a gold rim and lifts slightly.
 * @param dimmed a card that cannot legally be played right now.
 */
@Composable
fun PlayingCardFace(
    card: Card,
    width: Dp,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    dimmed: Boolean = false,
    elevation: Dp = 6.dp,
    onClick: (() -> Unit)? = null,
) {
    val height = width * CARD_ASPECT
    val shape = RoundedCornerShape(width * 0.08f)
    val suitColor = card.suit.color()
    val density = LocalDensity.current

    val glow by animateFloatAsState(if (highlighted) 1f else 0f, label = "cardGlow")
    // A card that is not raised in the first place does not get raised further for being the
    // engine's pick; in a fan the gold rim and the lift say that already.
    val raise = if (elevation > 0.dp && highlighted) elevation + 6.dp else elevation

    Box(
        modifier = modifier
            .size(width, height)
            // Card stock is opaque, and this is the whole of what keeps it that way: a plain
            // clip, a plain opaque fill, nothing exotic in between. It used to ask for a shadow
            // with `clip = false`, which was the one structural difference between this and
            // CardBack — the one card that never came out see-through.
            // Enough to read as unavailable, not so much that it stops reading as a card.
            .then(if (dimmed) Modifier.alpha(0.55f) else Modifier)
            .then(if (raise > 0.dp) Modifier.shadow(raise, shape) else Modifier)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(CardFace, CardFaceShade)))
            .border(
                width = if (highlighted) width * 0.035f else 1.dp,
                color = androidx.compose.ui.graphics.lerp(CardEdge, Gold, glow),
                shape = shape,
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        // Middle of the card: pips for the numbers, a panel for the court cards.
        if (card.rank == Rank.ACE) {
            Canvas(Modifier.fillMaxSize()) {
                val pip = size.width * 0.46f
                drawSuit(
                    suit = card.suit,
                    topLeft = Offset((size.width - pip) / 2f, (size.height - pip * 1.05f) / 2f),
                    size = Size(pip, pip * 1.05f),
                    color = suitColor,
                )
            }
        } else if (card.rank.points > 0 && card.rank != Rank.SEVEN) {
            CourtPanel(card, width, suitColor)
        } else {
            Canvas(
                Modifier
                    .fillMaxSize()
                    // Leaves room for the index corners, which sit outside this area.
                    .padding(horizontal = width * 0.16f, vertical = width * 0.22f),
            ) {
                // One pip size for every rank, as a real deck does. Sized so the two columns
                // of a six or a seven clear each other.
                val pip = size.width * 0.36f
                val pipHeight = pip * 1.05f
                for ((x, y, flipped) in pipLayout(card.rank)) {
                    drawSuit(
                        suit = card.suit,
                        topLeft = Offset(
                            x * size.width - pip / 2f,
                            y * (size.height - pipHeight),
                        ),
                        size = Size(pip, pipHeight),
                        color = suitColor,
                        flipped = flipped,
                    )
                }
            }
        }

        CornerIndex(card, width, suitColor, density, Modifier.align(Alignment.TopStart))
        CornerIndex(
            card, width, suitColor, density,
            Modifier
                .align(Alignment.BottomEnd)
                .rotate(180f),
        )
    }
}

@Composable
private fun CourtPanel(card: Card, width: Dp, suitColor: Color) {
    val density = LocalDensity.current
    Box(
        Modifier
            .fillMaxSize()
            .padding(horizontal = width * 0.17f, vertical = width * 0.22f)
            .clip(RoundedCornerShape(width * 0.04f))
            .background(suitColor.copy(alpha = 0.07f))
            .border(1.dp, suitColor.copy(alpha = 0.35f), RoundedCornerShape(width * 0.04f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = card.rank.label,
            color = suitColor,
            fontWeight = FontWeight.Bold,
            fontSize = with(density) { (width * 0.44f).toSp() },
            lineHeight = with(density) { (width * 0.44f).toSp() },
        )
        Canvas(
            Modifier
                .align(Alignment.TopStart)
                .size(width * 0.16f)
                .offset(x = width * 0.03f, y = width * 0.03f),
        ) {
            drawSuit(card.suit, Offset.Zero, Size(size.width, size.height), suitColor)
        }
        Canvas(
            Modifier
                .align(Alignment.BottomEnd)
                .size(width * 0.16f)
                .offset(x = -width * 0.03f, y = -width * 0.03f),
        ) {
            drawSuit(card.suit, Offset.Zero, Size(size.width, size.height), suitColor, flipped = true)
        }
    }
}

@Composable
private fun CornerIndex(
    card: Card,
    width: Dp,
    suitColor: Color,
    density: androidx.compose.ui.unit.Density,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(start = width * 0.07f, top = width * 0.05f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(width * 0.01f),
    ) {
        Text(
            text = card.rank.label,
            color = suitColor,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            fontSize = with(density) { (width * 0.26f).toSp() },
            lineHeight = with(density) { (width * 0.26f).toSp() },
        )
        Canvas(Modifier.size(width * 0.15f)) {
            drawSuit(card.suit, Offset.Zero, Size(size.width, size.height), suitColor)
        }
    }
}

/** The back of a card, for hands you cannot see. */
@Composable
fun CardBack(width: Dp, modifier: Modifier = Modifier, elevation: Dp = 4.dp) {
    val height = width * CARD_ASPECT
    val shape = RoundedCornerShape(width * 0.08f)

    Box(
        modifier
            .size(width, height)
            .shadow(elevation, shape)
            .clip(shape)
            .background(Brush.linearGradient(listOf(FeltLight, Felt)))
            .border(width * 0.04f, Gold.copy(alpha = 0.55f), shape),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val step = size.width / 5f
            var x = -size.height
            while (x < size.width + size.height) {
                drawLine(
                    color = Gold.copy(alpha = 0.16f),
                    start = Offset(x, 0f),
                    end = Offset(x + size.height, size.height),
                    strokeWidth = size.width * 0.02f,
                )
                x += step
            }
            val emblem = size.width * 0.34f
            translate(
                left = (size.width - emblem) / 2f,
                top = (size.height - emblem) / 2f,
            ) {
                drawPath(
                    path = SuitShapes.of(Suit.DIAMONDS).toPath(emblem, emblem),
                    color = Gold.copy(alpha = 0.7f),
                )
            }
        }
    }
}

/** An empty spot on the table, waiting for a card. */
@Composable
fun CardSlot(width: Dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(width * 0.08f)
    Box(
        modifier
            .size(width, width * CARD_ASPECT)
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.14f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), shape),
    )
}

/**
 * The player's hand: an overlapping arc that fits however many cards are left, with the legal
 * ones raised and the engine's pick raised further.
 */
@Composable
fun HandFan(
    cards: List<Card>,
    modifier: Modifier = Modifier,
    cardWidth: Dp = 74.dp,
    legal: Set<Card> = cards.toSet(),
    recommended: Card? = null,
    selected: Card? = null,
    onCardClick: (Card) -> Unit = {},
) {
    if (cards.isEmpty()) return

    BoxWithConstraints(modifier.height(cardWidth * CARD_ASPECT + 26.dp)) {
        val available = maxWidth
        val overlap = if (cards.size <= 1) 0.dp else {
            val ideal = cardWidth * 0.62f
            val needed = (available - cardWidth) / (cards.size - 1)
            minOf(ideal, needed).coerceAtLeast(cardWidth * 0.16f)
        }
        val totalWidth = cardWidth + overlap * (cards.size - 1)
        val startX = (available - totalWidth) / 2

        cards.forEachIndexed { index, card ->
            val middle = (cards.size - 1) / 2f
            val distance = index - middle
            val isLegal = card in legal
            val isRecommended = card == recommended
            val lift by animateDpAsState(
                targetValue = when {
                    card == selected -> 26.dp
                    isRecommended -> 16.dp
                    isLegal -> 6.dp
                    else -> 0.dp
                },
                animationSpec = spring(),
                label = "cardLift",
            )

            Box(
                Modifier
                    .offset(
                        x = startX + overlap * index,
                        y = 20.dp - lift + (abs(distance) * 1.6f).dp,
                    )
                    .rotate(distance * 2.2f),
            ) {
                PlayingCardFace(
                    card = card,
                    width = cardWidth,
                    highlighted = isRecommended,
                    dimmed = !isLegal,
                    // Flat, unlike a card on the table. Ten cards overlapping by half means ten
                    // shadows falling across the cards behind them, which stack into a grey
                    // smear down the hand; the printed edge does the separating instead.
                    elevation = 0.dp,
                    onClick = { onCardClick(card) },
                )
            }
        }
    }
}

/** A small tappable card, used in pickers and in lists of evaluations. */
@Composable
fun MiniCard(
    card: Card,
    modifier: Modifier = Modifier,
    width: Dp = 34.dp,
    selected: Boolean = false,
    dimmed: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    PlayingCardFace(
        card = card,
        width = width,
        modifier = modifier,
        highlighted = selected,
        dimmed = dimmed,
        elevation = 2.dp,
        onClick = onClick,
    )
}

/** Fanned card backs, for the three hands you cannot see. */
@Composable
fun HiddenHand(count: Int, modifier: Modifier = Modifier, cardWidth: Dp = 22.dp) {
    val shown = count.coerceIn(0, 10)
    if (shown == 0) return

    // Declared, not inferred: Modifier.offset moves a card without widening its parent, so the
    // box measured a single card wide while the fan spilled out to the right of it — enough to
    // leave the partner's hand visibly off-centre under their label.
    val spread = cardWidth * 0.42f
    Box(
        modifier
            .width(cardWidth + spread * (shown - 1))
            .height(cardWidth * CARD_ASPECT),
    ) {
        repeat(shown) { index ->
            CardBack(
                width = cardWidth,
                modifier = Modifier
                    .offset(x = spread * index)
                    .rotate((index - shown / 2f) * 1.2f),
                elevation = 2.dp,
            )
        }
    }
}
