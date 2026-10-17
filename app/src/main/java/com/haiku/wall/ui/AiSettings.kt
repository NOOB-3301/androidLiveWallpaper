package com.haiku.wall.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.haiku.wall.Config

private val PROVIDERS = listOf(Config.ANTHROPIC to "Anthropic", Config.OPENAI to "OpenAI-compatible")

/** Full-screen gate shown until the AI settings are filled in; nothing else in the app is reachable. */
@Composable
fun AiGate(onDone: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).safeDrawingPadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Haiku Wall", style = MaterialTheme.typography.displaySmall)
            Text("Set up the AI that writes your haiku before you start.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            AiSettings(initiallyOpen = true, onSaved = onDone)
        }
    }
}

/** Collapsible "AI settings" section. Only shown when the key was not baked in at build time. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettings(initiallyOpen: Boolean, onSaved: () -> Unit = {}) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var open by remember { mutableStateOf(initiallyOpen) }
    var info by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var provider by remember { mutableStateOf(Config.provider()) }
    var model by remember { mutableStateOf(Config.model()) }
    var key by remember { mutableStateOf(Config.apiKey()) }
    var url by remember { mutableStateOf(Config.apiUrl().takeIf { provider == Config.OPENAI } ?: Config.DEFAULT_URL) }
    var saved by remember { mutableStateOf(false) }
    val valid = model.isNotBlank() && key.isNotBlank() && (provider == Config.ANTHROPIC || url.startsWith("http"))

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton({ open = !open }, Modifier.weight(1f)) {
                Text(if (open) "AI settings  ▴" else "AI settings  ▾", Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleSmall)
            }
            OutlinedButton({ info = true }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text("i") }
        }
        if (open) {
            ExposedDropdownMenuBox(menu, { menu = it }) {
                OutlinedTextField(
                    PROVIDERS.first { it.first == provider }.second, {}, Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    readOnly = true, label = { Text("Model provider") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(menu) },
                )
                ExposedDropdownMenu(menu, { menu = false }) {
                    PROVIDERS.forEach { (id, label) ->
                        DropdownMenuItem({ Text(label) }, { provider = id; menu = false; saved = false })
                    }
                }
            }
            OutlinedTextField(model, { model = it; saved = false }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Model name") })
            if (provider == Config.OPENAI) {
                OutlinedTextField(
                    url, { url = it; saved = false }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Endpoint URL") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
            }
            OutlinedTextField(
                key, { key = it; saved = false }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("API key") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
            Button({
                Config.init(ctx)
                Config.save(provider, model, key, url)
                saved = true
                onSaved()
            }, enabled = valid) { Text(if (saved) "Saved" else "Save") }
        }
    }
    if (info) AlertDialog(
        onDismissRequest = { info = false },
        confirmButton = { TextButton({ info = false }) { Text("Got it") } },
        title = { Text("AI settings") },
        text = {
            Text(
                "Model provider: Anthropic uses Claude directly. OpenAI-compatible works with OpenRouter, OpenAI, Ollama and similar.\n\n" +
                    "Model name: the exact id, e.g. claude-haiku-5-5 (Anthropic) or anthropic/claude-haiku-5.5 (OpenRouter).\n\n" +
                    "Endpoint URL: the chat-completions address of an OpenAI-compatible service.\n\n" +
                    "API key: stored only on this phone and sent only to the endpoint above.",
            )
        },
    )
}
