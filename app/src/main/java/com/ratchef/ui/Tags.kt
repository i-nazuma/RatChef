package com.ratchef.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ratchef.core.Recipe
import com.ratchef.core.RecipeTags

fun dietLabel(d: RecipeTags.Diet) = when (d) {
    RecipeTags.Diet.VEGAN -> "Vegan"
    RecipeTags.Diet.VEGETARIAN -> "Veggie"
    RecipeTags.Diet.MEAT -> "Meat & fish"
}

fun effortLabel(e: RecipeTags.Effort) = when (e) {
    RecipeTags.Effort.QUICK -> "Quick"
    RecipeTags.Effort.EVERYDAY -> "Everyday"
    RecipeTags.Effort.WEEKEND -> "Weekend project"
}

@Composable
private fun dietColor(d: RecipeTags.Diet): Color = when (d) {
    RecipeTags.Diet.VEGAN, RecipeTags.Diet.VEGETARIAN -> MaterialTheme.colorScheme.tertiary
    RecipeTags.Diet.MEAT -> MaterialTheme.colorScheme.primary
}

/** Two scrollable rows of filter chips, shared by the Recipes and Pantry tabs. */
@Composable
fun RecipeFilters(vm: AppViewModel, modifier: Modifier = Modifier) {
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RecipeTags.Diet.entries.forEach { d ->
            FilterChip(
                selected = vm.dietFilter == d,
                onClick = { vm.dietFilter = if (vm.dietFilter == d) null else d },
                label = { Text(dietLabel(d)) },
            )
        }
        RecipeTags.Effort.entries.forEach { e ->
            FilterChip(
                selected = vm.effortFilter == e,
                onClick = { vm.effortFilter = if (vm.effortFilter == e) null else e },
                label = { Text(effortLabel(e)) },
            )
        }
    }
}

/** Small coloured label, e.g. on recipe cards. */
@Composable
fun TagPill(text: String, color: Color, onClick: (() -> Unit)? = null) {
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.14f),
        contentColor = color,
        onClick = onClick ?: {},
        enabled = onClick != null,
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
    }
}

@Composable
fun RecipeTagRow(vm: AppViewModel, r: Recipe) {
    val d = vm.diet(r)
    val e = vm.effort(r)
    val min = vm.minutes(r)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
        TagPill(dietLabel(d), dietColor(d))
        TagPill(effortLabel(e) + if (min > 0) " · ~$min min" else "", MaterialTheme.colorScheme.secondary)
    }
}

/** On the recipe screen: tap a tag to correct it. */
@Composable
fun EditableTags(vm: AppViewModel, r: Recipe) {
    var dietMenu by remember { mutableStateOf(false) }
    var effortMenu by remember { mutableStateOf(false) }
    val d = vm.diet(r)
    val e = vm.effort(r)
    val min = vm.minutes(r)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
        Box {
            TagPill(dietLabel(d) + " ▾", dietColor(d)) { dietMenu = true }
            DropdownMenu(expanded = dietMenu, onDismissRequest = { dietMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Detect automatically") },
                    onClick = { dietMenu = false; vm.setDiet(r.id, null) },
                )
                RecipeTags.Diet.entries.forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(dietLabel(opt) + if (r.dietOverride == opt.name) "  ✓" else "") },
                        onClick = { dietMenu = false; vm.setDiet(r.id, opt) },
                    )
                }
            }
        }
        Box {
            TagPill(effortLabel(e) + (if (min > 0) " · ~$min min" else "") + " ▾", MaterialTheme.colorScheme.secondary) {
                effortMenu = true
            }
            DropdownMenu(expanded = effortMenu, onDismissRequest = { effortMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Detect automatically") },
                    onClick = { effortMenu = false; vm.setEffort(r.id, null) },
                )
                RecipeTags.Effort.entries.forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(effortLabel(opt) + if (r.effortOverride == opt.name) "  ✓" else "") },
                        onClick = { effortMenu = false; vm.setEffort(r.id, opt) },
                    )
                }
            }
        }
    }
}
