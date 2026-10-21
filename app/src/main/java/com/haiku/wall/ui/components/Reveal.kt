package com.haiku.wall.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.haiku.wall.ui.theme.InkMotion

/** A quiet stagger-in: opacity and a short upward drift, without changing layout. */
@Composable
fun Modifier.reveal(visible: Boolean, delayMillis: Int = 0): Modifier {
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(
            durationMillis = 680,
            delayMillis = delayMillis,
            easing = InkMotion.enterEasing,
        ),
        label = "reveal",
    )
    return graphicsLayer {
        alpha = progress
        translationY = (1f - progress) * 14.dp.toPx()
    }
}
