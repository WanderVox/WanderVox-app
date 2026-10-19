package com.wandervox.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.wandervox.core.database.entity.TravelLogEntity
import com.wandervox.core.domain.model.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface TravelLogDao {

    @Query("SELECT * FROM travel_logs WHERE userId = :userId ORDER BY timestamp DESC")
    fun observeUserEntries(userId: String): Flow<List<TravelLogEntity>>

    @Query("SELECT * FROM travel_logs WHERE isPublic = 1 ORDER BY timestamp DESC")
    fun observePublicEntries(): Flow<List<TravelLogEntity>>

    /** Emits `null` while the row does not exist (or after it is deleted). */
    @Query("SELECT * FROM travel_logs WHERE id = :id")
    fun getEntryById(id: String): Flow<TravelLogEntity?>

    /** One-shot read for read-modify-write operations (e.g. toggling a like). */
    @Query("SELECT * FROM travel_logs WHERE id = :id")
    suspend fun findById(id: String): TravelLogEntity?

    /** Entries in any of [statuses], oldest first, so retries keep the original order. */
    @Query("SELECT * FROM travel_logs WHERE syncStatus IN (:statuses) ORDER BY updatedAt ASC")
    suspend fun getEntriesBySyncStatus(statuses: List<SyncStatus>): List<TravelLogEntity>

    @Upsert
    suspend fun upsert(entry: TravelLogEntity)

    @Upsert
    suspend fun upsertAll(entries: List<TravelLogEntity>)

    @Query("DELETE FROM travel_logs WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE travel_logs SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: SyncStatus)

    /**
     * Records the outcome of a sync attempt, but only if the row was not edited meanwhile
     * (compare-and-set on [updatedAt]). A newer edit is left PENDING and syncs on its own.
     *
     * @return the number of rows updated: 0 when the row changed or was deleted during the sync.
     */
    @Query(
        "UPDATE travel_logs SET syncStatus = :status, remotePhotoUrl = :remotePhotoUrl " +
            "WHERE id = :id AND updatedAt = :updatedAt"
    )
    suspend fun updateSyncResult(id: String, updatedAt: Long, status: SyncStatus, remotePhotoUrl: String?): Int
}
