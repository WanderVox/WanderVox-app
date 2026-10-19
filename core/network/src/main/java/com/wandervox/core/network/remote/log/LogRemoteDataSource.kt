package com.wandervox.core.network.remote.log

import com.wandervox.core.domain.model.TravelLogEntry
import kotlinx.coroutines.flow.Flow

/**
 * Remote copy of the Travel Log, used by the offline-first repository to push local changes and to
 * read the Community feed. Local-only fields (`localPhotoPath`, `syncStatus`) never leave the device.
 */
interface LogRemoteDataSource {

    /**
     * Creates or updates [entry] (merge). Likes are not written here, so concurrent likes are never
     * overwritten; use [setLiked]. Succeeds once the server acknowledges the write. Offline, the write
     * stays queued in Firestore's local cache and this returns a failure after a timeout, so the caller
     * keeps the entry as pending and may retry (the write is idempotent).
     */
    suspend fun upsert(entry: TravelLogEntry): Result<Unit>

    /** Deletes the entry; same acknowledgement semantics as [upsert]. */
    suspend fun delete(entryId: String): Result<Unit>

    /** Adds or removes [userId] from the entry's likes atomically (`arrayUnion` / `arrayRemove`). */
    suspend fun setLiked(entryId: String, userId: String, liked: Boolean): Result<Unit>

    /** Every entry owned by [userId], newest first. Emits from the local cache while offline. */
    fun observeUserEntries(userId: String): Flow<List<TravelLogEntry>>

    /** Every public entry (Community feed), newest first. Emits from the local cache while offline. */
    fun observePublicEntries(): Flow<List<TravelLogEntry>>
}
