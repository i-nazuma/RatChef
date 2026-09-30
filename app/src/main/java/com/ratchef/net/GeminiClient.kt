package com.ratchef.net

import com.ratchef.core.Ingredient
import com.ratchef.core.Recipe
import com.ratchef.core.Units
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Optional Gemini features (free tier is plenty for personal use):
 * - [parse]: captions the offline parser can't handle (only the caption text is sent)
 * - [parseVideo]: YouTube Shorts; Gemini watches the public video and reads its description
 * - [translate]: recipe text into English or German; amounts and units never change
 */
object GeminiClient {

    private const val UNITS =
        "g, kg, ml, l, tsp, tbsp, cup, oz, lb, pinch, clove, can, pack, bunch, handful, slice, piece, sprig, " +
            "dash, stick, cube, jar, scoop"

    private val RULES = """
        - "unit" must be one of: $UNITS — or "" for plain counts (e.g. 2 eggs) or when there is no amount.
        - "quantity" is a number (0.5, not "1/2"); null when no amount is given ("salt to taste").
        - "quantity_max" only for ranges ("1-2 tsp" -> quantity 1, quantity_max 2), otherwise null.
        - Put preparation hints ("finely chopped", "to taste") into "note", not into "name".
        - "servings": number of portions if stated, otherwise 0.
        - "language": ISO 639-1 code of the text you wrote (e.g. "en", "de", "es").
        - Ignore hashtags, calls to follow/save/comment, and nutrition/macro lines.
    """.trimIndent()

    private fun captionPrompt(caption: String) = """
        Extract the recipe from this Instagram reel caption into JSON.
        Rules:
        - Keep the caption's language for names and steps.
        $RULES
        - "steps": short imperative sentences in order, without numbering. If the caption has no steps,
          write brief steps that follow only from the caption. Never invent ingredients.
    """.trimIndent() + "\n\nCaption:\n---\n" + caption + "\n---"

    private fun videoPrompt(description: String?) = """
        Watch this cooking video (pictures, on-screen text and speech) and extract the recipe into JSON.
        Use the video description below as well: combine both, the description often has the exact amounts.
        Rules:
        - Write in the language the video or description uses.
        $RULES
        - "amount_source" per ingredient: "written" (description or on-screen text), "said" (spoken),
          or "estimated" (you judged it from what you saw). Never present a guess as written or said.
        - "steps": short imperative sentences in the order shown, without numbering.
        - Only list ingredients that are actually used in the video or named in the description.
    """.trimIndent() + "\n\nVideo description:\n---\n" + (description?.take(6000) ?: "(none)") + "\n---"

    private val recipeSchema = JSONObject(
        """
        {
          "type": "OBJECT",
          "properties": {
            "title": {"type": "STRING"},
            "language": {"type": "STRING"},
            "servings": {"type": "INTEGER"},
            "ingredients": {
              "type": "ARRAY",
              "items": {
                "type": "OBJECT",
                "properties": {
                  "quantity": {"type": "NUMBER", "nullable": true},
                  "quantity_max": {"type": "NUMBER", "nullable": true},
                  "unit": {"type": "STRING"},
                  "name": {"type": "STRING"},
                  "note": {"type": "STRING"},
                  "amount_source": {"type": "STRING", "nullable": true}
                },
                "required": ["name", "unit"]
              }
            },
            "steps": {"type": "ARRAY", "items": {"type": "STRING"}}
          },
          "required": ["title", "language", "servings", "ingredients", "steps"]
        }
        """.trimIndent()
    )

    private val translationSchema = JSONObject(
        """
        {
          "type": "OBJECT",
          "properties": {
            "title": {"type": "STRING"},
            "ingredients": {
              "type": "ARRAY",
              "items": {
                "type": "OBJECT",
                "properties": {"name": {"type": "STRING"}, "note": {"type": "STRING"}},
                "required": ["name", "note"]
              }
            },
            "steps": {"type": "ARRAY", "items": {"type": "STRING"}}
          },
          "required": ["title", "ingredients", "steps"]
        }
        """.trimIndent()
    )

    // ------------------------------------------------------------------ public calls (blocking)

    fun parse(caption: String, apiKey: String, model: String): Recipe {
        val parts = JSONArray().put(JSONObject().put("text", captionPrompt(caption.take(8000))))
        return toRecipe(call(parts, recipeSchema, apiKey, model, 60_000))
    }

    /**
     * YouTube: Gemini fetches the public video itself from [youtubeUrl] (Shorts included).
     * [description] is the video's description text, if it could be read.
     */
    fun parseVideo(youtubeUrl: String, description: String?, apiKey: String, model: String): Recipe {
        val parts = JSONArray()
            .put(JSONObject().put("fileData", JSONObject().put("fileUri", youtubeUrl)))
            .put(JSONObject().put("text", videoPrompt(description)))
        return toRecipe(call(parts, recipeSchema, apiKey, model, 180_000))
    }

    /**
     * Translates title, ingredient names/notes and steps into [targetLang] ("en" or "de").
     * Amounts and units are copied from [r] by position, so a translation can't change a quantity.
     */
    fun translate(r: Recipe, targetLang: String, apiKey: String, model: String): Recipe.Translation {
        val language = if (targetLang == "de") "German (as used in Austria)" else "English"
        val input = JSONObject().apply {
            put("title", r.title)
            put("ingredients", JSONArray().apply {
                r.ingredients.forEach { put(JSONObject().put("name", it.name).put("note", it.note)) }
            })
            put("steps", JSONArray(r.steps))
        }
        val prompt = """
            Translate this recipe into $language. Return the same JSON shape with exactly the same number
            and order of ingredients and steps. Translate ingredient names to the usual supermarket name,
            keep numbers, temperatures and times exactly as they are, keep brand names, and keep empty notes empty.
        """.trimIndent() + "\n\n" + input.toString()
        val parts = JSONArray().put(JSONObject().put("text", prompt))
        val o = call(parts, translationSchema, apiKey, model, 60_000)

        val ing = o.optJSONArray("ingredients") ?: JSONArray()
        val st = o.optJSONArray("steps") ?: JSONArray()
        if (ing.length() != r.ingredients.size || st.length() != r.steps.size) {
            throw IOException("Translation came back incomplete")
        }
        return Recipe.Translation().apply {
            title = o.optString("title").ifBlank { r.title }
            for (k in 0 until ing.length()) {
                val src = r.ingredients[k]
                val t = ing.getJSONObject(k)
                ingredients.add(
                    Ingredient(
                        src.qty, src.qtyMax, src.unit,
                        t.optString("name").trim().ifEmpty { src.name }.replaceFirstChar { it.uppercaseChar() },
                        if (src.note.isEmpty()) "" else t.optString("note").trim(),
                    )
                )
            }
            for (k in 0 until st.length()) steps.add(st.optString(k).trim().ifEmpty { r.steps[k] })
        }
    }

    // ------------------------------------------------------------------ plumbing

    private fun call(parts: JSONArray, schema: JSONObject, apiKey: String, model: String, readTimeout: Int): JSONObject {
        val body = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("parts", parts)
            }))
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.1)
                put("responseMimeType", "application/json")
                put("responseSchema", schema)
            })
        }

        val modelId = URLEncoder.encode(model.trim().removePrefix("models/"), "UTF-8")
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$modelId:generateContent")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 20_000
            conn.readTimeout = readTimeout
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("x-goog-api-key", apiKey.trim())
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val raw = stream?.use { String(it.readBytes(), Charsets.UTF_8) } ?: ""
            if (code !in 200..299) {
                val msg = runCatching { JSONObject(raw).getJSONObject("error").getString("message") }
                    .getOrDefault(raw.take(200))
                throw IOException("Gemini HTTP $code: $msg")
            }
            val text = JSONObject(raw)
                .getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts").getJSONObject(0)
                .getString("text")
            return JSONObject(text)
        } finally {
            conn.disconnect()
        }
    }

    private fun toRecipe(o: JSONObject): Recipe = Recipe().apply {
        title = o.optString("title").ifBlank { "Reel recipe" }
        servings = o.optInt("servings").coerceIn(0, 50)
        lang = o.optString("language").trim().lowercase().take(2)
        aiParsed = true
        val ing = o.optJSONArray("ingredients") ?: JSONArray()
        for (k in 0 until ing.length()) {
            val i = ing.getJSONObject(k)
            var name = i.optString("name").trim()
            if (name.isEmpty()) continue
            val unitRaw = i.optString("unit").trim()
            val unit = Units.lookup(unitRaw)
            // Unknown unit from the model: keep it readable by folding it into the name.
            if (unit == null && unitRaw.isNotEmpty()) name = "$unitRaw $name"
            var note = i.optString("note").trim()
            // Amounts the model only judged from the video get a visible "~ estimated".
            if (i.optString("amount_source") == "estimated" && !i.isNull("quantity")) {
                note = if (note.isEmpty()) "~ estimated" else "$note; ~ estimated"
            }
            ingredients.add(
                Ingredient(
                    if (i.isNull("quantity")) Double.NaN else i.optDouble("quantity", Double.NaN),
                    if (i.isNull("quantity_max")) Double.NaN else i.optDouble("quantity_max", Double.NaN),
                    unit?.key ?: "",
                    name.replaceFirstChar { it.uppercaseChar() },
                    note,
                )
            )
        }
        val st = o.optJSONArray("steps") ?: JSONArray()
        for (k in 0 until st.length()) st.optString(k).trim().takeIf { it.isNotEmpty() }?.let { steps.add(it) }
    }
}
