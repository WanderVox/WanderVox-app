package com.wandervox.core.network.remote.log

import com.wandervox.core.domain.model.Coordinates
import com.wandervox.core.domain.model.LogCategory
import com.wandervox.core.domain.model.SyncStatus
import com.wandervox.core.domain.model.TravelLogEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class LogEntryFirestoreMapperTest {

    private val entry = TravelLogEntry(
        id = "entry-1",
        userId = "user-1",
        originalText = "¿Dónde está el baño?",
        translatedText = "Where is the bathroom?",
        sourceLanguage = "ES",
        targetLanguage = "EN",
        timestamp = 1_700_000_000_000L,
        coordinates = Coordinates(-34.6, -58.4),
        localPhotoPath = "/data/user/0/app/files/log_photos/entry-1_x.jpg",
        remotePhotoUrl = "https://res.cloudinary.com/demo/image/upload/x.jpg",
        category = LogCategory.EMERGENCY,
        notes = "Museo",
        isPublic = true,
        likes = listOf("a", "b"),
        authorName = "Lucas",
        updatedAt = 1_700_000_500_000L,
        syncStatus = SyncStatus.FAILED,
    )

    @Test
    fun `round trip keeps every shared field`() {
        val data = LogEntryFirestoreMapper.toFirestore(entry) + (LogFields.LIKES to entry.likes)

        val restored = LogEntryFirestoreMapper.fromFirestore(entry.id, data, hasPendingWrites = false)

        assertEquals(entry.copy(localPhotoPath = null, syncStatus = SyncStatus.SYNCED), restored)
    }

    @Test
    fun `write map leaves out local-only fields and likes`() {
        val data = LogEntryFirestoreMapper.toFirestore(entry)

        assertFalse(data.containsKey("localPhotoPath"))
        assertFalse(data.containsKey("syncStatus"))
        assertFalse(data.containsKey("id"))
        // Likes only change through arrayUnion/arrayRemove, so an upsert can never drop someone's like.
        assertFalse(data.containsKey(LogFields.LIKES))
        assertEquals("EMERGENCY", data[LogFields.CATEGORY])
    }

    @Test
    fun `pending writes map to pending sync status`() {
        val restored = LogEntryFirestoreMapper.fromFirestore(
            "id", LogEntryFirestoreMapper.toFirestore(entry), hasPendingWrites = true,
        )

        assertEquals(SyncStatus.PENDING, restored.syncStatus)
    }

    @Test
    fun `missing and malformed fields fall back to defaults`() {
        val restored = LogEntryFirestoreMapper.fromFirestore(
            id = "id",
            data = mapOf(
                LogFields.TIMESTAMP to 42,
                LogFields.CATEGORY to "NOT_A_CATEGORY",
                LogFields.LATITUDE to 1.0,
                LogFields.LIKES to listOf("a", 7, null),
                LogFields.IS_PUBLIC to "yes",
            ),
            hasPendingWrites = false,
        )

        assertEquals(42L, restored.timestamp)
        assertEquals(42L, restored.updatedAt)
        assertEquals(LogCategory.GENERAL, restored.category)
        assertNull(restored.coordinates)
        assertEquals(listOf("a"), restored.likes)
        assertFalse(restored.isPublic)
        assertEquals("", restored.userId)
        assertNull(restored.authorName)
    }
}
