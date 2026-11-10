package com.example.gemini.theme

import android.app.Activity
import android.app.UiModeManager
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CheckboxColors
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButtonColors
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFE8E5DF),
    onPrimary = Color(0xFF0E0E11),
    primaryContainer = Color(0xFF27272A),
    onPrimaryContainer = Color(0xFFFAFAFA),
    secondary = Color(0xFFDFDBD3),
    onSecondary = Color(0xFF0E0E11),
    secondaryContainer = Color(0xFF1F1F26),
    onSecondaryContainer = Color(0xFFE4E4E7),
    background = Color(0xFF09090B),
    onBackground = Color(0xFFF1F1F4),
    surface = Color(0xFF141417),
    onSurface = Color(0xFFF1F1F4),
    surfaceVariant = Color(0xFF1C1C22),
    onSurfaceVariant = Color(0xFFA1A1AB),
    surfaceContainerLow = Color(0xFF101014),
    surfaceContainer = Color(0xFF141417),
    surfaceContainerHigh = Color(0xFF1C1C22),
    surfaceContainerHighest = Color(0xFF24242B),
    surfaceTint = Color.Transparent,
    outline = Color(0xFF282832),
    outlineVariant = Color(0xFF222226),
    error = Color(0xFFEF4444),
    onError = Color(0xFFFFFFFF)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF18181B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE4E4E7),
    onPrimaryContainer = Color(0xFF18181B),
    secondary = Color(0xFF3F3F46),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEEEEF1),
    onSecondaryContainer = Color(0xFF18181B),
    background = Color(0xFFF6F6F8),
    onBackground = Color(0xFF111113),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111113),
    surfaceVariant = Color(0xFFEEEEF1),
    onSurfaceVariant = Color(0xFF52525B),
    surfaceContainerLow = Color(0xFFFAFAFC),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF2F2F5),
    surfaceContainerHighest = Color(0xFFE8E8ED),
    surfaceTint = Color.Transparent,
    outline = Color(0xFFD4D4D8),
    outlineVariant = Color(0xFFE4E4E7),
    error = Color(0xFFDC2626),
    onError = Color(0xFFFFFFFF)
)

@Immutable
data class GraventUiTokens(
    val isDark: Boolean,
    val canvasBg: Color,
    val cardBg: Color,
    val elevatedBg: Color,
    val chipBg: Color,
    val border: Color,
    val subtleBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accent: Color,
    val onAccent: Color,
    val accentSoftBg: Color,
    val neutralBtnBg: Color,
    val neutralBtnText: Color,
    val neutralBtnBorder: Color,
    val gitAdd: Color,
    val gitAddBg: Color,
    val gitAddBorder: Color,
    val gitDel: Color,
    val warnAmber: Color,
    val warmHighlight: Color
)

private val DarkGraventTokens = GraventUiTokens(
    isDark = true,
    canvasBg = Color(0xFF09090B),
    cardBg = Color(0xFF141417),
    elevatedBg = Color(0xFF1C1C22),
    chipBg = Color(0xFF18181B),
    border = Color(0xFF27272A),
    subtleBorder = Color(0xFF222226),
    textPrimary = Color(0xFFFAFAFA),
    textSecondary = Color(0xFFA1A1AA),
    textMuted = Color(0xFF71717A),
    accent = Color(0xFFE8E5DF),
    onAccent = Color(0xFF0E0E11),
    accentSoftBg = Color(0xFFE8E5DF).copy(alpha = 0.13f),
    neutralBtnBg = Color(0xFF1F1F26),
    neutralBtnText = Color(0xFFE4E4E7),
    neutralBtnBorder = Color(0xFF2E2E38),
    gitAdd = Color(0xFF4ADE80),
    gitAddBg = Color(0xFF141715),
    gitAddBorder = Color(0xFF262D28),
    gitDel = Color(0xFFF87171),
    warnAmber = Color(0xFFFBBF24),
    warmHighlight = Color(0xFFE8E5DF)
)

private val LightGraventTokens = GraventUiTokens(
    isDark = false,
    canvasBg = Color(0xFFF6F6F8),
    cardBg = Color(0xFFFFFFFF),
    elevatedBg = Color(0xFFEEEEF1),
    chipBg = Color(0xFFF4F4F5),
    border = Color(0xFFE4E4E7),
    subtleBorder = Color(0xFFE8E8EC),
    textPrimary = Color(0xFF111113),
    textSecondary = Color(0xFF52525B),
    textMuted = Color(0xFF71717A),
    accent = Color(0xFF18181B),
    onAccent = Color(0xFFFFFFFF),
    accentSoftBg = Color(0xFF18181B).copy(alpha = 0.08f),
    neutralBtnBg = Color(0xFFEEEEF1),
    neutralBtnText = Color(0xFF18181B),
    neutralBtnBorder = Color(0xFFD4D4D8),
    gitAdd = Color(0xFF15803D),
    gitAddBg = Color(0xFFECFDF5),
    gitAddBorder = Color(0xFFBBF7D0),
    gitDel = Color(0xFFB91C1C),
    warnAmber = Color(0xFFB45309),
    warmHighlight = Color(0xFF3F3F46)
)

val LocalIsDarkTheme = staticCompositionLocalOf { true }
val LocalGraventColors = staticCompositionLocalOf { DarkGraventTokens }

@Composable
@ReadOnlyComposable
fun isAppInDarkTheme(): Boolean = LocalIsDarkTheme.current

@Composable
@ReadOnlyComposable
fun graventTokens(): GraventUiTokens = LocalGraventColors.current

@Composable
fun graventPrimaryButtonColors(
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary
): ButtonColors {
    val t = graventTokens()
    return ButtonDefaults.buttonColors(
        containerColor = containerColor,
        contentColor = contentColor,
        disabledContainerColor = t.elevatedBg,
        disabledContentColor = t.textMuted
    )
}

@Composable
fun graventNeutralButtonColors(): ButtonColors {
    val t = graventTokens()
    return ButtonDefaults.buttonColors(
        containerColor = t.neutralBtnBg,
        contentColor = t.neutralBtnText,
        disabledContainerColor = t.elevatedBg.copy(alpha = 0.6f),
        disabledContentColor = t.textMuted
    )
}

@Composable
fun graventErrorButtonColors(): ButtonColors {
    val t = graventTokens()
    return ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.error,
        contentColor = Color.White,
        disabledContainerColor = t.elevatedBg,
        disabledContentColor = t.textMuted
    )
}

@Composable
fun graventSliderColors(): SliderColors {
    val t = graventTokens()
    return SliderDefaults.colors(
        thumbColor = t.accent,
        activeTrackColor = t.accent,
        inactiveTrackColor = t.border
    )
}

@Composable
fun graventRadioColors(): RadioButtonColors {
    val t = graventTokens()
    return RadioButtonDefaults.colors(
        selectedColor = t.accent,
        unselectedColor = t.textMuted
    )
}

@Composable
fun graventCheckboxColors(): CheckboxColors {
    val t = graventTokens()
    return CheckboxDefaults.colors(
        checkedColor = t.accent,
        checkmarkColor = t.onAccent,
        uncheckedColor = t.textMuted
    )
}

@Composable
fun graventSwitchColors(): SwitchColors {
    val t = graventTokens()
    return SwitchDefaults.colors(
        checkedThumbColor = t.onAccent,
        checkedTrackColor = t.accent,
        checkedBorderColor = t.accent,
        uncheckedThumbColor = t.textSecondary,
        uncheckedTrackColor = t.elevatedBg,
        uncheckedBorderColor = t.border
    )
}

@Composable
fun isSystemInDarkThemeRobust(): Boolean {
    val context = LocalContext.current
    val composeIsDark = isSystemInDarkTheme()

    var secureNightMode by remember {
        mutableIntStateOf(
            try {
                Settings.Secure.getInt(context.contentResolver, "ui_night_mode", -1)
            } catch (e: Exception) {
                -1
            }
        )
    }

    DisposableEffect(context) {
        val uri = Settings.Secure.getUriFor("ui_night_mode")
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                try {
                    secureNightMode = Settings.Secure.getInt(context.contentResolver, "ui_night_mode", -1)
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
        try {
            context.contentResolver.registerContentObserver(uri, false, observer)
        } catch (e: Exception) {
            // Ignore
        }
        onDispose {
            try {
                context.contentResolver.unregisterContentObserver(observer)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    val uiModeManager = remember(context) {
        context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    }
    val isMgrDark = uiModeManager?.nightMode == UiModeManager.MODE_NIGHT_YES

    return when {
        secureNightMode == 2 -> true
        secureNightMode == 1 -> false
        isMgrDark -> true
        else -> composeIsDark
    }
}

@Composable
fun GeminiTheme(
    darkTheme: Boolean = isSystemInDarkThemeRobust(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val tokens = if (darkTheme) DarkGraventTokens else LightGraventTokens

    val view = LocalView.current
    if (!view.isInEditMode) {
        val activity = view.context as? Activity
        if (activity != null && activity !is com.example.gemini.ui.bubble.FloatingChatActivity) {
            SideEffect {
                val window = activity.window
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = colorScheme.background.toArgb()
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalIsDarkTheme provides darkTheme,
        LocalGraventColors provides tokens
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
