package com.example.fieldtechv20kc.data.database.dao

import androidx.room.*
import com.example.fieldtechv20kc.data.model.Report
import kotlinx.coroutines.flow.Flow

@Dao
interface ReportDao {
    
    // Get all non-deleted reports
    @Query("SELECT * FROM reports WHERE deleted = 0 ORDER BY createdAt DESC")
    fun getAllReports(): Flow<List<Report>>
    
    // Get deleted reports (trash bin)
    @Query("SELECT * FROM reports WHERE deleted = 1 ORDER BY createdAt DESC")
    fun getDeletedReports(): Flow<List<Report>>
    
    @Query("SELECT * FROM reports WHERE id = :id")
    suspend fun getReportById(id: Long): Report?
    
    // Get reports by client ID (for client detail screen) - exclude deleted
    @Query("SELECT * FROM reports WHERE clientId = :clientId AND deleted = 0 ORDER BY createdAt DESC")
    fun getReportsByClientId(clientId: String): Flow<List<Report>>
    
    // Get reports by locality (existing - updated to handle String clientId) - exclude deleted
    @Query("SELECT r.* FROM reports r INNER JOIN clients c ON r.clientId = c.id WHERE c.locality = :locality AND r.deleted = 0 ORDER BY r.createdAt DESC")
    suspend fun getReportsByLocality(locality: String): List<Report>
    
    // Assign a client to a report
    @Query("UPDATE reports SET clientId = :clientId WHERE id = :reportId")
    suspend fun assignClient(reportId: Long, clientId: String)
    
    // Soft delete (move to trash)
    @Query("UPDATE reports SET deleted = 1 WHERE id = :reportId")
    suspend fun softDelete(reportId: Long)
    
    // Restore from trash
    @Query("UPDATE reports SET deleted = 0 WHERE id = :reportId")
    suspend fun restore(reportId: Long)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReport(report: Report): Long
    
    @Update
    suspend fun updateReport(report: Report)
    
    // Permanent delete (only use for emptying trash)
    @Delete
    suspend fun deleteReport(report: Report)
}
