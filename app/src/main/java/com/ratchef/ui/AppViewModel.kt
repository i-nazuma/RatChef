package com.ratchef.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ratchef.core.Canon
import com.ratchef.core.Ingredient
import com.ratchef.core.LanguageGuess
import com.ratchef.core.Metric
import com.ratchef.core.PantryMatcher
import com.ratchef.core.Recipe
import com.ratchef.core.RecipeParser
import com.ratchef.core.RecipeTags
import com.ratchef.core.RecipeText
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

enum class Tab { RECIPES, PANTRY, SHOPPING, SETTINGS }

/** A saved recipe ranked against the pantry; [recipe] is as displayed (translated if set). */
data class PantryMatch(val recipe: Recipe, val result: PantryMatcher.Result)

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

    var recipes by mutableStateOf(
        store.loadRecipes().sortedByDescending { it.createdAt }.onEach { r ->
            // Re-check the language of every recipe from its content (an older, weaker guess could be wrong).
            val guess = LanguageGuess.guess(r.title + "\n" + r.steps.joinToString("\n") + "\n" +
                r.ingredients.joinToString("\n") { it.name })
            if (guess.isNotEmpty() && guess != r.lang && !r.aiParsed) {
                r.lang = guess
                r.translations.remove(guess)
            } else if (r.lang.isEmpty()) {
                r.lang = guess
            }
        }
    )
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
        if (Store.looksLikeBackup(input)) {
            importBackup(input)
            return
        }
        tab = Tab.RECIPES
        openRecipeId = null

        val youtube = CaptionFetcher.youtubeId(input)
        if (youtube != null && CaptionFetcher.isJustALink(input)) {
            importYoutube(youtube)
            return
        }

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

    /**
     * YouTube Shorts / videos: read the description, then (with a Gemini key) let Gemini watch the
     * video and combine both. Without a key the description is parsed offline.
     */
    private fun importYoutube(id: String) {
        val url = CaptionFetcher.youtubeUrl(id)
        importState = ImportState.Loading("Reading the video description…")
        viewModelScope.launch {
            val description = withContext(Dispatchers.IO) {
                runCatching { CaptionFetcher.youtubeDescription(id) }.getOrNull()
            }
            val s = settings
            if (s.apiKey.isNotBlank() && s.aiMode != AiMode.OFF) {
                importState = ImportState.Loading("Gemini is watching the video…")
                val ai = withContext(Dispatchers.IO) {
                    runCatching {
                        GeminiClient.parseVideo(url, description, s.apiKey, s.model.ifBlank { Settings.DEFAULT_MODEL })
                    }
                }
                val recipe = ai.getOrNull()
                if (recipe != null && recipe.ingredients.isNotEmpty()) {
                    saveRecipe(recipe, url, description ?: "", existingId = null)
                    return@launch
                }
                message = "Couldn't read the video: " + (ai.exceptionOrNull()?.message ?: "no recipe found")
            }
            if (!description.isNullOrBlank()) {
                parseAndSave(url, description, existingId = null)
            } else {
                importState = ImportState.NeedsCaption(
                    url,
                    if (s.apiKey.isBlank()) "To read recipes from the video itself, add a Gemini key in Settings. " +
                        "Or paste the recipe text below."
                    else "Couldn't get a recipe from this video. Paste the recipe text below.",
                )
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
        saveRecipe(recipe, url, caption, existingId, titleOverride)
    }

    private fun saveRecipe(
        recipe: Recipe,
        url: String,
        caption: String,
        existingId: String?,
        titleOverride: String? = null,
    ) {
        val old = existingId?.let { id -> recipes.firstOrNull { it.id == id } }
        recipe.id = existingId ?: UUID.randomUUID().toString()
        recipe.sourceUrl = url
        recipe.caption = caption
        recipe.createdAt = old?.createdAt ?: System.currentTimeMillis()
        if (recipe.servings == 0 && old != null) recipe.servings = old.servings
        if (!titleOverride.isNullOrBlank()) recipe.title = titleOverride.trim()
        if (recipe.lang.isEmpty()) recipe.lang = guessLanguage(recipe)

        recipes = if (old != null) recipes.map { if (it.id == recipe.id) recipe else it } else listOf(recipe) + recipes
        store.saveRecipes(recipes)
        importState = ImportState.Idle
        openRecipeId = recipe.id
        if (!recipe.looksComplete() && message == null) {
            message = "Parsed partially – check the recipe or edit the caption."
        }
        ensureTranslation(recipe)
    }

    // ------------------------------------------------------------------ tags & filters

    /** Vegan counts as vegetarian too; MEAT filter means "with meat or fish". */
    var dietFilter by mutableStateOf<RecipeTags.Diet?>(null)
    var effortFilter by mutableStateOf<RecipeTags.Effort?>(null)

    fun diet(r: Recipe): RecipeTags.Diet =
        runCatching { RecipeTags.Diet.valueOf(r.dietOverride) }.getOrNull()
            ?: RecipeTags.diet(r.ingredients + (r.translations.values.firstOrNull()?.ingredients ?: emptyList()))

    fun effort(r: Recipe): RecipeTags.Effort =
        runCatching { RecipeTags.Effort.valueOf(r.effortOverride) }.getOrNull()
            ?: RecipeTags.effort(r.ingredients, r.steps, r.caption)

    fun minutes(r: Recipe): Int = RecipeTags.minutes(r.steps, r.caption)

    fun matchesFilters(r: Recipe): Boolean {
        val d = dietFilter
        if (d != null) {
            val rd = diet(r)
            val ok = when (d) {
                RecipeTags.Diet.VEGAN -> rd == RecipeTags.Diet.VEGAN
                RecipeTags.Diet.VEGETARIAN -> rd != RecipeTags.Diet.MEAT
                RecipeTags.Diet.MEAT -> rd == RecipeTags.Diet.MEAT
            }
            if (!ok) return false
        }
        val e = effortFilter
        return e == null || effort(r) == e
    }

    val filteredRecipes: List<Recipe> get() = recipes.filter { matchesFilters(it) }

    /** null resets to the automatic guess. */
    fun setDiet(id: String, d: RecipeTags.Diet?) = updateRecipe(id) { dietOverride = d?.name ?: "" }
    fun setEffort(id: String, e: RecipeTags.Effort?) = updateRecipe(id) { effortOverride = e?.name ?: "" }

    // ------------------------------------------------------------------ pantry

    var pantry by mutableStateOf(store.loadPantry())
        private set

    /** Adds one or more items ("Zwiebeln, Feta, 2 Paradeiser"); duplicates are ignored. */
    fun addPantry(text: String) {
        val known = pantry.map { ShoppingMerger.normalizeName(PantryMatcher.cleanName(it)) }.toMutableSet()
        val added = text.split(',', ';', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filter { known.add(ShoppingMerger.normalizeName(PantryMatcher.cleanName(it))) }
            .map { PantryMatcher.cleanName(it).replaceFirstChar { c -> c.uppercaseChar() } }
        if (added.isEmpty()) return
        pantry = pantry + added
        store.savePantry(pantry)
    }

    fun removePantry(item: String) {
        pantry = pantry - item
        store.savePantry(pantry)
    }

    fun clearPantry() {
        pantry = emptyList()
        store.savePantry(pantry)
    }

    /** Things ticked off on the shopping list are now at home. */
    fun addTickedToPantry() {
        val names = shopping.filter { it.checked }.map { ShoppingFormat.line(it.ingredient.let { i ->
            Ingredient(Double.NaN, Double.NaN, "", i.name, "") }, settings.listGerman) }
        if (names.isEmpty()) {
            message = "Nothing ticked off on the shopping list yet"
            return
        }
        val before = pantry.size
        addPantry(names.joinToString(","))
        message = "Added ${pantry.size - before} items to your pantry"
    }

    /** Saved recipes ranked by how much of them you can cook right now. Offline, no AI. */
    val pantryMatches: List<PantryMatch>
        get() {
            if (pantry.isEmpty()) return emptyList()
            val shownRecipes = recipes.filter { matchesFilters(it) }.map { shown(it) }
            return PantryMatcher.rank(pantry, shownRecipes.map { it.ingredients }, settings.assumeBasics)
                .map { PantryMatch(shownRecipes[it.index], it) }
        }

    /** Puts only the missing ingredients of a recipe on the shopping list. */
    fun addMissingToShopping(match: PantryMatch) {
        var list = shopping
        val r = match.recipe
        for (k in match.result.missing) {
            val ing = r.ingredients[k].let { if (settings.metric) Metric.convert(it) else it }
            list = addOne(list, ing, r.title)
        }
        shopping = list
        store.saveShopping(shopping)
        message = "Added ${match.result.missing.size} missing items to the shopping list"
    }

    // ------------------------------------------------------------------ sharing & backup

    /** Readable text of a recipe as currently shown (language, portions, metric) for sending to friends. */
    fun shareText(recipe: Recipe, factor: Double): String {
        val r = shown(recipe)
        val german = (if (r === recipe) recipe.lang else recipeTarget ?: recipe.lang) == "de"
        val ingredients = r.ingredients.map { ing ->
            val scaled = ing.scaled(factor)
            ShoppingMerger.tidy(if (settings.metric) Metric.convert(scaled) else scaled)
        }
        val steps = if (settings.metric) r.steps.map { Metric.convertText(it) } else r.steps
        val servings = if (r.servings > 0) Math.round(r.servings * factor).toInt() else 0
        return RecipeText.format(r.title, servings, ingredients, steps, r.sourceUrl, german)
    }

    fun exportBackup(): String = Store.exportJson(recipes)

    /** Adds recipes from a backup or a friend's file; skips ones already here. */
    fun importBackup(text: String) {
        val incoming = runCatching { Store.importJson(text) }.getOrElse {
            message = "That file isn't a RatChef export"
            return
        }
        val ids = recipes.map { it.id }.toSet()
        val fresh = incoming.filter { it.id !in ids }.onEach { if (it.createdAt == 0L) it.createdAt = System.currentTimeMillis() }
        if (fresh.isNotEmpty()) {
            recipes = (fresh + recipes).sortedByDescending { it.createdAt }
            store.saveRecipes(recipes)
            fresh.forEach { ensureTranslation(it) }
        }
        tab = Tab.RECIPES
        openRecipeId = null
        val skipped = incoming.size - fresh.size
        message = "Imported ${fresh.size} recipe" + (if (fresh.size == 1) "" else "s") +
            (if (skipped > 0) " ($skipped already there)" else "")
    }

    // ------------------------------------------------------------------ translation

    /** Language recipes should be shown in, or null for "as written". */
    val recipeTarget: String?
        get() = when (settings.recipeLanguage) {
            "en" -> "en"
            "de" -> "de"
            "auto" -> java.util.Locale.getDefault().language.takeIf { it == "en" || it == "de" }
            else -> null
        }

    /** Recipes the user flipped back to their original language (this session). */
    var showOriginal by mutableStateOf(setOf<String>())
        private set
    var translating by mutableStateOf(setOf<String>())
        private set

    fun toggleOriginal(id: String) {
        showOriginal = if (id in showOriginal) showOriginal - id else showOriginal + id
    }

    fun needsTranslation(r: Recipe): Boolean {
        val t = recipeTarget ?: return false
        return r.lang != t
    }

    /** The recipe as it should be displayed and shopped: translated if a translation exists. */
    fun shown(r: Recipe): Recipe {
        val t = recipeTarget ?: return r
        if (r.id in showOriginal || r.lang == t) return r
        val tr = r.translations[t] ?: return r
        return r.translatedCopy(tr)
    }

    /** Translates in the background if needed and possible (Gemini key). */
    fun ensureTranslation(r: Recipe) {
        val t = recipeTarget ?: return
        if (r.lang == t || r.translations.containsKey(t) || r.id in translating) return
        val s = settings
        if (s.apiKey.isBlank()) return
        translating = translating + r.id
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { GeminiClient.translate(r, t, s.apiKey, s.model.ifBlank { Settings.DEFAULT_MODEL }) }
            }
            result.onSuccess { (tr, source) ->
                if (source == t) {
                    // It was already in the target language: fix the label instead of keeping a "translation".
                    updateRecipe(r.id) { lang = t; translations.remove(t) }
                } else {
                    updateRecipe(r.id) {
                        translations[t] = tr
                        if (source.length == 2) lang = source
                    }
                }
            }
                .onFailure { e -> message = "Couldn't translate “${r.title}”: ${e.message}" }
            translating = translating - r.id
        }
    }

    /** After changing the recipe language: translate the saved recipes one by one. */
    private fun translateAll() {
        val t = recipeTarget ?: return
        if (settings.apiKey.isBlank()) return
        viewModelScope.launch {
            for (r in recipes.filter { it.lang != t && !it.translations.containsKey(t) }) {
                ensureTranslation(r)
                kotlinx.coroutines.delay(5000) // free tier allows ~15 requests a minute on Flash-Lite
            }
        }
    }

    private fun guessLanguage(r: Recipe): String =
        LanguageGuess.guess(r.title + "\n" + r.steps.joinToString("\n") + "\n" +
            r.ingredients.joinToString("\n") { it.name }).ifEmpty { LanguageGuess.guess(r.caption) }

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
        val shownRecipe = shown(recipe)
        for (raw in shownRecipe.ingredients) {
            val ing = raw.scaled(factor).let { if (settings.metric) Metric.convert(it) else it }
            if (ShoppingMerger.shouldSkip(ing)) continue
            list = addOne(list, ing, shownRecipe.title)
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
        val languageChanged = s.recipeLanguage != settings.recipeLanguage
        settings = s
        store.saveSettings(s)
        if (languageChanged) translateAll()
    }
}
