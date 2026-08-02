package com.example.fieldtechv20kc.data.database.dao

import androidx.room.*
import com.example.fieldtechv20kc.data.model.OutboxJob
import kotlinx.coroutines.flow.Flow

@Dao
interface OutboxDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(job: OutboxJob): Long
    
    @Query("SELECT * FROM outbox_jobs WHERE quarantined = 0 ORDER BY createdAt ASC")
    suspend fun getAllActiveJobs(): List<OutboxJob>
    
    @Query("SELECT * FROM outbox_jobs WHERE quarantined = 1 ORDER BY createdAt ASC")
    suspend fun getQuarantinedJobs(): List<OutboxJob>
    
    @Query("SELECT * FROM outbox_jobs ORDER BY createdAt ASC")
    suspend fun getAllJobs(): List<OutboxJob>
    
    @Query("SELECT COUNT(*) FROM outbox_jobs WHERE quarantined = 0")
    fun observeActiveCount(): Flow<Int>
    
    @Query("SELECT COUNT(*) FROM outbox_jobs WHERE quarantined = 1")
    fun observeQuarantinedCount(): Flow<Int>
    
    @Query("SELECT COUNT(*) FROM outbox_jobs")
    fun observeCount(): Flow<Int>
    
    @Query("SELECT COUNT(*) FROM outbox_jobs WHERE quarantined = 0")
    suspend fun getActiveCount(): Int
    
    @Query("SELECT COUNT(*) FROM outbox_jobs WHERE quarantined = 1")
    suspend fun getQuarantinedCount(): Int
    
    @Query("SELECT COUNT(*) FROM outbox_jobs")
    suspend fun getCount(): Int
    
    @Delete
    suspend fun delete(job: OutboxJob)
    
    @Query("DELETE FROM outbox_jobs WHERE id = :jobId")
    suspend fun deleteById(jobId: Long)
    
    @Update
    suspend fun update(job: OutboxJob)
    
    /**
     * Mark a job as quarantined
     */
    @Query("UPDATE outbox_jobs SET quarantined = 1, lastError = :error WHERE id = :jobId")
    suspend fun quarantine(jobId: Long, error: String)
    
    /**
     * Un-quarantine a job (for manual retry)
     */
    @Query("UPDATE outbox_jobs SET quarantined = 0, attempts = 0, lastError = NULL WHERE id = :jobId")
    suspend fun unquarantine(jobId: Long)
    
    /**
     * Reset attempt count for all non-quarantined jobs (force retry)
     */
    @Query("UPDATE outbox_jobs SET attempts = 0, lastError = NULL WHERE quarantined = 0 AND attempts > 0")
    suspend fun resetAllAttempts(): Int
}



