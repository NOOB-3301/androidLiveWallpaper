package com.haiku.wall.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.haiku.wall.ui.theme.ControlShape
import com.haiku.wall.ui.theme.InkMotion
import com.haiku.wall.ui.theme.LocalHaikuColors
import kotlinx.coroutines.launch

@Composable
fun InkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    enabled: Boolean = true,
    loading: Boolean = false,
    loadingText: String = text,
) {
    val colors = LocalHaikuColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.975f else 1f,
        animationSpec = InkMotion.press,
        label = "inkButtonScale",
    )
    val bg by animateColorAsStateCompat(
        target = if (primary) colors.accent else Color.Transparent,
        label = "inkButtonBg",
    )
    val fg = if (primary) colors.onAccent else colors.text
    val ripple = remember { Animatable(0f) }
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    Box(
        modifier
            .heightIn(min = 52.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.45f
            }
            .clip(ControlShape)
            .background(bg)
            .then(
                if (primary) Modifier else Modifier.border(1.dp, colors.hairline, ControlShape),
            )
            .drawWithContent {
                if (ripple.value > 0f) {
                    val radius = size.maxDimension * 0.9f * ripple.value
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                (if (primary) colors.onAccent else colors.text).copy(alpha = 0.16f),
                                Color.Transparent,
                            ),
                            center = center,
                            radius = radius,
                        ),
                        radius = radius,
                        center = center,
                    )
                }
                drawContent()
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled && !loading,
                role = Role.Button,
                onClickLabel = text,
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                scope.launch {
                    ripple.snapTo(0f)
                    ripple.animateTo(1f, tween(InkMotion.RippleMillis, easing = InkMotion.enterEasing))
                    ripple.animateTo(0f, tween(180))
                }
                onClick()
            }
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = loadingText,
                    style = MaterialTheme.typography.labelLarge,
                    color = fg,
                    maxLines = 1,
                )
                BrushProgress(Modifier.width(54.dp).height(3.dp), color = fg)
            }
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun BrushProgress(
    modifier: Modifier = Modifier,
    color: Color = LocalHaikuColors.current.accent,
    determinateProgress: Float? = null,
) {
    val progress = if (determinateProgress != null) {
        determinateProgress.coerceIn(0f, 1f)
    } else {
        val transition = rememberInfiniteTransition(label = "brushProgress")
        val p by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing)),
            label = "brushSweep",
        )
        p
    }
    Canvas(modifier.fillMaxWidth()) {
        val y = size.height / 2f
        val stroke = size.height.coerceAtLeast(1.5f)
        drawLine(
            color = color.copy(alpha = 0.16f),
            start = androidx.compose.ui.geometry.Offset(0f, y),
            end = androidx.compose.ui.geometry.Offset(size.width, y),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        if (determinateProgress != null) {
            drawLine(
                color = color,
                start = androidx.compose.ui.geometry.Offset(0f, y),
                end = androidx.compose.ui.geometry.Offset(size.width * progress, y),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        } else {
            val segment = size.width * 0.34f
            val start = (progress * (size.width + segment) - segment).coerceIn(-segment, size.width)
            val end = (start + segment).coerceIn(0f, size.width)
            if (end > start) {
                drawLine(
                    color = color,
                    start = androidx.compose.ui.geometry.Offset(start, y),
                    end = androidx.compose.ui.geometry.Offset(end, y),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
private fun animateColorAsStateCompat(target: Color, label: String): androidx.compose.runtime.State<Color> =
    androidx.compose.animation.animateColorAsState(
        targetValue = target,
        animationSpec = InkMotion.effects(),
        label = label,
    )
