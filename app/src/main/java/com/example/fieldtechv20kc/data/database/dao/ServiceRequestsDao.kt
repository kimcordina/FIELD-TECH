package com.example.fieldtechv20kc.data.database.dao

import androidx.room.*
import com.example.fieldtechv20kc.data.model.ServiceRequest
import kotlinx.coroutines.flow.Flow

@Dao
interface ServiceRequestsDao {
    
    @Query("""
        SELECT sr.* FROM service_requests sr
        JOIN clients c ON c.id = sr.clientId
        WHERE sr.deleted = 0
          AND (:status IS NULL OR sr.status = :status)
          AND (:locality IS NULL OR LOWER(c.locality) = LOWER(:locality))
          AND (:q IS NULL OR c.name LIKE '%' || :q || '%' OR COALESCE(sr.notes,'') LIKE '%' || :q || '%')
        ORDER BY sr.requestedAt DESC
    """)
    fun observe(status: String?, locality: String?, q: String?): Flow<List<ServiceRequest>>
    
    @Query("SELECT * FROM service_requests WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<ServiceRequest?>
    
    @Query("SELECT * FROM service_requests WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ServiceRequest?
    
    @Query("SELECT * FROM service_requests WHERE id = :id LIMIT 1")
    suspend fun getByIdOnce(id: String): ServiceRequest?

    @Query("SELECT * FROM service_requests WHERE linkedTaskId = :taskId AND deleted = 0 LIMIT 1")
    suspend fun getByLinkedTaskId(taskId: String): ServiceRequest?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(request: ServiceRequest)
    
    @Update
    suspend fun update(request: ServiceRequest)
    
    @Query("UPDATE service_requests SET deleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long = System.currentTimeMillis())
    
    @Query("UPDATE service_requests SET status = :status, updatedAt = :now WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, now: Long = System.currentTimeMillis())
    
    @Query("UPDATE service_requests SET linkedTaskId = :taskId, status = 'ASSIGNED', updatedAt = :now WHERE id = :id")
    suspend fun linkTask(id: String, taskId: String, now: Long = System.currentTimeMillis())
    
    /**
     * Get old requests that are completed, cancelled, or soft-deleted
     * Used for cleanup after retention period
     */
    @Query("""
        SELECT * FROM service_requests
        WHERE updatedAt < :cutoffTime
          AND (status IN ('COMPLETED', 'CANCELLED') OR deleted = 1)
        ORDER BY updatedAt ASC
    """)
    suspend fun getOldCompletedOrDeleted(cutoffTime: Long): List<ServiceRequest>
    
    /**
     * Permanently delete a request from the database
     * This cannot be undone
     */
    @Query("DELETE FROM service_requests WHERE id = :id")
    suspend fun permanentlyDelete(id: String)
}
