package com.haiku.wall.ui.theme

import android.animation.ValueAnimator
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.IntOffset

@Immutable
object InkMotion {
    val enterEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
    const val EnterMillis = 820
    const val PressMillis = 220
    const val RippleMillis = 460
    const val StaggerMillis = 55

    fun <T> spatial(): SpringSpec<T> = spring(
        dampingRatio = 0.86f,
        stiffness = Spring.StiffnessMediumLow,
    )

    fun <T> effects(): SpringSpec<T> = spring(
        dampingRatio = 1f,
        stiffness = Spring.StiffnessMedium,
    )

    val press = spring<Float>(
        dampingRatio = 0.62f,
        stiffness = Spring.StiffnessMedium,
    )
    val offset = spring<IntOffset>(
        dampingRatio = 0.9f,
        stiffness = Spring.StiffnessMediumLow,
    )

    fun animationsEnabled(): Boolean = runCatching { ValueAnimator.areAnimatorsEnabled() }.getOrDefault(true)

    fun <T> enter(): FiniteAnimationSpec<T> =
        if (animationsEnabled()) tween(EnterMillis, easing = enterEasing) else snap()

    fun <T> quick(): FiniteAnimationSpec<T> =
        if (animationsEnabled()) tween(PressMillis, easing = enterEasing) else snap()
}
