package com.wandervox.core.domain.repository

import com.wandervox.core.domain.model.TravelLogEntry
import kotlinx.coroutines.flow.Flow

interface LogRepository {
    fun getUserEntries(userId: String): Flow<List<TravelLogEntry>>
    fun getCommunityEntries(): Flow<List<TravelLogEntry>>
    suspend fun saveEntry(entry: TravelLogEntry): Result<Unit>
    suspend fun toggleLike(entryId: String, userId: String): Result<Unit>
    suspend fun deleteEntry(entryId: String): Result<Unit>
}