package com.example.fieldtechv20kc.data.database.dao

import androidx.room.*
import com.example.fieldtechv20kc.data.model.ServiceTask
import com.example.fieldtechv20kc.data.model.ServiceTaskWithClient
import com.example.fieldtechv20kc.data.model.TaskStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ServiceTasksDao {
    
    /**
     * Observe tasks with optional filters
     */
    @Query("""
        SELECT * FROM service_tasks
        WHERE deleted = 0
          AND (:assignee IS NULL OR LOWER(assignedToName) = LOWER(:assignee))
          AND (:fromDate IS NULL OR scheduledDate >= :fromDate)
          AND (:toDate IS NULL OR scheduledDate < :toDate)
          AND (:status IS NULL OR status = :status)
        ORDER BY scheduledDate, assignedToName, status
    """)
    fun observe(
        assignee: String?,
        fromDate: Long?,
        toDate: Long?,
        status: String?
    ): Flow<List<ServiceTask>>
    
    /**
     * Observe tasks with client details
     */
    @Transaction
    @Query("""
        SELECT service_tasks.* FROM service_tasks
        LEFT JOIN clients ON service_tasks.clientId = clients.id
        WHERE service_tasks.deleted = 0
          AND (:assignee IS NULL OR LOWER(service_tasks.assignedToName) = LOWER(:assignee))
          AND (:fromDate IS NULL OR service_tasks.scheduledDate >= :fromDate)
          AND (:toDate IS NULL OR service_tasks.scheduledDate < :toDate)
          AND (:status IS NULL OR service_tasks.status = :status)
        ORDER BY service_tasks.scheduledDate, service_tasks.assignedToName, service_tasks.status
    """)
    fun observeWithClients(
        assignee: String?,
        fromDate: Long?,
        toDate: Long?,
        status: String?
    ): Flow<List<ServiceTaskWithClient>>
    
    /**
     * Observe tasks for a specific client
     */
    @Query("""
        SELECT * FROM service_tasks
        WHERE deleted = 0 AND clientId = :clientId
        ORDER BY scheduledDate DESC
    """)
    fun observeForClient(clientId: String): Flow<List<ServiceTask>>
    
    /**
     * Get pending task for a client (for color coding)
     */
    @Query("""
        SELECT * FROM service_tasks
        WHERE deleted = 0 
          AND clientId = :clientId 
          AND status = 'PENDING'
        ORDER BY scheduledDate ASC
        LIMIT 1
    """)
    suspend fun getPendingTaskForClient(clientId: String): ServiceTask?
    
    /**
     * Get ALL pending tasks for a client (for task selection)
     */
    @Query("""
        SELECT * FROM service_tasks
        WHERE deleted = 0 
          AND clientId = :clientId 
          AND status = 'PENDING'
        ORDER BY scheduledDate ASC
    """)
    suspend fun getPendingByClientOnce(clientId: String): List<ServiceTask>
    
    /**
     * Get single task by ID
     */
    @Query("SELECT * FROM service_tasks WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ServiceTask?
    
    /**
     * Get single task by ID once (for sync comparison)
     */
    @Query("SELECT * FROM service_tasks WHERE id = :id LIMIT 1")
    suspend fun getByIdOnce(id: String): ServiceTask?
    
    /**
     * Observe single task by ID
     */
    @Query("SELECT * FROM service_tasks WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<ServiceTask?>
    
    /**
     * Insert or replace task
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: ServiceTask)
    
    /**
     * Update task
     */
    @Update
    suspend fun update(task: ServiceTask)
    
    /**
     * Update task status
     */
    @Query("UPDATE service_tasks SET status = :status, updatedAt = :now WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, now: Long)
    
    /**
     * Link task to report and mark as DONE
     */
    @Query("""
        UPDATE service_tasks 
        SET linkedReportId = :reportId, status = 'DONE', updatedAt = :now 
        WHERE id = :id
    """)
    suspend fun linkReportAndComplete(id: String, reportId: String, now: Long)
    
    /**
     * Soft delete task
     */
    @Query("UPDATE service_tasks SET deleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)
    
    /**
     * Hard delete (for cleanup)
     */
    @Delete
    suspend fun delete(task: ServiceTask)
    
    /**
     * Get old tasks that are completed (DONE), cancelled, or soft-deleted
     * Used for cleanup after retention period
     */
    @Query("""
        SELECT * FROM service_tasks
        WHERE updatedAt < :cutoffTime
          AND (status IN ('DONE', 'CANCELLED') OR deleted = 1)
        ORDER BY updatedAt ASC
    """)
    suspend fun getOldCompletedOrDeleted(cutoffTime: Long): List<ServiceTask>
    
    /**
     * Permanently delete a task from the database
     * This cannot be undone
     */
    @Query("DELETE FROM service_tasks WHERE id = :id")
    suspend fun permanentlyDelete(id: String)
}



