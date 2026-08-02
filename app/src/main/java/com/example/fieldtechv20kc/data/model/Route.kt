package com.example.fieldtechv20kc.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.firestore.PropertyName
import java.util.UUID

@Entity(tableName = "routes")
data class Route(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,                           // e.g., "Jenson – Mon 19.10.25"
    val createdBy: String,                      // User who created the route
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val intendedAssignee: String? = null,       // Optional: tech name this route is for
    val totalEstimatedDistance: Double? = null, // in km
    val totalEstimatedTime: Int? = null,        // in minutes
    val completedStopsCount: Int = 0,           // Progress tracking
    val totalStopsCount: Int = 0,
    val isCompleted: Boolean = false,
    val completedAt: Long? = null,
    val completedBy: String? = null,
    val deleted: Boolean = false                // Soft delete
)

@Entity(tableName = "route_stops")
data class RouteStop(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val routeId: String,
    val jobId: String,
    val clientId: String,
    val clientName: String,
    val locality: String,
    val address: String? = null,                // Fallback if no pin
    val orderIndex: Int,                        // Manual order: 0, 1, 2...
    val latitude: Double? = null,               // From client pin
    val longitude: Double? = null,              // From client pin
    val distanceFromPrevious: Double? = null,   // km from previous stop (calculated)
    val timeFromPrevious: Int? = null,          // minutes from previous stop (estimated)
    val isCompleted: Boolean = false,
    val completedAt: Long? = null,
    val completedBy: String? = null
)

enum class RouteOptimization(val displayName: String) {
    CLOSEST_FIRST("Closest first (from current location)"),
    FARTHEST_FIRST("Farthest first (then optimize)"),
    MANUAL("Manual order (drag to reorder)")
}

// Helper data classes
data class RouteWithStops(
    val route: Route,
    val stops: List<RouteStop>
)

data class RouteProgress(
    val completedCount: Int,
    val totalCount: Int,
    val nextStopIndex: Int?,
    val percentComplete: Int
) {
    val isComplete: Boolean get() = completedCount >= totalCount
}

// Firestore DTO
data class RouteDto(
    val id: String? = null,
    val name: String = "",
    val createdBy: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val intendedAssignee: String? = null,
    val totalEstimatedDistance: Double? = null,
    val totalEstimatedTime: Int? = null,
    val completedStopsCount: Int = 0,
    val totalStopsCount: Int = 0,
    @get:PropertyName("isCompleted") @set:PropertyName("isCompleted")
    var isCompleted: Boolean = false,
    val completedAt: Long? = null,
    val completedBy: String? = null,
    @get:PropertyName("deleted") @set:PropertyName("deleted")
    var deleted: Boolean = false
)

data class RouteStopDto(
    val id: String? = null,
    val routeId: String = "",
    val jobId: String = "",
    val clientId: String = "",
    val clientName: String = "",
    val locality: String = "",
    val address: String? = null,
    val orderIndex: Int = 0,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val distanceFromPrevious: Double? = null,
    val timeFromPrevious: Int? = null,
    @get:PropertyName("isCompleted") @set:PropertyName("isCompleted")
    var isCompleted: Boolean = false,
    val completedAt: Long? = null,
    val completedBy: String? = null
)

