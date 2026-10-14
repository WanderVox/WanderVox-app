package com.wandervox.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.wandervox.ui.R

/**
 * Poppins bundled in `res/font` (about 476 KB), so typography is identical on every launch,
 * including the first one without connectivity and on devices without Google Play services.
 */
val PoppinsFontFamily = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_bold, FontWeight.Bold),
)

// Material 3 type scale sizes. Poppins is wide, so letter spacing is tighter than the baseline.
internal val WanderVoxTypography = Typography(
    displayLarge = poppins(FontWeight.Bold, size = 57, lineHeight = 64, tracking = -0.25),
    displayMedium = poppins(FontWeight.Bold, size = 45, lineHeight = 52),
    displaySmall = poppins(FontWeight.Bold, size = 36, lineHeight = 44),

    headlineLarge = poppins(FontWeight.Bold, size = 32, lineHeight = 40),
    headlineMedium = poppins(FontWeight.Bold, size = 28, lineHeight = 36),
    headlineSmall = poppins(FontWeight.Medium, size = 24, lineHeight = 32),

    titleLarge = poppins(FontWeight.Medium, size = 22, lineHeight = 28),
    titleMedium = poppins(FontWeight.Medium, size = 16, lineHeight = 24, tracking = 0.1),
    titleSmall = poppins(FontWeight.Medium, size = 14, lineHeight = 20, tracking = 0.1),

    bodyLarge = poppins(FontWeight.Normal, size = 16, lineHeight = 24, tracking = 0.15),
    bodyMedium = poppins(FontWeight.Normal, size = 14, lineHeight = 20, tracking = 0.1),
    bodySmall = poppins(FontWeight.Normal, size = 12, lineHeight = 16, tracking = 0.2),

    labelLarge = poppins(FontWeight.Medium, size = 14, lineHeight = 20, tracking = 0.1),
    labelMedium = poppins(FontWeight.Medium, size = 12, lineHeight = 16, tracking = 0.3),
    labelSmall = poppins(FontWeight.Medium, size = 11, lineHeight = 16, tracking = 0.3),
)

private fun poppins(
    weight: FontWeight,
    size: Int,
    lineHeight: Int,
    tracking: Double = 0.0,
): TextStyle = TextStyle(
    fontFamily = PoppinsFontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.sp,
)
