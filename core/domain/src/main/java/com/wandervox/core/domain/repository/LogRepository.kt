package com.wandervox.core.domain.repository

import com.wandervox.core.domain.model.TravelLogEntry
import kotlinx.coroutines.flow.Flow

interface LogRepository {
    fun getUserEntries(userId: String): Flow<List<TravelLogEntry>>
    fun getCommunityEntries(): Flow<List<TravelLogEntry>>
    /** Emits `null` while the entry does not exist (or after it is deleted). */
    fun getEntry(entryId: String): Flow<TravelLogEntry?>
    suspend fun saveEntry(entry: TravelLogEntry): Result<Unit>
    suspend fun toggleLike(entryId: String, userId: String): Result<Unit>
    suspend fun deleteEntry(entryId: String): Result<Unit>
    /** Retries the upload of every entry that is not [com.wandervox.core.domain.model.SyncStatus.SYNCED]. */
    suspend fun syncPending(): Result<Unit>
}
