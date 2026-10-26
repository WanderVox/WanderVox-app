package com.wandervox.features.log.presentation.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wandervox.core.domain.repository.LogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Observes a single Travel Log entry, identified by the [ENTRY_ID_ARG] navigation argument. The
 * state follows the repository, so a sync or a delete elsewhere is reflected while the screen is open.
 */
@HiltViewModel
class LogDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    logRepository: LogRepository,
) : ViewModel() {

    private val entryId: String? = savedStateHandle.get<String>(ENTRY_ID_ARG)?.takeIf { it.isNotBlank() }

    val uiState: StateFlow<LogDetailUiState> =
        (entryId?.let(logRepository::getEntry) ?: flowOf(null))
            .map { entry -> entry?.let(LogDetailUiState::Loaded) ?: LogDetailUiState.NotFound }
            // Offline-first source: a failed read means there is nothing local to show.
            .catch { emit(LogDetailUiState.NotFound) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = LogDetailUiState.Loading,
            )

    companion object {
        /** Navigation argument holding the id of the entry to show. */
        const val ENTRY_ID_ARG = "entryId"

        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
