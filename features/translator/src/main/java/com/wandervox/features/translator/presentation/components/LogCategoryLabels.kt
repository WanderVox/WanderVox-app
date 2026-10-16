package com.wandervox.features.translator.presentation.components

import androidx.annotation.StringRes
import com.wandervox.core.domain.model.LogCategory
import com.wandervox.translator.R

/** User-facing label for each domain [LogCategory]; the persisted value is the enum name. */
@get:StringRes
val LogCategory.labelRes: Int
    get() = when (this) {
        LogCategory.FOOD -> R.string.translator_category_food
        LogCategory.TRANSPORT -> R.string.translator_category_transport
        LogCategory.LODGING -> R.string.translator_category_lodging
        LogCategory.SHOPPING -> R.string.translator_category_shopping
        LogCategory.EMERGENCY -> R.string.translator_category_emergency
        LogCategory.GENERAL -> R.string.translator_category_general
    }
