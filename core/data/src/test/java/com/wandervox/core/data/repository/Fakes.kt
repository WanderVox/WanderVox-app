package com.wandervox.core.data.repository

import com.wandervox.core.database.dao.TravelLogDao
import com.wandervox.core.database.entity.TravelLogEntity
import com.wandervox.core.domain.model.SyncStatus
import com.wandervox.core.domain.model.TravelLogEntry
import com.wandervox.core.domain.model.User
import com.wandervox.core.domain.repository.AuthRepository
import com.wandervox.core.network.remote.image.ImageUploader
import com.wandervox.core.network.remote.log.LogRemoteDataSource
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

    override fun observeUserEntries(userId: String): Flow<List<TravelLogEntity>> =
        rows.map { all -> all.values.filter { it.userId == userId }.sortedByDescending { it.timestamp } }

    override fun observePublicEntries(): Flow<List<TravelLogEntity>> =
        rows.map { all -> all.values.filter { it.isPublic }.sortedByDescending { it.timestamp } }

    override fun getEntryById(id: String): Flow<TravelLogEntity?> = rows.map { it[id] }

    override suspend fun findById(id: String): TravelLogEntity? = rows.value[id]

    override suspend fun getEntriesBySyncStatus(statuses: List<SyncStatus>): List<TravelLogEntity> =
        rows.value.values.filter { it.syncStatus in statuses }.sortedBy { it.updatedAt }

    override suspend fun upsert(entry: TravelLogEntity) = rows.update { it + (entry.id to entry) }

    override suspend fun upsertAll(entries: List<TravelLogEntity>) = rows.update { it + entries.associateBy { e -> e.id } }

    override suspend fun delete(id: String) = rows.update { it - id }

    override suspend fun updateSyncStatus(id: String, status: SyncStatus) =
        rows.update { all -> all[id]?.let { all + (id to it.copy(syncStatus = status)) } ?: all }

    override suspend fun updateSyncResult(id: String, updatedAt: Long, status: SyncStatus, remotePhotoUrl: String?): Int {
        var updated = 0
        rows.update { all ->
            val row = all[id]
            if (row == null || row.updatedAt != updatedAt) {
                all
            } else {
                updated = 1
                all + (id to row.copy(syncStatus = status, remotePhotoUrl = remotePhotoUrl))
            }
        }
        return updated
    }
}

class FakeLogRemoteDataSource : LogRemoteDataSource {
    val upserted = mutableListOf<TravelLogEntry>()
    val deleted = mutableListOf<String>()
    val likeCalls = mutableListOf<Triple<String, String, Boolean>>()

    /** Runs before each upsert; suspend here to simulate a slow network, throw/return failure to fail. */
    var onUpsert: suspend (TravelLogEntry) -> Result<Unit> = { Result.success(Unit) }
    var likeResult: Result<Unit> = Result.success(Unit)
    var publicEntries: Flow<List<TravelLogEntry>> = flowOf(emptyList())

    override suspend fun upsert(entry: TravelLogEntry): Result<Unit> =
        onUpsert(entry).onSuccess { upserted += entry }

    override suspend fun delete(entryId: String): Result<Unit> {
        deleted += entryId
        return Result.success(Unit)
    }

    override fun observeUserEntries(userId: String): Flow<List<TravelLogEntry>> = flowOf(emptyList())

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

    override suspend fun upload(localPath: String): Result<String> {
        uploads += localPath
        return result
    }
}

class FakeAuthRepository(user: User?) : AuthRepository {
    override val currentUser = MutableStateFlow(user)
    override suspend fun signIn(email: String, password: String): Result<Unit> = Result.success(Unit)
    override suspend fun signUp(email: String, password: String): Result<Unit> = Result.success(Unit)
    override suspend fun signOut(): Result<Unit> = Result.success(Unit)
}
