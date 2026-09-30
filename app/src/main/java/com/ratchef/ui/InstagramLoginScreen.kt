package com.ratchef.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Build
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ratchef.net.InstagramSession

private const val LOGIN_URL = "${InstagramSession.BASE}/accounts/login/"

/**
 * A normal Chrome-on-Android user agent built from the WebView's own Chrome version.
 * The WebView default contains "; wv" and "Version/4.0", which Instagram treats as an embedded
 * browser and may answer with an empty page.
 */
private fun chromeUserAgent(default: String): String {
    val chrome = Regex("""Chrome/[\d.]+""").find(default)?.value ?: "Chrome/128.0.0.0"
    return "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE}; ${Build.MODEL}) " +
        "AppleWebKit/537.36 (KHTML, like Gecko) $chrome Mobile Safari/537.36"
}

/**
 * Shows Instagram's own login page. As soon as Instagram sets its session cookie the screen closes.
 * Your password goes straight to Instagram; RatChef only keeps the resulting login cookie.
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun InstagramLoginScreen(onSignedIn: () -> Unit, onCancel: () -> Unit) {
    var loading by remember { mutableStateOf(true) }
    var finished by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf<String?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    BackHandler {
        val wv = webView
        if (wv != null && wv.canGoBack()) wv.goBack() else onCancel()
    }

    fun checkDone() {
        if (!finished && InstagramSession.isSignedIn()) {
            finished = true
            CookieManager.getInstance().flush()
            onSignedIn()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sign in to Instagram") },
                navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, "Cancel") } },
                actions = {
                    IconButton(onClick = {
                        problem = null
                        webView?.loadUrl(LOGIN_URL)
                    }) { Icon(Icons.Filled.Refresh, "Reload") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Text(
                "Your password goes directly to Instagram. RatChef only keeps the login, to read captions of reels you open.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            // Tiny status line: which page is open and what went wrong, so problems can be reported.
            Text(
                (problem?.let { "⚠ $it · " } ?: "") + page,
                style = MaterialTheme.typography.labelSmall,
                color = if (problem != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                maxLines = 3,
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 4.dp),
            )
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            AndroidView(
                modifier = Modifier.fillMaxWidth().weight(1f),
                factory = { ctx ->
                    WebView(ctx).apply {
                        webView = this
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        @Suppress("DEPRECATION")
                        settings.databaseEnabled = true
                        settings.javaScriptCanOpenWindowsAutomatically = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.userAgentString = chromeUserAgent(settings.userAgentString)

                        val cm = CookieManager.getInstance()
                        cm.setAcceptCookie(true)
                        cm.setAcceptThirdPartyCookies(this, true)

                        webChromeClient = object : WebChromeClient() {
                            override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                                if (msg.messageLevel() == ConsoleMessage.MessageLevel.ERROR && problem == null) {
                                    problem = "JS: " + msg.message().take(120)
                                }
                                return true
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            // Instagram tries to hand over to its app (intent://, instagram://).
                            // Stay on the web page instead of loading an address the WebView can't show.
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                val scheme = request.url.scheme?.lowercase()
                                return scheme != "http" && scheme != "https"
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                loading = true
                                page = url?.removePrefix("https://")?.take(80) ?: ""
                                checkDone()
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                loading = false
                                checkDone()
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest,
                                error: WebResourceError,
                            ) {
                                if (request.isForMainFrame) problem = "Load error ${error.errorCode}: ${error.description}"
                            }

                            override fun onReceivedHttpError(
                                view: WebView?,
                                request: WebResourceRequest,
                                response: WebResourceResponse,
                            ) {
                                if (request.isForMainFrame) problem = "HTTP ${response.statusCode}"
                            }

                            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                                problem = "Page crashed – tap reload"
                                webView = null
                                return true // don't take the whole app down
                            }
                        }
                        loadUrl(LOGIN_URL)
                    }
                },
                onRelease = {
                    webView = null
                    it.destroy()
                },
            )
        }
    }
}
