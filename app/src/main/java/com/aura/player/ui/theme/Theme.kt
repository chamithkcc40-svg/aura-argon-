package com.aura.player.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/**
 * AURA visual identity: adaptive Liquid Glass palette supporting both
 * Deep Navy/Black ("Dark") and Crystal Pearl/Silver ("Light", matching WWDC/iOS 26 light glass references).
 */
object AuraColors {
    var isDark by mutableStateOf(true)
        internal set

    val Bg: Color get() = if (isDark) Color(0xFF05060F) else Color(0xFFEEF2FA)
    val Navy: Color get() = if (isDark) Color(0xFF0A1030) else Color(0xFFDCE4F5)
    val Ink: Color get() = if (isDark) Color(0xFF0E1230) else Color(0xFFE4EBF8)
    val Purple = Color(0xFF7B5CFF)
    val Blue = Color(0xFF3B82FF)
    val Cyan: Color get() = if (isDark) Color(0xFF22D3EE) else Color(0xFF0891B2)
    val Pink = Color(0xFFFF5C9D)
    val TextPrimary: Color get() = if (isDark) Color(0xFFF5F7FF) else Color(0xFF0B1026)
    val TextSecondary: Color get() = if (isDark) Color(0xB8DCE3FF) else Color(0xC4202C50)
    val TextTertiary: Color get() = if (isDark) Color(0x80C9D1F5) else Color(0x883B4A78)
    val Danger: Color get() = if (isDark) Color(0xFFFF6B7D) else Color(0xFFE11D48)
}

object AuraType {
    private val sans = FontFamily.SansSerif
    val Display = TextStyle(fontFamily = sans, fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.9).sp)
    val Headline = TextStyle(fontFamily = sans, fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
    val Title = TextStyle(fontFamily = sans, fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp)
    val Section = TextStyle(fontFamily = sans, fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp)
    val Body = TextStyle(fontFamily = sans, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
    val Caption = TextStyle(fontFamily = sans, fontSize = 12.5.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal)
    val Label = TextStyle(fontFamily = sans, fontSize = 10.5.sp, lineHeight = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp)
    val Wordmark = TextStyle(fontFamily = sans, fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 5.sp)
}

@Composable
fun AuraTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    AuraColors.isDark = darkTheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = AuraColors.Purple,
            secondary = AuraColors.Cyan,
            background = AuraColors.Bg,
            surface = AuraColors.Ink,
            onBackground = AuraColors.TextPrimary,
            onSurface = AuraColors.TextPrimary,
        )
    } else {
        lightColorScheme(
            primary = AuraColors.Purple,
            secondary = AuraColors.Cyan,
            background = AuraColors.Bg,
            surface = AuraColors.Ink,
            onBackground = AuraColors.TextPrimary,
            onSurface = AuraColors.TextPrimary,
        )
    }

    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalContentColor provides AuraColors.TextPrimary, content = content)
    }
}
