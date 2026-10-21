package com.haiku.wall.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.haiku.wall.Config
import com.haiku.wall.ui.components.HairlineField
import com.haiku.wall.ui.components.HairlinePicker
import com.haiku.wall.ui.components.InkButton
import com.haiku.wall.ui.components.MonoLabel
import com.haiku.wall.ui.components.PaperBackdrop
import com.haiku.wall.ui.components.PaperPanel
import com.haiku.wall.ui.components.SealMark
import com.haiku.wall.ui.theme.LocalHaikuColors

private val PROVIDERS = listOf(Config.ANTHROPIC to "Anthropic", Config.OPENAI to "OpenAI-compatible")

/** Full-screen onboarding shown until the AI endpoint is configured. */
@Composable
fun AiGate(onDone: () -> Unit) {
    val colors = LocalHaikuColors.current
    PaperBackdrop {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SealMark(size = 30.dp, contentDescription = "Haiku Wall seal")
                Spacer(Modifier.width(10.dp))
                MonoLabel("Haiku Wall", color = colors.text)
            }
            Spacer(Modifier.height(42.dp))
            MonoLabel("Setup", color = colors.accent)
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Set up your AI provider",
                style = MaterialTheme.typography.displayLarge,
                color = colors.text,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Haiku Wall requests each haiku from an AI endpoint you provide. " +
                    "The key stays on this phone and is sent only to the endpoint you choose.",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.muted,
            )
            Spacer(Modifier.height(26.dp))
            PaperPanel(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    AiForm(onSaved = onDone)
                }
            }
            Spacer(Modifier.height(22.dp))
            MonoLabel("The key is stored only on this phone", color = colors.muted)
            Spacer(Modifier.height(28.dp))
        }
    }
}

/**
 * The AI endpoint fields. Used in onboarding and in the settings sheet; the caller decides
 * whether saving closes a gate or simply updates the current screen.
 */
@Composable
fun AiForm(onSaved: () -> Unit = {}) {
    val ctx = LocalContext.current
    val colors = LocalHaikuColors.current
    var menu by remember { mutableStateOf(false) }
    var provider by remember { mutableStateOf(Config.provider()) }
    var model by remember { mutableStateOf(Config.model()) }
    var key by remember { mutableStateOf(Config.apiKey()) }
    var url by remember {
        mutableStateOf(if (Config.provider() == Config.OPENAI) Config.apiUrl() else Config.DEFAULT_URL)
    }
    var saved by remember { mutableStateOf(false) }
    val valid = model.isNotBlank() && key.isNotBlank() &&
        (provider == Config.ANTHROPIC || url.trim().startsWith("http"))

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box {
            HairlinePicker(
                label = "Provider",
                value = PROVIDERS.first { it.first == provider }.second,
                onClick = { menu = true },
            )
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                PROVIDERS.forEach { (id, label) ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.text,
                            )
                        },
                        onClick = {
                            provider = id
                            if (id == Config.OPENAI) url = Config.DEFAULT_URL
                            menu = false
                            saved = false
                        },
                    )
                }
            }
        }

        HairlineField(
            value = model,
            onValueChange = { model = it; saved = false },
            label = "Model name",
            placeholder = "e.g. anthropic/claude-haiku-5.5",
        )

        if (provider == Config.OPENAI) {
            HairlineField(
                value = url,
                onValueChange = { url = it; saved = false },
                label = "Endpoint URL",
                placeholder = Config.DEFAULT_URL,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
        }

        HairlineField(
            value = key,
            onValueChange = { key = it; saved = false },
            label = "API key",
            placeholder = "sk-…",
            isPassword = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            InkButton(
                text = if (saved) "Saved" else "Save AI settings",
                onClick = {
                    Config.init(ctx)
                    Config.save(provider, model, key, url)
                    saved = true
                    onSaved()
                },
                modifier = Modifier.weight(1f),
                enabled = valid,
            )
        }
        if (saved) MonoLabel("Saved on this phone", color = colors.pine)
    }
}
