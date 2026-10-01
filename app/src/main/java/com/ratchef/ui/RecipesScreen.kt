package com.ratchef.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ratchef.R
import com.ratchef.core.Recipe

@Composable
fun RecipesScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(R.drawable.ic_rat_mark),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(width = 40.dp, height = 25.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text("RatChef", style = MaterialTheme.typography.headlineMedium)
            }
            Text(
                "Share a reel to RatChef from Instagram, or paste a link or caption here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item { ImportCard(vm) }

        val state = vm.importState
        if (state is ImportState.NeedsCaption) {
            item(key = "caption-${state.url}-${state.reason.hashCode()}") {
                Box(Modifier.animateItem()) { CaptionCard(vm, state) }
            }
        }

        if (vm.recipes.isEmpty()) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(top = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_rat_mark),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.size(width = 96.dp, height = 60.dp),
                    )
                    Spacer(Modifier.size(16.dp))
                    VeggieRow()
                    Spacer(Modifier.size(16.dp))
                    Text("Nothing on the stove yet.", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Share a reel to get started.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (vm.recipes.isNotEmpty()) {
            item(key = "filters") { RecipeFilters(vm) }
            if (vm.filteredRecipes.isEmpty()) item(key = "nofilter") {
                Text("No recipes match these filters.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(vm.filteredRecipes, key = { it.id }) { r ->
            // New recipes slide in at the top, deleted ones fade out.
            Box(Modifier.animateItem()) { RecipeCard(vm, vm.shown(r)) { vm.openRecipeId = r.id } }
        }
    }
}

@Composable
private fun ImportCard(vm: AppViewModel) {
    var input by rememberSaveable { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    val loading = vm.importState as? ImportState.Loading

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("Reel link or caption") },
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                TextButton(onClick = { clipboard.getText()?.text?.let { input = it } }) { Text("Paste") }
                Spacer(Modifier.weight(1f))
                Button(
                    enabled = input.isNotBlank() && loading == null,
                    onClick = {
                        vm.importText(input)
                        input = ""
                    },
                ) { Text("Get recipe") }
            }
            AnimatedVisibility(visible = loading != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    AnimatedContent(targetState = loading?.message ?: "", label = "loading") { msg ->
                        Text(msg, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun CaptionCard(vm: AppViewModel, state: ImportState.NeedsCaption) {
    var caption by remember(state) { mutableStateOf(state.caption) }
    val uri = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(state.reason, style = MaterialTheme.typography.bodyMedium)
            Row {
                if (state.url.isNotEmpty()) {
                    TextButton(onClick = { runCatching { uri.openUri(state.url) } }) { Text("Open reel") }
                }
                TextButton(onClick = { clipboard.getText()?.text?.let { caption = it } }) { Text("Paste caption") }
            }
            if (!vm.instagramSignedIn && state.url.contains("instagram.com") && state.caption.isEmpty()) {
                FilledTonalButton(onClick = { vm.showInstagramLogin = true }) { Text("Sign in to Instagram") }
            }
            OutlinedTextField(
                value = caption,
                onValueChange = { caption = it },
                label = { Text("Caption / recipe text") },
                minLines = 4,
                maxLines = 12,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.padding(top = 8.dp)) {
                TextButton(onClick = { vm.cancelImport() }) { Text("Cancel") }
                Spacer(Modifier.weight(1f))
                Button(enabled = caption.isNotBlank(), onClick = { vm.submitCaption(state.url, caption) }) {
                    Text("Make recipe")
                }
            }
        }
    }
}

@Composable
private fun RecipeCard(vm: AppViewModel, r: Recipe, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        VeggieBadge(Veggie.forRecipe(r))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(r.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val parts = buildList {
                add("${r.ingredients.size} ingredients")
                add("${r.steps.size} steps")
                if (r.servings > 0) add("serves ${r.servings}")
                if (r.aiParsed) add("AI")
            }
            Text(
                parts.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            RecipeTagRow(vm, r)
        }
        }
    }
}
