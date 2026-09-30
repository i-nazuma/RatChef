package com.ratchef.net

import android.webkit.CookieManager

/**
 * Your own Instagram login, kept as cookies in the app's private WebView cookie store.
 * RatChef never sees or stores your password: you type it into Instagram's own login page.
 */
object InstagramSession {

    const val BASE = "https://www.instagram.com"

    private fun cookies(): String? = runCatching { CookieManager.getInstance().getCookie(BASE) }.getOrNull()

    private fun cookie(name: String): String? =
        cookies()?.split(';')
            ?.map { it.trim() }
            ?.firstOrNull { it.startsWith("$name=") }
            ?.substringAfter('=')
            ?.takeIf { it.isNotEmpty() }

    fun isSignedIn(): Boolean = cookie("sessionid") != null

    /** Value for the Cookie header, or null when signed out. */
    fun cookieHeader(): String? = if (isSignedIn()) cookies() else null

    fun csrfToken(): String? = cookie("csrftoken")

    /**
     * Alternative sign-in: the value of the "sessionid" cookie copied from a desktop browser where
     * you are logged in to instagram.com. Returns false if the value can't be a session id.
     */
    fun setSessionId(raw: String): Boolean {
        val value = raw.trim().removePrefix("sessionid=").trim().trim('"').substringBefore(';')
        if (value.length < 20 || value.any { it.isWhitespace() }) return false
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        cm.setCookie(BASE, "sessionid=$value; Domain=.instagram.com; Path=/; Secure; HttpOnly")
        cm.flush()
        return true
    }

    fun signOut(done: () -> Unit = {}) {
        val cm = CookieManager.getInstance()
        cm.removeAllCookies {
            cm.flush()
            done()
        }
    }
}
