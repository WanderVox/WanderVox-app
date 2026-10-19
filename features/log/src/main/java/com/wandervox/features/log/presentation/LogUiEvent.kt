package com.wandervox.features.log.presentation

import com.wandervox.core.domain.model.LogCategory

/** User intents sent from the Travel Log UI to [LogViewModel]. */
sealed interface LogUiEvent {
    data class OnTabSelected(val tab: LogTab) : LogUiEvent
    /** `null` clears the filter. */
    data class OnCategorySelected(val category: LogCategory?) : LogUiEvent
    data object OnRetry : LogUiEvent
    data class OnToggleLike(val entryId: String) : LogUiEvent
    /** Sent after the user confirmed the delete dialog. */
    data class OnDeleteEntry(val entryId: String) : LogUiEvent
    data object OnActionErrorShown : LogUiEvent
    /** [sourceUri] is the Photo Picker's `content://` URI, as a string. */
    data class OnPhotoPicked(val entryId: String, val sourceUri: String) : LogUiEvent
}
