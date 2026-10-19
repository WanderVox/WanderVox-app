package com.wandervox.core.data.repository

import com.wandervox.core.data.di.ApplicationScope
import com.wandervox.core.data.util.runSuspendCatching
import com.wandervox.core.database.dao.TravelLogDao
import com.wandervox.core.database.mapper.asDomain
import com.wandervox.core.database.mapper.asEntity
import com.wandervox.core.domain.model.SyncStatus
import com.wandervox.core.domain.model.TravelLogEntry
import com.wandervox.core.domain.repository.AuthRepository
import com.wandervox.core.domain.repository.LogRepository
import com.wandervox.core.network.remote.image.ImageUploader
import com.wandervox.core.network.remote.log.LogRemoteDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room is the source of truth for the user's own entries; the remote store is a replica.
 *
 * Writes land in Room as [SyncStatus.PENDING] and return immediately. The upload (photo, then
 * document) runs on the [ApplicationScope] so the caller never waits on the network, which with
 * Firestore offline persistence could otherwise block until connectivity returns.
 */
@Singleton
class OfflineFirstLogRepository @Inject constructor(
    private val dao: TravelLogDao,
    private val remote: LogRemoteDataSource,
    private val imageUploader: ImageUploader,
    private val authRepository: AuthRepository,
    @ApplicationScope private val appScope: CoroutineScope
) : LogRepository {

    /** Entry ids currently being uploaded, so `saveEntry` and `syncPending` never upload the same one twice. */
    private val inFlight: MutableSet<String> = ConcurrentHashMap.newKeySet()

    override fun getUserEntries(userId: String): Flow<List<TravelLogEntry>> =
        dao.observeUserEntries(userId).map { entities -> entities.map { it.asDomain() } }

    /** Remote feed; degrades to the public entries cached in Room when the remote is unreachable. */
    override fun getCommunityEntries(): Flow<List<TravelLogEntry>> =
        remote.observePublicEntries().catch {
            emitAll(dao.observePublicEntries().map { entities -> entities.map { it.asDomain() } })
        }

    override fun getEntry(entryId: String): Flow<TravelLogEntry?> =
        dao.getEntryById(entryId).map { it?.asDomain() }

    override suspend fun saveEntry(entry: TravelLogEntry): Result<Unit> = runSuspendCatching {
        // userId fix: entries (e.g. from the Translator) arrive without an owner; stamp the signed-in user.
        val user = authRepository.currentUser.first()
        val id = entry.id.ifBlank { UUID.randomUUID().toString() }
        val previous = dao.findById(id)
        val pending = entry.copy(
            id = id,
            userId = user?.uid ?: entry.userId,
            authorName = if (user != null) user.email ?: DEFAULT_AUTHOR_NAME else entry.authorName,
            // Strictly increasing per entry, so the compare-and-set in syncEntry detects every edit.
            updatedAt = maxOf(System.currentTimeMillis(), (previous?.updatedAt ?: 0L) + 1),
            syncStatus = SyncStatus.PENDING
        )
        dao.upsert(pending.asEntity())
        appScope.launch { runSuspendCatching { syncEntry(pending) } }
    }

    override suspend fun toggleLike(entryId: String, userId: String): Result<Unit> = runSuspendCatching {
        val local = dao.findById(entryId)
        // Community entries by other users are not in Room; read their current likes from the remote feed.
        val currentLikes = local?.likes
            ?: remote.observePublicEntries().first().firstOrNull { it.id == entryId }?.likes
            ?: throw entryNotFound(entryId)
        val liked = userId !in currentLikes
        // Likes are not content edits: updatedAt and syncStatus stay untouched.
        local?.let { dao.upsert(it.copy(likes = if (liked) currentLikes + userId else currentLikes - userId)) }
        remote.setLiked(entryId, userId, liked).onFailure { error ->
            // No offline queue for likes: roll back so the UI does not show a like that never reached the server.
            local?.let { dao.upsert(it) }
            throw error
        }
    }

    override suspend fun deleteEntry(entryId: String): Result<Unit> = runSuspendCatching {
        dao.findById(entryId) ?: throw entryNotFound(entryId)
        dao.delete(entryId)
        // Best effort: a failed remote delete leaves an orphan document (no tombstones yet, see STAGE_2_REPORT).
        appScope.launch { runSuspendCatching { remote.delete(entryId) } }
    }

    override suspend fun syncPending(): Result<Unit> = runSuspendCatching {
        val unsynced = dao.getEntriesBySyncStatus(listOf(SyncStatus.PENDING, SyncStatus.FAILED))
        val failures = unsynced.count { !syncEntry(it.asDomain()) }
        if (failures > 0) throw SyncIncompleteException(failures, unsynced.size)
    }

    /**
     * Uploads the photo (if not uploaded yet) and the document, then records the outcome in Room.
     *
     * If the entry was edited while uploading, the newer version is synced right after: its own
     * `saveEntry` call skipped the upload because this one was in flight.
     *
     * @return `false` only when the attempt failed; an entry already being synced counts as success.
     */
    private suspend fun syncEntry(entry: TravelLogEntry): Boolean {
        if (!inFlight.add(entry.id)) return true
        val (synced, rowsUpdated) = try {
            uploadAndRecord(entry)
        } finally {
            inFlight.remove(entry.id)
        }
        if (rowsUpdated == 0) {
            val newer = dao.findById(entry.id)
            if (newer != null && newer.syncStatus == SyncStatus.PENDING) return syncEntry(newer.asDomain())
        }
        return synced
    }

    /** @return whether the upload succeeded, and how many Room rows recorded the outcome (0 = row changed). */
    private suspend fun uploadAndRecord(entry: TravelLogEntry): Pair<Boolean, Int> {
        var remotePhotoUrl = entry.remotePhotoUrl
        // withTimeoutOrNull outside runSuspendCatching: the timeout is a CancellationException, which
        // runSuspendCatching rethrows on purpose.
        val synced = withTimeoutOrNull(SYNC_TIMEOUT_MILLIS) {
            runSuspendCatching {
                val localPhotoPath = entry.localPhotoPath
                if (localPhotoPath != null && remotePhotoUrl == null) {
                    remotePhotoUrl = imageUploader.upload(localPhotoPath).getOrThrow()
                }
                remote.upsert(entry.copy(remotePhotoUrl = remotePhotoUrl, syncStatus = SyncStatus.SYNCED)).getOrThrow()
            }.isSuccess
        } ?: false
        // The photo URL is kept even on failure, so a retry does not upload the same image again.
        val rowsUpdated = dao.updateSyncResult(
            id = entry.id,
            updatedAt = entry.updatedAt,
            status = if (synced) SyncStatus.SYNCED else SyncStatus.FAILED,
            remotePhotoUrl = remotePhotoUrl
        )
        return synced to rowsUpdated
    }

    private fun entryNotFound(entryId: String) = NoSuchElementException("No travel log entry with id '$entryId'")

    private companion object {
        const val SYNC_TIMEOUT_MILLIS = 30_000L
        const val DEFAULT_AUTHOR_NAME = "WanderVox traveler"
    }
}

class SyncIncompleteException(val failed: Int, val total: Int) :
    IllegalStateException("$failed of $total travel log entries could not be synced")
