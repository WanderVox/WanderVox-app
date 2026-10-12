package com.wandervox.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Material 3 buttons are already pill-shaped by default, so they need no token here.
 */
internal val WanderVoxShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),      // Text fields, menus, snackbars
    small = RoundedCornerShape(percent = 50),   // Chips -> pill
    medium = RoundedCornerShape(16.dp),         // Cards
    large = RoundedCornerShape(24.dp),          // FABs, navigation drawer
    extraLarge = RoundedCornerShape(24.dp),     // Bottom sheets, dialogs
)
