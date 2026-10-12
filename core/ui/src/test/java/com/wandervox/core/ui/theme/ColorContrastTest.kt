package com.wandervox.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the WCAG 2.1 AA contrast guarantees documented in DEV_LOG.md. */
class ColorContrastTest {

    private val scheme = LightColorScheme

    @Test
    fun textColorsMeetBodyTextContrast() {
        val textPairs = mapOf(
            "onPrimary / primary" to (scheme.onPrimary to scheme.primary),
            "onPrimaryContainer / primaryContainer" to (scheme.onPrimaryContainer to scheme.primaryContainer),
            "onSecondary / secondary" to (scheme.onSecondary to scheme.secondary),
            "onSecondaryContainer / secondaryContainer" to (scheme.onSecondaryContainer to scheme.secondaryContainer),
            "onTertiary / tertiary" to (scheme.onTertiary to scheme.tertiary),
            "onTertiaryContainer / tertiaryContainer" to (scheme.onTertiaryContainer to scheme.tertiaryContainer),
            "onBackground / background" to (scheme.onBackground to scheme.background),
            "onSurface / surface" to (scheme.onSurface to scheme.surface),
            "onSurface / surfaceContainerHighest" to (scheme.onSurface to scheme.surfaceContainerHighest),
            "onSurfaceVariant / surface" to (scheme.onSurfaceVariant to scheme.surface),
            "onSurfaceVariant / surfaceContainerLowest" to (scheme.onSurfaceVariant to scheme.surfaceContainerLowest),
            "onSurfaceVariant / surfaceContainerHighest" to (scheme.onSurfaceVariant to scheme.surfaceContainerHighest),
            "primary text / surface" to (scheme.primary to scheme.surface),
            "error text / surface" to (scheme.error to scheme.surface),
            "onError / error" to (scheme.onError to scheme.error),
            "onErrorContainer / errorContainer" to (scheme.onErrorContainer to scheme.errorContainer),
            "inverseOnSurface / inverseSurface" to (scheme.inverseOnSurface to scheme.inverseSurface),
            "inversePrimary / inverseSurface" to (scheme.inversePrimary to scheme.inverseSurface),
        )

        textPairs.forEach { (name, colors) ->
            val ratio = contrastRatio(colors.first, colors.second)
            assertTrue("$name is %.2f:1, expected >= 4.5:1".format(ratio), ratio >= 4.5)
        }
    }

    @Test
    fun outlineMeetsNonTextContrast() {
        listOf(scheme.surface, scheme.surfaceContainerLowest).forEach { background ->
            val ratio = contrastRatio(scheme.outline, background)
            assertTrue("outline is %.2f:1, expected >= 3:1".format(ratio), ratio >= 3.0)
        }
    }

    private fun contrastRatio(first: Color, second: Color): Double {
        val lighter = maxOf(first.luminance(), second.luminance())
        val darker = minOf(first.luminance(), second.luminance())
        return (lighter + 0.05) / (darker + 0.05)
    }
}
