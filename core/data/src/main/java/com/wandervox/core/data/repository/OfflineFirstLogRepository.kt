package com.wandervox.core.data.repository

import com.wandervox.core.data.di.ApplicationScope
import com.wandervox.core.data.util.runSuspendCatching
import com.wandervox.core.database.dao.TravelLogDao
import com.wandervox.core.database.entity.TravelLogEntity
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
import java.io.FileNotFoundException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeoutException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room is the source of truth for the user's own entries; the remote store only holds the public ones.
 *
 * Upload on demand: a private entry stays on the device (no photo upload, no Firestore document). It is
 * uploaded when it becomes public, and its document is deleted when it becomes private again. So
 * [SyncStatus] means "the remote matches what this entry should be": a private entry that was never
 * shared is [SyncStatus.SYNCED] with nothing on the server.
 *
 * Writes that need the remote land in Room as [SyncStatus.PENDING] (or [SyncStatus.PENDING_DELETE] for
 * deletes) and return immediately. The remote side (photo upload, document upsert or delete) runs on the [ApplicationScope]
 * so the caller never waits on the network, which with Firestore offline persistence could otherwise
 * block until connectivity returns.
 */
@Singleton
class OfflineFirstLogRepository @Inject constructor(
    private val dao: TravelLogDao,
    private val remote: LogRemoteDataSource,
    private val imageUploader: ImageUploader,
    private val authRepository: AuthRepository,
    @ApplicationScope private val appScope: CoroutineScope
) : LogRepository {

    /** Entry ids currently being synced, so `saveEntry`, `deleteEntry` and `syncPending` never race on one. */
    private val inFlight: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Ids whose remote delete was confirmed this session; the remote merge never re-inserts them. */
    private val confirmedDeletes: MutableSet<String> = ConcurrentHashMap.newKeySet()

    // Tombstones are filtered out by the DAO's observed queries.
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
        // Public: upload. Private: only unpublish a document that may exist; otherwise stay local.
        val needsRemote = entry.isPublic || previous.mayHaveRemoteDocument()
        val saved = entry.copy(
            id = id,
            userId = user?.uid ?: entry.userId,
            authorName = if (user != null) user.email ?: DEFAULT_AUTHOR_NAME else entry.authorName,
            updatedAt = nextUpdatedAt(previous?.updatedAt),
            syncStatus = if (needsRemote) SyncStatus.PENDING else SyncStatus.SYNCED
        )
        dao.upsert(saved.asEntity())
        if (needsRemote) appScope.launch { runSuspendCatching { syncEntry(saved) } }
    }

    /**
     * A shared row, or one with remote work still pending (e.g. an unpublish that has not reached the
     * server). Rows synced under the old upload-everything policy may also have a private document;
     * those are left alone, since the Community query never returns them.
     */
    private fun TravelLogEntity?.mayHaveRemoteDocument(): Boolean =
        this != null && (isPublic || syncStatus != SyncStatus.SYNCED)

    override suspend fun toggleLike(entryId: String, userId: String): Result<Unit> = runSuspendCatching {
        val local = dao.findById(entryId)
        if (local?.syncStatus == SyncStatus.PENDING_DELETE) throw entryNotFound(entryId)
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

    /**
     * Soft delete: the row becomes a [SyncStatus.PENDING_DELETE] tombstone (hidden from every read) and
     * is removed only once the remote delete is confirmed, so an offline delete is never lost and a
     * later remote pull cannot resurrect the entry.
     */
    override suspend fun deleteEntry(entryId: String): Result<Unit> = runSuspendCatching {
        val local = dao.findById(entryId)
            ?.takeUnless { it.syncStatus == SyncStatus.PENDING_DELETE }
            ?: throw entryNotFound(entryId)
        val tombstone = local.copy(syncStatus = SyncStatus.PENDING_DELETE, updatedAt = nextUpdatedAt(local.updatedAt))
        dao.upsert(tombstone)
        appScope.launch { runSuspendCatching { syncEntry(tombstone.asDomain()) } }
    }

    /**
     * Retries every unsynced entry: uploads for public PENDING/FAILED rows, unpublishes for private ones,
     * remote deletes for PENDING_DELETE.
     */
    override suspend fun syncPending(): Result<Unit> = runSuspendCatching {
        val unsynced = dao.getEntriesBySyncStatus(
            listOf(SyncStatus.PENDING, SyncStatus.FAILED, SyncStatus.PENDING_DELETE)
        )
        val failures = unsynced.count { !syncEntry(it.asDomain()) }
        if (failures > 0) throw SyncIncompleteException(failures, unsynced.size)
    }

    /**
     * Mirrors [uid]'s remote entries into Room (download on a fresh install, edits from other devices)
     * until the calling coroutine is cancelled. Returns early if the remote listener fails (e.g.
     * permission denied); `LogSyncCoordinator` starts it again on the next sign-in.
     *
     * Remote deletions are not propagated: a listener served from an empty offline cache emits an
     * empty list, which must not wipe local data.
     */
    suspend fun startUserSync(uid: String) {
        remote.observeUserEntries(uid)
            .catch { /* Listener failed; local data stays as is. */ }
            .collect { remoteEntries -> mergeRemote(remoteEntries) }
    }

    /**
     * Applies remote entries to Room. Local wins whenever it has unsynced work: rows that are PENDING,
     * FAILED or PENDING_DELETE are never touched. Each write is an atomic compare-and-set, so an edit
     * made while merging is not overwritten either.
     */
    private suspend fun mergeRemote(remoteEntries: List<TravelLogEntry>) {
        val localById = remoteEntries.map { it.id }
            .chunked(MAX_SQL_VARIABLES)
            .flatMap { ids -> dao.getEntriesByIds(ids) }
            .associateBy { it.id }
        for (remoteEntry in remoteEntries) {
            if (remoteEntry.id in confirmedDeletes) continue
            val local = localById[remoteEntry.id]
            when {
                // New on this device. localPhotoPath is device-local, so a pulled entry has none.
                local == null -> dao.insertIfAbsent(
                    remoteEntry.copy(localPhotoPath = null, syncStatus = SyncStatus.SYNCED).asEntity()
                )
                local.syncStatus != SyncStatus.SYNCED -> Unit
                remoteEntry.isNewerThan(local.asDomain()) -> remoteEntry.overwrite(expectedUpdatedAt = local.updatedAt)
            }
        }
    }

    /**
     * Newer content, or the same version with changes to remote-owned fields: likes (written by other
     * users through `setLiked`, which does not bump `updatedAt`) and the uploaded photo URL.
     */
    private fun TravelLogEntry.isNewerThan(local: TravelLogEntry): Boolean =
        updatedAt > local.updatedAt ||
            (updatedAt == local.updatedAt && (likes != local.likes || remotePhotoUrl != local.remotePhotoUrl))

    /** Leaves the local `localPhotoPath` (cached image) and `syncStatus` untouched. */
    private suspend fun TravelLogEntry.overwrite(expectedUpdatedAt: Long) {
        dao.updateFromRemote(
            id = id,
            expectedUpdatedAt = expectedUpdatedAt,
            userId = userId,
            originalText = originalText,
            translatedText = translatedText,
            sourceLanguage = sourceLanguage,
            targetLanguage = targetLanguage,
            timestamp = timestamp,
            latitude = coordinates?.latitude,
            longitude = coordinates?.longitude,
            remotePhotoUrl = remotePhotoUrl,
            category = category,
            notes = notes,
            isPublic = isPublic,
            likes = likes,
            authorName = authorName,
            updatedAt = updatedAt
        )
    }

    /**
     * Pushes one entry to the remote according to its status, then records the outcome in Room.
     *
     * If the row changed while syncing (an edit or a delete), the newer version is synced right after:
     * its own call skipped the work because this one was in flight.
     *
     * @return `false` only when the attempt failed; an entry already being synced counts as success.
     */
    private suspend fun syncEntry(entry: TravelLogEntry): Boolean {
        if (!inFlight.add(entry.id)) return true
        val outcome = try {
            when {
                entry.syncStatus == SyncStatus.PENDING_DELETE -> deleteRemotely(entry)
                entry.isPublic -> uploadAndRecord(entry)
                else -> unpublish(entry)
            }
        } finally {
            inFlight.remove(entry.id)
        }
        if (outcome.superseded) {
            val newer = dao.findById(entry.id)
            if (newer != null && newer.syncStatus in RESYNC_STATUSES) return syncEntry(newer.asDomain())
        }
        return outcome.succeeded
    }

    /**
     * Uploads the photo (if not uploaded yet), then the document. Each step has its own timeout: a
     * photo on a slow mobile link needs far longer than a Firestore write.
     */
    private suspend fun uploadAndRecord(entry: TravelLogEntry): SyncOutcome {
        var remotePhotoUrl = entry.remotePhotoUrl
        var localPhotoPath = entry.localPhotoPath
        val synced = run {
            val pathToUpload = localPhotoPath
            if (pathToUpload != null && remotePhotoUrl == null) {
                val upload = withRemoteTimeout(UPLOAD_TIMEOUT_MILLIS) { imageUploader.upload(pathToUpload) }
                when {
                    upload.isSuccess -> remotePhotoUrl = upload.getOrThrow()
                    // The local copy is gone (cleared storage, deleted file): retrying can never succeed,
                    // so drop the path and sync the entry without its photo instead of failing forever.
                    upload.exceptionOrNull() is FileNotFoundException -> localPhotoPath = null
                    else -> return@run false
                }
            }
            val document = entry.copy(
                remotePhotoUrl = remotePhotoUrl,
                localPhotoPath = localPhotoPath,
                syncStatus = SyncStatus.SYNCED
            )
            withRemoteTimeout(REMOTE_WRITE_TIMEOUT_MILLIS) { remote.upsert(document) }.isSuccess
        }
        // The photo URL is kept even on failure, so a retry does not upload the same image again; a
        // dropped (missing) photo path is persisted too, so a retry does not try it again either.
        val rowsUpdated = dao.updateSyncResult(
            id = entry.id,
            updatedAt = entry.updatedAt,
            status = if (synced) SyncStatus.SYNCED else SyncStatus.FAILED,
            remotePhotoUrl = remotePhotoUrl,
            localPhotoPath = localPhotoPath
        )
        return SyncOutcome(succeeded = synced, superseded = rowsUpdated == 0)
    }

    /**
     * Removes a now-private entry from the server (and so from the Community feed); the row stays.
     * Idempotent: deleting a document that does not exist succeeds. The Cloudinary photo is kept and its
     * URL too, so sharing the entry again does not upload the same image twice.
     */
    private suspend fun unpublish(entry: TravelLogEntry): SyncOutcome {
        val deleted = withRemoteTimeout(REMOTE_WRITE_TIMEOUT_MILLIS) { remote.delete(entry.id) }.isSuccess
        val rowsUpdated = dao.updateSyncResult(
            id = entry.id,
            updatedAt = entry.updatedAt,
            status = if (deleted) SyncStatus.SYNCED else SyncStatus.FAILED,
            remotePhotoUrl = entry.remotePhotoUrl,
            localPhotoPath = entry.localPhotoPath
        )
        return SyncOutcome(succeeded = deleted, superseded = rowsUpdated == 0)
    }

    /** Deletes the document remotely; the tombstone is removed only once that is confirmed. */
    private suspend fun deleteRemotely(entry: TravelLogEntry): SyncOutcome {
        val deleted = withRemoteTimeout(REMOTE_WRITE_TIMEOUT_MILLIS) { remote.delete(entry.id) }.isSuccess
        // On failure the tombstone simply stays for the next syncPending(); nothing to record.
        if (!deleted) return SyncOutcome(succeeded = false, superseded = false)
        val rowsDeleted = dao.deleteTombstone(entry.id, entry.updatedAt)
        // A remote snapshot taken before the delete may still be merging; it must not re-insert the row.
        if (rowsDeleted > 0) confirmedDeletes += entry.id
        return SyncOutcome(succeeded = true, superseded = rowsDeleted == 0)
    }

    /**
     * Runs a remote call, turning a thrown exception or a timeout into a failed [Result].
     * withTimeoutOrNull sits outside runSuspendCatching: the timeout is a CancellationException,
     * which runSuspendCatching rethrows on purpose.
     */
    private suspend fun <T> withRemoteTimeout(timeoutMillis: Long, call: suspend () -> Result<T>): Result<T> =
        withTimeoutOrNull(timeoutMillis) { runSuspendCatching { call().getOrThrow() } }
            ?: Result.failure(TimeoutException("No remote response within $timeoutMillis ms"))

    /** Strictly increasing per entry, so every compare-and-set on `updatedAt` detects every change. */
    private fun nextUpdatedAt(previous: Long?): Long = maxOf(System.currentTimeMillis(), (previous ?: 0L) + 1)

    private fun entryNotFound(entryId: String) = NoSuchElementException("No travel log entry with id '$entryId'")

    /** [superseded]: the row changed while syncing, so the outcome was not recorded. */
    private data class SyncOutcome(val succeeded: Boolean, val superseded: Boolean)

    private companion object {
        /** Photo upload (Cloudinary) on a slow mobile link. */
        const val UPLOAD_TIMEOUT_MILLIS = 120_000L
        /** Firestore write; a backstop: the data source already gives up after its own ack timeout. */
        const val REMOTE_WRITE_TIMEOUT_MILLIS = 30_000L
        const val DEFAULT_AUTHOR_NAME = "WanderVox traveler"
        val RESYNC_STATUSES = setOf(SyncStatus.PENDING, SyncStatus.PENDING_DELETE)
        /** Below SQLite's 999 bound-variable limit on API < 30. */
        const val MAX_SQL_VARIABLES = 500
    }
}

class SyncIncompleteException(val failed: Int, val total: Int) :
    IllegalStateException("$failed of $total travel log entries could not be synced")
