package com.ratchef.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ratchef.core.Ingredient
import com.ratchef.core.Metric
import com.ratchef.core.Recipe
import com.ratchef.core.RecipeParser
import com.ratchef.core.ShoppingMerger
import com.ratchef.data.AiMode
import com.ratchef.data.Settings
import com.ratchef.data.ShoppingItem
import com.ratchef.data.Store
import com.ratchef.net.CaptionFetcher
import com.ratchef.net.GeminiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

enum class Tab { RECIPES, SHOPPING, SETTINGS }

sealed interface ImportState {
    data object Idle : ImportState
    data class Loading(val message: String) : ImportState
    /** Caption couldn't be fetched or contained no recipe: let the user paste/edit it. */
    data class NeedsCaption(val url: String, val reason: String, val caption: String = "") : ImportState
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val store = Store(app)

    var recipes by mutableStateOf(store.loadRecipes().sortedByDescending { it.createdAt })
        private set
    var shopping by mutableStateOf(store.loadShopping())
        private set
    var settings by mutableStateOf(store.loadSettings())
        private set
    var importState by mutableStateOf<ImportState>(ImportState.Idle)
        private set
    var openRecipeId by mutableStateOf<String?>(null)
    var tab by mutableStateOf(Tab.RECIPES)

    /** One-shot message for the snackbar. */
    var message by mutableStateOf<String?>(null)

    val openRecipe: Recipe? get() = recipes.firstOrNull { it.id == openRecipeId }

    // ------------------------------------------------------------------ import

    /** Entry point for shared text, pasted links and pasted captions. */
    fun importText(text: String) {
        val input = text.trim()
        if (input.isEmpty()) return
        if (importState is ImportState.Loading) return
        tab = Tab.RECIPES
        openRecipeId = null

        val url = CaptionFetcher.extractUrl(input)
        if (url == null || !CaptionFetcher.isJustALink(input)) {
            // The user pasted the caption itself (maybe with a link in it).
            viewModelScope.launch { parseAndSave(url ?: "", input, existingId = null) }
            return
        }
        importState = ImportState.Loading("Fetching caption…")
        viewModelScope.launch {
            val caption = withContext(Dispatchers.IO) { runCatching { CaptionFetcher.fetch(url) }.getOrNull() }
            if (caption.isNullOrBlank()) {
                importState = ImportState.NeedsCaption(
                    url,
                    "Couldn't read the caption automatically (Instagram may require login for this post). " +
                        "Open the reel, copy the caption and paste it here.",
                )
            } else {
                parseAndSave(url, caption, existingId = null)
            }
        }
    }

    fun submitCaption(url: String, caption: String) {
        if (caption.isBlank()) return
        viewModelScope.launch { parseAndSave(url, caption.trim(), existingId = null) }
    }

    fun cancelImport() {
        importState = ImportState.Idle
    }

    /** Re-run parsing on an edited caption, keeping the recipe's id. */
    fun reparse(recipe: Recipe, caption: String, forceAi: Boolean, titleOverride: String? = null) {
        viewModelScope.launch { parseAndSave(recipe.sourceUrl, caption.trim(), recipe.id, forceAi, titleOverride) }
    }

    private suspend fun parseAndSave(
        url: String,
        caption: String,
        existingId: String?,
        forceAi: Boolean = false,
        titleOverride: String? = null,
    ) {
        importState = ImportState.Loading("Reading recipe…")
        var recipe = withContext(Dispatchers.Default) { RecipeParser.parse(caption) }

        val s = settings
        val wantAi = s.apiKey.isNotBlank() && s.aiMode != AiMode.OFF &&
            (forceAi || s.aiMode == AiMode.ALWAYS || !recipe.looksComplete())
        if (wantAi) {
            importState = ImportState.Loading("Asking Gemini…")
            val ai = withContext(Dispatchers.IO) {
                runCatching { GeminiClient.parse(caption, s.apiKey, s.model.ifBlank { Settings.DEFAULT_MODEL }) }
            }
            ai.onSuccess { r -> if (r.ingredients.isNotEmpty()) recipe = r }
                .onFailure { e -> message = "AI failed, used offline parser: ${e.message}" }
        } else if (forceAi) {
            message = "Add a Gemini API key in Settings to use AI parsing."
        }

        if (recipe.ingredients.isEmpty() && recipe.steps.isEmpty()) {
            importState = ImportState.NeedsCaption(
                url,
                "No recipe found in that text. Paste or edit the recipe below:",
                caption,
            )
            return
        }

        val old = existingId?.let { id -> recipes.firstOrNull { it.id == id } }
        recipe.id = existingId ?: UUID.randomUUID().toString()
        recipe.sourceUrl = url
        recipe.caption = caption
        recipe.createdAt = old?.createdAt ?: System.currentTimeMillis()
        if (recipe.servings == 0 && old != null) recipe.servings = old.servings
        if (!titleOverride.isNullOrBlank()) recipe.title = titleOverride.trim()

        recipes = if (old != null) recipes.map { if (it.id == recipe.id) recipe else it } else listOf(recipe) + recipes
        store.saveRecipes(recipes)
        importState = ImportState.Idle
        openRecipeId = recipe.id
        if (!recipe.looksComplete() && message == null) {
            message = "Parsed partially – check the recipe or edit the caption."
        }
    }

    // ------------------------------------------------------------------ recipes

    fun deleteRecipe(id: String) {
        recipes = recipes.filterNot { it.id == id }
        store.saveRecipes(recipes)
        if (openRecipeId == id) openRecipeId = null
    }

    fun updateRecipe(id: String, edit: Recipe.() -> Unit) {
        recipes = recipes.map { if (it.id == id) Store.copy(it, edit) else it }
        store.saveRecipes(recipes)
    }

    // ------------------------------------------------------------------ shopping list

    fun addToShopping(recipe: Recipe, factor: Double) {
        var list = shopping
        var added = 0
        for (raw in recipe.ingredients) {
            val ing = raw.scaled(factor).let { if (settings.metric) Metric.convert(it) else it }
            if (ShoppingMerger.shouldSkip(ing)) continue
            list = addOne(list, ing, recipe.title)
            added++
        }
        shopping = list
        store.saveShopping(shopping)
        message = "Added $added items to the shopping list"
    }

    fun addManualItem(text: String) {
        val ing = RecipeParser.parseIngredient(text.trim()) ?: return
        shopping = addOne(shopping, ing, null)
        store.saveShopping(shopping)
    }

    private fun addOne(list: List<ShoppingItem>, ing: Ingredient, source: String?): List<ShoppingItem> {
        val key = ShoppingMerger.key(ing)
        val name = ShoppingMerger.normalizeName(ing.name)
        fun ShoppingItem.withSource() =
            if (source == null || source in sources) this else copy(sources = sources + source)

        // Same thing, same kind of unit, still unchecked -> add up.
        val idx = list.indexOfFirst { !it.checked && ShoppingMerger.key(it.ingredient) == key }
        if (idx >= 0) {
            val merged = list[idx].copy(ingredient = ShoppingMerger.combine(list[idx].ingredient, ing)).withSource()
            return list.toMutableList().also { it[idx] = merged }
        }
        // "salt (to taste)" when "1 tsp salt" is already there -> nothing to add.
        if (!ing.hasQty()) {
            val same = list.indexOfFirst { !it.checked && ShoppingMerger.normalizeName(it.ingredient.name) == name }
            if (same >= 0) return list.toMutableList().also { it[same] = it[same].withSource() }
        } else {
            // Replace a quantity-less placeholder with the real amount.
            val placeholder = list.indexOfFirst {
                !it.checked && !it.ingredient.hasQty() && ShoppingMerger.normalizeName(it.ingredient.name) == name
            }
            if (placeholder >= 0) {
                val merged = list[placeholder].copy(ingredient = ing).withSource()
                return list.toMutableList().also { it[placeholder] = merged }
            }
        }
        return list + ShoppingItem(UUID.randomUUID().toString(), ing, false, listOfNotNull(source))
    }

    fun toggleItem(id: String) {
        shopping = shopping.map { if (it.id == id) it.copy(checked = !it.checked) else it }
        store.saveShopping(shopping)
    }

    fun removeItem(id: String) {
        shopping = shopping.filterNot { it.id == id }
        store.saveShopping(shopping)
    }

    fun clearChecked() {
        shopping = shopping.filterNot { it.checked }
        store.saveShopping(shopping)
    }

    fun clearAll() {
        shopping = emptyList()
        store.saveShopping(shopping)
    }

    fun shoppingAsText(): String =
        shopping.filterNot { it.checked }.joinToString("\n") { "☐ " + ShoppingMerger.tidy(it.ingredient).display() }

    // ------------------------------------------------------------------ settings

    fun updateSettings(s: Settings) {
        settings = s
        store.saveSettings(s)
    }
}
