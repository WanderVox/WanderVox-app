package com.wandervox.core.database.converter

import androidx.room.TypeConverter
import com.wandervox.core.domain.model.LogCategory
import com.wandervox.core.domain.model.SyncStatus

/**
 * Column converters for [com.wandervox.core.database.entity.TravelLogEntity].
 *
 * Enums are stored by name (the stable value shared with Firestore). Unknown names, e.g. written by
 * a newer app version, fall back to a safe default instead of crashing the query.
 */
class Converters {

    @TypeConverter
    fun fromLikes(likes: List<String>): String = likes.joinToString(LIKES_SEPARATOR)

    @TypeConverter
    fun toLikes(value: String): List<String> =
        if (value.isEmpty()) emptyList() else value.split(LIKES_SEPARATOR)

    @TypeConverter
    fun fromCategory(category: LogCategory): String = category.name

    @TypeConverter
    fun toCategory(value: String): LogCategory =
        LogCategory.entries.firstOrNull { it.name == value } ?: LogCategory.GENERAL

    @TypeConverter
    fun fromSyncStatus(status: SyncStatus): String = status.name

    /** Unknown values map to [SyncStatus.PENDING] so the entry is retried rather than lost. */
    @TypeConverter
    fun toSyncStatus(value: String): SyncStatus =
        SyncStatus.entries.firstOrNull { it.name == value } ?: SyncStatus.PENDING

    private companion object {
        /**
         * ASCII "unit separator". Likes are Firebase Auth UIDs (alphanumeric), so this control
         * character can never appear inside one; it avoids pulling a JSON library for a flat list.
         */
        const val LIKES_SEPARATOR = "\u001F"
    }
}
