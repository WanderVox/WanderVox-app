package com.wandervox.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.wandervox.core.domain.model.LogCategory
import com.wandervox.core.domain.model.SyncStatus

/**
 * Room row for a [com.wandervox.core.domain.model.TravelLogEntry]. Coordinates are flattened into
 * two nullable columns; an entry has a location only when both are non-null.
 */
@Entity(
    tableName = TravelLogEntity.TABLE_NAME,
    indices = [
        Index(value = ["userId"]),
        Index(value = ["isPublic"]),
        Index(value = ["syncStatus"])
    ]
)
data class TravelLogEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val originalText: String,
    val translatedText: String,
    val sourceLanguage: String,
    val targetLanguage: String,
    val timestamp: Long,
    val latitude: Double?,
    val longitude: Double?,
    val localPhotoPath: String?,
    val remotePhotoUrl: String?,
    val category: LogCategory,
    val notes: String,
    val isPublic: Boolean,
    val likes: List<String>,
    val authorName: String?,
    val updatedAt: Long,
    val syncStatus: SyncStatus
) {
    companion object {
        const val TABLE_NAME = "travel_logs"

        // Stored values of SyncStatus (Converters persist enum names) for use inside @Query strings,
        // which need compile-time constants. Kept in step with the enum by ConvertersTest.
        const val SYNCED = "SYNCED"
        const val PENDING_DELETE = "PENDING_DELETE"
    }
}
