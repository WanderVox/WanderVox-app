package com.wandervox.features.map.presentation

/** A geotagged travel log entry, already flattened for the map view. */
data class MapMarker(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val title: String,
    val snippet: String,
)

data class MapUiState(
    val isLoading: Boolean = true,
    val markers: List<MapMarker> = emptyList(),
)
