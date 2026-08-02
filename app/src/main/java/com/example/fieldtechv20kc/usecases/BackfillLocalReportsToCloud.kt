package com.example.fieldtechv20kc.usecases

import android.util.Log
import com.example.fieldtechv20kc.data.remote.firestore.ReportCloudDto
import com.example.fieldtechv20kc.data.remote.firestore.ReportsRemote
import com.example.fieldtechv20kc.data.repository.ReportRepository

/**
 * One-time backfill use case to push recent legacy local reports into Firestore.
 * 
 * This ensures that reports created before the immediate cloud upsert feature
 * appear in the cloud and stop showing as "pending".
 */
class BackfillLocalReportsToCloud(
    private val reportRepository: ReportRepository,
    private val reportsRemote: ReportsRemote
) {
    companion object {
        private const val TAG = "FT/CLOUD_BACKFILL"
    }
    
    /**
     * Backfills recent local reports to Firestore.
     * 
     * @param days Number of days to look back (default 90)
     */
    suspend fun run(days: Int = 90) {
        Log.d(TAG, "Starting backfill for last $days days")
        
        try {
            // Get recent local reports
            val recentReports = reportRepository.getRecentLocalReportsBlocking(days)
            Log.d(TAG, "Found ${recentReports.size} recent local reports")
            
            var backfilled = 0
            var skipped = 0
            var failed = 0
            
            for (reportWithDetails in recentReports) {
                val report = reportWithDetails.report
                val client = reportWithDetails.client
                val id = report.id.toString()
                
                try {
                    // Check if already exists in cloud
                    val exists = reportsRemote.exists(id)
                    if (exists) {
                        skipped++
                        Log.d(TAG, "Report already exists in cloud, skipping id=$id")
                        continue
                    }
                    
                    // Determine job type display name
                    val jobTypeDisplay = if (report.isCustomJobType && 
                        !report.customJobTypeDisplayName.isNullOrBlank()) {
                        report.customJobTypeDisplayName
                    } else {
                        report.jobType.displayName
                    }
                    
                    // Create cloud DTO with metadata only (pdfPath will be patched later if uploaded)
                    val dto = ReportCloudDto(
                        id = id,
                        clientId = report.clientId,
                        clientName = client?.name ?: "Unknown Client",
                        clientLocality = client?.locality ?: "",
                        technicianName = report.technicianName,
                        jobType = jobTypeDisplay,
                        timestamp = report.createdAt.time,
                        pdfPath = null,  // Will be patched later if uploaded
                        photoCount = reportWithDetails.photos.size,
                        updatedAt = System.currentTimeMillis(),
                        deleted = false
                    )
                    
                    // Upsert to Firestore
                    reportsRemote.upsertReport(dto)
                    backfilled++
                    Log.d(TAG, "Backfilled report id=$id")
                    
                } catch (t: Throwable) {
                    failed++
                    Log.e(TAG, "Backfill failed for id=$id", t)
                }
            }
            
            Log.d(TAG, "Backfill complete: backfilled=$backfilled skipped=$skipped failed=$failed")
            
        } catch (e: Exception) {
            Log.e(TAG, "Backfill process failed", e)
            throw e
        }
    }
}




