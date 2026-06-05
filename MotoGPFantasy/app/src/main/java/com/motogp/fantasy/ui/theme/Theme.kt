package com.motogp.fantasy.ui.theme
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
private val Dark = darkColorScheme(primary=Color(0xFFE53935), secondary=Color(0xFFFFB300))
private val Light = lightColorScheme(primary=Color(0xFFD32F2F), secondary=Color(0xFFF57F17))
@Composable
fun MotoGPTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val scheme = if (Build.VERSION.SDK_INT >= 31) { val c = LocalContext.current; if (dark) dynamicDarkColorScheme(c) else dynamicLightColorScheme(c) } else if (dark) Dark else Light
    MaterialTheme(colorScheme = scheme, content = content)
}
