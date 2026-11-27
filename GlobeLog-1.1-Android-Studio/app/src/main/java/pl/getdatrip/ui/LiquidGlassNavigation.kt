package pl.getdatrip.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import pl.getdatrip.ui.theme.GetDaTripDesign
import pl.getdatrip.ui.theme.tripSurface

private val NavigationShape = RoundedCornerShape(31.dp)
private val SelectionShape = RoundedCornerShape(24.dp)

@Composable
internal fun LiquidGlassNavigation(
    labels: List<String>,
    icons: List<ImageVector>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Modifier = Modifier,
) {
    val count = minOf(labels.size, icons.size)
    if (count == 0) return

    val colors = GetDaTripDesign.colors
    val transparency = GetDaTripDesign.glassTransparency
    val lensDepth = lerp(colors.canvas, colors.accent, 0.24f)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .shadow(14.dp, NavigationShape, clip = false)
            .clip(NavigationShape)
            .then(backdrop)
            .tripSurface(NavigationShape, elevated = true)
            .padding(5.dp),
    ) {
        val itemWidth = maxWidth / count
        val lensMotion = updateTransition(
            targetState = selectedIndex.coerceIn(0, count - 1),
            label = "Selected tab",
        )
        val lensStart by lensMotion.animateDp(
            transitionSpec = {
                tween(if (targetState > initialState) 340 else 205, easing = FastOutSlowInEasing)
            },
            label = "Leading glass edge",
        ) { index -> itemWidth * index }
        val lensEnd by lensMotion.animateDp(
            transitionSpec = {
                tween(if (targetState < initialState) 340 else 205, easing = FastOutSlowInEasing)
            },
            label = "Trailing glass edge",
        ) { index -> itemWidth * (index + 1) }

        if (selectedIndex in 0 until count) {
            val lensWidth = (lensEnd - lensStart).coerceAtLeast(itemWidth * 0.68f)
            val stretch = (lensWidth.value / itemWidth.value.coerceAtLeast(1f) - 1f).coerceIn(0f, 1f)
            val lensShape = JoinedGlassShape(neck = 0.28f * stretch)
            Box(Modifier.matchParentSize()) {
                Box(
                    Modifier
                        .offset(x = lensStart)
                        .width(lensWidth)
                        .fillMaxHeight()
                        .padding(horizontal = 2.dp, vertical = 1.dp)
                        .glassHighlight(lensShape, colors.accent, lensDepth, transparency),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).selectableGroup(),
        ) {
            repeat(count) { index ->
                val selected = index == selectedIndex
                val contentColor by animateColorAsState(
                    targetValue = when {
                        !selected -> colors.text
                        GetDaTripDesign.darkTheme -> lerp(Color.White, colors.accent, 0.16f)
                        else -> colors.accent
                    },
                    label = "Tab color",
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .heightIn(min = 54.dp)
                        .clip(SelectionShape)
                        .selectable(
                            selected = selected,
                            onClick = { onSelect(index) },
                            role = Role.Tab,
                        )
                        .padding(horizontal = 3.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = icons[index],
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        text = labels[index],
                        color = contentColor,
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

internal data class JoinedGlassShape(
    val neck: Float,
    val vertical: Boolean = false,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val length = if (vertical) size.height else size.width
        val breadth = if (vertical) size.width else size.height
        val cap = minOf(length, breadth) * 0.5f
        val shoulder = cap * 0.5523f
        val mid = length * 0.5f
        val rightCap = length - cap
        val waist = minOf(neck * breadth, (rightCap - cap) * 0.38f)
        val centerControl = (rightCap - cap) * 0.18f
        val path = Path()

        fun curve(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
            if (vertical) path.cubicTo(y1, x1, y2, x2, y3, x3)
            else path.cubicTo(x1, y1, x2, y2, x3, y3)
        }

        if (vertical) path.moveTo(0f, cap) else path.moveTo(cap, 0f)
        curve(cap + centerControl, 0f, mid - centerControl, waist, mid, waist)
        curve(mid + centerControl, waist, rightCap - centerControl, 0f, rightCap, 0f)
        curve(rightCap + shoulder, 0f, length, cap - shoulder, length, cap)
        curve(length, breadth - cap + shoulder, rightCap + shoulder, breadth, rightCap, breadth)
        curve(rightCap - centerControl, breadth, mid + centerControl, breadth - waist, mid, breadth - waist)
        curve(mid - centerControl, breadth - waist, cap + centerControl, breadth, cap, breadth)
        curve(cap - shoulder, breadth, 0f, breadth - cap + shoulder, 0f, breadth - cap)
        curve(0f, cap - shoulder, cap - shoulder, 0f, cap, 0f)
        path.close()
        return Outline.Generic(path)
    }
}

internal fun Modifier.glassHighlight(
    shape: Shape,
    accent: Color,
    depth: Color,
    transparency: Float,
    backgroundOpacity: Float = 0f,
): Modifier {
    val fill = 1f - transparency
    val tintStrength = 0.40f * fill
    val edge = Brush.linearGradient(
        0f to Color.White.copy(alpha = 0.94f),
        0.22f to Color.White.copy(alpha = 0.44f),
        0.57f to accent.copy(alpha = 0.25f),
        0.78f to accent.copy(alpha = 0.52f),
        1f to Color.White.copy(alpha = 0.86f),
    )
    if (fill <= 0f) return clip(shape).border(1.dp, edge, shape)
    return clip(shape).drawWithCache {
        val glass = Brush.verticalGradient(
            0f to accent.copy(alpha = tintStrength),
            0.29f to accent.copy(alpha = tintStrength * 0.52f),
            0.65f to depth.copy(alpha = tintStrength),
            1f to accent.copy(alpha = tintStrength * 0.83f),
            endY = size.height,
        )
        val reflection = Brush.radialGradient(
            0f to Color.White.copy(alpha = 0.47f * fill),
            0.43f to Color.White.copy(alpha = 0.17f * fill),
            1f to Color.Transparent,
            center = Offset(size.width * 0.20f, -size.height * 0.16f),
            radius = maxOf(size.width * 0.74f, size.height, 1f),
        )
        val lowerRefraction = Brush.radialGradient(
            0f to accent.copy(alpha = 0.25f * fill),
            1f to Color.Transparent,
            center = Offset(size.width * 0.83f, size.height * 1.10f),
            radius = maxOf(size.width * 0.53f, size.height, 1f),
        )
        val specularLine = Brush.horizontalGradient(
            0f to Color.Transparent,
            0.31f to Color.White.copy(alpha = 0.63f * fill),
            0.59f to Color.White.copy(alpha = 0.22f * fill),
            1f to Color.Transparent,
            endX = size.width,
        )
        onDrawBehind {
            if (backgroundOpacity > 0f) drawRect(depth.copy(alpha = backgroundOpacity * fill))
            drawRect(glass)
            drawRect(reflection)
            drawRect(lowerRefraction)
            val inset = 1.75.dp.toPx()
            drawLine(
                brush = specularLine,
                start = Offset(size.width * 0.11f, inset),
                end = Offset(size.width * 0.86f, inset),
                strokeWidth = 0.8.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }.border(1.dp, edge, shape)
}
