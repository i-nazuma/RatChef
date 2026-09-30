package com.ratchef.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ratchef.core.Canon
import com.ratchef.core.Ingredient
import com.ratchef.core.Metric
import com.ratchef.core.Recipe
import com.ratchef.core.RecipeParser
import com.ratchef.core.ShoppingFormat
import com.ratchef.core.ShoppingMerger
import com.ratchef.core.ShoppingSuggestions
import com.ratchef.data.AiMode
import com.ratchef.data.Settings
import com.ratchef.data.ShoppingItem
import com.ratchef.data.Store
import com.ratchef.net.CaptionFetcher
import com.ratchef.net.GeminiClient
import com.ratchef.net.InstagramSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

enum class Tab { RECIPES, SHOPPING, SETTINGS }

/** A proposed merge of several shopping-list items into one. */
data class MergeSuggestion(val items: List<ShoppingItem>, val merged: Ingredient, val reason: String) {
    /** Stable while the same items are on the list; used to remember "keep separate". */
    val key: String get() = items.map { it.id }.sorted().joinToString(",")
}

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

    /** Signed in to Instagram inside RatChef (your own account, via its login page). */
    var instagramSignedIn by mutableStateOf(InstagramSession.isSignedIn())
        private set
    var showInstagramLogin by mutableStateOf(false)

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
            val cookies = InstagramSession.cookieHeader()
            val csrf = InstagramSession.csrfToken()
            val caption = withContext(Dispatchers.IO) {
                runCatching { CaptionFetcher.fetch(url, cookies, csrf) }.getOrNull()
            }
            if (caption.isNullOrBlank()) {
                importState = ImportState.NeedsCaption(
                    url,
                    if (cookies == null) {
                        "Instagram wants a login to show this reel. Sign in to Instagram here, or open the reel, " +
                            "copy the caption and paste it below."
                    } else {
                        "Couldn't read the caption, even signed in. Open the reel, copy the caption and paste it below."
                    },
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

    // ------------------------------------------------------------------ instagram

    fun onInstagramSignedIn() {
        showInstagramLogin = false
        instagramSignedIn = InstagramSession.isSignedIn()
        message = "Signed in to Instagram"
        // Retry the reel that needed a login.
        val pending = importState as? ImportState.NeedsCaption
        if (pending != null && pending.url.isNotEmpty() && pending.caption.isEmpty()) {
            importState = ImportState.Idle
            importText(pending.url)
        }
    }

    /** Sign in by pasting the sessionid cookie from a desktop browser. */
    fun pasteInstagramSession(value: String): Boolean {
        if (!InstagramSession.setSessionId(value)) {
            message = "That doesn't look like a sessionid value"
            return false
        }
        // setCookie is applied asynchronously; the login screen callback path handles the rest.
        onInstagramSignedIn()
        instagramSignedIn = true
        return true
    }

    fun signOutInstagram() {
        InstagramSession.signOut {
            instagramSignedIn = false
            message = "Signed out of Instagram"
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

    private fun addOne(list: List<ShoppingItem>, raw: Ingredient, source: String?): List<ShoppingItem> {
        val ing = Canon.normalize(raw)
        val key = ShoppingMerger.key(ing)
        val base = ShoppingMerger.baseKey(ing)
        fun ShoppingItem.withSource() =
            if (source == null || source in sources) this else copy(sources = sources + source)

        // Same thing (in any language), same kind of amount, still unchecked -> add up.
        val idx = list.indexOfFirst { !it.checked && ShoppingMerger.key(it.ingredient) == key }
        if (idx >= 0) {
            val merged = list[idx].copy(ingredient = ShoppingMerger.combine(list[idx].ingredient, ing)).withSource()
            return list.toMutableList().also { it[idx] = merged }
        }
        // "salt (to taste)" when "1 tsp salt" is already there -> nothing to add.
        if (!ing.hasQty()) {
            val same = list.indexOfFirst { !it.checked && ShoppingMerger.baseKey(it.ingredient) == base }
            if (same >= 0) return list.toMutableList().also { it[same] = it[same].withSource() }
        } else {
            // Replace a quantity-less placeholder with the real amount.
            val placeholder = list.indexOfFirst {
                !it.checked && !it.ingredient.hasQty() && ShoppingMerger.baseKey(it.ingredient) == base
            }
            if (placeholder >= 0) {
                val merged = list[placeholder].copy(ingredient = ing).withSource()
                return list.toMutableList().also { it[placeholder] = merged }
            }
        }
        return list + ShoppingItem(UUID.randomUUID().toString(), ing, false, listOfNotNull(source))
    }

    // ------------------------------------------------------------------ merge suggestions

    var dismissedMerges by mutableStateOf(store.loadDismissed())
        private set

    /** Items that are probably the same thing to buy; nothing changes until the user accepts. */
    val mergeSuggestions: List<MergeSuggestion>
        get() {
            val open = shopping.filterNot { it.checked }
            return ShoppingSuggestions.compute(open.map { it.ingredient }, settings.listGerman)
                .map { s -> MergeSuggestion(s.items.map { open[it] }, s.merged, s.reason) }
                .filterNot { it.key in dismissedMerges }
        }

    fun applyMerge(s: MergeSuggestion) {
        val ids = s.items.map { it.id }.toSet()
        val first = shopping.indexOfFirst { it.id in ids }
        if (first < 0) return
        val merged = ShoppingItem(
            id = UUID.randomUUID().toString(),
            ingredient = s.merged,
            checked = false,
            sources = s.items.flatMap { it.sources }.distinct(),
        )
        val list = shopping.toMutableList()
        list[first] = merged
        shopping = list.filterIndexed { i, it -> i == first || it.id !in ids }
        store.saveShopping(shopping)
    }

    fun applyAllMerges() {
        mergeSuggestions.forEach { applyMerge(it) }
        message = "Shopping list tidied up"
    }

    fun dismissMerge(s: MergeSuggestion) {
        dismissedMerges = dismissedMerges + s.key
        store.saveDismissed(dismissedMerges)
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

    fun shoppingAsText(): String {
        val german = settings.listGerman
        return shopping.filterNot { it.checked }
            .groupBy { ShoppingFormat.aisle(it.ingredient) }
            .toSortedMap()
            .entries.joinToString("\n\n") { (aisle, items) ->
                aisle.label(german) + "\n" + items.joinToString("\n") { "☐ " + ShoppingFormat.line(it.ingredient, german) }
            }
    }

    // ------------------------------------------------------------------ settings

    fun updateSettings(s: Settings) {
        settings = s
        store.saveSettings(s)
    }
}
