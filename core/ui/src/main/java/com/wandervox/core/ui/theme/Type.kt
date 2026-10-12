package com.wandervox.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.wandervox.ui.R

private val GoogleFontsProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private val Poppins = GoogleFont("Poppins")

/**
 * Poppins downloaded through Google Play services and cached by the system, so it adds nothing to
 * the APK. Compose tries fonts in order for each weight: if the download is unavailable (no Play
 * services, first launch offline) the device's sans-serif is used instead.
 */
val PoppinsFontFamily = FontFamily(
    Font(googleFont = Poppins, fontProvider = GoogleFontsProvider, weight = FontWeight.Normal),
    Font(googleFont = Poppins, fontProvider = GoogleFontsProvider, weight = FontWeight.Medium),
    Font(googleFont = Poppins, fontProvider = GoogleFontsProvider, weight = FontWeight.Bold),
    Font(DeviceFontFamilyName("sans-serif"), weight = FontWeight.Normal),
    Font(DeviceFontFamilyName("sans-serif-medium"), weight = FontWeight.Medium),
    Font(DeviceFontFamilyName("sans-serif"), weight = FontWeight.Bold),
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
