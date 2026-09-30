package com.ratchef.net

import android.text.Html
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Reads the caption of a public Instagram post/reel without an account or API.
 *
 * Instagram has no public API for this, so it works the way a browser does. Signed out, it loads the
 * public embed page and falls back to the page's og:description tag. Signed in (your own login, see
 * InstagramSession), it first asks for the post's data like instagram.com does. If Instagram changes
 * its pages, this returns null and the app asks you to paste the caption instead.
 */
object CaptionFetcher {

    private val INSTAGRAM = Regex(
        """https?://(?:www\.|m\.)?instagram\.com/(?:[\w.]+/)?(reels?|p|tv)/([A-Za-z0-9_-]+)""",
        RegexOption.IGNORE_CASE,
    )
    private val ANY_URL = Regex("""https?://\S+""")

    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/128.0.0.0 Safari/537.36"

    /** Pulls the reel link out of whatever was shared ("Check out this reel https://…?igsh=…"). */
    fun extractUrl(text: String): String? {
        INSTAGRAM.find(text)?.let { m ->
            val type = m.groupValues[1].lowercase().let { if (it == "reels") "reel" else it }
            return "https://www.instagram.com/$type/${m.groupValues[2]}/"
        }
        return ANY_URL.find(text)?.value?.trimEnd('.', ',', ')', '"')
    }

    /** True when the shared text is (almost) only a link, i.e. there is no caption to parse. */
    fun isJustALink(text: String): Boolean {
        val url = ANY_URL.find(text)?.value ?: return false
        return text.replace(url, "").trim().length < 40
    }

    /**
     * Blocking; call from Dispatchers.IO. Returns null if no caption could be found.
     * [cookies] is your own Instagram login (see InstagramSession); null = not signed in.
     */
    fun fetch(url: String, cookies: String? = null, csrf: String? = null): String? {
        val ig = INSTAGRAM.find(url)
        if (ig != null) {
            val code = ig.groupValues[2]
            // Signed in: ask for the post's data the same way instagram.com does in your browser.
            if (cookies != null) {
                runCatching { fromMediaInfo(code, cookies, csrf) }.getOrNull()?.let { return it }
            }
            val embed = runCatching { get("https://www.instagram.com/p/$code/embed/captioned/", cookies) }.getOrNull()
            embed?.let { html -> fromEmbed(html) ?: fromJson(html) }?.let { return it }
        }
        val page = runCatching { get(url, cookies) }.getOrNull() ?: return null
        return fromJson(page) ?: fromOgDescription(page)
    }

    /** Instagram's public web app id, sent by instagram.com itself with every request. */
    private const val IG_WEB_APP_ID = "936619743392459"
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    /** Shortcode ("C9xYz…") -> numeric media id. */
    internal fun mediaId(shortcode: String): String? {
        var id = java.math.BigInteger.ZERO
        val sixtyFour = java.math.BigInteger.valueOf(64)
        for (c in shortcode.take(11)) {
            val idx = ALPHABET.indexOf(c)
            if (idx < 0) return null
            id = id.multiply(sixtyFour).add(java.math.BigInteger.valueOf(idx.toLong()))
        }
        return id.toString()
    }

    private fun fromMediaInfo(code: String, cookies: String, csrf: String?): String? {
        val id = mediaId(code) ?: return null
        val json = get(
            "https://www.instagram.com/api/v1/media/$id/info/",
            cookies,
            extra = buildMap {
                put("X-IG-App-ID", IG_WEB_APP_ID)
                put("X-Requested-With", "XMLHttpRequest")
                put("Referer", "https://www.instagram.com/")
                put("Accept", "application/json")
                if (csrf != null) put("X-CSRFToken", csrf)
            },
        )
        val item = JSONObject(json).optJSONArray("items")?.optJSONObject(0) ?: return null
        val text = item.optJSONObject("caption")?.optString("text")
        return text?.trim()?.takeIf { it.length > 10 }
    }

    private fun get(url: String, cookies: String? = null, extra: Map<String, String> = emptyMap()): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml")
            conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9,de;q=0.8")
            if (cookies != null) conn.setRequestProperty("Cookie", cookies)
            extra.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            return conn.inputStream.use { stream ->
                val bytes = stream.readBytes()
                String(bytes.copyOf(minOf(bytes.size, 4_000_000)), Charsets.UTF_8)
            }
        } finally {
            conn.disconnect()
        }
    }

    /** <div class="Caption"><a class="CaptionUsername">user</a><br/>caption text…<div class="CaptionComments"> */
    private fun fromEmbed(html: String): String? {
        val m = Regex("""class="Caption"[^>]*>(.*?)<div class="CaptionComments"""", RegexOption.DOT_MATCHES_ALL)
            .find(html)
            ?: Regex("""class="Caption"[^>]*>(.*?)</div>""", RegexOption.DOT_MATCHES_ALL).find(html)
            ?: return null
        val inner = m.groupValues[1]
            .replace(Regex("""<a[^>]*class="CaptionUsername"[^>]*>.*?</a>""", RegexOption.DOT_MATCHES_ALL), "")
        return htmlToText(inner).takeIf { it.length > 10 }
    }

    /** Caption embedded as JSON: "edge_media_to_caption":{"edges":[{"node":{"text":"…"}}]} */
    private fun fromJson(html: String): String? {
        val patterns = listOf(
            Regex(""""edge_media_to_caption"\s*:\s*\{\s*"edges"\s*:\s*\[\s*\{\s*"node"\s*:\s*\{\s*"text"\s*:\s*"((?:[^"\\]|\\.)*)""""),
            Regex(""""caption"\s*:\s*\{[^{}]*?"text"\s*:\s*"((?:[^"\\]|\\.)*)""""),
        )
        for (p in patterns) {
            val raw = p.find(html)?.groupValues?.get(1) ?: continue
            val text = runCatching { JSONObject("{\"t\":\"$raw\"}").getString("t") }.getOrNull()
            if (!text.isNullOrBlank() && text.length > 10) return text
        }
        return null
    }

    /** og:description looks like: 1,234 likes, 56 comments - user on May 1, 2025: "caption…". */
    private fun fromOgDescription(html: String): String? {
        val m = Regex("""<meta[^>]+property="og:description"[^>]+content="([^"]*)"""").find(html)
            ?: Regex("""<meta[^>]+content="([^"]*)"[^>]+property="og:description"""").find(html)
            ?: return null
        val text = htmlToText(m.groupValues[1].replace("\n", "<br>"))
        val quoted = Regex("""^.*?:\s*[“"](.*)[”"]\.?\s*$""", RegexOption.DOT_MATCHES_ALL).find(text)
        return (quoted?.groupValues?.get(1) ?: text).trim().takeIf { it.length > 10 }
    }

    private fun htmlToText(html: String): String =
        Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString()
            .replace(' ', ' ')
            .trim()
}
