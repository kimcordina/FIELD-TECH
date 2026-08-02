package com.example.fieldtechv20kc.utils

import android.content.Context
import android.content.Intent
import android.location.Location
import android.net.Uri
import android.widget.Toast
import com.example.fieldtechv20kc.data.model.RouteStop
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Helper for building Google Maps Directions URLs and launching navigation.
 */
object GoogleMapsHelper {
    private const val TAG = "GoogleMapsHelper"
    private const val MAX_WAYPOINTS = 9 // Google Maps allows origin + 9 waypoints + destination = 11 total

    /**
     * Build Google Maps Directions URL from current location to route stops.
     * 
     * @param currentLocation Current device location (origin)
     * @param stops List of route stops (ordered)
     * @return Directions URL or null if invalid
     */
    fun buildDirectionsUrl(
        currentLocation: Location?,
        stops: List<RouteStop>
    ): String? {
        if (stops.isEmpty()) {
            FTLog.w(TAG, "❌ No stops provided for directions")
            return null
        }

        FTLog.i(TAG, "🔨 Building directions URL for ${stops.size} stops")

        // Origin: current location or "Current Location" string
        val origin = if (currentLocation != null) {
            "${currentLocation.latitude},${currentLocation.longitude}"
        } else {
            "Current+Location"
        }
        FTLog.i(TAG, "  📍 Origin: $origin")

        // Destination: last stop
        val lastStop = stops.last()
        FTLog.i(TAG, "  🏁 Last stop: ${lastStop.clientName} @ ${lastStop.latitude},${lastStop.longitude}")
        
        val destination = if (lastStop.latitude != null && lastStop.longitude != null) {
            "${lastStop.latitude},${lastStop.longitude}"
        } else if (!lastStop.address.isNullOrBlank()) {
            URLEncoder.encode(lastStop.address, StandardCharsets.UTF_8.toString())
        } else {
            URLEncoder.encode(lastStop.clientName, StandardCharsets.UTF_8.toString())
        }
        FTLog.i(TAG, "  🏁 Destination param: $destination")

        // Waypoints: all stops except the last one
        val waypoints = if (stops.size > 1) {
            val waypointStops = stops.dropLast(1)
            FTLog.i(TAG, "  🚩 Processing ${waypointStops.size} waypoint(s)")
            
            waypointStops.mapIndexedNotNull { index, stop ->
                FTLog.i(TAG, "    Waypoint ${index + 1}: ${stop.clientName} @ ${stop.latitude},${stop.longitude}")
                when {
                    stop.latitude != null && stop.longitude != null -> {
                        "${stop.latitude},${stop.longitude}"
                    }
                    !stop.address.isNullOrBlank() -> {
                        URLEncoder.encode(stop.address, StandardCharsets.UTF_8.toString())
                    }
                    else -> {
                        URLEncoder.encode(stop.clientName, StandardCharsets.UTF_8.toString())
                    }
                }
            }.joinToString("|")
        } else {
            ""
        }
        
        if (waypoints.isNotEmpty()) {
            FTLog.i(TAG, "  🚩 Waypoints string: $waypoints")
        } else {
            FTLog.i(TAG, "  🚩 No waypoints (direct route)")
        }

        // Build URL
        val url = buildString {
            append("https://www.google.com/maps/dir/?api=1")
            append("&origin=").append(origin)
            append("&destination=").append(destination)
            if (waypoints.isNotEmpty()) {
                append("&waypoints=").append(waypoints)
            }
            append("&travelmode=driving")
            append("&dir_action=navigate")
        }

        FTLog.i(TAG, "🔗 Final URL: $url")
        return url
    }

    /**
     * Build directions URL for remaining stops only (for resuming navigation).
     */
    fun buildDirectionsUrlForRemaining(
        currentLocation: Location?,
        remainingStops: List<RouteStop>
    ): String? {
        return buildDirectionsUrl(currentLocation, remainingStops)
    }

    /**
     * Check if route needs to be split due to waypoint limit.
     * 
     * @param stops Total number of stops
     * @return Pair of (needsSplit, numberOfLegs)
     */
    fun checkWaypointLimit(stops: List<RouteStop>): Pair<Boolean, Int> {
        // Google Maps allows: origin + MAX_WAYPOINTS + destination
        // So total stops can be MAX_WAYPOINTS + 2 (including origin and destination)
        val maxStops = MAX_WAYPOINTS + 1 // +1 for destination (origin is current location)
        
        return if (stops.size > maxStops) {
            val legs = (stops.size + maxStops - 1) / maxStops // Ceiling division
            Pair(true, legs)
        } else {
            Pair(false, 1)
        }
    }

    /**
     * Split stops into multiple legs if needed.
     */
    fun splitStopsIntoLegs(stops: List<RouteStop>): List<List<RouteStop>> {
        val (needsSplit, _) = checkWaypointLimit(stops)
        
        if (!needsSplit) {
            return listOf(stops)
        }

        val maxStops = MAX_WAYPOINTS + 1
        val legs = mutableListOf<List<RouteStop>>()
        
        var index = 0
        while (index < stops.size) {
            val endIndex = minOf(index + maxStops, stops.size)
            legs.add(stops.subList(index, endIndex))
            index = endIndex
        }

        FTLog.i(TAG, "Split ${stops.size} stops into ${legs.size} legs")
        return legs
    }

    /**
     * Launch Google Maps with directions.
     * 
     * @param context Android context
     * @param currentLocation Current device location
     * @param stops List of route stops
     * @param showSplitWarning Show toast if route needs to be split
     */
    fun launchNavigation(
        context: Context,
        currentLocation: Location?,
        stops: List<RouteStop>,
        showSplitWarning: Boolean = true
    ) {
        if (stops.isEmpty()) {
            Toast.makeText(context, "No stops to navigate to", Toast.LENGTH_SHORT).show()
            return
        }

        // Check waypoint limit
        val (needsSplit, legs) = checkWaypointLimit(stops)
        
        if (needsSplit && showSplitWarning) {
            Toast.makeText(
                context,
                "Route has ${stops.size} stops. Google Maps will open with the first $MAX_WAYPOINTS stops. You can continue with the rest after.",
                Toast.LENGTH_LONG
            ).show()
        }

        // Build URL (use first leg if split needed)
        val stopsToUse = if (needsSplit) {
            splitStopsIntoLegs(stops).first()
        } else {
            stops
        }

        val url = buildDirectionsUrl(currentLocation, stopsToUse)
        if (url == null) {
            Toast.makeText(context, "Could not build navigation route", Toast.LENGTH_SHORT).show()
            return
        }

        // Launch Google Maps
        try {
            val uri = Uri.parse(url)
            val mapIntent = Intent(Intent.ACTION_VIEW, uri)
            mapIntent.setPackage("com.google.android.apps.maps") // Prefer Google Maps app

            if (mapIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(mapIntent)
                FTLog.i(TAG, "Launched Google Maps navigation")
            } else {
                // Fallback to browser if Google Maps app not installed
                val webIntent = Intent(Intent.ACTION_VIEW, uri)
                if (webIntent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(webIntent)
                    FTLog.i(TAG, "Launched Google Maps in browser")
                } else {
                    Toast.makeText(context, "No application found to handle maps navigation", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            FTLog.e(TAG, "Failed to launch navigation: ${e.message}", e)
            Toast.makeText(context, "Failed to launch navigation: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Open a single location in Google Maps.
     */
    fun openLocation(
        context: Context,
        latitude: Double,
        longitude: Double,
        label: String? = null
    ) {
        try {
            val labelParam = if (!label.isNullOrBlank()) {
                "(${URLEncoder.encode(label, StandardCharsets.UTF_8.toString())})"
            } else {
                ""
            }
            
            val uri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude$labelParam")
            val mapIntent = Intent(Intent.ACTION_VIEW, uri)
            mapIntent.setPackage("com.google.android.apps.maps")

            if (mapIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(mapIntent)
            } else {
                // Fallback to browser
                val webUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=$latitude,$longitude")
                val webIntent = Intent(Intent.ACTION_VIEW, webUri)
                context.startActivity(webIntent)
            }
        } catch (e: Exception) {
            FTLog.e(TAG, "Failed to open location: ${e.message}", e)
            Toast.makeText(context, "Failed to open location", Toast.LENGTH_SHORT).show()
        }
    }
}

