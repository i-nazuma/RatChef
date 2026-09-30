package com.ratchef.ui

import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.font.FontWeight
import com.ratchef.core.ShoppingFormat
import com.ratchef.data.ShoppingItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var newItem by rememberSaveable { mutableStateOf("") }
    val german = vm.settings.listGerman
    val byRecipe = vm.settings.shoppingGroup == "recipe"
    val open = vm.shopping.filterNot { it.checked }
    val checked = vm.shopping.filter { it.checked }
    val suggestions = vm.mergeSuggestions
    val sections = if (byRecipe) groupByRecipe(open, german) else groupByAisle(open, german)

    // Ticking an item: the checkbox and strike-through animate in place first, then the item
    // slides into "In the cart" (or back up) and briefly lights up where it lands.
    val scope = rememberCoroutineScope()
    val pending = remember { mutableStateMapOf<String, Boolean>() }
    var landed by remember { mutableStateOf<String?>(null) }
    fun toggle(item: ShoppingItem) {
        if (item.id in pending) return
        pending[item.id] = !item.checked
        scope.launch {
            delay(450)
            vm.toggleItem(item.id)
            pending.remove(item.id)
            landed = item.id
            delay(1200)
            if (landed == item.id) landed = null
        }
    }

    fun addItem() {
        if (newItem.isNotBlank()) {
            vm.addManualItem(newItem)
            newItem = ""
        }
    }

    Column(modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Shopping list", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            IconButton(enabled = open.isNotEmpty(), onClick = {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, vm.shoppingAsText())
                context.startActivity(Intent.createChooser(send, "Share shopping list"))
            }) { Icon(Icons.Filled.Share, "Share") }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Remove checked") },
                        enabled = checked.isNotEmpty(),
                        onClick = { menu = false; vm.clearChecked() },
                    )
                    DropdownMenuItem(
                        text = { Text("Clear list") },
                        enabled = vm.shopping.isNotEmpty(),
                        onClick = { menu = false; vm.clearAll() },
                    )
                }
            }
        }

        OutlinedTextField(
            value = newItem,
            onValueChange = { newItem = it },
            placeholder = { Text("Add item, e.g. 2 lemons") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { addItem() }),
            trailingIcon = { IconButton(onClick = { addItem() }) { Icon(Icons.Filled.Add, "Add") } },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )

        if (vm.shopping.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(top = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                VeggieRow()
                Text(
                    "Empty. Open a recipe and tap “Add to shopping list”.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            return@Column
        }

        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !byRecipe,
                onClick = { vm.updateSettings(vm.settings.copy(shoppingGroup = "aisle")) },
                label = { Text("By aisle") },
            )
            FilterChip(
                selected = byRecipe,
                onClick = { vm.updateSettings(vm.settings.copy(shoppingGroup = "recipe")) },
                label = { Text("By recipe") },
            )
        }

        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            if (suggestions.isNotEmpty()) {
                item(key = "suggestions") {
                    Box(Modifier.animateItem()) { SuggestionsCard(vm, suggestions, german) }
                }
            }
            sections.forEach { (title, sectionItems) ->
                item(key = "h-$title") { SectionHeader(title, Modifier.animateItem()) }
                items(sectionItems, key = { it.id }) {
                    ItemRow(
                        it, vm, german,
                        showSources = !byRecipe || title == severalLabel(german),
                        checked = pending[it.id] ?: it.checked,
                        highlight = landed == it.id,
                        onToggle = { toggle(it) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            if (checked.isNotEmpty()) {
                item(key = "h-cart") { SectionHeader("In the cart", Modifier.animateItem()) }
                items(checked, key = { it.id }) {
                    ItemRow(
                        it, vm, german,
                        showSources = false,
                        checked = pending[it.id] ?: it.checked,
                        highlight = landed == it.id,
                        onToggle = { toggle(it) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

private fun severalLabel(german: Boolean) = if (german) "Mehrere Rezepte" else "Several recipes"

private fun groupByAisle(items: List<ShoppingItem>, german: Boolean): List<Pair<String, List<ShoppingItem>>> =
    items.groupBy { ShoppingFormat.aisle(it.ingredient) }
        .toSortedMap()
        .map { (aisle, list) ->
            aisle.label(german) to list.sortedBy { ShoppingFormat.sortName(it.ingredient, german) }
        }

private fun groupByRecipe(items: List<ShoppingItem>, german: Boolean): List<Pair<String, List<ShoppingItem>>> {
    val several = severalLabel(german)
    val mine = if (german) "Selbst hinzugefügt" else "Added by you"
    val groups = LinkedHashMap<String, MutableList<ShoppingItem>>()
    for (item in items) {
        val title = when (item.sources.size) {
            0 -> mine
            1 -> item.sources[0]
            else -> several
        }
        groups.getOrPut(title) { mutableListOf() }.add(item)
    }
    // Recipes first, then shared items, then things added by hand.
    val order = groups.keys.sortedBy { if (it == several) 1 else if (it == mine) 2 else 0 }
    return order.map { it to groups.getValue(it) }
}

@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SuggestionsCard(vm: AppViewModel, suggestions: List<MergeSuggestion>, german: Boolean) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.animateContentSize().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (suggestions.size == 1) "1 thing could be merged" else "${suggestions.size} things could be merged",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f).clickable { expanded = !expanded },
                )
                TextButton(onClick = { vm.applyAllMerges() }) { Text("Merge all") }
            }
            if (expanded) {
                suggestions.forEach { s ->
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        VeggieIcon(Veggie.forIngredient(s.merged.name), size = 20.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                s.items.joinToString(" + ") { ShoppingFormat.line(it.ingredient, german) },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
                            )
                            Text(
                                "→ " + ShoppingFormat.line(s.merged, german),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                reasonText(s.reason),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f),
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { vm.dismissMerge(s) }) { Text("Keep separate") }
                        FilledTonalButton(onClick = { vm.applyMerge(s) }) { Text("Merge") }
                    }
                }
            }
        }
    }
}

private fun reasonText(reason: String) = when (reason) {
    "kinds" -> "Different kinds of the same thing"
    "fruit" -> "Juice or zest – buy the whole fruit"
    "similar" -> "Similar names"
    else -> "Same thing, written differently"
}

@Composable
private fun ItemRow(
    item: ShoppingItem,
    vm: AppViewModel,
    german: Boolean,
    showSources: Boolean,
    checked: Boolean,
    highlight: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val textColor by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        tween(250), label = "text",
    )
    val iconAlpha by animateFloatAsState(if (checked) 0.4f else 1f, tween(250), label = "icon")
    val background by animateColorAsState(
        if (highlight) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        tween(if (highlight) 200 else 900), label = "landed",
    )
    Row(
        modifier.fillMaxWidth().background(background).clickable(onClick = onToggle).padding(start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        VeggieIcon(Veggie.forIngredient(item.ingredient.name), size = 20.dp, modifier = Modifier.alpha(iconAlpha))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                ShoppingFormat.line(item.ingredient, german),
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (checked) TextDecoration.LineThrough else null,
                color = textColor,
            )
            if (showSources && item.sources.isNotEmpty()) {
                Text(
                    item.sources.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        IconButton(onClick = { vm.removeItem(item.id) }) { Icon(Icons.Filled.Close, "Remove") }
    }
}
