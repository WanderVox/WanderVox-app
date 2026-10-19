package com.wandervox.core.data.location

import com.wandervox.core.data.location.LocationRepositoryImpl.Companion.selectProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocationProviderSelectionTest {

    @Test
    fun prefersFused() {
        assertEquals("fused", selectProvider(listOf("gps", "network", "fused", "passive"), hasFinePermission = true))
    }

    @Test
    fun prefersNetworkOverGps() {
        assertEquals("network", selectProvider(listOf("gps", "network", "passive"), hasFinePermission = true))
    }

    @Test
    fun usesGpsWithFinePermission() {
        assertEquals("gps", selectProvider(listOf("gps", "passive"), hasFinePermission = true))
    }

    @Test
    fun skipsGpsWithCoarseOnly() {
        assertNull(selectProvider(listOf("gps", "passive"), hasFinePermission = false))
    }

    @Test
    fun neverSelectsPassive() {
        assertNull(selectProvider(listOf("passive"), hasFinePermission = true))
    }

    @Test
    fun allDisabled_returnsNull() {
        assertNull(selectProvider(emptyList(), hasFinePermission = true))
    }
}
