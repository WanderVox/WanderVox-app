package com.wandervox.core.domain.repository

import com.wandervox.core.domain.model.Coordinates

interface LocationRepository {
    /** Fails when the location permission is missing or no fix is available. */
    suspend fun getCurrentLocation(): Result<Coordinates>
}
