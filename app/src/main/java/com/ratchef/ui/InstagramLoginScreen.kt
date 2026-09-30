package com.ratchef.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
    BackHandler(onBack = onCancel)

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
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        // The default WebView user agent contains "; wv", which some login pages reject.
                        settings.userAgentString = settings.userAgentString.replace("; wv", "")
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                loading = true
                                checkDone()
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                loading = false
                                checkDone()
                            }
                        }
                        loadUrl("${InstagramSession.BASE}/accounts/login/")
                    }
                },
                onRelease = { it.destroy() },
            )
        }
    }
}
