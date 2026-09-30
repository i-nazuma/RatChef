package com.ratchef.data

import android.content.Context
import android.util.AtomicFile
import com.ratchef.core.Ingredient
import com.ratchef.core.Recipe
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class AiMode { OFF, FALLBACK, ALWAYS }

data class Settings(
    val apiKey: String = "",
    val model: String = DEFAULT_MODEL,
    val aiMode: AiMode = AiMode.FALLBACK,
    /** Show cups/oz/lb as g/ml and °F as °C. */
    val metric: Boolean = true,
) {
    val aiAvailable: Boolean get() = apiKey.isNotBlank() && aiMode != AiMode.OFF

    companion object {
        /** Alias that Google keeps pointing at the current Flash-Lite model. */
        const val DEFAULT_MODEL = "gemini-flash-lite-latest"
    }
}

data class ShoppingItem(
    val id: String,
    val ingredient: Ingredient,
    val checked: Boolean = false,
    val sources: List<String> = emptyList(),
)

/** Tiny JSON-file persistence. Everything lives in the app's private storage. */
class Store(context: Context) {
    private val dir = context.filesDir
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    // ------------------------------------------------------------ recipes

    fun loadRecipes(): List<Recipe> = readArray("recipes.json").mapObjects { recipeFromJson(it) }

    fun saveRecipes(list: List<Recipe>) =
        writeArray("recipes.json", JSONArray().apply { list.forEach { put(recipeToJson(it)) } })

    // ------------------------------------------------------------ shopping list

    fun loadShopping(): List<ShoppingItem> = readArray("shopping.json").mapObjects { o ->
        ShoppingItem(
            id = o.getString("id"),
            ingredient = ingredientFromJson(o.getJSONObject("i")),
            checked = o.optBoolean("c"),
            sources = o.optJSONArray("s").strings(),
        )
    }

    fun saveShopping(list: List<ShoppingItem>) = writeArray("shopping.json", JSONArray().apply {
        list.forEach { item ->
            put(JSONObject().apply {
                put("id", item.id)
                put("i", ingredientToJson(item.ingredient))
                put("c", item.checked)
                put("s", JSONArray(item.sources))
            })
        }
    })

    // ------------------------------------------------------------ settings

    fun loadSettings() = Settings(
        apiKey = prefs.getString("apiKey", "") ?: "",
        model = prefs.getString("model", Settings.DEFAULT_MODEL)?.takeIf { it.isNotBlank() } ?: Settings.DEFAULT_MODEL,
        aiMode = runCatching { AiMode.valueOf(prefs.getString("aiMode", null) ?: "") }.getOrDefault(AiMode.FALLBACK),
        metric = prefs.getBoolean("metric", true),
    )

    fun saveSettings(s: Settings) {
        prefs.edit()
            .putString("apiKey", s.apiKey.trim())
            .putString("model", s.model.trim())
            .putString("aiMode", s.aiMode.name)
            .putBoolean("metric", s.metric)
            .apply()
    }

    // ------------------------------------------------------------ helpers

    private fun readArray(name: String): JSONArray {
        val f = AtomicFile(File(dir, name))
        return try {
            JSONArray(String(f.readFully(), Charsets.UTF_8))
        } catch (e: Exception) {
            JSONArray()
        }
    }

    private fun writeArray(name: String, arr: JSONArray) {
        val f = AtomicFile(File(dir, name))
        val out = f.startWrite()
        try {
            out.write(arr.toString().toByteArray(Charsets.UTF_8))
            f.finishWrite(out)
        } catch (e: Exception) {
            f.failWrite(out)
        }
    }

    private inline fun <T> JSONArray.mapObjects(fn: (JSONObject) -> T): List<T> =
        (0 until length()).mapNotNull { idx -> runCatching { fn(getJSONObject(idx)) }.getOrNull() }

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).map { getString(it) }

    companion object {
        fun ingredientToJson(i: Ingredient) = JSONObject().apply {
            if (i.hasQty()) put("q", i.qty)
            if (!i.qtyMax.isNaN()) put("qm", i.qtyMax)
            put("u", i.unit)
            put("n", i.name)
            put("no", i.note)
        }

        fun ingredientFromJson(o: JSONObject) = Ingredient(
            o.optDouble("q", Double.NaN),
            o.optDouble("qm", Double.NaN),
            o.optString("u"),
            o.optString("n"),
            o.optString("no"),
        )

        fun recipeToJson(r: Recipe) = JSONObject().apply {
            put("id", r.id)
            put("title", r.title)
            put("servings", r.servings)
            put("sourceUrl", r.sourceUrl)
            put("caption", r.caption)
            put("aiParsed", r.aiParsed)
            put("createdAt", r.createdAt)
            put("ingredients", JSONArray().apply { r.ingredients.forEach { put(ingredientToJson(it)) } })
            put("steps", JSONArray(r.steps))
        }

        fun recipeFromJson(o: JSONObject) = Recipe().apply {
            id = o.getString("id")
            title = o.optString("title")
            servings = o.optInt("servings")
            sourceUrl = o.optString("sourceUrl")
            caption = o.optString("caption")
            aiParsed = o.optBoolean("aiParsed")
            createdAt = o.optLong("createdAt")
            val ing = o.optJSONArray("ingredients") ?: JSONArray()
            for (k in 0 until ing.length()) ingredients.add(ingredientFromJson(ing.getJSONObject(k)))
            val st = o.optJSONArray("steps") ?: JSONArray()
            for (k in 0 until st.length()) steps.add(st.getString(k))
        }

        /** Recipe is a mutable Java object; UI state always gets a fresh copy. */
        fun copy(r: Recipe, edit: Recipe.() -> Unit = {}): Recipe = recipeFromJson(recipeToJson(r)).apply(edit)
    }
}
