package com.haiku.wall.ui

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.haiku.wall.Prefs
import com.haiku.wall.render.Plan
import com.haiku.wall.render.PlanRenderer
import com.haiku.wall.store.PlanStore
import com.haiku.wall.ui.components.BrushProgress
import com.haiku.wall.ui.components.InkButton
import com.haiku.wall.ui.components.InkDivider
import com.haiku.wall.ui.components.InkPreview
import com.haiku.wall.ui.components.MonoLabel
import com.haiku.wall.ui.components.PaperBackdrop
import com.haiku.wall.ui.components.SectionHeader
import com.haiku.wall.ui.components.TextAction
import com.haiku.wall.ui.components.reveal
import com.haiku.wall.ui.theme.LocalHaikuColors
import com.haiku.wall.wall.HaikuWallpaperService
import com.haiku.wall.work.Scheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MainScreen() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val wm = remember { WorkManager.getInstance(ctx) }
    val now by remember { wm.getWorkInfosForUniqueWorkFlow(Scheduler.NOW) }.collectAsState(emptyList())
    val periodic by remember { wm.getWorkInfosForUniqueWorkFlow(Scheduler.PERIODIC) }.collectAsState(emptyList())
    val nowState = now.firstOrNull()?.state
    val busy = nowState == WorkInfo.State.RUNNING || periodic.firstOrNull()?.state == WorkInfo.State.RUNNING
    var picked by remember { mutableIntStateOf(0) }
    var settingsOpen by remember { mutableStateOf(false) }
    var replayToken by remember { mutableIntStateOf(0) }
    var interval by remember { mutableIntStateOf(prefs.intervalHours) }
    var entered by remember { mutableStateOf(false) }
    val key = listOf(busy, nowState, picked)
    val lastError = remember(key) { prefs.lastError }
    val lastRunMs = remember(key) { prefs.lastRunMs }
    val error = lastError ?: "Generation failed.".takeIf { nowState == WorkInfo.State.FAILED }

    LaunchedEffect(Unit) { entered = true }

    var seenRun by remember { mutableLongStateOf(lastRunMs) }
    var runSeen by remember { mutableStateOf(false) }
    LaunchedEffect(lastRunMs) {
        if (!runSeen) {
            runSeen = true
            seenRun = lastRunMs
        } else if (lastRunMs != seenRun) {
            seenRun = lastRunMs
            if (lastRunMs > 0) replayToken++
        }
    }

    val planJson by produceState<String?>(null, key) {
        value = withContext(Dispatchers.IO) { runCatching { PlanStore.currentJson(ctx) }.getOrNull() }
    }
    val meta = remember(planJson) { planJson?.let(PlanMetaReader::read) }
    val history by produceState(emptyList<File>(), key) {
        value = withContext(Dispatchers.IO) {
            runCatching { PlanStore.history(ctx).take(12) }.getOrDefault(emptyList())
        }
    }
    val relative = remember(lastRunMs) { relativeLabel(lastRunMs) }
    val onGenerate = {
        Scheduler.generateNow(ctx)
    }
    val onSetWallpaper = {
        if (!setWallpaper(ctx)) {
            scope.launch { snackbar.showSnackbar("No live wallpaper chooser was found on this device.") }
        }
    }
    val onPick: (File) -> Unit = { file ->
        scope.launch {
            val restored = withContext(Dispatchers.IO) {
                runCatching { PlanStore.restore(ctx, file.readText()) }.isSuccess
            }
            if (restored) {
                picked++
                replayToken++
            } else {
                snackbar.showSnackbar("That page could not be restored.")
            }
        }
    }

    PaperBackdrop {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            TopBar(onSettings = { settingsOpen = true })

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 4.dp, bottom = 30.dp),
            ) {
                item {
                    PlateSection(
                        planJson = planJson,
                        meta = meta,
                        replayToken = replayToken,
                        relative = relative,
                        interval = interval,
                        onReplay = { replayToken++ },
                        modifier = Modifier.reveal(entered, 90),
                    )
                }
                item { StatusBlock(busy = busy, state = nowState, error = error, onRetry = onGenerate) }
                if (history.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(26.dp))
                        SectionHeader(label = "Earlier")
                        Spacer(Modifier.height(12.dp))
                    }
                    item {
                        HistoryRow(
                            history = history,
                            onPick = onPick,
                            modifier = Modifier.reveal(entered, 250),
                        )
                    }
                }
            }

            BottomActions(
                busy = busy,
                onGenerate = onGenerate,
                onSetWallpaper = onSetWallpaper,
            )
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 92.dp),
        ) { data ->
            Box(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(LocalHaikuColors.current.text)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = data.visuals.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalHaikuColors.current.bg,
                )
            }
        }
    }

    if (settingsOpen) {
        SettingsSheet(prefs = prefs, onDismiss = {
            settingsOpen = false
            interval = prefs.intervalHours
        })
    }
}

@Composable
private fun TopBar(onSettings: () -> Unit) {
    val colors = LocalHaikuColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Haiku Wall",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            color = colors.text,
        )
        TextAction("Settings", onClick = onSettings, color = colors.muted)
    }
}

@Composable
private fun PlateSection(
    planJson: String?,
    meta: PlanMeta?,
    replayToken: Int,
    relative: String,
    interval: Int,
    onReplay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHaikuColors.current
    val lines = meta?.haiku.orEmpty().filter { it.isNotBlank() }

    Column(modifier = modifier.fillMaxWidth()) {
        InkPreview(
            planJson = planJson,
            meta = meta,
            replayToken = replayToken,
            modifier = Modifier.fillMaxWidth(0.62f).align(Alignment.CenterHorizontally),
            onClick = onReplay,
        )
        Spacer(Modifier.height(24.dp))
        if (lines.isNotEmpty()) {
            Text(
                text = lines.joinToString("\n"),
                style = MaterialTheme.typography.titleLarge,
                color = colors.text,
            )
        } else {
            Text(
                text = "No haiku yet. Write the first one.",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.muted,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (meta != null) {
                MetaSwatch(meta.ground)
                Spacer(Modifier.width(4.dp))
                meta.inks.take(2).forEach {
                    MetaSwatch(it)
                    Spacer(Modifier.width(4.dp))
                }
                Spacer(Modifier.width(4.dp))
            }
            val schedule = if (interval > 0) "new one every ${interval}h" else "manual"
            Text(
                text = if (relative.isEmpty()) schedule.replaceFirstChar { it.uppercase() } else "Written $relative \u00B7 $schedule",
                style = MaterialTheme.typography.bodySmall,
                color = colors.muted,
            )
        }
    }
}

@Composable
private fun MetaSwatch(hex: String?) {
    if (hex == null) return
    val colors = LocalHaikuColors.current
    val swatch = remember(hex) {
        runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color.Transparent)
    }
    Box(
        Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(swatch)
            .border(1.dp, colors.hairline, CircleShape),
    )
}

@Composable
private fun StatusBlock(busy: Boolean, state: WorkInfo.State?, error: String?, onRetry: () -> Unit) {
    val colors = LocalHaikuColors.current
    val showWaiting = state == WorkInfo.State.ENQUEUED || state == WorkInfo.State.BLOCKED
    when {
        busy -> Row(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrushProgress(Modifier.width(68.dp), color = colors.accent)
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Writing a new haiku\u2026",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.muted,
            )
        }
        error != null -> Row(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Couldn't write a new haiku.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.text,
                )
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.error,
                )
            }
            TextAction("Retry", onClick = onRetry, color = colors.text)
        }
        showWaiting -> Text(
            text = "Waiting for a network connection.",
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.muted,
        )
    }
}

@Composable
private fun HistoryRow(history: List<File>, onPick: (File) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(end = 4.dp),
    ) {
        items(history, key = { it.path }) { file ->
            HistoryThumb(file = file, onClick = { onPick(file) })
        }
    }
}

@Composable
private fun HistoryThumb(file: File, onClick: () -> Unit) {
    val colors = LocalHaikuColors.current
    val bmp by produceState<ImageBitmap?>(null, file) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                PlanRenderer.renderFinal(Plan.parse(file.readText()), 150, 330).asImageBitmap()
            }.getOrNull()
        }
    }
    val stamp = remember(file) {
        file.nameWithoutExtension.toLongOrNull()?.let {
            SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(it))
        }.orEmpty()
    }
    Column(Modifier.width(80.dp)) {
        Box(
            Modifier
                .width(80.dp)
                .height(160.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(colors.surfaceSolid)
                .border(1.dp, colors.hairline, RoundedCornerShape(6.dp))
                .clickable(role = Role.Button, onClickLabel = "Use this haiku", onClick = onClick),
        ) {
            bmp?.let {
                Image(
                    bitmap = it,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        MonoLabel(stamp, color = colors.muted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun BottomActions(busy: Boolean, onGenerate: () -> Unit, onSetWallpaper: () -> Unit) {
    val colors = LocalHaikuColors.current
    Column(Modifier.fillMaxWidth().background(colors.bg)) {
        InkDivider()
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            InkButton(
                text = "New haiku",
                onClick = onGenerate,
                modifier = Modifier.fillMaxWidth(),
                loading = busy,
                loadingText = "Writing\u2026",
            )
            Spacer(Modifier.height(4.dp))
            TextAction("Set as wallpaper", onClick = onSetWallpaper, color = colors.text)
        }
    }
}

private fun relativeLabel(ms: Long): String {
    if (ms <= 0) return ""
    val delta = (System.currentTimeMillis() - ms).coerceAtLeast(0L)
    return when {
        delta < 60_000L -> "just now"
        delta < 3_600_000L -> "${delta / 60_000L} min ago"
        else -> DateUtils.getRelativeTimeSpanString(
            ms,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
        ).toString()
    }
}

private fun setWallpaper(ctx: Context): Boolean {
    val direct = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
        .putExtra(
            WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
            ComponentName(ctx, HaikuWallpaperService::class.java),
        )
    if (runCatching { ctx.startActivity(direct) }.isSuccess) return true
    return runCatching {
        ctx.startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
    }.isSuccess
}
