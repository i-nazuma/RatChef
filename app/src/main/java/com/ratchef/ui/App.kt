package com.ratchef.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.material.icons.filled.Home
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
import androidx.compose.ui.unit.IntOffset

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

    // Plain slide, no fading: the recipe pushes in from the right over the list and slides back
    // out to the right. Both screens are opaque the whole time, so nothing flashes through.
    AnimatedContent(
        targetState = open?.id,
        transitionSpec = {
            val opening = targetState != null
            val spec = tween<IntOffset>(280, easing = FastOutSlowInEasing)
            if (opening) {
                (slideInHorizontally(spec) { it } togetherWith slideOutHorizontally(spec) { -it / 4 })
                    .apply { targetContentZIndex = 1f }
            } else {
                (slideInHorizontally(spec) { -it / 4 } togetherWith slideOutHorizontally(spec) { it })
                    .apply { targetContentZIndex = -1f }
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
                    selected = vm.tab == Tab.PANTRY,
                    onClick = { vm.tab = Tab.PANTRY },
                    icon = { Icon(Icons.Filled.Home, null) },
                    label = { Text("Pantry") },
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
                Tab.PANTRY -> PantryScreen(vm, m)
                Tab.SHOPPING -> ShoppingScreen(vm, m)
                Tab.SETTINGS -> SettingsScreen(vm, m)
            }
        }
    }
}
