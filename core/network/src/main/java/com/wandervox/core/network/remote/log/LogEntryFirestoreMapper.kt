package com.wandervox.core.network.remote.log

import com.wandervox.core.domain.model.Coordinates
import com.wandervox.core.domain.model.LogCategory
import com.wandervox.core.domain.model.SyncStatus
import com.wandervox.core.domain.model.TravelLogEntry

/** Firestore layout of a Travel Log document (`log_entries/{entryId}`). */
internal object LogFields {
    const val COLLECTION = "log_entries"

    const val USER_ID = "userId"
    const val ORIGINAL_TEXT = "originalText"
    const val TRANSLATED_TEXT = "translatedText"
    const val SOURCE_LANGUAGE = "sourceLanguage"
    const val TARGET_LANGUAGE = "targetLanguage"
    const val TIMESTAMP = "timestamp"
    const val LATITUDE = "latitude"
    const val LONGITUDE = "longitude"
    const val REMOTE_PHOTO_URL = "remotePhotoUrl"
    const val CATEGORY = "category"
    const val NOTES = "notes"
    const val IS_PUBLIC = "isPublic"
    const val LIKES = "likes"
    const val AUTHOR_NAME = "authorName"
    const val UPDATED_AT = "updatedAt"
}

/**
 * Converts between [TravelLogEntry] and plain Firestore maps. Kept free of Firebase types so it can
 * be unit-tested on the JVM.
 */
internal object LogEntryFirestoreMapper {

    /**
     * Fields written by an upsert. Leaves out the document id, the local-only fields and [LogFields.LIKES],
     * which is only changed through atomic array operations.
     */
    fun toFirestore(entry: TravelLogEntry): Map<String, Any?> = mapOf(
        LogFields.USER_ID to entry.userId,
        LogFields.ORIGINAL_TEXT to entry.originalText,
        LogFields.TRANSLATED_TEXT to entry.translatedText,
        LogFields.SOURCE_LANGUAGE to entry.sourceLanguage,
        LogFields.TARGET_LANGUAGE to entry.targetLanguage,
        LogFields.TIMESTAMP to entry.timestamp,
        LogFields.LATITUDE to entry.coordinates?.latitude,
        LogFields.LONGITUDE to entry.coordinates?.longitude,
        LogFields.REMOTE_PHOTO_URL to entry.remotePhotoUrl,
        LogFields.CATEGORY to entry.category.name,
        LogFields.NOTES to entry.notes,
        LogFields.IS_PUBLIC to entry.isPublic,
        LogFields.AUTHOR_NAME to entry.authorName,
        LogFields.UPDATED_AT to entry.updatedAt,
    )

    /**
     * Builds an entry from a document. Missing or malformed fields fall back to the model defaults so
     * one bad document never breaks the whole feed. [hasPendingWrites] marks local, unacknowledged data.
     */
    fun fromFirestore(id: String, data: Map<String, Any?>, hasPendingWrites: Boolean): TravelLogEntry {
        val defaults = TravelLogEntry()
        val timestamp = data.long(LogFields.TIMESTAMP) ?: defaults.timestamp
        val latitude = data.double(LogFields.LATITUDE)
        val longitude = data.double(LogFields.LONGITUDE)
        return TravelLogEntry(
            id = id,
            userId = data.string(LogFields.USER_ID) ?: defaults.userId,
            originalText = data.string(LogFields.ORIGINAL_TEXT) ?: defaults.originalText,
            translatedText = data.string(LogFields.TRANSLATED_TEXT) ?: defaults.translatedText,
            sourceLanguage = data.string(LogFields.SOURCE_LANGUAGE) ?: defaults.sourceLanguage,
            targetLanguage = data.string(LogFields.TARGET_LANGUAGE) ?: defaults.targetLanguage,
            timestamp = timestamp,
            coordinates = if (latitude != null && longitude != null) Coordinates(latitude, longitude) else null,
            localPhotoPath = null,
            remotePhotoUrl = data.string(LogFields.REMOTE_PHOTO_URL),
            category = data.string(LogFields.CATEGORY)
                ?.let { name -> LogCategory.entries.firstOrNull { it.name == name } }
                ?: LogCategory.GENERAL,
            notes = data.string(LogFields.NOTES) ?: defaults.notes,
            isPublic = data[LogFields.IS_PUBLIC] as? Boolean ?: false,
            likes = (data[LogFields.LIKES] as? List<*>)?.filterIsInstance<String>().orEmpty(),
            authorName = data.string(LogFields.AUTHOR_NAME),
            updatedAt = data.long(LogFields.UPDATED_AT) ?: timestamp,
            syncStatus = if (hasPendingWrites) SyncStatus.PENDING else SyncStatus.SYNCED,
        )
    }

    private fun Map<String, Any?>.string(key: String): String? = this[key] as? String

    // Firestore returns integers as Long and decimals as Double; accept either.
    private fun Map<String, Any?>.long(key: String): Long? = (this[key] as? Number)?.toLong()

    private fun Map<String, Any?>.double(key: String): Double? = (this[key] as? Number)?.toDouble()
}
