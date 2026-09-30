package com.ratchef.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.ratchef.data.AiMode
import com.ratchef.data.Settings

@Composable
fun SettingsScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val s = vm.settings
    val uri = LocalUriHandler.current
    var showKey by remember { mutableStateOf(false) }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("AI fallback (optional)", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Recipes are converted offline on your phone. For messy captions the app can ask Google " +
                        "Gemini instead; only the caption text is sent. The free tier is enough for personal use.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = s.apiKey,
                    onValueChange = { vm.updateSettings(s.copy(apiKey = it.trim())) },
                    label = { Text("Gemini API key") },
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "Hide" else "Show") }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { runCatching { uri.openUri("https://aistudio.google.com/apikey") } }) {
                    Text("Get a free key in Google AI Studio")
                }
                OutlinedTextField(
                    value = s.model,
                    onValueChange = { vm.updateSettings(s.copy(model = it)) },
                    label = { Text("Model") },
                    supportingText = { Text("Default: ${Settings.DEFAULT_MODEL}") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Use AI", style = MaterialTheme.typography.titleSmall)
                ModeOption("Never (offline only)", s.aiMode == AiMode.OFF) {
                    vm.updateSettings(s.copy(aiMode = AiMode.OFF))
                }
                ModeOption("Only when the offline parser is unsure", s.aiMode == AiMode.FALLBACK) {
                    vm.updateSettings(s.copy(aiMode = AiMode.FALLBACK))
                }
                ModeOption("Always", s.aiMode == AiMode.ALWAYS) {
                    vm.updateSettings(s.copy(aiMode = AiMode.ALWAYS))
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("How importing works", style = MaterialTheme.typography.titleMedium)
                Text(
                    "In Instagram tap Share on a reel → RatChef (or copy the link and paste it here). " +
                        "The app reads the public caption. If Instagram blocks that for a post, open the reel, " +
                        "copy the caption text and paste it instead – that always works.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Recipes and the shopping list are stored only on this phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ModeOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
