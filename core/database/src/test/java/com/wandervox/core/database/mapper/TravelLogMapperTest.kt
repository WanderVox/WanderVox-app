package com.wandervox.core.database.mapper

import com.wandervox.core.domain.model.Coordinates
import com.wandervox.core.domain.model.LogCategory
import com.wandervox.core.domain.model.SyncStatus
import com.wandervox.core.domain.model.TravelLogEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TravelLogMapperTest {

    private val fullEntry = TravelLogEntry(
        id = "entry-1",
        userId = "uid-1",
        originalText = "¿Dónde está la estación?",
        translatedText = "Where is the station?",
        sourceLanguage = "ES",
        targetLanguage = "EN",
        timestamp = 1_000L,
        coordinates = Coordinates(-34.6037, -58.3816),
        localPhotoPath = "/data/photo.jpg",
        remotePhotoUrl = "https://res.cloudinary.com/x/photo.jpg",
        category = LogCategory.TRANSPORT,
        notes = "Line A",
        isPublic = true,
        likes = listOf("uid-2", "uid-3"),
        authorName = "lautaro@example.com",
        updatedAt = 2_000L,
        syncStatus = SyncStatus.SYNCED
    )

    @Test
    fun roundTrip_preservesEveryField() {
        assertEquals(fullEntry, fullEntry.asEntity().asDomain())
    }

    @Test
    fun asEntity_flattensCoordinates() {
        val entity = fullEntry.asEntity()
        assertEquals(-34.6037, entity.latitude!!, 0.0)
        assertEquals(-58.3816, entity.longitude!!, 0.0)
    }

    @Test
    fun noCoordinates_mapsToNullColumns_andBack() {
        val entity = fullEntry.copy(coordinates = null).asEntity()
        assertNull(entity.latitude)
        assertNull(entity.longitude)
        assertNull(entity.asDomain().coordinates)
    }

    @Test
    fun asDomain_partialCoordinates_areTreatedAsNoLocation() {
        val entity = fullEntry.asEntity().copy(longitude = null)
        assertNull(entity.asDomain().coordinates)
    }
}
