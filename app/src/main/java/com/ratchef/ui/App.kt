package com.ratchef.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

@Composable
fun App(vm: AppViewModel) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.message = null
        }
    }

    val open = vm.openRecipe
    BackHandler(enabled = open != null) { vm.openRecipeId = null }

    if (open != null) {
        RecipeDetailScreen(vm, open, snackbar, onBack = { vm.openRecipeId = null })
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = vm.tab == Tab.RECIPES,
                    onClick = { vm.tab = Tab.RECIPES },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, null) },
                    label = { Text("Recipes") },
                )
                NavigationBarItem(
                    selected = vm.tab == Tab.SHOPPING,
                    onClick = { vm.tab = Tab.SHOPPING },
                    icon = {
                        val openCount = vm.shopping.count { !it.checked }
                        BadgedBox(badge = { if (openCount > 0) Badge { Text("$openCount") } }) {
                            Icon(Icons.Filled.ShoppingCart, null)
                        }
                    },
                    label = { Text("Shopping") },
                )
                NavigationBarItem(
                    selected = vm.tab == Tab.SETTINGS,
                    onClick = { vm.tab = Tab.SETTINGS },
                    icon = { Icon(Icons.Filled.Settings, null) },
                    label = { Text("Settings") },
                )
            }
        },
    ) { padding ->
        val m = Modifier.padding(padding)
        when (vm.tab) {
            Tab.RECIPES -> RecipesScreen(vm, m)
            Tab.SHOPPING -> ShoppingScreen(vm, m)
            Tab.SETTINGS -> SettingsScreen(vm, m)
        }
    }
}
