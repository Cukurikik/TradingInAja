package com.tradinginaja.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Brand & Trading Semantic Palette
val EmeraldLight = Color(0xFF007A53)
val EmeraldOnLight = Color(0xFFFFFFFF)
val EmeraldContainerLight = Color(0xFFC4F7DE)
val EmeraldOnContainerLight = Color(0xFF002114)

val AmberLight = Color(0xFF9E5A00)
val AmberContainerLight = Color(0xFFFFDDB7)
val AmberOnContainerLight = Color(0xFF331A00)

val CyanLight = Color(0xFF00658E)
val CyanContainerLight = Color(0xFFC7E7FF)

val BearishLight = Color(0xFFD12442)
val BearishContainerLight = Color(0xFFFFDAD9)

val LightBackground = Color(0xFFF4F7FA)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFE7EDF4)
val LightOnSurface = Color(0xFF0D1522)
val LightOnSurfaceVariant = Color(0xFF4A586E)
val LightOutline = Color(0xFFCBD5E1)
val LightOutlineVariant = Color(0xFFE2E8F0)

// Dark Theme (Slate Obsidian)
val EmeraldDark = Color(0xFF1CE89F)
val EmeraldOnDark = Color(0xFF003823)
val EmeraldContainerDark = Color(0xFF005236)
val EmeraldOnContainerDark = Color(0xFFC4F7DE)

val AmberDark = Color(0xFFFFBA49)
val AmberOnDark = Color(0xFF452400)
val AmberContainerDark = Color(0xFF663800)
val AmberOnContainerDark = Color(0xFFFFDDB7)

val CyanDark = Color(0xFF7AD0FF)
val CyanContainerDark = Color(0xFF004C6D)

val BearishDark = Color(0xFFFF5E73)
val BearishContainerDark = Color(0xFF5D1120)

val DarkBackground = Color(0xFF0B121E)
val DarkSurface = Color(0xFF101928)
val DarkSurfaceContainer = Color(0xFF152133)
val DarkSurfaceVariant = Color(0xFF1D2C42)
val DarkOnSurface = Color(0xFFF1F5F9)
val DarkOnSurfaceVariant = Color(0xFF94A3B8)
val DarkOutline = Color(0xFF334155)
val DarkOutlineVariant = Color(0xFF1E293B)

// Palet ramah buta warna (Okabe–Ito biru/oranye). Arah naik-turun tetap terbaca pada
// deuteranopia/protanopia; kontras teks >= 4,5:1 terhadap surface tema masing-masing.
val ColorBlindBullishLight = Color(0xFF0072B2)
val ColorBlindBullishContainerLight = Color(0xFFD4E9F7)
val ColorBlindBearishLight = Color(0xFFB34700)
val ColorBlindBearishContainerLight = Color(0xFFFCE3CD)

val ColorBlindBullishDark = Color(0xFF56B4E9)
val ColorBlindBullishContainerDark = Color(0xFF0B3448)
val ColorBlindBearishDark = Color(0xFFF0A030)
val ColorBlindBearishContainerDark = Color(0xFF4A2A00)

// Pasangan on*Container dipilih agar >= 4,5:1 terhadap container-nya (WCAG AA teks normal):
// #00395C pada #D4E9F7 = 9,66:1; #6B2A00 pada #FCE3CD = 8,69:1;
// #BFE4F7 pada #0B3448 = 9,79:1; #FFDDB6 pada #4A2A00 = 10,03:1.
val ColorBlindOnBullishContainerLight = Color(0xFF00395C)
val ColorBlindOnBearishContainerLight = Color(0xFF6B2A00)
val ColorBlindOnBullishContainerDark = Color(0xFFBFE4F7)
val ColorBlindOnBearishContainerDark = Color(0xFFFFDDB6)

// AMOLED Theme (Pure #000000 Black Surface)
val AmoledBackground = Color(0xFF000000)
val AmoledSurface = Color(0xFF000000)
val AmoledSurfaceContainer = Color(0xFF0A0E14)
val AmoledSurfaceVariant = Color(0xFF131A24)
val AmoledOutline = Color(0xFF283241)
val AmoledOutlineVariant = Color(0xFF171E29)

val TradingLightColorScheme = lightColorScheme(
    primary = EmeraldLight,
    onPrimary = EmeraldOnLight,
    primaryContainer = EmeraldContainerLight,
    onPrimaryContainer = EmeraldOnContainerLight,
    secondary = AmberLight,
    onSecondary = Color.White,
    secondaryContainer = AmberContainerLight,
    onSecondaryContainer = AmberOnContainerLight,
    tertiary = CyanLight,
    onTertiary = Color.White,
    tertiaryContainer = CyanContainerLight,
    error = BearishLight,
    onError = Color.White,
    errorContainer = BearishContainerLight,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainerLowest = LightSurface,
    surfaceContainerLow = Color(0xFFF9FBFD),
    surfaceContainer = Color(0xFFEEF3F8),
    surfaceContainerHigh = Color(0xFFE7EDF4),
    surfaceContainerHighest = Color(0xFFDCE4EE),
    outline = LightOutline,
    outlineVariant = LightOutlineVariant
)

val TradingDarkColorScheme = darkColorScheme(
    primary = EmeraldDark,
    onPrimary = EmeraldOnDark,
    primaryContainer = EmeraldContainerDark,
    onPrimaryContainer = EmeraldOnContainerDark,
    secondary = AmberDark,
    onSecondary = AmberOnDark,
    secondaryContainer = AmberContainerDark,
    onSecondaryContainer = AmberOnContainerDark,
    tertiary = CyanDark,
    onTertiary = Color(0xFF00344C),
    tertiaryContainer = CyanContainerDark,
    error = BearishDark,
    onError = Color(0xFF4D0011),
    errorContainer = BearishContainerDark,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainerLowest = Color(0xFF080E17),
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceVariant,
    surfaceContainerHighest = Color(0xFF24354F),
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant
)

val TradingAmoledColorScheme = darkColorScheme(
    primary = EmeraldDark,
    onPrimary = EmeraldOnDark,
    primaryContainer = EmeraldContainerDark,
    onPrimaryContainer = EmeraldOnContainerDark,
    secondary = AmberDark,
    onSecondary = AmberOnDark,
    secondaryContainer = AmberContainerDark,
    onSecondaryContainer = AmberOnContainerDark,
    tertiary = CyanDark,
    onTertiary = Color(0xFF00344C),
    tertiaryContainer = CyanContainerDark,
    error = BearishDark,
    onError = Color(0xFF4D0011),
    errorContainer = BearishContainerDark,
    background = AmoledBackground,
    onBackground = DarkOnSurface,
    surface = AmoledSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = AmoledSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainerLowest = AmoledBackground,
    surfaceContainerLow = Color(0xFF05070B),
    surfaceContainer = AmoledSurfaceContainer,
    surfaceContainerHigh = AmoledSurfaceVariant,
    surfaceContainerHighest = Color(0xFF1A222F),
    outline = AmoledOutline,
    outlineVariant = AmoledOutlineVariant
)
