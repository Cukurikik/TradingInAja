package com.tradinginaja.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.tradinginaja.domain.model.ThemeMode

/**
 * Token semantik khusus domain trading yang melengkapi [MaterialTheme.colorScheme].
 */
@Immutable
data class TradingExtendedColors(
    val bullish: Color,
    val onBullish: Color,
    val bullishContainer: Color,
    val bearish: Color,
    val onBearish: Color,
    val bearishContainer: Color,
    val warning: Color,
    val cardBorder: Color,
    val isAmoled: Boolean
)

private val LightExtendedColors = TradingExtendedColors(
    bullish = EmeraldLight,
    onBullish = Color.White,
    bullishContainer = EmeraldContainerLight,
    bearish = BearishLight,
    onBearish = Color.White,
    bearishContainer = BearishContainerLight,
    warning = AmberLight,
    cardBorder = LightOutlineVariant,
    isAmoled = false
)

private val DarkExtendedColors = TradingExtendedColors(
    bullish = EmeraldDark,
    onBullish = EmeraldOnDark,
    bullishContainer = EmeraldContainerDark,
    bearish = BearishDark,
    onBearish = Color(0xFF4D0011),
    bearishContainer = BearishContainerDark,
    warning = AmberDark,
    cardBorder = DarkOutlineVariant,
    isAmoled = false
)

private val AmoledExtendedColors = TradingExtendedColors(
    bullish = EmeraldDark,
    onBullish = EmeraldOnDark,
    bullishContainer = EmeraldContainerDark,
    bearish = BearishDark,
    onBearish = Color(0xFF4D0011),
    bearishContainer = BearishContainerDark,
    warning = AmberDark,
    cardBorder = AmoledOutline,
    isAmoled = true
)

val LocalTradingColors = staticCompositionLocalOf { DarkExtendedColors }

object TradingTheme {
    val colors: TradingExtendedColors
        @Composable
        get() = LocalTradingColors.current
}

@Composable
fun TradingTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
    }
    val useAmoled = themeMode == ThemeMode.AMOLED

    val colorScheme = when {
        useAmoled -> TradingAmoledColorScheme
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        useDark -> TradingDarkColorScheme
        else -> TradingLightColorScheme
    }

    val extendedColors = when {
        useAmoled -> AmoledExtendedColors
        useDark -> DarkExtendedColors
        else -> LightExtendedColors
    }

    CompositionLocalProvider(LocalTradingColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = TradingTypography,
            shapes = TradingShapes,
            content = content
        )
    }
}
