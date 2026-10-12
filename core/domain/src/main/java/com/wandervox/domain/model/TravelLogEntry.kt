package com.wandervox.domain.model

data class Coordinates(
    val latitude: Double,
    val longitude: Double
)

data class TravelLogEntry(
    val id: String = "",
    val userId: String = "",
    val originalText: String = "",
    val translatedText: String = "",
    val sourceLanguage: String = "ES",
    val targetLanguage: String = "EN",
    val timestamp: Long = System.currentTimeMillis(),
    val coordinates: Coordinates? = null,
    val localPhotoPath: String? = null,
    val remotePhotoUrl: String? = null,
    val category: String = "General",
    val notes: String = "",
    val isPublic: Boolean = false,
    val likes: List<String> = emptyList()
)