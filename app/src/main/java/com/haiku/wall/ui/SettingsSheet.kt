package com.haiku.wall.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.haiku.wall.Config
import com.haiku.wall.Prefs
import com.haiku.wall.ui.components.HairlineField
import com.haiku.wall.ui.components.HankoChip
import com.haiku.wall.ui.components.InkDots
import com.haiku.wall.ui.components.InkTabs
import com.haiku.wall.ui.components.MonoLabel
import com.haiku.wall.ui.components.SealSwitch
import com.haiku.wall.ui.components.SectionHeader
import com.haiku.wall.ui.components.TextAction
import com.haiku.wall.ui.theme.LocalHaikuColors
import com.haiku.wall.ui.theme.SheetShape
import com.haiku.wall.work.Scheduler

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsSheet(prefs: Prefs, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val colors = LocalHaikuColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var categories by remember { mutableStateOf(prefs.categories) }
    var custom by remember { mutableStateOf(prefs.customTheme) }
    var interval by remember { mutableIntStateOf(prefs.intervalHours) }
    var seconds by remember { mutableIntStateOf(prefs.seconds) }
    var replay by remember { mutableStateOf(prefs.replayOnUnlock) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        contentColor = colors.text,
        scrimColor = Color.Black.copy(alpha = 0.36f),
        shape = SheetShape,
        dragHandle = {
            Box(
                Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .width(38.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.hairline),
                )
            }
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 22.dp)
                .padding(bottom = 26.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Settings",
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.text,
                    modifier = Modifier.weight(1f),
                )
                TextAction("Close", onClick = onDismiss, color = colors.muted)
            }
            Spacer(Modifier.height(22.dp))

            SectionHeader("Topics")
            Spacer(Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                HAIKU_CATEGORIES.forEach { category ->
                    val selected = category in categories
                    HankoChip(
                        label = category,
                        selected = selected,
                        onClick = {
                            categories = if (selected) categories - category else categories + category
                            prefs.categories = categories
                        },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            HairlineField(
                value = custom,
                onValueChange = { custom = it; prefs.customTheme = it },
                label = "Your own topic",
                placeholder = "a quiet temple in the rain",
            )
            Spacer(Modifier.height(22.dp))

            SectionHeader("New haiku every")
            Spacer(Modifier.height(4.dp))
            InkTabs(
                options = INTERVAL_OPTIONS.map { it to if (it == 0) "Off" else "${it}h" },
                selected = interval,
                onSelect = {
                    interval = it
                    prefs.intervalHours = it
                    Scheduler.apply(ctx, it)
                },
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Off means the page changes only when you ask.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.muted,
            )
            Spacer(Modifier.height(22.dp))

            SectionHeader("Drawing length")
            Spacer(Modifier.height(6.dp))
            InkDots(
                options = LENGTH_OPTIONS.map { it to "${it}s" },
                selected = seconds,
                onSelect = { seconds = it; prefs.seconds = it },
            )
            Spacer(Modifier.height(22.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Replay on unlock",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.text,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "Redraws the current haiku each time you unlock.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted,
                    )
                }
                Spacer(Modifier.width(14.dp))
                SealSwitch(checked = replay, onCheckedChange = { replay = it; prefs.replayOnUnlock = it })
            }
            Spacer(Modifier.height(24.dp))

            SectionHeader("AI provider", trailing = { MonoLabel(if (Config.fromBuild) "Built in" else "Optional") })
            Spacer(Modifier.height(12.dp))
            if (Config.fromBuild) {
                Text(
                    text = "This build already has an endpoint. No key is stored in the app.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.muted,
                )
            } else {
                Text(
                    text = "Used only when a new haiku is written. The key stays on this phone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.muted,
                )
                Spacer(Modifier.height(18.dp))
                AiForm()
            }
        }
    }
}
