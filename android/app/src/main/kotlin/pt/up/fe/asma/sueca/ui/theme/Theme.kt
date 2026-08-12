package pt.up.fe.asma.sueca.ui.theme

import androidx.compose.foundation.background
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import pt.up.fe.asma.sueca.engine.Suit
import pt.up.fe.asma.sueca.engine.TeamId

// ---------------------------------------------------------------------------------------------
// Palette
// ---------------------------------------------------------------------------------------------

/** The table. */
val FeltDeep = Color(0xFF061C15)
val Felt = Color(0xFF0B2E22)
val FeltLight = Color(0xFF144534)
val FeltEdge = Color(0xFF1D5D46)

/** The accent, borrowed from the gold trim on a deck of cards. */
val Gold = Color(0xFFE8C468)
val GoldMuted = Color(0xFF9C853F)

/** Card stock. */
val CardFace = Color(0xFFFAF7F0)
val CardFaceShade = Color(0xFFEDE7D9)
val CardEdge = Color(0xFFCFC7B4)
val CardBackInk = Color(0xFF0E3A2C)

val Ink = Color(0xFF122019)
val SuitRed = Color(0xFFC42B2B)
val SuitBlack = Color(0xFF17231D)

val SportingGreen = Color(0xFF32B36A)
val BenficaRed = Color(0xFFE23B4E)

val Positive = Color(0xFF4ED08A)
val Negative = Color(0xFFF06A72)

fun Suit.color(): Color = if (isRed) SuitRed else SuitBlack

fun TeamId.color(): Color = if (this == TeamId.SPORTING) SportingGreen else BenficaRed

/** The felt, with the light falling on the middle of the table. */
val TableBrush: Brush
    get() = Brush.radialGradient(
        colors = listOf(FeltLight, Felt, FeltDeep),
        center = Offset.Unspecified,
        radius = Float.POSITIVE_INFINITY,
    )

fun Modifier.tableBackground(): Modifier = background(TableBrush)

private val SuecaColors = darkColorScheme(
    primary = Gold,
    onPrimary = Color(0xFF241B00),
    primaryContainer = GoldMuted,
    onPrimaryContainer = Color(0xFF1A1204),
    secondary = SportingGreen,
    onSecondary = Color(0xFF00210F),
    tertiary = BenficaRed,
    onTertiary = Color.White,
    background = Felt,
    onBackground = Color(0xFFE7EFE9),
    surface = FeltLight,
    onSurface = Color(0xFFE7EFE9),
    surfaceVariant = Color(0xFF1B4A3A),
    onSurfaceVariant = Color(0xFFB9CFC3),
    outline = FeltEdge,
    outlineVariant = Color(0xFF235744),
    error = Negative,
    onError = Color(0xFF3B0709),
)

// ---------------------------------------------------------------------------------------------
// Type
// ---------------------------------------------------------------------------------------------

private val SuecaTypography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp),
        labelMedium = labelMedium.copy(letterSpacing = 0.6.sp),
    )
}

/** Tabular-ish style for scores and evaluations, so digits stop jittering as they change. */
val NumberStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
    fontSize = 13.sp,
)

/**
 * One committed look rather than a light theme and a dark one: the app is a card table, and a
 * card table is green. Everything else is tuned to sit on that felt.
 *
 * [LocalContentColor] is overridden on purpose. Material3 defaults it to black and only moves
 * off that when a container's colour matches one of the scheme's roles — which the felt, being
 * a gradient behind transparent scaffolds, never does. Left alone, every piece of text that does
 * not name its own colour comes out black on dark green.
 */
@Composable
fun SuecaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SuecaColors, typography = SuecaTypography) {
        CompositionLocalProvider(LocalContentColor provides SuecaColors.onBackground, content = content)
    }
}
