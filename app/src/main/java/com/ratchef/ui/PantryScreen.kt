package com.ratchef.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** "What can I cook?": list what's at home, see saved recipes ranked by how much you already have. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PantryScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    var input by rememberSaveable { mutableStateOf("") }
    val matches = vm.pantryMatches

    fun add() {
        if (input.isNotBlank()) {
            vm.addPantry(input)
            input = ""
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "head") {
            Text("What can I cook?", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Add what you have at home – any language, roughly is fine.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item(key = "input") {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("e.g. Zwiebeln, feta, 2 Paradeiser") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
                trailingIcon = { IconButton(onClick = { add() }) { Icon(Icons.Filled.Add, "Add") } },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { vm.addTickedToPantry() }) { Text("Add ticked shopping items") }
                Spacer(Modifier.weight(1f))
                if (vm.pantry.isNotEmpty()) TextButton(onClick = { vm.clearPantry() }) { Text("Clear") }
            }
        }

        if (vm.pantry.isNotEmpty()) {
            item(key = "chips") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    vm.pantry.forEach { item ->
                        InputChip(
                            selected = false,
                            onClick = { vm.removePantry(item) },
                            label = { Text(item) },
                            leadingIcon = Veggie.forIngredient(item)?.let { v ->
                                { VeggieIcon(v, size = InputChipDefaults.AvatarSize) }
                            },
                            trailingIcon = { Icon(Icons.Filled.Close, "Remove", Modifier.size(16.dp)) },
                        )
                    }
                }
            }
            item(key = "filters") { RecipeFilters(vm) }
            item(key = "basics") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Basics are at home", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Salt, pepper, oil, sugar and flour don't count against a recipe.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = vm.settings.assumeBasics,
                        onCheckedChange = { vm.updateSettings(vm.settings.copy(assumeBasics = it)) },
                    )
                }
            }
        }

        when {
            vm.pantry.isEmpty() -> item(key = "empty") {
                Column(Modifier.fillMaxWidth().padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    VeggieRow()
                    Text(
                        "Your pantry is empty.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            vm.recipes.isEmpty() -> item(key = "norecipes") {
                Text("Save some recipes first – then they show up here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            matches.isEmpty() -> item(key = "nomatch") {
                Text(
                    "None of your recipes use these ingredients yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> {
                item(key = "title") {
                    Text(
                        "Best matches",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(matches, key = { it.recipe.id }) { m ->
                    Box(Modifier.animateItem()) { MatchCard(vm, m) }
                }
            }
        }
    }
}

@Composable
private fun MatchCard(vm: AppViewModel, m: PantryMatch) {
    val r = m.recipe
    val res = m.result
    Card(onClick = { vm.openRecipeId = r.id }, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                VeggieBadge(Veggie.forRecipe(r))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (res.complete()) "You have everything"
                        else "You have ${res.have} of ${res.counted}" +
                            (if (res.swaps.isNotEmpty()) " · ${res.swaps.size} swap" + (if (res.swaps.size > 1) "s" else "") else ""),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (res.complete()) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (res.complete()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            LinearProgressIndicator(
                progress = { res.score.toFloat() },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
            if (res.swaps.isNotEmpty()) {
                Text(
                    res.swaps.entries.joinToString(" · ") { (k, have) -> "$have instead of ${r.ingredients[k].name}" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (res.missing.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Text(
                        "Missing: " + res.missing.joinToString(", ") { r.ingredients[it].name },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { vm.addMissingToShopping(m) }) { Text("Add to list") }
                }
            }
        }
    }
}
