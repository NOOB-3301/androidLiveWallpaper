package com.haiku.wall.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.haiku.wall.ui.theme.CardShape
import com.haiku.wall.ui.theme.LocalHaikuColors
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private const val GRAIN_TILE = 192

private fun grainTile(ink: Color): ImageBitmap {
    val size = GRAIN_TILE
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val pixels = IntArray(size * size)
    val random = Random(0x5EED_2026)
    val red = (ink.red * 255f).roundToInt()
    val green = (ink.green * 255f).roundToInt()
    val blue = (ink.blue * 255f).roundToInt()
    for (i in pixels.indices) {
        val roll = random.nextInt(100)
        val alpha = when {
            roll < 3 -> random.nextInt(9, 20)
            roll < 10 -> random.nextInt(3, 9)
            else -> 0
        }
        pixels[i] = if (alpha == 0) 0 else android.graphics.Color.argb(alpha, red, green, blue)
    }
    bitmap.setPixels(pixels, 0, size, 0, 0, size, size)

    val canvas = AndroidCanvas(bitmap)
    val paint = Paint().apply {
        color = android.graphics.Color.argb(7, red, green, blue)
        strokeWidth = 1f
        strokeCap = Paint.Cap.ROUND
    }
    repeat(28) {
        val x = random.nextInt(4, size - 20).toFloat()
        val y = random.nextInt(4, size - 4).toFloat()
        val length = random.nextInt(5, 18).toFloat()
        canvas.drawLine(x, y, x + length, y + random.nextInt(-1, 2), paint)
    }
    return bitmap.asImageBitmap()
}

/** A barely-there silk-fibre grain, generated once and tiled. */
@Composable
fun Modifier.paperGrain(alpha: Float = 1f): Modifier {
    val ink = LocalHaikuColors.current.text
    val tile = remember(ink) { grainTile(ink) }
    return drawWithCache {
        val shader = BitmapShader(tile.asAndroidBitmap(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        val paint = Paint().apply {
            this.shader = shader
            this.alpha = (alpha * 255f).roundToInt().coerceIn(0, 255)
        }
        onDrawBehind {
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
            }
        }
    }
}

@Composable
fun PaperBackdrop(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val colors = LocalHaikuColors.current
    Box(
        modifier
            .fillMaxSize()
            .background(colors.bg)
            .paperGrain(0.3f),
        content = content,
    )
}

/** A paper panel with a warm ink shadow and no decorative border. */
@Composable
fun PaperPanel(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = CardShape,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = LocalHaikuColors.current
    val shadow = if (colors.isDark) Color.Black else colors.text
    Box(
        modifier
            .shadow(
                elevation = 26.dp,
                shape = shape,
                clip = false,
                ambientColor = shadow.copy(alpha = if (colors.isDark) 0.38f else 0.10f),
                spotColor = shadow.copy(alpha = if (colors.isDark) 0.48f else 0.14f),
            )
            .clip(shape)
            .background(colors.surface)
            .paperGrain(0.34f),
        content = content,
    )
}

/**
 * A tapered wet-to-dry brush mark. [progress] draws it from the left, like the wallpaper's strokes.
 */
@Composable
fun BrushUnderline(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = LocalHaikuColors.current.text,
) {
    androidx.compose.foundation.Canvas(modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val halfMax = h * 0.34f
        val samples = 48
        val path = Path()
        for (i in 0..samples) {
            val t = i / samples.toFloat()
            val x = t * w
            val y = h / 2f + sin(t * PI.toFloat() * 1.15f) * h * 0.11f
            val half = halfMax * sin(PI.toFloat() * t).coerceAtLeast(0f).pow(0.55f)
            if (i == 0) path.moveTo(x, y - half) else path.lineTo(x, y - half)
        }
        for (i in samples downTo 0) {
            val t = i / samples.toFloat()
            val x = t * w
            val y = h / 2f + sin(t * PI.toFloat() * 1.15f) * h * 0.11f
            val half = halfMax * sin(PI.toFloat() * t).coerceAtLeast(0f).pow(0.55f)
            path.lineTo(x, y + half)
        }
        path.close()
        clipRect(0f, 0f, w * progress.coerceIn(0f, 1f), h) {
            drawPath(
                path = path,
                brush = Brush.horizontalGradient(
                    colors = listOf(color, color.copy(alpha = 0.58f)),
                ),
            )
        }
    }
}

/** A cinnabar hanko with the app's enso cut out of it. */
@Composable
fun SealMark(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    contentDescription: String? = null,
) {
    val colors = LocalHaikuColors.current
    val semantics = if (contentDescription == null) Modifier else Modifier.semantics {
        this.contentDescription = contentDescription
    }
    Box(
        modifier
            .then(semantics)
            .size(size)
            .graphicsLayer { rotationZ = -1.4f }
            .clip(RoundedCornerShape(2.dp))
            .background(colors.accent),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize().padding(size * 0.19f)) {
            val stroke = this.size.minDimension * 0.12f
            drawArc(
                color = colors.onAccent,
                startAngle = 38f,
                sweepAngle = 292f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawCircle(
                color = colors.onAccent,
                radius = stroke * 0.62f,
                center = Offset(this.size.width * 0.72f, this.size.height * 0.28f),
            )
        }
    }
}

@Composable
fun InkDivider(modifier: Modifier = Modifier) {
    val colors = LocalHaikuColors.current
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, colors.hairline, colors.hairline, Color.Transparent),
                ),
            ),
    )
}

@Composable
fun MonoLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalHaikuColors.current.muted,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.labelMedium,
) {
    androidx.compose.material3.Text(
        text = text,
        modifier = modifier,
        style = style,
        color = color,
        maxLines = 1,
    )
}
