package com.wandervox.core.domain.model

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
    val category: LogCategory = LogCategory.GENERAL,
    val notes: String = "",
    val isPublic: Boolean = false,
    val likes: List<String> = emptyList(),
    /** Display name shown on the Community tab. */
    val authorName: String? = null,
    /** Last modification time, used for last-write-wins conflict resolution. */
    val updatedAt: Long = timestamp,
    val syncStatus: SyncStatus = SyncStatus.PENDING
)
