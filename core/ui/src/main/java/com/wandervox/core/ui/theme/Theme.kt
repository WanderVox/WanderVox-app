package com.wandervox.core.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * WanderVox design system theme (light brand palette, Poppins, rounded shapes).
 *
 * Dynamic color is intentionally not used so the brand greens stay consistent on every device.
 * Draw edge-to-edge by calling `enableEdgeToEdge()` in the Activity and handling insets
 * (e.g. Scaffold's `innerPadding`); this theme keeps the system bar icons dark so they stay
 * readable on the light background, even when the device is in dark mode.
 */
@Composable
fun WanderVoxTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
            }
        }
    }

    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = WanderVoxTypography,
        shapes = WanderVoxShapes,
        content = content,
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
