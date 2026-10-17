package com.haiku.wall.ui

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.haiku.wall.Config
import com.haiku.wall.Prefs
import com.haiku.wall.render.Plan
import com.haiku.wall.render.PlanRenderer
import com.haiku.wall.store.PlanStore
import com.haiku.wall.wall.HaikuWallpaperService
import com.haiku.wall.work.Scheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val CATEGORIES = listOf(
    "Nature", "Seasons", "Rain", "Mountains", "Ocean", "City night", "Love", "Solitude",
    "Winter", "Spring", "Autumn", "Summer", "Moon", "Garden", "Travel", "Memory",
)
private val INTERVALS = listOf(0, 1, 3, 6, 12, 24)
private val LENGTHS = listOf(8, 12, 20)

/** Call off the main thread. A missing or broken plan just leaves the frame empty. */
private fun render(w: Int, h: Int, json: () -> String): ImageBitmap? =
    runCatching { PlanRenderer.renderFinal(Plan.parse(json()), w, h).asImageBitmap() }.getOrNull()

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainScreen() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val scope = rememberCoroutineScope()
    val wm = remember { WorkManager.getInstance(ctx) }
    val now by remember { wm.getWorkInfosForUniqueWorkFlow(Scheduler.NOW) }.collectAsState(emptyList())
    val periodic by remember { wm.getWorkInfosForUniqueWorkFlow(Scheduler.PERIODIC) }.collectAsState(emptyList())
    val nowState = now.firstOrNull()?.state
    val busy = nowState == WorkInfo.State.RUNNING || periodic.firstOrNull()?.state == WorkInfo.State.RUNNING
    var picked by remember { mutableIntStateOf(0) } // bumped after a history tap
    val key = listOf(busy, nowState, picked) // anything that may have changed the current plan or the last result

    val lastError = remember(key) { prefs.lastError }
    val lastRunMs = remember(key) { prefs.lastRunMs }
    val preview by produceState<ImageBitmap?>(null, key) {
        value = withContext(Dispatchers.Default) { render(540, 1200) { PlanStore.currentJson(ctx) } }
    }
    val history by produceState(emptyList<File>(), key) {
        value = withContext(Dispatchers.IO) { PlanStore.history(ctx).sortedByDescending { it.name }.take(12) }
    }
    var categories by remember { mutableStateOf(prefs.categories) }
    var custom by remember { mutableStateOf(prefs.customTheme) }
    var interval by remember { mutableIntStateOf(prefs.intervalHours) }
    var seconds by remember { mutableIntStateOf(prefs.seconds) }
    var replay by remember { mutableStateOf(prefs.replayOnUnlock) }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).safeDrawingPadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Haiku Wall", style = MaterialTheme.typography.displaySmall, fontFamily = FontFamily.Serif)
            Status("A fresh haiku, drawn stroke by stroke on your home screen.")
            Box(
                Modifier.fillMaxWidth(0.62f).aspectRatio(540f / 1200f).align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                preview?.let { Image(it, "Current wallpaper", Modifier.fillMaxSize()) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button({ Scheduler.generateNow(ctx) }, Modifier.weight(1f), enabled = !busy) { Text("Generate now") }
                FilledTonalButton({ setWallpaper(ctx) }, Modifier.weight(1f)) { Text("Set as wallpaper") }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            val error = lastError ?: "Generation failed".takeIf { nowState == WorkInfo.State.FAILED }
            when {
                busy -> Status("Writing and drawing a new haiku…")
                nowState == WorkInfo.State.ENQUEUED -> Status("Waiting for a network connection…")
                error != null -> Status(error, MaterialTheme.colorScheme.error)
                lastRunMs > 0 -> Status("Last new haiku ${DateUtils.getRelativeTimeSpanString(lastRunMs)}")
            }

            if (history.isNotEmpty()) {
                Label("History")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(history, key = { it.path }) { file ->
                        Thumb(file) {
                            scope.launch {
                                withContext(Dispatchers.IO) { runCatching { PlanStore.save(ctx, file.readText()) } }
                                picked++
                            }
                        }
                    }
                }
            }

            Label("Themes")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CATEGORIES.forEach { c ->
                    FilterChip(
                        selected = c in categories,
                        onClick = {
                            categories = if (c in categories) categories - c else categories + c
                            prefs.categories = categories
                        },
                        label = { Text(c) },
                    )
                }
            }
            OutlinedTextField(
                custom, { custom = it; prefs.customTheme = it }, Modifier.fillMaxWidth(),
                label = { Text("Your own theme (optional)") }, singleLine = true,
            )
            Label("New haiku every")
            Segments(INTERVALS, interval, { if (it == 0) "Off" else "${it}h" }) {
                interval = it
                prefs.intervalHours = it
                Scheduler.apply(ctx, it)
            }
            Label("Drawing length")
            Segments(LENGTHS, seconds, { "${it}s" }) { seconds = it; prefs.seconds = it }
            Row(
                Modifier.fillMaxWidth().toggleable(replay, role = Role.Switch) { replay = it; prefs.replayOnUnlock = it },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Replay drawing when I return to the home screen", Modifier.weight(1f))
                Switch(replay, onCheckedChange = null)
            }
        }
    }
}

private fun setWallpaper(ctx: Context) {
    val direct = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
        .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(ctx, HaikuWallpaperService::class.java))
    // Some launchers have no direct preview activity; the generic live-wallpaper list still works there.
    runCatching { ctx.startActivity(direct) }
        .onFailure { ctx.startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)) }
}

@Composable
private fun Thumb(file: File, onClick: () -> Unit) {
    val bmp by produceState<ImageBitmap?>(null, file) {
        value = withContext(Dispatchers.Default) { render(180, 400) { file.readText() } }
    }
    Box(
        Modifier.size(72.dp, 160.dp).clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClickLabel = "Use this wallpaper", onClick = onClick),
    ) {
        bmp?.let { Image(it, null, Modifier.fillMaxSize()) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Segments(options: List<Int>, selected: Int, label: (Int) -> String, onSelect: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, o ->
            SegmentedButton(
                selected = o == selected,
                onClick = { onSelect(o) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                icon = {},
                label = { Text(label(o), maxLines = 1) },
            )
        }
    }
}

@Composable
private fun Label(text: String) =
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

@Composable
private fun Status(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) =
    Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
