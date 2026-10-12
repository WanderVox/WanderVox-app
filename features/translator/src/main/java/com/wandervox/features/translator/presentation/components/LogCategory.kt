package com.wandervox.features.translator.presentation.components

import androidx.annotation.StringRes
import com.wandervox.translator.R

/**
 * Travel log categories offered when saving a translation. [storageKey] is the stable,
 * language-independent value persisted in `TravelLogEntry.category`.
 */
enum class LogCategory(val storageKey: String, @StringRes val labelRes: Int) {
    FOOD("Food", R.string.translator_category_food),
    TRANSPORT("Transport", R.string.translator_category_transport),
    LODGING("Lodging", R.string.translator_category_lodging),
    SHOPPING("Shopping", R.string.translator_category_shopping),
    EMERGENCY("Emergency", R.string.translator_category_emergency),
}
