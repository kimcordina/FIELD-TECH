package com.example.fieldtechv20kc.data.database

import androidx.room.*
import com.example.fieldtechv20kc.data.model.Statistics
import com.example.fieldtechv20kc.data.model.LocalityStatistics
import kotlinx.coroutines.flow.Flow

@Dao
interface StatisticsDao {
    
    // Global Statistics
    @Query("SELECT * FROM statistics WHERE id = 1")
    fun getStatistics(): Flow<Statistics?>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStatistics(statistics: Statistics)
    
    @Update
    suspend fun updateStatistics(statistics: Statistics)
    
    @Query("UPDATE statistics SET totalReports = totalReports + :increment WHERE id = 1 AND :increment > 0")
    suspend fun incrementTotalReports(increment: Int)
    
    @Query("UPDATE statistics SET totalReports = 0 WHERE id = 1")
    suspend fun resetTotalReports()
    
    // Locality Statistics
    @Query("SELECT * FROM locality_statistics ORDER BY reportCount DESC, locality ASC")
    fun getAllLocalityStatistics(): Flow<List<LocalityStatistics>>
    
    @Query("SELECT * FROM locality_statistics WHERE locality = :locality")
    suspend fun getLocalityStatistics(locality: String): LocalityStatistics?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocalityStatistics(localityStatistics: LocalityStatistics)
    
    @Update
    suspend fun updateLocalityStatistics(localityStatistics: LocalityStatistics)
    
    @Query("DELETE FROM locality_statistics WHERE locality = :locality")
    suspend fun deleteLocalityStatistics(locality: String)
    
    @Query("DELETE FROM locality_statistics")
    suspend fun resetAllLocalityStatistics()
    
    @Transaction
    suspend fun incrementLocalityReport(locality: String, increment: Int = 1) {
        // Only allow positive increments (no decrements)
        if (increment <= 0) return
        
        val existing = getLocalityStatistics(locality)
        if (existing != null) {
            val updated = existing.copy(
                reportCount = existing.reportCount + increment,
                lastUpdated = System.currentTimeMillis()
            )
            updateLocalityStatistics(updated)
        } else {
            val newStats = LocalityStatistics(
                locality = locality,
                reportCount = increment,
                lastUpdated = System.currentTimeMillis()
            )
            insertLocalityStatistics(newStats)
        }
    }
    
    @Transaction
    suspend fun resetLocalityReport(locality: String) {
        deleteLocalityStatistics(locality)
    }
}

