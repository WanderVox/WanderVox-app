package com.wandervox.core.ui.theme

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Brand palette (WanderVox style guide).
val ForestGreen = Color(0xFF0B6B3A)
val NatureGreen = Color(0xFF2E9E6B)
val MarigoldYellow = Color(0xFFFFC107)
val CanvasMist = Color(0xFFF7F8F5)
val CardWhite = Color(0xFFFFFFFF)
val InkCharcoal = Color(0xFF1F2937)
val SlateGray = Color(0xFF6B7280)

// Derived tones. Every text/background pairing is >= 4.5:1 (see DEV_LOG.md §6.4).
private val DeepForest = Color(0xFF00210F)
private val MintContainer = Color(0xFFC9EBD6)
private val OnMintContainer = Color(0xFF00391C)
private val SageContainer = Color(0xFFD3F1E2)
private val OnSageContainer = Color(0xFF0A3D27)
private val MarigoldContainer = Color(0xFFFFECB3)
private val OnMarigoldContainer = Color(0xFF4A3700)
private val SageVariant = Color(0xFFEEF2EC)
private val SageOutlineVariant = Color(0xFFD7DCD3)
private val SageDim = Color(0xFFD8DBD5)
private val InverseCharcoal = Color(0xFF2B3530)
private val InverseMist = Color(0xFFF1F3EE)
private val InverseMint = Color(0xFF8FD8AE)
private val ErrorRed = Color(0xFFB3261E)
private val ErrorContainerRose = Color(0xFFF9DEDC)
private val OnErrorContainerMaroon = Color(0xFF410E0B)

internal val LightColorScheme = lightColorScheme(
    primary = ForestGreen,
    onPrimary = CardWhite,
    primaryContainer = MintContainer,
    onPrimaryContainer = OnMintContainer,
    inversePrimary = InverseMint,

    secondary = NatureGreen,
    // White on NatureGreen is only 3.38:1; deep forest text reaches 5.08:1.
    onSecondary = DeepForest,
    secondaryContainer = SageContainer,
    onSecondaryContainer = OnSageContainer,

    tertiary = MarigoldYellow,
    onTertiary = InkCharcoal,
    tertiaryContainer = MarigoldContainer,
    onTertiaryContainer = OnMarigoldContainer,

    background = CanvasMist,
    onBackground = InkCharcoal,
    surface = CanvasMist,
    onSurface = InkCharcoal,
    surfaceVariant = SageVariant,
    onSurfaceVariant = SlateGray,
    surfaceTint = ForestGreen,
    inverseSurface = InverseCharcoal,
    inverseOnSurface = InverseMist,

    // Cards, sheets, navigation bars and text fields sit on white containers above the mist
    // background, as the style guide specifies. Keeping every container at least as light as the
    // background is also what keeps SlateGray subtitles above 4.5:1.
    surfaceBright = CanvasMist,
    surfaceDim = SageDim,
    surfaceContainerLowest = CardWhite,
    surfaceContainerLow = CardWhite,
    surfaceContainer = CardWhite,
    surfaceContainerHigh = CardWhite,
    surfaceContainerHighest = CardWhite,

    outline = SlateGray,
    outlineVariant = SageOutlineVariant,

    error = ErrorRed,
    onError = CardWhite,
    errorContainer = ErrorContainerRose,
    onErrorContainer = OnErrorContainerMaroon,

    scrim = Color.Black,
)
