package com.ratchef.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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

    if (vm.showInstagramLogin) {
        InstagramLoginScreen(
            onSignedIn = { vm.onInstagramSignedIn() },
            onCancel = { vm.showInstagramLogin = false },
        )
        return
    }

    val open = vm.openRecipe
    BackHandler(enabled = open != null) { vm.openRecipeId = null }

    // Recipe opens by sliding in from the right and slides back out; the list stays underneath.
    AnimatedContent(
        targetState = open?.id,
        transitionSpec = {
            val opening = targetState != null
            if (opening) {
                (slideInHorizontally(tween(300)) { it / 4 } + fadeIn(tween(300))) togetherWith
                    fadeOut(tween(150))
            } else {
                fadeIn(tween(250)) togetherWith
                    (slideOutHorizontally(tween(250)) { it / 4 } + fadeOut(tween(250)))
            }
        },
        label = "recipe",
    ) { recipeId ->
        val recipe = recipeId?.let { id -> vm.recipes.firstOrNull { it.id == id } }
        if (recipe != null) {
            RecipeDetailScreen(vm, recipe, snackbar, onBack = { vm.openRecipeId = null })
        } else {
            Tabs(vm, snackbar)
        }
    }
}

@Composable
private fun Tabs(vm: AppViewModel, snackbar: SnackbarHostState) {
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
                        BadgedBox(badge = {
                            if (openCount > 0) Badge {
                                // Count rolls when items are ticked off.
                                AnimatedContent(
                                    targetState = openCount,
                                    transitionSpec = {
                                        val down = targetState < initialState
                                        (slideInVertically { if (down) -it else it } + fadeIn()) togetherWith
                                            (slideOutVertically { if (down) it else -it } + fadeOut())
                                    },
                                    label = "badge",
                                ) { Text("$it") }
                            }
                        }) {
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
        Crossfade(targetState = vm.tab, animationSpec = tween(200), label = "tabs") { tab ->
            when (tab) {
                Tab.RECIPES -> RecipesScreen(vm, m)
                Tab.SHOPPING -> ShoppingScreen(vm, m)
                Tab.SETTINGS -> SettingsScreen(vm, m)
            }
        }
    }
}
