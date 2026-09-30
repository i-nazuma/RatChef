package com.ratchef

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import com.ratchef.ui.App
import com.ratchef.ui.AppViewModel

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleShare(intent)
        setContent {
            RatChefTheme { App(vm) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShare(intent)
    }

    /** Instagram → Share → RatChef sends the reel link as plain text. */
    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type?.startsWith("text/") != true) return
        val text = listOfNotNull(
            intent.getStringExtra(Intent.EXTRA_TEXT),
            intent.getStringExtra(Intent.EXTRA_SUBJECT)?.takeIf { it.contains("http") },
        ).firstOrNull { it.isNotBlank() } ?: return
        vm.importText(text)
    }
}

/** Warm terracotta Material 3 palette. Headlines use the system serif for a bistro-menu feel. */
private val LightColors = lightColorScheme(
    primary = Color(0xFFA0421E), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDBCF), onPrimaryContainer = Color(0xFF3A0B00),
    secondary = Color(0xFF77574C), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDBCF), onSecondaryContainer = Color(0xFF2C160E),
    tertiary = Color(0xFF5E7F3A), onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFFF8F6), onBackground = Color(0xFF231917),
    surface = Color(0xFFFFF8F6), onSurface = Color(0xFF231917),
    surfaceVariant = Color(0xFFF5DED6), onSurfaceVariant = Color(0xFF53433E),
    outline = Color(0xFF85736D), outlineVariant = Color(0xFFD8C2BA),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFFFF1ED),
    surfaceContainer = Color(0xFFFCEAE4), surfaceContainerHigh = Color(0xFFF6E4DE),
    surfaceContainerHighest = Color(0xFFF0DFD9),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB59C), onPrimary = Color(0xFF5C1900),
    primaryContainer = Color(0xFF7E2C0A), onPrimaryContainer = Color(0xFFFFDBCF),
    secondary = Color(0xFFE7BDB0), onSecondary = Color(0xFF442A21),
    secondaryContainer = Color(0xFF5D4036), onSecondaryContainer = Color(0xFFFFDBCF),
    tertiary = Color(0xFFB5D08A), onTertiary = Color(0xFF223A05),
    background = Color(0xFF1A110E), onBackground = Color(0xFFF1DFD9),
    surface = Color(0xFF1A110E), onSurface = Color(0xFFF1DFD9),
    surfaceVariant = Color(0xFF53433E), onSurfaceVariant = Color(0xFFD8C2BA),
    outline = Color(0xFFA08D86), outlineVariant = Color(0xFF53433E),
    surfaceContainerLowest = Color(0xFF140C09), surfaceContainerLow = Color(0xFF231917),
    surfaceContainer = Color(0xFF271D1A), surfaceContainerHigh = Color(0xFF322824),
    surfaceContainerHighest = Color(0xFF3D322E),
)

private val AppTypography = Typography().let { t ->
    fun TextStyle.serif() = copy(fontFamily = FontFamily.Serif)
    t.copy(
        headlineLarge = t.headlineLarge.serif(),
        headlineMedium = t.headlineMedium.serif(),
        headlineSmall = t.headlineSmall.serif(),
        titleLarge = t.titleLarge.serif(),
    )
}

@Composable
fun RatChefTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
