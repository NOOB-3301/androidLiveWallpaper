package com.haiku.wall.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.haiku.wall.ui.theme.InkMotion
import com.haiku.wall.ui.theme.LocalHaikuColors

@Composable
fun HairlineField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    isPassword: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val colors = LocalHaikuColors.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    var revealed by remember { mutableStateOf(false) }
    val line by animateColorAsState(
        targetValue = if (focused) colors.accent else colors.hairline,
        animationSpec = InkMotion.effects(),
        label = "fieldLine",
    )
    val thickness by animateDpAsState(
        targetValue = if (focused) 1.5.dp else 1.dp,
        animationSpec = InkMotion.effects(),
        label = "fieldThickness",
    )

    Column(modifier.fillMaxWidth()) {
        MonoLabel(label, color = if (focused) colors.accent else colors.muted)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f).padding(vertical = 10.dp),
                textStyle = MaterialTheme.typography.titleMedium.copy(color = colors.text),
                singleLine = singleLine,
                cursorBrush = SolidColor(colors.accent),
                visualTransformation = if (isPassword && !revealed) {
                    PasswordVisualTransformation()
                } else {
                    VisualTransformation.None
                },
                keyboardOptions = keyboardOptions,
                interactionSource = interaction,
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty() && placeholder.isNotEmpty()) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.muted.copy(alpha = 0.68f),
                            )
                        }
                        inner()
                    }
                },
            )
            if (isPassword) {
                Box(
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Button,
                            onClickLabel = if (revealed) "Hide API key" else "Show API key",
                        ) { revealed = !revealed }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    MonoLabel(if (revealed) "HIDE" else "SHOW", color = colors.muted)
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(thickness)
                .background(line),
        )
    }
}

@Composable
fun HairlinePicker(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHaikuColors.current
    val haptics = LocalHapticFeedback.current
    Column(modifier.fillMaxWidth()) {
        MonoLabel(label, color = colors.muted)
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClickLabel = label,
                ) {
                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    onClick()
                }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = value,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = colors.text,
            )
            Canvas(Modifier.size(14.dp)) {
                val chevron = Path().apply {
                    moveTo(size.width * 0.18f, size.height * 0.36f)
                    lineTo(size.width * 0.5f, size.height * 0.68f)
                    lineTo(size.width * 0.82f, size.height * 0.36f)
                }
                drawPath(
                    path = chevron,
                    color = colors.muted,
                    style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
    }
}
