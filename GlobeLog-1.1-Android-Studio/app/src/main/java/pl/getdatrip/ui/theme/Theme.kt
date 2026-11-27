package pl.getdatrip.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.getdatrip.AppAccent
import pl.getdatrip.DEFAULT_GLASS_TRANSPARENCY
import pl.getdatrip.R

@Immutable
data class TripColors(
    val canvas: Color,
    val card: Color,
    val cardRaised: Color,
    val line: Color,
    val text: Color,
    val mutedText: Color,
    val accent: Color,
    val accentInk: Color,
    val mint: Color,
    val skyTop: Color,
    val skyBottom: Color,
)

private val StandardColors = TripColors(
    canvas = Color(0xFF141414),
    card = Color(0xFF444444),
    cardRaised = Color(0xFF4C4C4C),
    line = Color(0xFF5A5A5A),
    text = Color(0xFFE5E5E5),
    mutedText = Color(0xFFC8C8C8),
    accent = Color(0xFFB1C5FA),
    accentInk = Color(0xFF142C56),
    mint = Color(0xFFAFEBD4),
    skyTop = Color(0xFF2D4575),
    skyBottom = Color(0xFF202A43),
)

private val LightColors = TripColors(
    canvas = Color(0xFFF7F6FB),
    card = Color(0xFFFFFFFF),
    cardRaised = Color(0xFFEFEDF6),
    line = Color(0xFFC9C5D5),
    text = Color(0xFF1C1928),
    mutedText = Color(0xFF524E62),
    accent = Color(0xFF1E3F80),
    accentInk = Color.White,
    mint = Color(0xFF11634F),
    skyTop = Color(0xFFB9D5F9),
    skyBottom = Color(0xFFEDF4FF),
)

@Immutable
private data class TripAppearance(
    val colors: TripColors,
    val darkTheme: Boolean,
    val liquidGlass: Boolean,
    val glassTransparency: Float,
)

private val LocalTripAppearance = staticCompositionLocalOf {
    TripAppearance(StandardColors, true, false, DEFAULT_GLASS_TRANSPARENCY)
}

object GetDaTripDesign {
    val colors: TripColors
        @Composable @ReadOnlyComposable get() = LocalTripAppearance.current.colors

    val liquidGlass: Boolean
        @Composable @ReadOnlyComposable get() = LocalTripAppearance.current.liquidGlass

    val darkTheme: Boolean
        @Composable @ReadOnlyComposable get() = LocalTripAppearance.current.darkTheme

    val glassTransparency: Float
        @Composable @ReadOnlyComposable get() = LocalTripAppearance.current.glassTransparency
}

private val TripShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

private val TripDisplayFont = FontFamily(
    Font(R.font.bricolage_grotesque, weight = FontWeight.SemiBold),
)

private val TripTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = TripDisplayFont,
        fontSize = 68.sp,
        lineHeight = 72.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-5).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = TripDisplayFont,
        fontSize = 44.sp,
        lineHeight = 49.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-2.8).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = TripDisplayFont,
        fontSize = 37.sp,
        lineHeight = 42.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-2).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = TripDisplayFont,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-1.4).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = TripDisplayFont,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.75).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = TripDisplayFont,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.45).sp,
    ),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GetDaTripTheme(
    darkTheme: Boolean,
    liquidGlass: Boolean,
    accent: AppAccent = AppAccent.Blue,
    glassTransparency: Float = DEFAULT_GLASS_TRANSPARENCY,
    content: @Composable () -> Unit,
) {
    val baseColors = if (darkTheme) StandardColors else LightColors
    val defaultAccent = accent == AppAccent.Blue
    val colors = if (defaultAccent) {
        baseColors
    } else if (darkTheme) {
        baseColors.copy(
            accent = accent.color,
            accentInk = accent.ink,
            skyTop = lerp(accent.container, accent.ink, 0.12f),
            skyBottom = lerp(accent.ink, baseColors.canvas, 0.30f),
        )
    } else {
        baseColors.copy(
            accent = lerp(accent.container, accent.ink, 0.32f),
            skyTop = lerp(baseColors.skyTop, accent.color, 0.55f),
            skyBottom = lerp(baseColors.skyBottom, accent.color, 0.14f),
        )
    }
    val transparency = if (glassTransparency.isFinite()) {
        glassTransparency.coerceIn(0f, 1f)
    } else {
        DEFAULT_GLASS_TRANSPARENCY
    }
    val scheme = if (darkTheme) darkColorScheme(
        primary = colors.accent,
        onPrimary = colors.accentInk,
        primaryContainer = if (defaultAccent) Color(0xFF1E3F80) else accent.container,
        onPrimaryContainer = if (defaultAccent) Color(0xFFD9E4FF) else lerp(accent.color, Color.White, 0.37f),
        secondary = if (defaultAccent) Color(0xFFC0C7DD) else lerp(accent.color, Color(0xFFC9CBCD), 0.30f),
        onSecondary = if (defaultAccent) Color(0xFF293247) else lerp(accent.ink, colors.canvas, 0.23f),
        secondaryContainer = if (defaultAccent) Color(0xFF3F485E) else lerp(accent.container, colors.card, 0.25f),
        onSecondaryContainer = if (defaultAccent) Color(0xFFDCE3F9) else lerp(accent.color, Color.White, 0.40f),
        tertiary = colors.mint,
        onTertiary = Color(0xFF0B3A2C),
        tertiaryContainer = Color(0xFF1E4F40),
        onTertiaryContainer = Color(0xFFCBFCE9),
        background = colors.canvas,
        onBackground = colors.text,
        surface = colors.card,
        onSurface = colors.text,
        surfaceVariant = colors.cardRaised,
        onSurfaceVariant = colors.mutedText,
        outline = Color(0xFF929298),
        outlineVariant = colors.line,
        surfaceDim = colors.canvas,
        surfaceBright = colors.cardRaised,
        surfaceContainerLowest = colors.canvas,
        surfaceContainerLow = colors.card,
        surfaceContainer = colors.card,
        surfaceContainerHigh = colors.cardRaised,
        surfaceContainerHighest = colors.cardRaised,
    ) else lightColorScheme(
        primary = colors.accent,
        onPrimary = colors.accentInk,
        primaryContainer = lerp(accent.color, Color.White, 0.43f),
        onPrimaryContainer = accent.ink,
        secondary = Color(0xFF555571),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE6E3F7),
        onSecondaryContainer = Color(0xFF22213A),
        tertiary = colors.mint,
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFD2F2E4),
        onTertiaryContainer = Color(0xFF153D31),
        background = colors.canvas,
        onBackground = colors.text,
        surface = colors.card,
        onSurface = colors.text,
        surfaceVariant = colors.cardRaised,
        onSurfaceVariant = colors.mutedText,
        outline = Color(0xFF777087),
        outlineVariant = colors.line,
        surfaceDim = colors.cardRaised,
        surfaceBright = colors.card,
        surfaceContainerLowest = colors.card,
        surfaceContainerLow = colors.canvas,
        surfaceContainer = colors.cardRaised,
        surfaceContainerHigh = Color(0xFFE9E6F1),
        surfaceContainerHighest = Color(0xFFE3DFED),
    )
    CompositionLocalProvider(LocalTripAppearance provides TripAppearance(colors, darkTheme, liquidGlass, transparency)) {
        MaterialExpressiveTheme(colorScheme = scheme, shapes = TripShapes, typography = TripTypography, content = content)
    }
}

@Composable
fun Modifier.tripSurface(shape: Shape, elevated: Boolean = false): Modifier {
    val colors = GetDaTripDesign.colors
    val base = if (elevated) colors.cardRaised else colors.card
    val clipped = clip(shape)
    if (!GetDaTripDesign.liquidGlass) {
        return clipped.background(base).border(1.dp, colors.line.copy(alpha = 0.65f), shape)
    }

    val transparency = GetDaTripDesign.glassTransparency
    val rimColor = if (GetDaTripDesign.darkTheme) Color.White else colors.accent
    val tint = lerp(colors.canvas, base, if (elevated) 0.26f else 0.54f)
    val fill = 1f - transparency
    val opacity = Math.pow(fill.toDouble(), 0.8).toFloat() * if (elevated) 0.99f else 0.97f
    val rim = Brush.linearGradient(
        0f to rimColor.copy(alpha = 0.81f + 0.12f * transparency),
        0.23f to rimColor.copy(alpha = 0.39f),
        0.51f to rimColor.copy(alpha = 0.14f),
        0.74f to colors.accent.copy(alpha = 0.24f),
        1f to rimColor.copy(alpha = 0.72f + 0.12f * transparency),
    )
    val bevel = Brush.linearGradient(
        0f to Color.White.copy(alpha = 0.19f),
        0.42f to Color.Transparent,
        0.66f to Color.Black.copy(alpha = 0.15f),
        1f to Color.White.copy(alpha = 0.15f),
    )
    if (fill <= 0f) return clipped.border(1.dp, rim, shape).border(2.dp, bevel, shape)

    return clipped.drawWithCache {
        val veil = Brush.verticalGradient(
            0f to tint.copy(alpha = opacity * 0.96f),
            0.46f to tint.copy(alpha = opacity),
            1f to tint.copy(alpha = (opacity * 1.04f).coerceAtMost(1f)),
            endY = size.height,
        )
        val topGlint = Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.25f * fill),
            0.38f to Color.White.copy(alpha = 0.10f * fill),
            1f to Color.Transparent,
            endY = minOf(size.height * 0.23f, 14.dp.toPx()).coerceAtLeast(1f),
        )
        val light = Brush.radialGradient(
            0f to Color.White.copy(alpha = (if (elevated) 0.16f else 0.23f) * fill),
            0.55f to Color.White.copy(alpha = (if (elevated) 0.04f else 0.07f) * fill),
            1f to Color.Transparent,
            center = Offset(size.width * 0.12f, -size.height * 0.18f),
            radius = maxOf(size.width * 0.45f, size.height, 1f),
        )
        val reflection = Brush.radialGradient(
            0f to lerp(colors.accent, Color.White, 0.58f).copy(alpha = 0.20f * fill),
            0.48f to colors.accent.copy(alpha = 0.04f * fill),
            1f to Color.Transparent,
            center = Offset(size.width * 0.88f, size.height * 1.20f),
            radius = maxOf(size.width * 0.39f, size.height * 0.80f, 1f),
        )
        onDrawBehind {
            drawRect(veil)
            drawRect(light)
            drawRect(reflection)
            drawRect(topGlint)
        }
    }.border(1.dp, rim, shape).border(2.dp, bevel, shape)
}
