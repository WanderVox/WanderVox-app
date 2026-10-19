package com.wandervox.features.map.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wandervox.core.domain.model.TravelLogEntry
import com.wandervox.core.domain.repository.AuthRepository
import com.wandervox.core.domain.repository.LogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(
    authRepository: AuthRepository,
    logRepository: LogRepository,
) : ViewModel() {

    /** The signed-in user's geotagged entries; switches automatically when the user changes. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<MapUiState> = authRepository.currentUser
        .flatMapLatest { user ->
            if (user == null) flowOf(emptyList()) else logRepository.getUserEntries(user.uid)
        }
        .map { entries -> MapUiState(isLoading = false, markers = entries.mapNotNull { it.toMarker() }) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = MapUiState(isLoading = true),
        )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** `null` for entries saved without a location: they have nothing to plot. */
private fun TravelLogEntry.toMarker(): MapMarker? = coordinates?.let {
    MapMarker(
        id = id,
        latitude = it.latitude,
        longitude = it.longitude,
        title = originalText,
        snippet = translatedText,
    )
}
