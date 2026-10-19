package com.wandervox.core.network.remote.log

import com.wandervox.core.domain.model.TravelLogEntry
import kotlinx.coroutines.flow.Flow

/**
 * Remote (Firestore) store for travel log entries. Contract stub agreed with Track B; the Firestore
 * implementation lives on B's branch.
 *
 * [upsert] must be idempotent on [TravelLogEntry.id] and must NOT overwrite `likes`: those are
 * owned by [setLiked] (array union/remove) so concurrent likes from other users are not lost.
 */
interface LogRemoteDataSource {
    suspend fun upsert(entry: TravelLogEntry): Result<Unit>
    suspend fun delete(entryId: String): Result<Unit>
    fun observeUserEntries(userId: String): Flow<List<TravelLogEntry>>
    fun observePublicEntries(): Flow<List<TravelLogEntry>>
    suspend fun setLiked(entryId: String, userId: String, liked: Boolean): Result<Unit>
}
