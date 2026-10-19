package com.wandervox.core.database.converter

import com.wandervox.core.domain.model.LogCategory
import com.wandervox.core.domain.model.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun likes_roundTrip() {
        val likes = listOf("uidA123", "uidB456", "uidC789")
        assertEquals(likes, converters.toLikes(converters.fromLikes(likes)))
    }

    @Test
    fun likes_emptyList_roundTripsToEmptyList() {
        assertEquals("", converters.fromLikes(emptyList()))
        assertEquals(emptyList<String>(), converters.toLikes(""))
    }

    @Test
    fun likes_singleElement_roundTrip() {
        assertEquals(listOf("only"), converters.toLikes(converters.fromLikes(listOf("only"))))
    }

    @Test
    fun category_roundTrip_allValues() {
        LogCategory.entries.forEach {
            assertEquals(it, converters.toCategory(converters.fromCategory(it)))
        }
    }

    @Test
    fun category_unknownValue_fallsBackToGeneral() {
        assertEquals(LogCategory.GENERAL, converters.toCategory("NOT_A_CATEGORY"))
    }

    @Test
    fun syncStatus_roundTrip_allValues() {
        SyncStatus.entries.forEach {
            assertEquals(it, converters.toSyncStatus(converters.fromSyncStatus(it)))
        }
    }

    @Test
    fun syncStatus_unknownValue_fallsBackToPending() {
        assertEquals(SyncStatus.PENDING, converters.toSyncStatus("ARCHIVED"))
    }
}
