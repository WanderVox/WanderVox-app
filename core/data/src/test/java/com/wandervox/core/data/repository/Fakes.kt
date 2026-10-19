package com.wandervox.core.data.repository

import com.wandervox.core.database.dao.TravelLogDao
import com.wandervox.core.database.entity.TravelLogEntity
import com.wandervox.core.domain.model.LogCategory
import com.wandervox.core.domain.model.SyncStatus
import com.wandervox.core.domain.model.TravelLogEntry
import com.wandervox.core.domain.model.User
import com.wandervox.core.domain.repository.AuthRepository
import com.wandervox.core.network.remote.image.ImageUploader
import com.wandervox.core.network.remote.log.LogRemoteDataSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory [TravelLogDao] with the same semantics as the Room queries. */
class FakeTravelLogDao : TravelLogDao {
    val rows = MutableStateFlow<Map<String, TravelLogEntity>>(emptyMap())

    fun row(id: String): TravelLogEntity? = rows.value[id]

    private val TravelLogEntity.isVisible get() = syncStatus != SyncStatus.PENDING_DELETE

    override fun observeUserEntries(userId: String): Flow<List<TravelLogEntity>> =
        rows.map { all -> all.values.filter { it.userId == userId && it.isVisible }.sortedByDescending { it.timestamp } }

    override fun observePublicEntries(): Flow<List<TravelLogEntity>> =
        rows.map { all -> all.values.filter { it.isPublic && it.isVisible }.sortedByDescending { it.timestamp } }

    override fun getEntryById(id: String): Flow<TravelLogEntity?> = rows.map { all -> all[id]?.takeIf { it.isVisible } }

    override suspend fun findById(id: String): TravelLogEntity? = rows.value[id]

    override suspend fun getEntriesByIds(ids: List<String>): List<TravelLogEntity> = ids.mapNotNull { rows.value[it] }

    override suspend fun getEntriesBySyncStatus(statuses: List<SyncStatus>): List<TravelLogEntity> =
        rows.value.values.filter { it.syncStatus in statuses }.sortedBy { it.updatedAt }

    override suspend fun upsert(entry: TravelLogEntity) = rows.update { it + (entry.id to entry) }

    override suspend fun upsertAll(entries: List<TravelLogEntity>) = rows.update { it + entries.associateBy { e -> e.id } }

    override suspend fun insertIfAbsent(entry: TravelLogEntity): Long {
        var inserted = -1L
        rows.update { all -> if (entry.id in all) all else all + (entry.id to entry).also { inserted = 1L } }
        return inserted
    }

    override suspend fun delete(id: String) = rows.update { it - id }

    override suspend fun deleteTombstone(id: String, updatedAt: Long): Int =
        compareAndSet(id, { it.updatedAt == updatedAt && it.syncStatus == SyncStatus.PENDING_DELETE }) { null }

    override suspend fun updateSyncStatus(id: String, status: SyncStatus) =
        rows.update { all -> all[id]?.let { all + (id to it.copy(syncStatus = status)) } ?: all }

    override suspend fun updateSyncResult(
        id: String,
        updatedAt: Long,
        status: SyncStatus,
        remotePhotoUrl: String?,
        localPhotoPath: String?,
    ): Int = compareAndSet(id, { it.updatedAt == updatedAt }) {
        it.copy(syncStatus = status, remotePhotoUrl = remotePhotoUrl, localPhotoPath = localPhotoPath)
    }

    override suspend fun updateFromRemote(
        id: String,
        expectedUpdatedAt: Long,
        userId: String,
        originalText: String,
        translatedText: String,
        sourceLanguage: String,
        targetLanguage: String,
        timestamp: Long,
        latitude: Double?,
        longitude: Double?,
        remotePhotoUrl: String?,
        category: LogCategory,
        notes: String,
        isPublic: Boolean,
        likes: List<String>,
        authorName: String?,
        updatedAt: Long,
    ): Int = compareAndSet(id, { it.updatedAt == expectedUpdatedAt && it.syncStatus == SyncStatus.SYNCED }) {
        it.copy(
            userId = userId, originalText = originalText, translatedText = translatedText,
            sourceLanguage = sourceLanguage, targetLanguage = targetLanguage, timestamp = timestamp,
            latitude = latitude, longitude = longitude, remotePhotoUrl = remotePhotoUrl, category = category,
            notes = notes, isPublic = isPublic, likes = likes, authorName = authorName, updatedAt = updatedAt,
        )
    }

    /** Atomically replaces (or removes, when [transform] returns null) the row if [condition] holds. */
    private fun compareAndSet(
        id: String,
        condition: (TravelLogEntity) -> Boolean,
        transform: (TravelLogEntity) -> TravelLogEntity?,
    ): Int {
        var changed = 0
        rows.update { all ->
            val row = all[id]?.takeIf(condition) ?: return@update all
            changed = 1
            transform(row)?.let { all + (id to it) } ?: (all - id)
        }
        return changed
    }
}

class FakeLogRemoteDataSource : LogRemoteDataSource {
    val upserted = mutableListOf<TravelLogEntry>()
    val deleted = mutableListOf<String>()
    val likeCalls = mutableListOf<Triple<String, String, Boolean>>()

    /** Runs before each upsert; suspend here to simulate a slow network, throw/return failure to fail. */
    var onUpsert: suspend (TravelLogEntry) -> Result<Unit> = { Result.success(Unit) }
    var likeResult: Result<Unit> = Result.success(Unit)
    var deleteResult: Result<Unit> = Result.success(Unit)
    var publicEntries: Flow<List<TravelLogEntry>> = flowOf(emptyList())
    /** What `observeUserEntries` emits; push new lists to simulate remote changes. */
    val userEntries = MutableStateFlow<List<TravelLogEntry>>(emptyList())

    /** Successful remote writes in order, e.g. "upsert:e1", "delete:e1". */
    val operations = mutableListOf<String>()

    override suspend fun upsert(entry: TravelLogEntry): Result<Unit> =
        onUpsert(entry).onSuccess {
            upserted += entry
            operations += "upsert:${entry.id}"
        }

    override suspend fun delete(entryId: String): Result<Unit> =
        deleteResult.onSuccess {
            deleted += entryId
            operations += "delete:$entryId"
        }

    override fun observeUserEntries(userId: String): Flow<List<TravelLogEntry>> =
        userEntries.map { all -> all.filter { it.userId == userId } }

    override fun observePublicEntries(): Flow<List<TravelLogEntry>> = publicEntries

    override suspend fun setLiked(entryId: String, userId: String, liked: Boolean): Result<Unit> {
        likeCalls += Triple(entryId, userId, liked)
        return likeResult
    }

    fun failObserving() {
        publicEntries = flow { throw IllegalStateException("offline") }
    }
}

class FakeImageUploader : ImageUploader {
    val uploads = mutableListOf<String>()
    var result: Result<String> = Result.success("https://cdn.example/photo.jpg")
    /** Simulated upload duration (virtual time under runTest). */
    var delayMillis = 0L

    override suspend fun upload(localPath: String): Result<String> {
        uploads += localPath
        if (delayMillis > 0) delay(delayMillis)
        return result
    }
}

class FakeAuthRepository(user: User?) : AuthRepository {
    override val currentUser = MutableStateFlow(user)
    override suspend fun signIn(email: String, password: String): Result<Unit> = Result.success(Unit)
    override suspend fun signUp(email: String, password: String): Result<Unit> = Result.success(Unit)
    override suspend fun signOut(): Result<Unit> = Result.success(Unit)
}
