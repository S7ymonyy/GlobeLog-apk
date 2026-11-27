package pl.getdatrip.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp

private enum class Sky { Clear, Mixed, Overcast, Fog, Rain, Snow, Thunder }

@Composable
fun WeatherArtwork(code: Int, isDay: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val sky = when (code) {
            0 -> Sky.Clear
            1, 2 -> Sky.Mixed
            45, 48 -> Sky.Fog
            in 51..67, in 80..82 -> Sky.Rain
            in 71..77, 85, 86 -> Sky.Snow
            in 95..99 -> Sky.Thunder
            else -> Sky.Overcast
        }
        val compact = size.minDimension < 70.dp.toPx()
        val artworkSize = if (compact) 106f else 160f
        val factor = size.minDimension / artworkSize
        val left = (size.width - 160f * factor) / 2f
        val top = (size.height - 160f * factor) / 2f

        withTransform({
            translate(left, top)
            scale(factor, factor, pivot = Offset.Zero)
        }) {
            if (!compact) drawSkyDecoration(isDay)

            when (sky) {
                Sky.Clear -> drawSunOrMoon(Offset(80f, 79f), 42f, isDay)
                Sky.Mixed -> {
                    drawSunOrMoon(Offset(92f, 63f), 33f, isDay)
                    drawCloud(Offset(72f, 105f), 0.88f)
                }
                Sky.Overcast -> {
                    drawCloud(Offset(89f, 69f), 0.75f, shaded = true)
                    drawCloud(Offset(73f, 99f), 0.91f)
                }
                Sky.Fog -> {
                    drawCloud(Offset(79f, 80f), 0.91f)
                    drawFog()
                }
                Sky.Rain -> {
                    drawCloud(Offset(80f, 81f), 0.92f)
                    drawRain()
                }
                Sky.Snow -> {
                    drawCloud(Offset(80f, 80f), 0.92f)
                    drawSnow()
                }
                Sky.Thunder -> {
                    drawCloud(Offset(80f, 78f), 0.92f)
                    drawLightning()
                }
            }
        }
    }
}

private fun DrawScope.drawSkyDecoration(isDay: Boolean) {
    val center = Offset(82f, 80f)
    val glow = if (isDay) Color(0xFFB7A4FF) else Color(0xFF9389FF)
    drawCircle(
        brush = Brush.radialGradient(listOf(glow.copy(alpha = 0.31f), Color.Transparent), center, 77f),
        radius = 77f,
        center = center,
    )
    drawCircle(Color(0xFFBDAAFF).copy(alpha = 0.27f), 66f, center, style = Stroke(width = 1f))
    drawCircle(Color(0xFFE1D6FF).copy(alpha = 0.15f), 53f, center, style = Stroke(width = 1f))
    drawArc(
        color = Color(0xFFD9C8FF).copy(alpha = 0.55f),
        startAngle = 203f,
        sweepAngle = 51f,
        useCenter = false,
        topLeft = Offset(16f, 14f),
        size = Size(132f, 132f),
        style = Stroke(width = 1.7f, cap = StrokeCap.Round),
    )
    drawArc(
        color = Color(0xFFE2D4FF).copy(alpha = 0.47f),
        startAngle = 41f,
        sweepAngle = 39f,
        useCenter = false,
        topLeft = Offset(16f, 14f),
        size = Size(132f, 132f),
        style = Stroke(width = 1.7f, cap = StrokeCap.Round),
    )
    drawSparkle(Offset(29f, 43f), 5f, Color(0xFFECE0FF))
    drawSparkle(Offset(134f, 116f), 7f, Color(0xFFFFD5B7))
    drawSparkle(Offset(125f, 22f), 4f, Color(0xFFF2DCFF))
    drawCircle(Color(0xFFE4C9FF), 2.2f, Offset(24f, 117f))
    drawCircle(Color(0xFFFDDDBF), 1.5f, Offset(139f, 49f))
}

private fun DrawScope.drawSparkle(center: Offset, radius: Float, color: Color) {
    withTransform({ translate(center.x, center.y) }) {
        val sparkle = Path().apply {
            moveTo(0f, -radius)
            quadraticTo(radius * 0.16f, -radius * 0.16f, radius, 0f)
            quadraticTo(radius * 0.16f, radius * 0.16f, 0f, radius)
            quadraticTo(-radius * 0.16f, radius * 0.16f, -radius, 0f)
            quadraticTo(-radius * 0.16f, -radius * 0.16f, 0f, -radius)
            close()
        }
        drawPath(sparkle, color)
    }
}

private fun DrawScope.drawSunOrMoon(center: Offset, radius: Float, isDay: Boolean) {
    val light = if (isDay) Color(0xFFFFD49C) else Color(0xFFCEB8FF)
    drawCircle(
        brush = Brush.radialGradient(listOf(light.copy(alpha = 0.46f), Color.Transparent), center, radius * 1.5f),
        radius = radius * 1.5f,
        center = center,
    )
    if (isDay) {
        drawCircle(
            brush = Brush.linearGradient(
                listOf(Color(0xFFFFF4C1), Color(0xFFFFC58D), Color(0xFFFF8F83)),
                start = center - Offset(radius, radius),
                end = center + Offset(radius, radius),
            ),
            radius = radius,
            center = center,
        )
        drawCircle(Color.White.copy(alpha = 0.48f), radius - 0.6f, center, style = Stroke(width = 1.2f))
        return
    }

    val outer = Path().apply {
        addOval(androidx.compose.ui.geometry.Rect(center, radius))
    }
    val inner = Path().apply {
        addOval(androidx.compose.ui.geometry.Rect(center + Offset(radius * 0.42f, -radius * 0.35f), radius * 0.87f))
    }
    drawPath(
        path = Path.combine(PathOperation.Difference, outer, inner),
        brush = Brush.linearGradient(
            listOf(Color(0xFFFFF0D6), Color(0xFFD2BEFF), Color(0xFF8D83F7)),
            start = center - Offset(radius, radius),
            end = center + Offset(radius, radius),
        ),
    )
    drawSparkle(center + Offset(radius * 0.67f, -radius * 0.73f), radius * 0.17f, Color(0xFFFCE5FF))
}

private fun DrawScope.drawCloud(center: Offset, factor: Float, shaded: Boolean = false) {
    withTransform({
        translate(center.x, center.y)
        scale(factor, factor, pivot = Offset.Zero)
    }) {
        val cloud = Path().apply {
            moveTo(-38f, 16f)
            cubicTo(-50f, 16f, -57f, 7f, -57f, -3f)
            cubicTo(-57f, -14f, -49f, -21f, -39f, -21f)
            cubicTo(-35f, -21f, -32f, -20f, -30f, -19f)
            cubicTo(-25f, -35f, -13f, -40f, -1f, -40f)
            cubicTo(12f, -40f, 22f, -33f, 27f, -21f)
            cubicTo(30f, -22f, 33f, -23f, 37f, -23f)
            cubicTo(48f, -23f, 57f, -14f, 57f, -3f)
            cubicTo(57f, 8f, 48f, 16f, 37f, 16f)
            close()
        }
        val shades = if (shaded) {
            listOf(Color(0xFFDBD2FF), Color(0xFF9A88E5), Color(0xFF7463C2))
        } else {
            listOf(Color(0xFFFFF3F5), Color(0xFFE2D4FF), Color(0xFFA798F5))
        }
        drawPath(cloud, Brush.linearGradient(shades, Offset(-40f, -39f), Offset(45f, 23f)))
        drawPath(cloud, Color(0xFFFDF2FF).copy(alpha = 0.42f), style = Stroke(width = 1.1f))
    }
}

private fun DrawScope.drawFog() {
    val fog = Color(0xFFD8D0FF)
    drawRoundRect(fog.copy(alpha = 0.90f), Offset(38f, 102f), Size(80f, 4.5f), CornerRadius(3f))
    drawRoundRect(fog.copy(alpha = 0.64f), Offset(49f, 114f), Size(79f, 4.5f), CornerRadius(3f))
    drawRoundRect(fog.copy(alpha = 0.44f), Offset(32f, 126f), Size(50f, 4.5f), CornerRadius(3f))
}

private fun DrawScope.drawRain() {
    val rain = Color(0xFFA5C9FF)
    drawLine(rain, Offset(58f, 104f), Offset(51f, 119f), 5f, StrokeCap.Round)
    drawLine(rain.copy(alpha = 0.87f), Offset(83f, 107f), Offset(75f, 125f), 5f, StrokeCap.Round)
    drawLine(rain, Offset(106f, 104f), Offset(99f, 119f), 5f, StrokeCap.Round)
}

private fun DrawScope.drawSnow() {
    val snow = Color(0xFFE7EDFF)
    drawSparkle(Offset(54f, 107f), 7f, snow)
    drawSparkle(Offset(80f, 124f), 8f, snow)
    drawSparkle(Offset(105f, 107f), 7f, snow)
}

private fun DrawScope.drawLightning() {
    val bolt = Path().apply {
        moveTo(84f, 83f)
        lineTo(68f, 108f)
        lineTo(81f, 108f)
        lineTo(74f, 132f)
        lineTo(98f, 102f)
        lineTo(85f, 102f)
        lineTo(94f, 83f)
        close()
    }
    drawPath(bolt, Brush.verticalGradient(listOf(Color(0xFFFFF7AF), Color(0xFFFF9F92)), 83f, 132f))
}
