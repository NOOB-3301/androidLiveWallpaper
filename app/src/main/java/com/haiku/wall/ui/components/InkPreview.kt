package com.haiku.wall.ui.components

import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.haiku.wall.render.Plan
import com.haiku.wall.render.PlanRenderer
import com.haiku.wall.render.Player
import com.haiku.wall.ui.PlanMeta
import com.haiku.wall.ui.theme.LocalHaikuColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val REPLAY_MS = 6_400L
private const val FRAME_MS = 33L
private const val PLAY_W = 360
private const val PLAY_H = 800

@Composable
fun InkPreview(
    planJson: String?,
    meta: PlanMeta?,
    replayToken: Int,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val colors = LocalHaikuColors.current
    var frame by remember(planJson) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(planJson, replayToken) {
        if (planJson == null) {
            frame = null
            return@LaunchedEffect
        }
        if (replayToken == 0) {
            frame = withContext(Dispatchers.Default) {
                runCatching {
                    PlanRenderer.renderFinal(Plan.parse(planJson), 540, 1200).asImageBitmap()
                }.getOrNull()
            }
            return@LaunchedEffect
        }

        val plan = withContext(Dispatchers.Default) {
            runCatching { Plan.parse(planJson) }.getOrNull()
        } ?: return@LaunchedEffect
        val player = withContext(Dispatchers.Default) {
            runCatching { Player(plan, PLAY_W, PLAY_H) }.getOrNull()
        } ?: return@LaunchedEffect
        val started = SystemClock.uptimeMillis()
        val total = plan.total.coerceAtLeast(1L)
        while (true) {
            val elapsed = (SystemClock.uptimeMillis() - started).coerceAtMost(REPLAY_MS)
            val t = (elapsed.toFloat() / REPLAY_MS * total).toLong().coerceIn(0L, total)
            player.advance(t)
            frame = player.bitmap().asImageBitmap()
            if (elapsed >= REPLAY_MS) break
            delay(FRAME_MS)
        }
        player.advance(total)
        frame = player.bitmap().asImageBitmap()
    }

    val ground = remember(meta?.ground) {
        runCatching { Color(android.graphics.Color.parseColor(meta?.ground)) }
            .getOrDefault(colors.surfaceSolid)
    }

    PaperPanel(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 20f)
                .padding(10.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(ground)
                .then(
                    if (onClick == null) Modifier
                    else Modifier.clickable(role = Role.Button, onClickLabel = "Replay drawing", onClick = onClick),
                ),
        ) {
            frame?.let {
                Image(
                    bitmap = it,
                    contentDescription = "The current haiku wallpaper",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}
