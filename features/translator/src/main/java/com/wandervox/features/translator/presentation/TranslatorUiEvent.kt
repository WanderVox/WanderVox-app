package com.wandervox.features.translator.presentation

import com.wandervox.core.domain.model.LogCategory

/** Every user intent the translator screen can send to [TranslatorViewModel]. */
sealed interface TranslatorUiEvent {
    data object OnToggleListening : TranslatorUiEvent
    data object OnSwapLanguages : TranslatorUiEvent
    /** The ViewModel attaches the current location itself when the permission is granted. */
    data class OnSaveToLog(
        val category: LogCategory,
        val notes: String,
    ) : TranslatorUiEvent
    data object OnDismissError : TranslatorUiEvent
    data object OnResetSaveSuccess : TranslatorUiEvent
}
