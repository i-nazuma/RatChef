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
 * Optional cloud fallback for captions the offline parser can't handle.
 * Uses the Gemini API (free tier is plenty for personal use). Only the caption text is sent.
 */
object GeminiClient {

    private const val UNITS =
        "g, kg, ml, l, tsp, tbsp, cup, oz, lb, pinch, clove, can, pack, bunch, handful, slice, piece, sprig, " +
            "dash, stick, cube, jar, scoop"

    private fun prompt(caption: String) = """
        Extract the recipe from this Instagram reel caption into JSON.
        Rules:
        - Keep the caption's language for names and steps.
        - "unit" must be one of: $UNITS — or "" for plain counts (e.g. 2 eggs) or when there is no amount.
        - "quantity" is a number (0.5, not "1/2"); null when the caption gives no amount ("salt to taste").
        - "quantity_max" only for ranges ("1-2 tsp" -> quantity 1, quantity_max 2), otherwise null.
        - Put preparation hints ("finely chopped", "to taste") into "note", not into "name".
        - "servings": number of portions if stated, otherwise 0.
        - "steps": short imperative sentences in order, without numbering. If the caption has no steps,
          write brief steps that follow only from the caption. Never invent ingredients.
        - Ignore hashtags, calls to follow/save/comment, and nutrition/macro lines.
    """.trimIndent() + "\n\nCaption:\n---\n" + caption + "\n---"

    private val schema = JSONObject(
        """
        {
          "type": "OBJECT",
          "properties": {
            "title": {"type": "STRING"},
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
                  "note": {"type": "STRING"}
                },
                "required": ["name", "unit"]
              }
            },
            "steps": {"type": "ARRAY", "items": {"type": "STRING"}}
          },
          "required": ["title", "servings", "ingredients", "steps"]
        }
        """.trimIndent()
    )

    /** Blocking; call from Dispatchers.IO. Throws IOException with a readable message on failure. */
    fun parse(caption: String, apiKey: String, model: String): Recipe {
        val body = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().put("text", prompt(caption.take(8000)))))
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
        val text: String
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 20_000
            conn.readTimeout = 60_000
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
            text = JSONObject(raw)
                .getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts").getJSONObject(0)
                .getString("text")
        } finally {
            conn.disconnect()
        }
        return toRecipe(JSONObject(text))
    }

    private fun toRecipe(o: JSONObject): Recipe = Recipe().apply {
        title = o.optString("title").ifBlank { "Reel recipe" }
        servings = o.optInt("servings").coerceIn(0, 50)
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
            ingredients.add(
                Ingredient(
                    if (i.isNull("quantity")) Double.NaN else i.optDouble("quantity", Double.NaN),
                    if (i.isNull("quantity_max")) Double.NaN else i.optDouble("quantity_max", Double.NaN),
                    unit?.key ?: "",
                    name.replaceFirstChar { it.uppercaseChar() },
                    i.optString("note").trim(),
                )
            )
        }
        val st = o.optJSONArray("steps") ?: JSONArray()
        for (k in 0 until st.length()) st.optString(k).trim().takeIf { it.isNotEmpty() }?.let { steps.add(it) }
    }
}
