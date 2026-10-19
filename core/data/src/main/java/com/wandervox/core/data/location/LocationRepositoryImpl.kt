package com.wandervox.core.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import com.wandervox.core.data.util.runSuspendCatching
import com.wandervox.core.domain.model.Coordinates
import com.wandervox.core.domain.repository.LocationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.coroutines.resume

/**
 * One-shot location from the platform [LocationManager] (no Google Play services dependency).
 *
 * Strategy: a recent last-known fix is returned immediately; otherwise a fresh fix is requested for
 * up to [CURRENT_FIX_TIMEOUT_MILLIS], falling back to the last-known fix of any age.
 */
class LocationRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : LocationRepository {

    private val locationManager: LocationManager? = context.getSystemService(LocationManager::class.java)

    override suspend fun getCurrentLocation(): Result<Coordinates> = runSuspendCatching {
        val hasFine = isGranted(Manifest.permission.ACCESS_FINE_LOCATION)
        if (!hasFine && !isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)) throw LocationPermissionException()

        val manager = locationManager ?: throw LocationUnavailableException("This device has no location service")
        val enabledProviders = manager.getProviders(true)
        val lastKnown = freshestLastKnownLocation(manager, enabledProviders)
        if (lastKnown != null && lastKnown.ageMillis() <= MAX_LAST_KNOWN_AGE_MILLIS) {
            return@runSuspendCatching lastKnown.toCoordinates()
        }

        val current = selectProvider(enabledProviders, hasFine)?.let { provider ->
            withTimeoutOrNull(CURRENT_FIX_TIMEOUT_MILLIS) { requestCurrentLocation(manager, provider) }
        }
        (current ?: lastKnown)?.toCoordinates()
            ?: throw LocationUnavailableException("No location fix available; location services may be off")
    }

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    // Only called after the permission check in getCurrentLocation.
    @SuppressLint("MissingPermission")
    private fun freshestLastKnownLocation(manager: LocationManager, providers: List<String>): Location? =
        providers
            .mapNotNull { provider ->
                // A provider can still refuse (e.g. GPS with only coarse permission); skip it.
                try {
                    manager.getLastKnownLocation(provider)
                } catch (_: SecurityException) {
                    null
                }
            }
            .maxByOrNull { it.elapsedRealtimeNanos }

    // Only called after the permission check in getCurrentLocation.
    @SuppressLint("MissingPermission")
    private suspend fun requestCurrentLocation(manager: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { continuation ->
            val cancellationSignal = CancellationSignal()
            continuation.invokeOnCancellation { cancellationSignal.cancel() }
            LocationManagerCompat.getCurrentLocation(
                manager,
                provider,
                cancellationSignal,
                ContextCompat.getMainExecutor(context)
            ) { location -> continuation.resume(location) }
        }

    private fun Location.ageMillis(): Long =
        (SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos) / NANOS_PER_MILLI

    private fun Location.toCoordinates() = Coordinates(latitude = latitude, longitude = longitude)

    internal companion object {
        const val MAX_LAST_KNOWN_AGE_MILLIS = 2 * 60 * 1000L
        const val CURRENT_FIX_TIMEOUT_MILLIS = 10_000L
        private const val NANOS_PER_MILLI = 1_000_000L

        /** `LocationManager.FUSED_PROVIDER` (API 31); the literal keeps it usable on minSdk 27. */
        private const val FUSED_PROVIDER = "fused"

        /**
         * Best provider for a quick one-shot fix: fused, then network (fast, works indoors), then
         * GPS (needs fine permission and can take long from a cold start). The passive provider
         * never produces a fix on its own, so it is never selected.
         */
        fun selectProvider(enabledProviders: List<String>, hasFinePermission: Boolean): String? = when {
            FUSED_PROVIDER in enabledProviders -> FUSED_PROVIDER
            LocationManager.NETWORK_PROVIDER in enabledProviders -> LocationManager.NETWORK_PROVIDER
            hasFinePermission && LocationManager.GPS_PROVIDER in enabledProviders -> LocationManager.GPS_PROVIDER
            else -> null
        }
    }
}

class LocationPermissionException : SecurityException("Location permission has not been granted")

class LocationUnavailableException(message: String) : IllegalStateException(message)
