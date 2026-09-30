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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
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
    var showPaste by remember { mutableStateOf(false) }

    if (showPaste) SessionPasteDialog(
        onDismiss = { showPaste = false },
        onSave = { if (vm.pasteInstagramSession(it)) showPaste = false },
    )

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        Card(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Metric units", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Show cups, oz and lb as g / ml, and °F as °C. Teaspoons and tablespoons stay.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = s.metric, onCheckedChange = { vm.updateSettings(s.copy(metric = it)) })
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Recipe language", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Recipes in other languages (Spanish, Italian, …) are translated with Gemini. " +
                        "Amounts and units are always kept from the original.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ModeOption("As written", s.recipeLanguage == "original") { vm.updateSettings(s.copy(recipeLanguage = "original")) }
                ModeOption("Phone language", s.recipeLanguage == "auto") { vm.updateSettings(s.copy(recipeLanguage = "auto")) }
                ModeOption("Deutsch", s.recipeLanguage == "de") { vm.updateSettings(s.copy(recipeLanguage = "de")) }
                ModeOption("English", s.recipeLanguage == "en") { vm.updateSettings(s.copy(recipeLanguage = "en")) }
                if (s.recipeLanguage != "original" && s.apiKey.isBlank()) {
                    Text(
                        "Add a Gemini key below to translate.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Shopping list language", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Items from English and German recipes are named in one language, so the same thing merges.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ModeOption("Phone language", s.listLanguage == "auto") { vm.updateSettings(s.copy(listLanguage = "auto")) }
                ModeOption("Deutsch", s.listLanguage == "de") { vm.updateSettings(s.copy(listLanguage = "de")) }
                ModeOption("English", s.listLanguage == "en") { vm.updateSettings(s.copy(listLanguage = "en")) }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Instagram", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (vm.instagramSignedIn) "Signed in. Reels load with your account."
                    else "Not signed in. Many reels need a login before their caption can be read.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "You sign in on Instagram's own page; RatChef keeps only the login on this phone. " +
                        "Instagram doesn't like apps reading posts for you: at a few recipes a day the risk is " +
                        "small, but it could flag your account, and it can break this at any time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (vm.instagramSignedIn) {
                    OutlinedButton(onClick = { vm.signOutInstagram() }) { Text("Sign out") }
                } else {
                    Button(onClick = { vm.showInstagramLogin = true }) { Text("Sign in to Instagram") }
                    TextButton(onClick = { showPaste = true }) { Text("Login page won't load? Paste a session cookie") }
                }
            }
        }

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
                        "The app reads the caption (signed in, if you are). If Instagram blocks that for a post, open the " +
                        "reel, copy the caption text and paste it instead – that always works.",
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

@Composable
private fun SessionPasteDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Paste Instagram session") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "On a computer, log in at instagram.com, press F12 → Application (Chrome) or Storage (Firefox) → " +
                        "Cookies → https://www.instagram.com, and copy the value of \"sessionid\".",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "This value is your login: treat it like a password and don't share it. " +
                        "Signing out here or on instagram.com ends it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("sessionid") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(enabled = value.isNotBlank(), onClick = { onSave(value) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
