package com.wandervox.features.log.presentation.components

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.ui.graphics.vector.ImageVector
import com.wandervox.core.domain.model.LogCategory
import com.wandervox.log.R

/** User-facing label for each domain [LogCategory]; the persisted value is the enum name. */
@get:StringRes
val LogCategory.labelRes: Int
    get() = when (this) {
        LogCategory.FOOD -> R.string.log_category_food
        LogCategory.TRANSPORT -> R.string.log_category_transport
        LogCategory.LODGING -> R.string.log_category_lodging
        LogCategory.SHOPPING -> R.string.log_category_shopping
        LogCategory.EMERGENCY -> R.string.log_category_emergency
        LogCategory.GENERAL -> R.string.log_category_general
    }

val LogCategory.icon: ImageVector
    get() = when (this) {
        LogCategory.FOOD -> Icons.Outlined.Restaurant
        LogCategory.TRANSPORT -> Icons.Outlined.DirectionsBus
        LogCategory.LODGING -> Icons.Outlined.Hotel
        LogCategory.SHOPPING -> Icons.Outlined.ShoppingBag
        LogCategory.EMERGENCY -> Icons.Outlined.LocalHospital
        LogCategory.GENERAL -> Icons.Outlined.Category
    }
