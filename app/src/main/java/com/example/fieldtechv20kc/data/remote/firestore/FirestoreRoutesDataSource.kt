package com.example.fieldtechv20kc.data.remote.firestore

import android.util.Log
import com.example.fieldtechv20kc.BuildConfig
import com.example.fieldtechv20kc.data.model.RouteDto
import com.example.fieldtechv20kc.data.model.RouteStopDto
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Firestore data source for routes and route stops.
 * Routes are stored at company level and synced in real-time.
 */
class FirestoreRoutesDataSource(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val companyId: String = BuildConfig.COMPANY_ID
) {
    private val TAG = "FirestoreRoutesDS"

    // === Route Operations ===

    /**
     * Listen to all routes (not deleted) in real-time
     */
    fun listenAllRoutes(): Flow<List<RouteDto>> = callbackFlow {
        Log.d(TAG, "🎯 Starting routes listener for company: $companyId")

        val reg = db.collection("companies")
            .document(companyId)
            .collection("routes")
            .whereEqualTo("deleted", false)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e(TAG, "❌ Error in routes listener: ${err.message}", err)
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                if (snap != null) {
                    val routes = snap.documents.mapNotNull { doc ->
                        try {
                            doc.toObject(RouteDto::class.java)?.copy(id = doc.id)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to parse route ${doc.id}: ${e.message}")
                            null
                        }
                    }
                    Log.d(TAG, "✅ Received ${routes.size} routes from Firestore")
                    trySend(routes)
                } else {
                    trySend(emptyList())
                }
            }

        awaitClose {
            Log.d(TAG, "Closing routes listener")
            reg.remove()
        }
    }

    /**
     * Upload a route to Firestore
     */
    suspend fun uploadRoute(route: RouteDto) {
        try {
            val routeId = route.id ?: throw IllegalArgumentException("Route ID cannot be null")
            
            db.collection("companies")
                .document(companyId)
                .collection("routes")
                .document(routeId)
                .set(route, SetOptions.merge())
                .await()
            
            Log.d(TAG, "✅ Uploaded route: $routeId")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to upload route: ${e.message}", e)
            throw e
        }
    }

    /**
     * Delete a route (soft delete)
     */
    suspend fun deleteRoute(routeId: String) {
        try {
            db.collection("companies")
                .document(companyId)
                .collection("routes")
                .document(routeId)
                .update(
                    mapOf(
                        "deleted" to true,
                        "updatedAt" to System.currentTimeMillis()
                    )
                )
                .await()
            
            Log.d(TAG, "✅ Soft-deleted route: $routeId")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to delete route: ${e.message}", e)
            throw e
        }
    }

    // === Route Stop Operations ===

    /**
     * Listen to stops for a specific route in real-time
     */
    fun listenRouteStops(routeId: String): Flow<List<RouteStopDto>> = callbackFlow {
        Log.d(TAG, "🎯 Starting stops listener for route: $routeId")

        val reg = db.collection("companies")
            .document(companyId)
            .collection("routes")
            .document(routeId)
            .collection("stops")
            .orderBy("orderIndex")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e(TAG, "❌ Error in stops listener: ${err.message}", err)
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                if (snap != null) {
                    val stops = snap.documents.mapNotNull { doc ->
                        try {
                            doc.toObject(RouteStopDto::class.java)?.copy(id = doc.id)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to parse stop ${doc.id}: ${e.message}")
                            null
                        }
                    }
                    Log.d(TAG, "✅ Received ${stops.size} stops for route $routeId")
                    trySend(stops)
                } else {
                    trySend(emptyList())
                }
            }

        awaitClose {
            Log.d(TAG, "Closing stops listener for route: $routeId")
            reg.remove()
        }
    }

    /**
     * Upload multiple stops for a route
     */
    suspend fun uploadStops(routeId: String, stops: List<RouteStopDto>) {
        try {
            val batch = db.batch()
            
            stops.forEach { stop ->
                val stopId = stop.id ?: throw IllegalArgumentException("Stop ID cannot be null")
                val stopRef = db.collection("companies")
                    .document(companyId)
                    .collection("routes")
                    .document(routeId)
                    .collection("stops")
                    .document(stopId)
                
                batch.set(stopRef, stop, SetOptions.merge())
            }
            
            batch.commit().await()
            Log.d(TAG, "✅ Uploaded ${stops.size} stops for route: $routeId")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to upload stops: ${e.message}", e)
            throw e
        }
    }

    /**
     * Upload a single stop
     */
    suspend fun uploadStop(routeId: String, stop: RouteStopDto) {
        try {
            val stopId = stop.id ?: throw IllegalArgumentException("Stop ID cannot be null")
            
            db.collection("companies")
                .document(companyId)
                .collection("routes")
                .document(routeId)
                .collection("stops")
                .document(stopId)
                .set(stop, SetOptions.merge())
                .await()
            
            Log.d(TAG, "✅ Uploaded stop: $stopId for route: $routeId")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to upload stop: ${e.message}", e)
            throw e
        }
    }

    /**
     * Delete all stops for a route
     */
    suspend fun deleteStopsForRoute(routeId: String) {
        try {
            val stopsSnapshot = db.collection("companies")
                .document(companyId)
                .collection("routes")
                .document(routeId)
                .collection("stops")
                .get()
                .await()
            
            val batch = db.batch()
            stopsSnapshot.documents.forEach { doc ->
                batch.delete(doc.reference)
            }
            batch.commit().await()
            
            Log.d(TAG, "✅ Deleted ${stopsSnapshot.size()} stops for route: $routeId")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to delete stops: ${e.message}", e)
            throw e
        }
    }

    /**
     * Update route progress (completion counts)
     */
    suspend fun updateRouteProgress(routeId: String, completedCount: Int, totalCount: Int) {
        try {
            db.collection("companies")
                .document(companyId)
                .collection("routes")
                .document(routeId)
                .update(
                    mapOf(
                        "completedStopsCount" to completedCount,
                        "totalStopsCount" to totalCount,
                        "updatedAt" to System.currentTimeMillis()
                    )
                )
                .await()
            
            Log.d(TAG, "✅ Updated progress for route $routeId: $completedCount/$totalCount")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to update route progress: ${e.message}", e)
            throw e
        }
    }
}










