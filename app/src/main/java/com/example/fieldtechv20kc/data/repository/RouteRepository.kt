package com.example.fieldtechv20kc.data.repository

import android.util.Log
import com.example.fieldtechv20kc.data.database.dao.ClientDao
import com.example.fieldtechv20kc.data.database.dao.ClientPinsDao
import com.example.fieldtechv20kc.data.database.dao.RouteDao
import com.example.fieldtechv20kc.data.database.dao.ServiceTasksDao
import com.example.fieldtechv20kc.data.model.*
import com.example.fieldtechv20kc.data.remote.firestore.FirestoreRoutesDataSource
import com.example.fieldtechv20kc.utils.FTLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*

/**
 * Repository for managing routes with local Room database and Firestore sync.
 */
class RouteRepository(
    private val routeDao: RouteDao,
    private val serviceTasksDao: ServiceTasksDao,
    private val clientDao: ClientDao,
    private val clientPinsDao: ClientPinsDao,
    private val remote: FirestoreRoutesDataSource = FirestoreRoutesDataSource()
) {
    private val TAG = "RouteRepository"

    /**
     * Start syncing routes from Firestore to local database
     */
    fun startSync(scope: CoroutineScope) {
        // Sync routes
        scope.launch {
            remote.listenAllRoutes().collect { remoteRoutes ->
                try {
                    val remoteRouteIds = remoteRoutes.mapNotNull { it.id }.toSet()
                    
                    // Insert/update routes from Firestore
                    remoteRoutes.forEach { dto ->
                        val route = dto.toEntity()
                        routeDao.insertRoute(route)
                    }
                    
                    // Delete local routes that are no longer in Firestore (deleted on another device)
                    val localRoutes = routeDao.getAllRoutesOnce()
                    val localRoutesToDelete = localRoutes.filter { !remoteRouteIds.contains(it.id) }
                    localRoutesToDelete.forEach { route ->
                        routeDao.softDeleteRoute(route.id)
                        FTLog.i(TAG, "  🗑️ Deleted route locally (removed from Firestore): ${route.name}")
                    }
                    
                    FTLog.i(TAG, "✅ Synced ${remoteRoutes.size} routes from Firestore (deleted ${localRoutesToDelete.size} locally)")
                    
                    // Sync stops for each route
                    remoteRoutes.forEach { routeDto ->
                        val routeId = routeDto.id
                        if (routeId != null) {
                            scope.launch {
                                try {
                                    remote.listenRouteStops(routeId).collect { remoteStops ->
                                        val stops = remoteStops.map { it.toEntity() }
                                        if (stops.isNotEmpty()) {
                                            routeDao.insertStops(stops)
                                            FTLog.i(TAG, "  ✅ Synced ${stops.size} stops for route: ${routeDto.name}")
                                        }
                                    }
                                } catch (e: Exception) {
                                    FTLog.e(TAG, "  ❌ Failed to sync stops for route $routeId: ${e.message}", e)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    FTLog.e(TAG, "❌ Failed to sync routes: ${e.message}", e)
                }
            }
        }
    }

    /**
     * Observe all routes
     */
    fun observeAllRoutes(): Flow<List<Route>> {
        return routeDao.observeAllRoutes()
    }

    /**
     * Observe active routes for a specific technician
     */
    fun observeActiveRoutes(technicianName: String): Flow<List<Route>> {
        return routeDao.observeActiveRoutes(technicianName)
    }

    /**
     * Observe a route with its stops
     */
    fun observeRouteWithStops(routeId: String): Flow<RouteWithStops?> {
        return combine(
            routeDao.observeAllRoutes(),
            routeDao.observeRouteStops(routeId)
        ) { routes, stops ->
            val route = routes.find { it.id == routeId }
            if (route != null) {
                RouteWithStops(route, stops)
            } else {
                null
            }
        }
    }

    /**
     * Get optimized stops without creating a route (for preview/planning)
     * This method does NOT save to database or Firestore
     */
    suspend fun getOptimizedStops(
        jobIds: List<String>,
        optimization: RouteOptimization = RouteOptimization.CLOSEST_FIRST,
        startLatitude: Double? = null,
        startLongitude: Double? = null
    ): RouteOptimizationResult {
        try {
            val failedJobs = mutableListOf<String>()
            
            // Fetch jobs - track which ones don't exist
            val jobs = jobIds.mapNotNull { jobId ->
                val job = serviceTasksDao.getByIdOnce(jobId)
                if (job == null) {
                    failedJobs.add("Job not found")
                    FTLog.w(TAG, "⚠️ Job $jobId not found in database")
                }
                job
            }
            
            if (jobs.isEmpty()) {
                throw IllegalArgumentException("No valid jobs found for the provided IDs")
            }

            // Fetch client pins for all jobs
            val clientIds = jobs.map { it.clientId }.distinct()
            val pinsMap = mutableMapOf<String, ClientPinEntity>()

            FTLog.i(TAG, "Optimizing ${jobs.size} jobs for ${clientIds.size} clients")

            clientIds.forEach { clientId ->
                // Try primary pin first, then any pin
                var pin = clientPinsDao.getPrimaryOnce(clientId)
                if (pin == null) {
                    pin = clientPinsDao.getFirstPin(clientId)
                }

                if (pin != null && pin.latitude != null && pin.longitude != null) {
                    pinsMap[clientId] = pin
                } else {
                    FTLog.w(TAG, "⚠️ Client $clientId has no valid GPS coordinates")
                }
            }

            if (pinsMap.isEmpty()) {
                throw IllegalArgumentException("None of the selected jobs have clients with GPS coordinates.")
            }

            // Create route stops from jobs - track which ones fail
            val unorderedStops = jobs.mapNotNull { job ->
                val pin = pinsMap[job.clientId]
                val client = clientDao.getClientById(job.clientId)
                
                if (pin == null) {
                    failedJobs.add("${job.id}: No GPS coordinates")
                    FTLog.w(TAG, "⚠️ Job ${job.id} excluded: Client has no GPS coordinates")
                    null
                } else if (client == null) {
                    failedJobs.add("${job.id}: Client not found")
                    FTLog.w(TAG, "⚠️ Job ${job.id} excluded: Client not found")
                    null
                } else {
                    RouteStop(
                        id = UUID.randomUUID().toString(),
                        routeId = "", // Not assigned yet
                        jobId = job.id,
                        clientId = job.clientId,
                        clientName = client.name,
                        locality = client.locality ?: "",
                        address = client.address ?: "",
                        orderIndex = 0, // Will be set by optimizer
                        latitude = pin.latitude,
                        longitude = pin.longitude
                    )
                }
            }

            // Optimize route based on strategy
            val optimizedStops = when (optimization) {
                RouteOptimization.CLOSEST_FIRST -> {
                    if (startLatitude != null && startLongitude != null) {
                        optimizeClosestFirst(unorderedStops, startLatitude, startLongitude)
                    } else {
                        optimizeClosestFirst(unorderedStops, unorderedStops.first().latitude!!, unorderedStops.first().longitude!!)
                    }
                }
                RouteOptimization.FARTHEST_FIRST -> {
                    optimizeFarthestFirst(unorderedStops)
                }
                RouteOptimization.MANUAL -> {
                    unorderedStops.mapIndexed { index, stop ->
                        stop.copy(orderIndex = index)
                    }
                }
            }

            // Calculate distances and times
            val finalStops = calculateDistancesAndTimes(optimizedStops)
            
            return RouteOptimizationResult(
                stops = finalStops,
                failedJobs = failedJobs,
                totalRequested = jobIds.size,
                totalAdded = finalStops.size
            )
        } catch (e: Exception) {
            FTLog.e(TAG, "❌ Failed to optimize stops: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Save a route from already-optimized stops (from Route Planner UI)
     * This avoids recreating stops and uses the exact stops from the UI
     */
    suspend fun saveRouteFromStops(
        stops: List<RouteStop>,
        routeName: String,
        createdBy: String,
        intendedAssignee: String? = null
    ): String {
        try {
            if (stops.isEmpty()) {
                throw IllegalArgumentException("Cannot save route with no stops")
            }
            
            // Create route ID
            val routeId = UUID.randomUUID().toString()
            
            // Calculate distances and times
            val stopsWithDistances = calculateDistancesAndTimes(stops)
            
            // Calculate totals
            val totalDistance = stopsWithDistances.sumOf { it.distanceFromPrevious ?: 0.0 }
            val totalTime = stopsWithDistances.sumOf { (it.timeFromPrevious ?: 0).toLong() }.toInt()
            
            // Create route
            val route = Route(
                id = routeId,
                name = routeName,
                createdBy = createdBy,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                intendedAssignee = intendedAssignee,
                totalEstimatedDistance = totalDistance,
                totalEstimatedTime = totalTime,
                completedStopsCount = 0,
                totalStopsCount = stopsWithDistances.size
            )
            
            // Set route ID on all stops and ensure correct order
            val finalStops = stopsWithDistances.mapIndexed { index, stop ->
                stop.copy(
                    routeId = routeId,
                    orderIndex = index
                )
            }
            
            // Save to local DB
            routeDao.insertRoute(route)
            routeDao.insertStops(finalStops)
            
            // Upload to Firestore
            try {
                remote.uploadRoute(route.toDto())
                remote.uploadStops(routeId, finalStops.map { it.toDto() })
                FTLog.i(TAG, "✅ Saved route from UI: $routeName ($routeId) with ${finalStops.size} stops")
            } catch (e: Exception) {
                FTLog.e(TAG, "⚠️ Route saved locally but failed to sync: ${e.message}", e)
            }
            
            return routeId
        } catch (e: Exception) {
            FTLog.e(TAG, "❌ Failed to save route: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Create a new route from a list of job IDs
     */
    suspend fun createRoute(
        jobIds: List<String>,
        routeName: String,
        createdBy: String,
        intendedAssignee: String? = null,
        optimization: RouteOptimization = RouteOptimization.CLOSEST_FIRST,
        startLatitude: Double? = null,
        startLongitude: Double? = null
    ): String {
        try {
            // Fetch jobs
            val jobs = jobIds.mapNotNull { serviceTasksDao.getByIdOnce(it) }
            if (jobs.isEmpty()) {
                throw IllegalArgumentException("No valid jobs found for the provided IDs")
            }

            // Fetch client pins for all jobs
            val clientIds = jobs.map { it.clientId }.distinct()
            val pinsMap = mutableMapOf<String, ClientPinEntity>()

            FTLog.i(TAG, "Creating route with ${jobs.size} jobs for ${clientIds.size} clients")

            clientIds.forEach { clientId ->
                // Try primary pin first, then any pin
                var pin = clientPinsDao.getPrimaryOnce(clientId)
                if (pin == null) {
                    pin = clientPinsDao.getFirstPin(clientId)
                }

                if (pin != null && pin.latitude != null && pin.longitude != null) {
                    pinsMap[clientId] = pin
                    FTLog.i(TAG, "✅ Client $clientId has valid coordinates")
                } else {
                    FTLog.w(TAG, "⚠️ Client $clientId has no valid GPS coordinates")
                }
            }

            if (pinsMap.isEmpty()) {
                throw IllegalArgumentException("None of the selected jobs have clients with GPS coordinates. Please add location pins first.")
            }

            // Create route stops from jobs
            val unorderedStops = jobs.mapNotNull { job ->
                val pin = pinsMap[job.clientId]
                val client = clientDao.getClientById(job.clientId)
                
                if (pin != null && client != null) {
                    RouteStop(
                        id = UUID.randomUUID().toString(),
                        routeId = "", // Will be set after route creation
                        jobId = job.id,
                        clientId = job.clientId,
                        clientName = client.name,
                        locality = client.locality ?: "",
                        address = client.address ?: "",
                        orderIndex = 0, // Will be set by optimizer
                        latitude = pin.latitude,
                        longitude = pin.longitude
                    )
                } else {
                    null
                }
            }

            // Optimize route based on strategy
            val optimizedStops = when (optimization) {
                RouteOptimization.CLOSEST_FIRST -> {
                    if (startLatitude != null && startLongitude != null) {
                        optimizeClosestFirst(unorderedStops, startLatitude, startLongitude)
                    } else {
                        // Fallback: use centroid or first stop
                        optimizeClosestFirst(unorderedStops, unorderedStops.first().latitude!!, unorderedStops.first().longitude!!)
                    }
                }
                RouteOptimization.FARTHEST_FIRST -> {
                    optimizeFarthestFirst(unorderedStops)
                }
                RouteOptimization.MANUAL -> {
                    // Keep original order, just assign indices
                    unorderedStops.mapIndexed { index, stop ->
                        stop.copy(orderIndex = index)
                    }
                }
            }

            // Calculate distances and times
            val stopsWithDistances = calculateDistancesAndTimes(optimizedStops)

            // Calculate totals
            val totalDistance = stopsWithDistances.sumOf { it.distanceFromPrevious ?: 0.0 }
            val totalTime = stopsWithDistances.sumOf { (it.timeFromPrevious ?: 0).toLong() }.toInt()

            // Create route
            val routeId = UUID.randomUUID().toString()
            val route = Route(
                id = routeId,
                name = routeName,
                createdBy = createdBy,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                intendedAssignee = intendedAssignee,
                totalEstimatedDistance = totalDistance,
                totalEstimatedTime = totalTime,
                completedStopsCount = 0,
                totalStopsCount = stopsWithDistances.size
            )

            // Set route ID on stops
            val finalStops = stopsWithDistances.map { it.copy(routeId = routeId) }

            // Save to local DB
            routeDao.insertRoute(route)
            routeDao.insertStops(finalStops)

            // Upload to Firestore
            try {
                remote.uploadRoute(route.toDto())
                remote.uploadStops(routeId, finalStops.map { it.toDto() })
                FTLog.i(TAG, "✅ Created and synced route: $routeName ($routeId)")
            } catch (e: Exception) {
                FTLog.e(TAG, "⚠️ Route created locally but failed to sync: ${e.message}", e)
            }

            return routeId
        } catch (e: Exception) {
            FTLog.e(TAG, "❌ Failed to create route: ${e.message}", e)
            throw e
        }
    }

    /**
     * Update route stops order (for manual reordering)
     */
    suspend fun updateStopsOrder(routeId: String, reorderedStops: List<RouteStop>) {
        try {
            // Update order indices
            val updatedStops = reorderedStops.mapIndexed { index, stop ->
                stop.copy(orderIndex = index)
            }

            // Recalculate distances and times
            val stopsWithDistances = calculateDistancesAndTimes(updatedStops)

            // Save to local DB
            stopsWithDistances.forEach { routeDao.updateStop(it) }

            // Update route totals
            val totalDistance = stopsWithDistances.sumOf { it.distanceFromPrevious ?: 0.0 }
            val totalTime = stopsWithDistances.sumOf { (it.timeFromPrevious ?: 0).toLong() }.toInt()
            
            val route = routeDao.getRouteById(routeId)
            if (route != null) {
                val updatedRoute = route.copy(
                    totalEstimatedDistance = totalDistance,
                    totalEstimatedTime = totalTime,
                    updatedAt = System.currentTimeMillis()
                )
                routeDao.updateRoute(updatedRoute)

                // Sync to Firestore
                remote.uploadRoute(updatedRoute.toDto())
                remote.uploadStops(routeId, stopsWithDistances.map { it.toDto() })
            }

            FTLog.i(TAG, "✅ Updated stops order for route: $routeId")
        } catch (e: Exception) {
            FTLog.e(TAG, "❌ Failed to update stops order: ${e.message}", e)
            throw e
        }
    }

    /**
     * Mark a stop as completed
     */
    suspend fun markStopCompleted(stopId: String, completedBy: String) {
        try {
            routeDao.markStopCompleted(stopId, completedBy)
            
            // Get the stop to find its route
            val stop = routeDao.getStopById(stopId)
            if (stop != null) {
                // Update route progress
                val allStops = routeDao.getRouteStops(stop.routeId)
                val completedCount = allStops.count { it.isCompleted }
                
                val route = routeDao.getRouteById(stop.routeId)
                if (route != null) {
                    val updatedRoute = route.copy(
                        completedStopsCount = completedCount,
                        updatedAt = System.currentTimeMillis()
                    )
                    routeDao.updateRoute(updatedRoute)

                    // Sync to Firestore
                    remote.uploadStop(stop.routeId, stop.copy(isCompleted = true, completedAt = System.currentTimeMillis(), completedBy = completedBy).toDto())
                    remote.updateRouteProgress(stop.routeId, completedCount, allStops.size)
                }
            }

            FTLog.i(TAG, "✅ Marked stop $stopId as completed")
        } catch (e: Exception) {
            FTLog.e(TAG, "❌ Failed to mark stop completed: ${e.message}", e)
            throw e
        }
    }

    /**
     * Unmark a stop as completed (allow users to undo completion)
     */
    suspend fun markStopUncompleted(stopId: String) {
        try {
            routeDao.markStopUncompleted(stopId)
            
            // Get the stop to find its route
            val stop = routeDao.getStopById(stopId)
            if (stop != null) {
                // Update route progress
                val allStops = routeDao.getRouteStops(stop.routeId)
                val completedCount = allStops.count { it.isCompleted }
                
                val route = routeDao.getRouteById(stop.routeId)
                if (route != null) {
                    val updatedRoute = route.copy(
                        completedStopsCount = completedCount,
                        updatedAt = System.currentTimeMillis()
                    )
                    routeDao.updateRoute(updatedRoute)

                    // Sync to Firestore
                    remote.uploadStop(stop.routeId, stop.copy(isCompleted = false, completedAt = null, completedBy = null).toDto())
                    remote.updateRouteProgress(stop.routeId, completedCount, allStops.size)
                }
            }

            FTLog.i(TAG, "✅ Unmarked stop $stopId (set to incomplete)")
        } catch (e: Exception) {
            FTLog.e(TAG, "❌ Failed to unmark stop: ${e.message}", e)
            throw e
        }
    }

    /**
     * Mark entire route as completed
     */
    suspend fun markRouteCompleted(routeId: String, completedBy: String) {
        try {
            // First, mark all stops as completed
            val stops = routeDao.getRouteStops(routeId)
            val now = System.currentTimeMillis()
            stops.forEach { stop ->
                if (!stop.isCompleted) {
                    routeDao.markStopCompleted(stop.id, completedBy, now)
                }
            }
            
            // Then mark the route as completed
            routeDao.markRouteCompleted(routeId, completedBy)

            // Update in Firestore
            val route = routeDao.getRouteById(routeId)
            if (route != null) {
                // Upload route
                remote.uploadRoute(route.toDto())
                
                // Upload all updated stops
                val updatedStops = routeDao.getRouteStops(routeId)
                remote.uploadStops(routeId, updatedStops.map { it.toDto() })
            }

            FTLog.i(TAG, "✅ Marked route $routeId as completed (all ${stops.size} stops completed)")
        } catch (e: Exception) {
            FTLog.e(TAG, "❌ Failed to mark route completed: ${e.message}", e)
            throw e
        }
    }

    /**
     * Delete a route (soft delete)
     */
    suspend fun deleteRoute(routeId: String) {
        try {
            routeDao.softDeleteRoute(routeId)
            remote.deleteRoute(routeId)
            FTLog.i(TAG, "✅ Deleted route: $routeId")
        } catch (e: Exception) {
            FTLog.e(TAG, "❌ Failed to delete route: ${e.message}", e)
            throw e
        }
    }

    // === Helper Functions ===

    /**
     * Optimize route using closest-first algorithm from starting location
     */
    private fun optimizeClosestFirst(stops: List<RouteStop>, startLat: Double, startLon: Double): List<RouteStop> {
        if (stops.isEmpty()) return emptyList()
        
        val remaining = stops.toMutableList()
        val ordered = mutableListOf<RouteStop>()
        var currentLat = startLat
        var currentLon = startLon

        while (remaining.isNotEmpty()) {
            // Find nearest stop
            val nearest = remaining.minByOrNull { stop ->
                calculateDistance(currentLat, currentLon, stop.latitude!!, stop.longitude!!)
            }

            if (nearest != null) {
                ordered.add(nearest.copy(orderIndex = ordered.size))
                remaining.remove(nearest)
                currentLat = nearest.latitude!!
                currentLon = nearest.longitude!!
            }
        }

        return ordered
    }

    /**
     * Optimize route using farthest-first algorithm
     */
    private fun optimizeFarthestFirst(stops: List<RouteStop>): List<RouteStop> {
        if (stops.isEmpty()) return emptyList()

        val remaining = stops.toMutableList()
        val ordered = mutableListOf<RouteStop>()

        // Start with the stop that is farthest from all others (most isolated)
        val first = remaining.maxByOrNull { s1 ->
            remaining.filter { it != s1 }.sumOf { s2 ->
                calculateDistance(s1.latitude!!, s1.longitude!!, s2.latitude!!, s2.longitude!!)
            }
        }

        if (first != null) {
            ordered.add(first.copy(orderIndex = 0))
            remaining.remove(first)

            // Then use nearest neighbor from there
            var currentLat = first.latitude!!
            var currentLon = first.longitude!!

            while (remaining.isNotEmpty()) {
                val nearest = remaining.minByOrNull { stop ->
                    calculateDistance(currentLat, currentLon, stop.latitude!!, stop.longitude!!)
                }

                if (nearest != null) {
                    ordered.add(nearest.copy(orderIndex = ordered.size))
                    remaining.remove(nearest)
                    currentLat = nearest.latitude!!
                    currentLon = nearest.longitude!!
                }
            }
        }

        return ordered
    }

    /**
     * Calculate distances and estimated times between stops
     */
    private fun calculateDistancesAndTimes(stops: List<RouteStop>): List<RouteStop> {
        if (stops.isEmpty()) return emptyList()

        val result = mutableListOf<RouteStop>()
        var previousStop: RouteStop? = null

        stops.forEach { stop ->
            if (previousStop != null) {
                val distance = calculateDistance(
                    previousStop!!.latitude!!,
                    previousStop!!.longitude!!,
                    stop.latitude!!,
                    stop.longitude!!
                )
                // Estimate time: assume 40 km/h average speed, convert to minutes
                val timeMinutes = ((distance / 40.0) * 60.0).toInt()
                
                result.add(stop.copy(
                    distanceFromPrevious = distance,
                    timeFromPrevious = timeMinutes
                ))
            } else {
                // First stop has no previous
                result.add(stop.copy(
                    distanceFromPrevious = 0.0,
                    timeFromPrevious = 0
                ))
            }
            previousStop = stop
        }

        return result
    }

    /**
     * Calculate distance between two GPS coordinates using Haversine formula
     */
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadiusKm = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return earthRadiusKm * c
    }
}

// Extension functions for DTO conversion

fun Route.toDto(): RouteDto = RouteDto(
    id = id,
    name = name,
    createdBy = createdBy,
    createdAt = createdAt,
    updatedAt = updatedAt,
    intendedAssignee = intendedAssignee,
    totalEstimatedDistance = totalEstimatedDistance,
    totalEstimatedTime = totalEstimatedTime,
    completedStopsCount = completedStopsCount,
    totalStopsCount = totalStopsCount,
    isCompleted = isCompleted,
    completedAt = completedAt,
    completedBy = completedBy,
    deleted = deleted
)

fun RouteDto.toEntity(): Route = Route(
    id = id ?: UUID.randomUUID().toString(),
    name = name,
    createdBy = createdBy,
    createdAt = createdAt,
    updatedAt = updatedAt,
    intendedAssignee = intendedAssignee,
    totalEstimatedDistance = totalEstimatedDistance,
    totalEstimatedTime = totalEstimatedTime,
    completedStopsCount = completedStopsCount,
    totalStopsCount = totalStopsCount,
    isCompleted = isCompleted,
    completedAt = completedAt,
    completedBy = completedBy,
    deleted = deleted
)

fun RouteStop.toDto(): RouteStopDto = RouteStopDto(
    id = id,
    routeId = routeId,
    jobId = jobId,
    clientId = clientId,
    clientName = clientName,
    locality = locality,
    address = address,
    orderIndex = orderIndex,
    latitude = latitude,
    longitude = longitude,
    distanceFromPrevious = distanceFromPrevious,
    timeFromPrevious = timeFromPrevious,
    isCompleted = isCompleted,
    completedAt = completedAt,
    completedBy = completedBy
)

fun RouteStopDto.toEntity(): RouteStop = RouteStop(
    id = id ?: UUID.randomUUID().toString(),
    routeId = routeId,
    jobId = jobId,
    clientId = clientId,
    clientName = clientName,
    locality = locality,
    address = address,
    orderIndex = orderIndex,
    latitude = latitude,
    longitude = longitude,
    distanceFromPrevious = distanceFromPrevious,
    timeFromPrevious = timeFromPrevious,
    isCompleted = isCompleted,
    completedAt = completedAt,
    completedBy = completedBy
)

/**
 * Result of route optimization containing stops and information about any failures
 */
data class RouteOptimizationResult(
    val stops: List<RouteStop>,
    val failedJobs: List<String>,
    val totalRequested: Int,
    val totalAdded: Int
) {
    val hasFailures: Boolean get() = failedJobs.isNotEmpty()
    val failureCount: Int get() = totalRequested - totalAdded
}

