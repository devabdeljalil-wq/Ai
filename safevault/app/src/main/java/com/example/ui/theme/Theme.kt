package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = EmeraldPrimary,
    onPrimary = BlackBG,
    primaryContainer = EmeraldAlpha,
    onPrimaryContainer = EmeraldPrimary,
    secondary = Slate800,
    onSecondary = TextPrimary,
    secondaryContainer = SmoothCardBg,
    onSecondaryContainer = TextSecondary,
    background = BlackBG,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = Slate900,
    onSurfaceVariant = TextSecondary,
    outline = Slate800,
    error = RoseAccent,
    onError = BlackBG
  )

private val LightColorScheme =
  lightColorScheme(
    primary = EmeraldAccent,
    onPrimary = Color.White,
    primaryContainer = EmeraldAlpha,
    onPrimaryContainer = EmeraldAccent,
    secondary = Slate800,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF1F5F9), // slate-100
    onSecondaryContainer = Color(0xFF334155), // slate-700
    background = Color(0xFFF8FAFC), // slate-50
    onBackground = Color(0xFF0F172A), // slate-900
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE2E8F0), // slate-200
    onSurfaceVariant = Color(0xFF475569), // slate-600
    outline = Color(0xFFCBD5E1), // slate-300
    error = Color(0xFFE11D48), // rose-600
    onError = Color.White
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = true,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
