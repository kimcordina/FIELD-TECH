package com.example.fieldtechv20kc.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Helper class for getting device's current location with accuracy checks.
 */
object LocationHelper {
    private const val TAG = "LocationHelper"
    private const val TIMEOUT_MS = 5000L // 5 seconds
    const val GOOD_ACCURACY_METERS = 100f // Warn if accuracy > 100m

    data class LocationResult(
        val location: Location?,
        val hasPermission: Boolean,
        val accuracyWarning: String? = null,
        val error: String? = null
    )

    /**
     * Get current location with timeout and accuracy check.
     * 
     * @param context Android context
     * @param timeoutMs Maximum time to wait for location (default 5 seconds)
     * @return LocationResult with location data, permissions status, and warnings
     */
    suspend fun getCurrentLocation(
        context: Context,
        timeoutMs: Long = TIMEOUT_MS
    ): LocationResult {
        // Check permissions
        if (!hasLocationPermission(context)) {
            FTLog.w(TAG, "Location permission not granted")
            return LocationResult(
                location = null,
                hasPermission = false,
                error = "Location permission not granted. Please enable location access in Settings."
            )
        }

        try {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

            // Try to get last known location first (faster)
            val lastLocation = getLastKnownLocation(fusedLocationClient)
            if (lastLocation != null && isLocationRecent(lastLocation)) {
                val warning = checkAccuracy(lastLocation)
                FTLog.i(TAG, "Using last known location: ${lastLocation.latitude}, ${lastLocation.longitude}, accuracy: ${lastLocation.accuracy}m")
                return LocationResult(
                    location = lastLocation,
                    hasPermission = true,
                    accuracyWarning = warning
                )
            }

            // Get fresh location with timeout
            val freshLocation = withTimeoutOrNull(timeoutMs) {
                getCurrentLocationFresh(fusedLocationClient)
            }

            if (freshLocation != null) {
                val warning = checkAccuracy(freshLocation)
                FTLog.i(TAG, "Got fresh location: ${freshLocation.latitude}, ${freshLocation.longitude}, accuracy: ${freshLocation.accuracy}m")
                return LocationResult(
                    location = freshLocation,
                    hasPermission = true,
                    accuracyWarning = warning
                )
            } else {
                FTLog.w(TAG, "Location request timed out after ${timeoutMs}ms")
                return LocationResult(
                    location = lastLocation, // Return last known even if stale
                    hasPermission = true,
                    accuracyWarning = if (lastLocation != null) "Using stale location (couldn't get fresh GPS fix)" else null,
                    error = "Could not get GPS fix within ${timeoutMs / 1000} seconds. Make sure you're outdoors with clear sky view."
                )
            }
        } catch (e: SecurityException) {
            FTLog.e(TAG, "Security exception getting location: ${e.message}", e)
            return LocationResult(
                location = null,
                hasPermission = false,
                error = "Location permission denied"
            )
        } catch (e: Exception) {
            FTLog.e(TAG, "Error getting location: ${e.message}", e)
            return LocationResult(
                location = null,
                hasPermission = true,
                error = "Error getting location: ${e.message}"
            )
        }
    }

    /**
     * Check if app has location permissions
     */
    fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Get last known location (fast, may be stale)
     */
    private suspend fun getLastKnownLocation(fusedLocationClient: FusedLocationProviderClient): Location? {
        return try {
            suspendCancellableCoroutine { continuation ->
                fusedLocationClient.lastLocation
                    .addOnSuccessListener { location ->
                        continuation.resume(location)
                    }
                    .addOnFailureListener {
                        continuation.resume(null)
                    }
            }
        } catch (e: SecurityException) {
            null
        }
    }

    /**
     * Get fresh location (slower, more accurate)
     */
    private suspend fun getCurrentLocationFresh(fusedLocationClient: FusedLocationProviderClient): Location? {
        return try {
            suspendCancellableCoroutine { continuation ->
                val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
                    .setMaxUpdates(1)
                    .build()

                val callback = object : LocationCallback() {
                    override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
                        continuation.resume(result.lastLocation)
                    }
                }

                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    callback,
                    null
                )

                continuation.invokeOnCancellation {
                    fusedLocationClient.removeLocationUpdates(callback)
                }
            }
        } catch (e: SecurityException) {
            null
        }
    }

    /**
     * Check if location is recent (within 5 minutes)
     */
    private fun isLocationRecent(location: Location): Boolean {
        val ageMs = System.currentTimeMillis() - location.time
        return ageMs < 5 * 60 * 1000 // 5 minutes
    }

    /**
     * Check location accuracy and return warning if poor
     */
    private fun checkAccuracy(location: Location): String? {
        return if (location.hasAccuracy() && location.accuracy > GOOD_ACCURACY_METERS) {
            "GPS accuracy is ${location.accuracy.toInt()}m (not very precise). Consider moving to an open area for better accuracy."
        } else {
            null
        }
    }

    /**
     * Format location result for display
     */
    fun formatLocation(location: Location?): String {
        if (location == null) return "Unknown"
        return "${String.format("%.6f", location.latitude)}, ${String.format("%.6f", location.longitude)}"
    }
}

