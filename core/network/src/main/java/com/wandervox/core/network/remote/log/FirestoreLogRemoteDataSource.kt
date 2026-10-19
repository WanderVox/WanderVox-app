package com.wandervox.core.network.remote.log

import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.wandervox.core.domain.model.TravelLogEntry
import com.wandervox.core.network.util.awaitResult
import com.wandervox.core.network.util.runSuspendCatching
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeoutException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [LogRemoteDataSource] on Cloud Firestore. Offline persistence is enabled where [FirebaseFirestore] is
 * provided (`NetworkRemoteModule`), so listeners serve cached data and writes queue while offline.
 */
@Singleton
class FirestoreLogRemoteDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
) : LogRemoteDataSource {

    private val collection get() = firestore.collection(LogFields.COLLECTION)

    override suspend fun upsert(entry: TravelLogEntry): Result<Unit> = runSuspendCatching {
        require(entry.id.isNotBlank()) { "Cannot upload an entry without id" }
        collection.document(entry.id)
            .set(LogEntryFirestoreMapper.toFirestore(entry), SetOptions.merge())
            .awaitAcknowledged()
    }

    override suspend fun delete(entryId: String): Result<Unit> = runSuspendCatching {
        collection.document(entryId).delete().awaitAcknowledged()
    }

    override suspend fun setLiked(entryId: String, userId: String, liked: Boolean): Result<Unit> =
        runSuspendCatching {
            val change = if (liked) FieldValue.arrayUnion(userId) else FieldValue.arrayRemove(userId)
            collection.document(entryId).update(LogFields.LIKES, change).awaitAcknowledged()
        }

    override fun observeUserEntries(userId: String): Flow<List<TravelLogEntry>> =
        collection.whereEqualTo(LogFields.USER_ID, userId).observeEntries()

    override fun observePublicEntries(): Flow<List<TravelLogEntry>> =
        collection.whereEqualTo(LogFields.IS_PUBLIC, true).observeEntries()

    /**
     * Sorted on the client: a server-side `orderBy` next to the equality filter would need a composite
     * index to be deployed. `MetadataChanges.INCLUDE` re-emits when a pending write is acknowledged, so
     * `syncStatus` moves from PENDING to SYNCED.
     */
    private fun Query.observeEntries(): Flow<List<TravelLogEntry>> = callbackFlow {
        val registration = addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (snapshot == null) return@addSnapshotListener
            val entries = snapshot.documents.mapNotNull { document ->
                document.data?.let { data ->
                    LogEntryFirestoreMapper.fromFirestore(document.id, data, document.metadata.hasPendingWrites())
                }
            }
            trySend(entries.sortedByDescending { it.timestamp })
        }
        awaitClose { registration.remove() }
    }

    /**
     * Firestore applies the write to the local cache immediately but completes the task only when the
     * server confirms it, which never happens offline. Give up waiting after [WRITE_ACK_TIMEOUT_MILLIS];
     * the write stays queued and is delivered when the connection returns.
     */
    private suspend fun Task<Void>.awaitAcknowledged() {
        // A Task<Void> completes with null, so map success to Unit to tell it apart from the timeout.
        withTimeoutOrNull(WRITE_ACK_TIMEOUT_MILLIS) { awaitResult(); Unit }
            ?: throw TimeoutException("Write queued offline; not acknowledged by the server yet")
    }

    private companion object {
        const val WRITE_ACK_TIMEOUT_MILLIS = 15_000L
    }
}
