package com.wandervox.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.wandervox.core.database.entity.TravelLogEntity
import com.wandervox.core.domain.model.LogCategory
import com.wandervox.core.domain.model.SyncStatus
import kotlinx.coroutines.flow.Flow

/**
 * Observed queries hide tombstones (`syncStatus = PENDING_DELETE`): to the UI a deleted entry is gone
 * at once, while the row stays until the remote delete is confirmed. The one-shot reads used by the
 * sync engine ([findById], [getEntriesByIds], [getEntriesBySyncStatus]) return them.
 */
@Dao
interface TravelLogDao {

    @Query(
        "SELECT * FROM travel_logs WHERE userId = :userId " +
            "AND syncStatus != '${TravelLogEntity.PENDING_DELETE}' ORDER BY timestamp DESC"
    )
    fun observeUserEntries(userId: String): Flow<List<TravelLogEntity>>

    @Query(
        "SELECT * FROM travel_logs WHERE isPublic = 1 " +
            "AND syncStatus != '${TravelLogEntity.PENDING_DELETE}' ORDER BY timestamp DESC"
    )
    fun observePublicEntries(): Flow<List<TravelLogEntity>>

    /** Emits `null` while the row does not exist, is deleted, or is a tombstone. */
    @Query("SELECT * FROM travel_logs WHERE id = :id AND syncStatus != '${TravelLogEntity.PENDING_DELETE}'")
    fun getEntryById(id: String): Flow<TravelLogEntity?>

    /** One-shot read for read-modify-write operations; includes tombstones. */
    @Query("SELECT * FROM travel_logs WHERE id = :id")
    suspend fun findById(id: String): TravelLogEntity?

    /** One-shot batch read for the remote merge; includes tombstones. Keep [ids] under 999 (SQLite limit). */
    @Query("SELECT * FROM travel_logs WHERE id IN (:ids)")
    suspend fun getEntriesByIds(ids: List<String>): List<TravelLogEntity>

    /** Entries in any of [statuses], oldest first, so retries keep the original order. */
    @Query("SELECT * FROM travel_logs WHERE syncStatus IN (:statuses) ORDER BY updatedAt ASC")
    suspend fun getEntriesBySyncStatus(statuses: List<SyncStatus>): List<TravelLogEntity>

    @Upsert
    suspend fun upsert(entry: TravelLogEntity)

    @Upsert
    suspend fun upsertAll(entries: List<TravelLogEntity>)

    /** Inserts a row pulled from the remote; a row created locally in the meantime wins. @return -1 if skipped. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entry: TravelLogEntity): Long

    @Query("DELETE FROM travel_logs WHERE id = :id")
    suspend fun delete(id: String)

    /**
     * Physically removes a tombstone once its remote delete is confirmed, unless the row changed
     * meanwhile (compare-and-set on [updatedAt]). @return the number of rows deleted.
     */
    @Query(
        "DELETE FROM travel_logs WHERE id = :id AND updatedAt = :updatedAt " +
            "AND syncStatus = '${TravelLogEntity.PENDING_DELETE}'"
    )
    suspend fun deleteTombstone(id: String, updatedAt: Long): Int

    @Query("UPDATE travel_logs SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: SyncStatus)

    /**
     * Records the outcome of a sync attempt, but only if the row was not edited meanwhile
     * (compare-and-set on [updatedAt]). A newer edit is left PENDING and syncs on its own.
     *
     * @return the number of rows updated: 0 when the row changed or was deleted during the sync.
     */
    @Query(
        "UPDATE travel_logs SET syncStatus = :status, remotePhotoUrl = :remotePhotoUrl, " +
            "localPhotoPath = :localPhotoPath WHERE id = :id AND updatedAt = :updatedAt"
    )
    suspend fun updateSyncResult(
        id: String,
        updatedAt: Long,
        status: SyncStatus,
        remotePhotoUrl: String?,
        localPhotoPath: String?,
    ): Int

    /**
     * Overwrites a SYNCED row with the remote version, atomically, only if it is still the version
     * that was compared ([expectedUpdatedAt]) and has no local changes. `localPhotoPath` and
     * `syncStatus` are not touched, so a cached photo survives the merge.
     *
     * @return 1 if applied, 0 if the row changed locally in the meantime (the local edit wins).
     */
    @Query(
        "UPDATE travel_logs SET userId = :userId, originalText = :originalText, " +
            "translatedText = :translatedText, sourceLanguage = :sourceLanguage, " +
            "targetLanguage = :targetLanguage, timestamp = :timestamp, latitude = :latitude, " +
            "longitude = :longitude, remotePhotoUrl = :remotePhotoUrl, category = :category, " +
            "notes = :notes, isPublic = :isPublic, likes = :likes, authorName = :authorName, " +
            "updatedAt = :updatedAt " +
            "WHERE id = :id AND updatedAt = :expectedUpdatedAt AND syncStatus = '${TravelLogEntity.SYNCED}'"
    )
    suspend fun updateFromRemote(
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
    ): Int
}
