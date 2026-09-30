package com.ratchef.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.ratchef.core.Ingredient
import com.ratchef.core.Quantities
import com.ratchef.core.Recipe
import com.ratchef.core.ShoppingMerger

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailScreen(vm: AppViewModel, recipe: Recipe, snackbar: SnackbarHostState, onBack: () -> Unit) {
    val base = recipe.servings
    // Known servings: target is a number of portions. Unknown: target is a multiplier (×1, ×1.5, …).
    var target by rememberSaveable(recipe.id, base) { mutableStateOf(if (base > 0) base.toDouble() else 1.0) }
    val factor = if (base > 0) target / base else target

    var showEdit by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showServings by remember { mutableStateOf(false) }
    var showCaption by remember { mutableStateOf(false) }
    val done = remember(recipe.id) { mutableStateListOf<Int>() }
    val uri = LocalUriHandler.current

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(recipe.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = { showEdit = true }) { Icon(Icons.Filled.Edit, "Edit") }
                    IconButton(onClick = { showDelete = true }) { Icon(Icons.Filled.Delete, "Delete") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                PortionsCard(
                    base = base,
                    target = target,
                    onChange = { target = it },
                    onSetServings = { showServings = true },
                )
            }
            item {
                Button(
                    onClick = { vm.addToShopping(recipe, factor) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                ) {
                    Icon(Icons.Filled.ShoppingCart, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add to shopping list")
                }
            }

            item { SectionTitle("Ingredients") }
            if (recipe.ingredients.isEmpty()) item { Hint("No ingredients found – tap ✎ to edit the caption.") }
            items(recipe.ingredients) { ing -> IngredientRow(ShoppingMerger.tidy(ing.scaled(factor))) }

            item { SectionTitle("Steps") }
            if (recipe.steps.isEmpty()) item { Hint("No steps in the caption.") }
            itemsIndexed(recipe.steps) { i, step ->
                StepRow(i + 1, step, i in done) { if (i in done) done.removeAll { it == i } else done.add(i) }
            }

            item {
                HorizontalDivider(Modifier.padding(vertical = 16.dp))
                if (recipe.sourceUrl.isNotEmpty()) {
                    TextButton(onClick = { runCatching { uri.openUri(recipe.sourceUrl) } }) { Text("Open original reel") }
                }
                TextButton(onClick = { showCaption = !showCaption }) {
                    Text(if (showCaption) "Hide original caption" else "Show original caption")
                }
                if (showCaption) {
                    Text(
                        recipe.caption,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (recipe.aiParsed) Hint("Converted with Gemini – double-check amounts.")
            }
        }
    }

    if (showEdit) EditDialog(recipe, onDismiss = { showEdit = false }) { title, caption, ai ->
        showEdit = false
        if (caption.trim() == recipe.caption.trim() && !ai) {
            vm.updateRecipe(recipe.id) { this.title = title.trim().ifEmpty { this.title } }
        } else {
            vm.reparse(recipe, caption, forceAi = ai, titleOverride = title.takeIf { it.trim() != recipe.title })
        }
    }

    if (showServings) ServingsDialog(base, onDismiss = { showServings = false }) { n ->
        showServings = false
        vm.updateRecipe(recipe.id) { servings = n }
    }

    if (showDelete) AlertDialog(
        onDismissRequest = { showDelete = false },
        title = { Text("Delete recipe?") },
        text = { Text(recipe.title) },
        confirmButton = {
            TextButton(onClick = {
                showDelete = false
                vm.deleteRecipe(recipe.id)
            }) { Text("Delete") }
        },
        dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Cancel") } },
    )
}

@Composable
private fun PortionsCard(base: Int, target: Double, onChange: (Double) -> Unit, onSetServings: () -> Unit) {
    fun down(v: Double) = if (v <= 1.0) 0.5 else if (base > 0) v - 1 else v - 0.5
    fun up(v: Double) = if (v < 1.0) 1.0 else if (base > 0) v + 1 else v + 0.5

    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable(onClick = onSetServings)) {
                Text(if (base > 0) "Portions" else "Amount", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (base > 0) "Recipe is for $base · tap to change" else "Servings unknown · tap to set",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(onClick = { onChange(down(target)) }, enabled = target > 0.5) { Text("−") }
            Text(
                (if (base > 0) "" else "×") + Quantities.formatNumber(target, null),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(64.dp),
            )
            FilledTonalButton(onClick = { onChange(up(target)) }, enabled = target < 50) { Text("+") }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun IngredientRow(ing: Ingredient) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val amount = Quantities.formatAmount(ing.qty, ing.qtyMax, ing.unit)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
    VeggieIcon(Veggie.forIngredient(ing.name), size = 20.dp)
    Spacer(Modifier.width(12.dp))
    Text(
        buildAnnotatedString {
            if (amount.isNotEmpty()) {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(amount) }
                append(" ")
            }
            append(ing.name)
            if (ing.note.isNotEmpty()) withStyle(SpanStyle(color = muted)) { append(", ${ing.note}") }
        },
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.weight(1f),
    )
    }
}

@Composable
private fun StepRow(n: Int, text: String, done: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp)) {
        Surface(
            shape = CircleShape,
            color = if (done) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    "$n",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            textDecoration = if (done) TextDecoration.LineThrough else null,
            color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun EditDialog(recipe: Recipe, onDismiss: () -> Unit, onSave: (String, String, Boolean) -> Unit) {
    var title by remember { mutableStateOf(recipe.title) }
    var caption by remember { mutableStateOf(recipe.caption) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit recipe") },
        text = {
            Column {
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true)
                Spacer(Modifier.size(8.dp))
                OutlinedTextField(
                    caption,
                    { caption = it },
                    label = { Text("Caption (re-parsed on save)") },
                    minLines = 6,
                    maxLines = 12,
                    modifier = Modifier.heightIn(max = 360.dp),
                )
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = { onSave(title, caption, true) }) { Text("Re-parse with AI") }
                TextButton(onClick = { onSave(title, caption, false) }) { Text("Save") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ServingsDialog(current: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var text by remember { mutableStateOf(if (current > 0) "$current" else "") }
    val value = text.toIntOrNull()?.takeIf { it in 1..50 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("This recipe serves…") },
        text = {
            OutlinedTextField(
                text,
                { text = it.filter(Char::isDigit).take(2) },
                label = { Text("Portions") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = { TextButton(enabled = value != null, onClick = { onSave(value!!) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
