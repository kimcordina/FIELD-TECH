package com.example.fieldtechv20kc.data.repository

import android.util.Log
import com.example.fieldtechv20kc.data.database.dao.ClientDao
import com.example.fieldtechv20kc.data.database.dao.PhotoDao
import com.example.fieldtechv20kc.data.database.dao.ReportDao
import com.example.fieldtechv20kc.data.model.Client
import com.example.fieldtechv20kc.data.model.Photo
import com.example.fieldtechv20kc.data.model.Report
import com.example.fieldtechv20kc.data.model.ReportWithDetails
import com.example.fieldtechv20kc.data.remote.firestore.ReportCloudDto
import com.example.fieldtechv20kc.data.remote.firestore.ReportPhotoDto
import com.example.fieldtechv20kc.data.remote.firestore.ReportsRemote
import com.example.fieldtechv20kc.data.remote.storage.ReportStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

class ReportRepository(
    private val reportDao: ReportDao,
    private val clientDao: ClientDao,
    private val photoDao: PhotoDao,
    private val reportStorage: ReportStorage? = null,
    private val reportsRemote: ReportsRemote? = null
) {
    
    fun getAllReportsWithDetails(): Flow<List<ReportWithDetails>> {
        return reportDao.getAllReports().combine(clientDao.getAllClients()) { reports, clients ->
            reports.map { report ->
                val client = clients.find { it.id == report.clientId }
                val photos = emptyList<Photo>() // We'll load photos separately if needed
                if (client != null) {
                    ReportWithDetails(report, client, photos)
                } else {
                    null
                }
            }.filterNotNull()
        }
    }
    
    suspend fun getReportWithDetailsById(id: Long): ReportWithDetails? {
        return try {
            val report = reportDao.getReportById(id) ?: return null
            val client = if (report.clientId != null) {
                clientDao.getClientById(report.clientId)
            } else null
            val photos = emptyList<Photo>() // Simplified for now
            ReportWithDetails(report, client, photos)
        } catch (e: Exception) {
            null
        }
    }
    
    suspend fun getReportsWithDetailsByLocality(locality: String): List<ReportWithDetails> {
        return try {
            val reports = reportDao.getReportsByLocality(locality)
            
            reports.map { report ->
                val client = if (report.clientId != null) {
                    clientDao.getClientById(report.clientId)
                } else null
                ReportWithDetails(report, client, emptyList())
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    suspend fun insertClient(client: Client) {
        clientDao.upsert(client)
    }
    
    suspend fun getClientById(id: String): Client? {
        return clientDao.getClientById(id)
    }
    
    suspend fun insertReport(report: Report): Long {
        val reportId = reportDao.insertReport(report)
        
        // DISABLED: Immediate cloud upsert now handled by OutboxWorker only
        // This prevents duplicate uploads and ensures proper retry logic
        // upsertReportMetaToCloud(reportId, report)
        
        return reportId
    }
    
    /**
     * DISABLED: Upserts report metadata to Firestore immediately after local save.
     * Now handled by OutboxWorker for proper retry and queue management.
     */
    @Deprecated("Use OutboxWorker instead", ReplaceWith("OutboxWorker"))
    private fun upsertReportMetaToCloud(reportId: Long, report: Report) {
        // DISABLED - All uploads now go through OutboxWorker
        /*
        if (reportsRemote == null) return
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = report.clientId?.let { clientDao.getClientById(it) }
                val jobTypeDisplay = if (report.isCustomJobType && !report.customJobTypeDisplayName.isNullOrBlank()) {
                    report.customJobTypeDisplayName
                } else {
                    report.jobType.displayName
                }
                
                val dto = ReportCloudDto(
                    id = reportId.toString(),
                    clientId = report.clientId,
                    clientName = client?.name ?: "Unknown Client",
                    clientLocality = client?.locality ?: "",
                    technicianName = report.technicianName,
                    jobType = jobTypeDisplay,
                    timestamp = report.createdAt.time,
                    pdfPath = null,  // Will be set after PDF upload
                    photoCount = 0,   // Will be incremented as photos upload
                    updatedAt = System.currentTimeMillis(),
                    deleted = false
                )
                
                reportsRemote.upsertReport(dto)
                Log.d("FT/CLOUD_REPORT", "Upsert meta ok id=$reportId")
            } catch (t: Throwable) {
                Log.e("FT/CLOUD_REPORT", "Upsert meta FAILED id=$reportId", t)
                // Optional: enqueue outbox job here
            }
        }
        */
    }
    
    suspend fun insertPhoto(photo: Photo): Long {
        val photoId = photoDao.insertPhoto(photo)
        
        // DISABLED: Photo upload now handled by OutboxWorker only
        // This prevents duplicate uploads and ensures proper retry logic
        // uploadPhotoToCloud(photo.reportId, photoId, photo.filePath, photo.caption)
        
        return photoId
    }
    
    /**
     * DISABLED: Uploads photo to cloud immediately after local save.
     * Now handled by OutboxWorker for proper retry and queue management.
     */
    @Deprecated("Use OutboxWorker instead", ReplaceWith("OutboxWorker"))
    private fun uploadPhotoToCloud(reportId: Long, photoId: Long, filePath: String, description: String) {
        // DISABLED - All uploads now go through OutboxWorker
        /*
        if (reportStorage == null || reportsRemote == null) return
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val file = File(filePath)
                if (!file.exists()) return@launch
                
                // Upload photo to Storage
                val storagePath = reportStorage.uploadPhoto(reportId, photoId, file)
                
                // Upload photo metadata to Firestore
                val photoDto = ReportPhotoDto(
                    id = photoId,
                    path = storagePath,
                    description = description.takeIf { it.isNotBlank() },
                    timestamp = System.currentTimeMillis()
                )
                reportsRemote.upsertPhoto(reportId, photoDto)
                
                // Increment photo count on report doc
                val currentPhotos = photoDao.getPhotosByReportIdSync(reportId)
                reportsRemote.upsertReport(
                    ReportCloudDto(
                        id = reportId.toString(),
                        photoCount = currentPhotos.size,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                
                Log.d("FT/REPORT_UPLOAD", "Photo uploaded id=$reportId photoId=$photoId count=${currentPhotos.size}")
            } catch (t: Throwable) {
                Log.e("FT/REPORT_UPLOAD", "Photo upsert FAILED id=$reportId photoId=$photoId", t)
                // Optional: enqueue outbox job
            }
        }
        */
    }
    
    suspend fun updateReport(report: Report) {
        reportDao.updateReport(report)
    }
    
    suspend fun deleteReport(report: Report) {
        reportDao.deleteReport(report)
        photoDao.deletePhotosByReportId(report.id)
    }
    
    suspend fun deleteReportById(reportId: Long) {
        val report = reportDao.getReportById(reportId)
        if (report != null) {
            deleteReport(report)
        }
    }
    
    // Trash bin methods
    suspend fun moveToTrash(reportId: Long) {
        reportDao.softDelete(reportId)
    }
    
    suspend fun restoreFromTrash(reportId: Long) {
        reportDao.restore(reportId)
    }
    
    fun observeDeletedReports(): Flow<List<ReportWithDetails>> {
        return reportDao.getDeletedReports().combine(clientDao.getAllClients()) { reports, clients ->
            reports.map { report ->
                val client = clients.find { it.id == report.clientId }
                val photos = emptyList<Photo>() // Simplified for now
                ReportWithDetails(report, client, photos)
            }
        }
    }
    
    fun getPhotosByReportId(reportId: Long): Flow<List<Photo>> {
        return photoDao.getPhotosByReportId(reportId)
    }
    
    /**
     * Observes local pending reports that haven't been fully synced to cloud.
     * 
     * Returns reports created in the last 14 days with a local PDF.
     * These are considered "pending" until they appear in the cloud feed.
     */
    fun observeLocalPendingReports(): Flow<List<ReportWithDetails>> {
        val fourteenDaysAgo = System.currentTimeMillis() - (14 * 24 * 60 * 60 * 1000L)
        
        return reportDao.getAllReports().combine(clientDao.getAllClients()) { reports, clients ->
            reports.filter { report ->
                // Include reports that:
                // 1. Have a local PDF generated
                // 2. Were created in the last 14 days
                report.pdfPath.isNotEmpty() && report.createdAt.time > fourteenDaysAgo
            }.map { report ->
                val client = clients.find { it.id == report.clientId }
                val photos = emptyList<Photo>() // Simplified for now
                ReportWithDetails(report, client, photos)
            }
        }
    }
    
    /**
     * Observes recent local reports (last 30 days) for unified view.
     * Used to provide local PDF fallback when cloud PDF isn't ready yet.
     */
    fun observeRecentLocalReports(): Flow<List<ReportWithDetails>> {
        val thirtyDaysAgo = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000L)
        
        return reportDao.getAllReports().combine(clientDao.getAllClients()) { reports, clients ->
            reports.filter { report ->
                report.createdAt.time > thirtyDaysAgo
            }.map { report ->
                val client = clients.find { it.id == report.clientId }
                val photos = emptyList<Photo>() // Simplified for now
                ReportWithDetails(report, client, photos)
            }
        }
    }
    
    /**
     * Gets the local PDF path for a report if it exists.
     */
    suspend fun getLocalPdfPath(reportId: Long): String? {
        val report = reportDao.getReportById(reportId)
        return report?.pdfPath?.takeIf { it.isNotEmpty() }
    }
    
    /**
     * Gets recent local reports as a blocking list (for backfill).
     * 
     * @param days Number of days to look back
     * @return List of reports with details
     */
    suspend fun getRecentLocalReportsBlocking(days: Int): List<ReportWithDetails> {
        val cutoffTime = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L)
        
        val reports = reportDao.getAllReports().first()
        val clients = clientDao.getAllClients().first()
        
        return reports.filter { report ->
            report.createdAt.time > cutoffTime
        }.map { report ->
            val client = clients.find { it.id == report.clientId }
            val photos = try {
                photoDao.getPhotosByReportIdSync(report.id)
            } catch (e: Exception) {
                emptyList()
            }
            ReportWithDetails(report, client, photos)
        }
    }
}
