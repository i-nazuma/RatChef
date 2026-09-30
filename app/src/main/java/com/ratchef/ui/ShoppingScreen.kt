package com.ratchef.ui

import android.content.Intent
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
import com.ratchef.core.ShoppingMerger
import com.ratchef.data.ShoppingItem

@Composable
fun ShoppingScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var newItem by rememberSaveable { mutableStateOf("") }
    val open = vm.shopping.filterNot { it.checked }
    val checked = vm.shopping.filter { it.checked }

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
        }

        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            items(open, key = { it.id }) { ItemRow(it, vm) }
            if (checked.isNotEmpty()) {
                item {
                    Text(
                        "In the cart",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                items(checked, key = { it.id }) { ItemRow(it, vm) }
            }
        }
    }
}

@Composable
private fun ItemRow(item: ShoppingItem, vm: AppViewModel) {
    Row(
        Modifier.fillMaxWidth().clickable { vm.toggleItem(item.id) }.padding(start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = item.checked, onCheckedChange = { vm.toggleItem(item.id) })
        VeggieIcon(
            Veggie.forIngredient(item.ingredient.name),
            size = 20.dp,
            modifier = Modifier.alpha(if (item.checked) 0.4f else 1f),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                ShoppingMerger.tidy(item.ingredient).display(),
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                color = if (item.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            if (item.sources.isNotEmpty()) {
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
