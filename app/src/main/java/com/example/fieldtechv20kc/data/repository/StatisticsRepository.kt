package com.example.fieldtechv20kc.data.repository

import com.example.fieldtechv20kc.data.database.StatisticsDao
import com.example.fieldtechv20kc.data.model.Statistics
import com.example.fieldtechv20kc.data.model.LocalityStatistics
import kotlinx.coroutines.flow.Flow

class StatisticsRepository(
    private val statisticsDao: StatisticsDao
) {
    
    // Global Statistics
    fun getStatistics(): Flow<Statistics?> = statisticsDao.getStatistics()
    
    suspend fun incrementTotalReports() {
        statisticsDao.incrementTotalReports(1)
    }
    
    // Note: Removed decrementTotalReports - counters should only be reset manually, not decremented on deletion
    
    suspend fun resetTotalReports() {
        statisticsDao.resetTotalReports()
    }
    
    // Locality Statistics
    fun getAllLocalityStatistics(): Flow<List<LocalityStatistics>> = statisticsDao.getAllLocalityStatistics()
    
    suspend fun incrementLocalityReport(locality: String) {
        statisticsDao.incrementLocalityReport(locality, 1)
    }
    
    // Note: Removed decrementLocalityReport - counters should only be reset manually, not decremented on deletion
    
    suspend fun resetLocalityReports(locality: String) {
        statisticsDao.resetLocalityReport(locality)
    }
    
    suspend fun resetAllLocalityStatistics() {
        statisticsDao.resetAllLocalityStatistics()
    }
    
    // Combined operations
    suspend fun onReportCreated(locality: String) {
        incrementTotalReports()
        incrementLocalityReport(locality)
    }
    
    // Note: Removed onReportDeleted - counters should only be reset manually, not decremented on deletion
}
