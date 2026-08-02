package com.example.fieldtechv20kc.data.database.dao

import androidx.room.*
import com.example.fieldtechv20kc.data.model.Route
import com.example.fieldtechv20kc.data.model.RouteStop
import kotlinx.coroutines.flow.Flow

@Dao
interface RouteDao {
    
    // === Route queries ===
    
    @Query("SELECT * FROM routes WHERE deleted = 0 ORDER BY createdAt DESC")
    fun observeAllRoutes(): Flow<List<Route>>
    
    @Query("SELECT * FROM routes WHERE deleted = 0 ORDER BY createdAt DESC")
    suspend fun getAllRoutesOnce(): List<Route>
    
    @Query("SELECT * FROM routes WHERE id = :routeId AND deleted = 0")
    suspend fun getRouteById(routeId: String): Route?
    
    @Query("SELECT * FROM routes WHERE intendedAssignee = :technicianName AND isCompleted = 0 AND deleted = 0 ORDER BY createdAt DESC")
    fun observeActiveRoutes(technicianName: String): Flow<List<Route>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoute(route: Route)
    
    @Update
    suspend fun updateRoute(route: Route)
    
    @Query("UPDATE routes SET isCompleted = 1, completedAt = :completedAt, completedBy = :completedBy, updatedAt = :updatedAt WHERE id = :routeId")
    suspend fun markRouteCompleted(routeId: String, completedBy: String, completedAt: Long = System.currentTimeMillis(), updatedAt: Long = System.currentTimeMillis())
    
    @Query("UPDATE routes SET deleted = 1, updatedAt = :updatedAt WHERE id = :routeId")
    suspend fun softDeleteRoute(routeId: String, updatedAt: Long = System.currentTimeMillis())
    
    @Delete
    suspend fun deleteRoute(route: Route)
    
    // === RouteStop queries ===
    
    @Query("SELECT * FROM route_stops WHERE routeId = :routeId ORDER BY orderIndex ASC")
    fun observeRouteStops(routeId: String): Flow<List<RouteStop>>
    
    @Query("SELECT * FROM route_stops WHERE routeId = :routeId ORDER BY orderIndex ASC")
    suspend fun getRouteStops(routeId: String): List<RouteStop>
    
    @Query("SELECT * FROM route_stops WHERE routeId = :routeId AND isCompleted = 0 ORDER BY orderIndex ASC")
    suspend fun getRemainingStops(routeId: String): List<RouteStop>
    
    @Query("SELECT * FROM route_stops WHERE id = :stopId")
    suspend fun getStopById(stopId: String): RouteStop?
    
    @Query("SELECT * FROM route_stops WHERE jobId = :jobId LIMIT 1")
    suspend fun getStopByJobId(jobId: String): RouteStop?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStop(stop: RouteStop)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStops(stops: List<RouteStop>)
    
    @Update
    suspend fun updateStop(stop: RouteStop)
    
    @Query("UPDATE route_stops SET isCompleted = 1, completedAt = :completedAt, completedBy = :completedBy WHERE id = :stopId")
    suspend fun markStopCompleted(stopId: String, completedBy: String, completedAt: Long = System.currentTimeMillis())
    
    @Query("UPDATE route_stops SET isCompleted = 0, completedAt = NULL, completedBy = NULL WHERE id = :stopId")
    suspend fun markStopUncompleted(stopId: String)
    
    @Query("DELETE FROM route_stops WHERE routeId = :routeId")
    suspend fun deleteStopsByRouteId(routeId: String)
    
    @Delete
    suspend fun deleteStop(stop: RouteStop)
}

