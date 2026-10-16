package com.wandervox.core.data.repository

import com.wandervox.core.domain.model.TravelLogEntry
import com.wandervox.core.domain.repository.LogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TEMPORARY placeholder so the Hilt graph is complete while the persistent travel log
 * (Room + Firestore offline persistence) is being built. Entries live only in memory and are lost
 * when the process dies. Replace the binding in `DataModule` once the real repository exists.
 */
@Singleton
class InMemoryLogRepository @Inject constructor() : LogRepository {

    private val entries = MutableStateFlow<List<TravelLogEntry>>(emptyList())

    override fun getUserEntries(userId: String): Flow<List<TravelLogEntry>> =
        entries.map { all -> all.filter { it.userId == userId }.sortedByDescending { it.timestamp } }

    override fun getCommunityEntries(): Flow<List<TravelLogEntry>> =
        entries.map { all -> all.filter { it.isPublic }.sortedByDescending { it.timestamp } }

    override fun getEntry(entryId: String): Flow<TravelLogEntry?> =
        entries.map { all -> all.firstOrNull { it.id == entryId } }

    override suspend fun saveEntry(entry: TravelLogEntry): Result<Unit> {
        val storedEntry = if (entry.id.isBlank()) entry.copy(id = UUID.randomUUID().toString()) else entry
        entries.update { all -> all.filterNot { it.id == storedEntry.id } + storedEntry }
        return Result.success(Unit)
    }

    override suspend fun toggleLike(entryId: String, userId: String): Result<Unit> =
        updateExisting(entryId) { entry ->
            val likes = if (userId in entry.likes) entry.likes - userId else entry.likes + userId
            entry.copy(likes = likes)
        }

    override suspend fun deleteEntry(entryId: String): Result<Unit> {
        var found = false
        entries.update { all ->
            found = all.any { it.id == entryId }
            all.filterNot { it.id == entryId }
        }
        return if (found) Result.success(Unit) else Result.failure(entryNotFound(entryId))
    }

    // Nothing to upload: this placeholder has no remote store.
    override suspend fun syncPending(): Result<Unit> = Result.success(Unit)

    private fun updateExisting(entryId: String, transform: (TravelLogEntry) -> TravelLogEntry): Result<Unit> {
        var found = false
        entries.update { all ->
            found = all.any { it.id == entryId }
            all.map { if (it.id == entryId) transform(it) else it }
        }
        return if (found) Result.success(Unit) else Result.failure(entryNotFound(entryId))
    }

    private fun entryNotFound(entryId: String) = NoSuchElementException("No travel log entry with id '$entryId'")
}
