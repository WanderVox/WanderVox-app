package com.wandervox.features.translator.presentation

import com.wandervox.core.domain.model.Coordinates
import com.wandervox.core.domain.model.LogCategory

/** Every user intent the translator screen can send to [TranslatorViewModel]. */
sealed interface TranslatorUiEvent {
    data object OnToggleListening : TranslatorUiEvent
    data object OnSwapLanguages : TranslatorUiEvent
    data class OnSaveToLog(
        val category: LogCategory,
        val notes: String,
        val coordinates: Coordinates?,
    ) : TranslatorUiEvent
    data object OnDismissError : TranslatorUiEvent
    data object OnResetSaveSuccess : TranslatorUiEvent
}
