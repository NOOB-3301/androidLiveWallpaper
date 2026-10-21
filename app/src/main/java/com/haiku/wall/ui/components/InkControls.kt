package com.haiku.wall.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.haiku.wall.ui.theme.InkMotion
import com.haiku.wall.ui.theme.LocalHaikuColors
import com.haiku.wall.ui.theme.StampShape
import kotlin.math.roundToInt

@Composable
fun SectionHeader(
    label: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = LocalHaikuColors.current
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MonoLabel(label, Modifier.weight(1f), color = colors.text)
            trailing?.invoke()
        }
        Spacer(Modifier.height(8.dp))
        InkDivider()
    }
}

@Composable
fun TextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = LocalHaikuColors.current.muted,
) {
    MonoLabel(
        text = text,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = text,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        color = color,
    )
}

@Composable
fun HankoChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHaikuColors.current
    val haptics = LocalHapticFeedback.current
    val bg by animateColorAsState(
        targetValue = if (selected) colors.text else Color.Transparent,
        animationSpec = InkMotion.effects(),
        label = "chipBg",
    )
    val fg by animateColorAsState(
        targetValue = if (selected) colors.bg else colors.text,
        animationSpec = InkMotion.effects(),
        label = "chipFg",
    )
    val edge by animateColorAsState(
        targetValue = if (selected) Color.Transparent else colors.hairline,
        animationSpec = InkMotion.effects(),
        label = "chipEdge",
    )
    Box(
        modifier
            .heightIn(min = 38.dp)
            .clip(StampShape)
            .background(bg)
            .border(1.dp, edge, StampShape)
            .selectable(
                selected = selected,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Checkbox,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                },
            )
            .padding(horizontal = 9.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

@Composable
fun InkDots(
    options: List<Pair<Int, String>>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHaikuColors.current
    val haptics = LocalHapticFeedback.current
    Row(
        modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            val dot by animateDpAsState(
                targetValue = if (isSelected) 12.dp else 6.dp,
                animationSpec = InkMotion.spatial(),
                label = "inkDot",
            )
            val ring by animateColorAsState(
                targetValue = if (isSelected) colors.accent else colors.hairline,
                animationSpec = InkMotion.effects(),
                label = "inkDotRing",
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .selectable(
                        selected = isSelected,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.RadioButton,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSelect(value)
                        },
                    )
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .border(if (isSelected) 1.5.dp else 1.dp, ring, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(dot)
                            .clip(CircleShape)
                            .background(if (isSelected) colors.text else colors.muted.copy(alpha = 0.5f)),
                    )
                }
                MonoLabel(
                    text = label,
                    color = if (isSelected) colors.text else colors.muted,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
fun InkTabs(
    options: List<Pair<Int, String>>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHaikuColors.current
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    var rowWidth by remember { mutableIntStateOf(0) }
    val index = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    val itemWidth = if (options.isEmpty()) 0f else rowWidth / options.size.toFloat()
    val indicatorX by animateFloatAsState(
        targetValue = index * itemWidth,
        animationSpec = InkMotion.spatial(),
        label = "tabIndicatorX",
    )
    val indicatorWidth = with(density) { itemWidth.toDp() }

    Box(modifier.fillMaxWidth().onSizeChanged { rowWidth = it.width }) {
        Row(Modifier.fillMaxWidth().selectableGroup()) {
            options.forEach { (value, label) ->
                val isSelected = value == selected
                val fg by animateColorAsState(
                    targetValue = if (isSelected) colors.text else colors.muted,
                    animationSpec = InkMotion.effects(),
                    label = "tabFg",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .selectable(
                            selected = isSelected,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.RadioButton,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSelect(value)
                            },
                        )
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = fg,
                        maxLines = 1,
                    )
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .align(Alignment.BottomStart)
                .background(colors.hairline),
        )
        Box(
            Modifier
                .offset { IntOffset(indicatorX.roundToInt(), 0) }
                .width(indicatorWidth)
                .height(2.dp)
                .align(Alignment.BottomStart)
                .background(colors.accent),
        )
    }
}

@Composable
fun SealSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHaikuColors.current
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 26.dp else 4.dp,
        animationSpec = InkMotion.spatial(),
        label = "sealThumb",
    )
    val track by animateColorAsState(
        targetValue = if (checked) colors.text else Color.Transparent,
        animationSpec = InkMotion.effects(),
        label = "sealTrack",
    )
    val edge by animateColorAsState(
        targetValue = if (checked) Color.Transparent else colors.hairline,
        animationSpec = InkMotion.effects(),
        label = "sealEdge",
    )
    Box(
        modifier
            .size(52.dp, 30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(track)
            .border(1.dp, edge, RoundedCornerShape(15.dp))
            .toggleable(
                value = checked,
                interactionSource = interaction,
                indication = null,
                role = Role.Switch,
                onValueChange = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onCheckedChange(it)
                },
            ),
    ) {
        Box(
            Modifier
                .padding(start = thumbOffset, top = 4.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(if (checked) colors.accent else colors.surface)
                .border(1.dp, colors.hairline, CircleShape),
        )
    }
}
