package com.wandervox.features.log.presentation.detail

import com.wandervox.core.domain.model.TravelLogEntry

/** What the entry detail screen renders: the entry is loading, gone, or available. */
sealed interface LogDetailUiState {
    data object Loading : LogDetailUiState

    /** No entry for the requested id: deleted, never synced to this device, or the read failed. */
    data object NotFound : LogDetailUiState

    data class Loaded(val entry: TravelLogEntry) : LogDetailUiState
}
