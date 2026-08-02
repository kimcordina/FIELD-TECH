package com.example.fieldtechv20kc.data.database.dao

import androidx.room.*
import com.example.fieldtechv20kc.data.model.ErrorLog
import kotlinx.coroutines.flow.Flow

@Dao
interface ErrorLogDao {
    
    /**
     * Observe all error logs (most recent first), limited to last 50
     */
    @Query("SELECT * FROM error_logs ORDER BY timestamp DESC LIMIT 50")
    fun observeAll(): Flow<List<ErrorLog>>
    
    /**
     * Get all error logs for export
     */
    @Query("SELECT * FROM error_logs ORDER BY timestamp DESC LIMIT 50")
    suspend fun getAll(): List<ErrorLog>
    
    /**
     * Get count of errors (for badge)
     */
    @Query("SELECT COUNT(*) FROM error_logs WHERE level = 'Error'")
    fun observeErrorCount(): Flow<Int>
    
    /**
     * Insert error log
     */
    @Insert
    suspend fun insert(errorLog: ErrorLog)
    
    /**
     * Clear all logs
     */
    @Query("DELETE FROM error_logs")
    suspend fun clearAll()
    
    /**
     * Clear old logs (keep only last 7 days)
     */
    @Query("DELETE FROM error_logs WHERE timestamp < :cutoffTime")
    suspend fun clearOldLogs(cutoffTime: Long)
    
    /**
     * Delete logs older than X days
     */
    suspend fun deleteOlderThan(days: Int) {
        val cutoffTime = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L)
        clearOldLogs(cutoffTime)
    }
}










