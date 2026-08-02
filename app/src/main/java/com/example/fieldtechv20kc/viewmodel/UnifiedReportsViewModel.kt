package com.example.fieldtechv20kc.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtechv20kc.data.remote.firestore.ReportCloudDto
import com.example.fieldtechv20kc.data.remote.firestore.ReportsRemote
import com.example.fieldtechv20kc.data.remote.storage.ReportStorage
import com.example.fieldtechv20kc.data.repository.OutboxRepository
import com.example.fieldtechv20kc.data.repository.ReportRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.File

/**
 * Unified row representing either a cloud-synced report or a local pending draft.
 */
data class UnifiedReportRow(
    val id: String,
    val clientName: String,
    val clientLocality: String,
    val jobType: String,
    val technicianName: String,
    val timestamp: Long,
    val cloudExists: Boolean,          // NEW: true if report exists in Firestore
    val pdfPathCloud: String? = null,
    val photoCount: Int = 0,
    val localPdfPath: String? = null,
    val reportRef: String = ""
) {
    val isFullySynced: Boolean get() = cloudExists && !pdfPathCloud.isNullOrBlank()
    val isPending: Boolean get() = !cloudExists || pdfPathCloud.isNullOrBlank()
}

/**
 * UnifiedReportsViewModel combines cloud-synced reports with local pending drafts.
 * 
 * Pending logic:
 * - Report is pending if: no cloud doc exists OR cloud doc has no pdfPath
 * - Report is fully synced if: cloud doc exists AND has pdfPath
 */
class UnifiedReportsViewModel(
    private val reportsRemote: ReportsRemote,
    private val reportStorage: ReportStorage,
    private val reportRepository: ReportRepository,
    private val outbox: OutboxRepository
) : ViewModel() {

    companion object {
        private const val TAG = "FT/REPORTS/UNIFIED"
    }

    // 1) Cloud feed → map by id
    private val cloudDtos: Flow<List<ReportCloudDto>> = reportsRemote.listenAll()

    private val cloudRows: Flow<Map<String, UnifiedReportRow>> =
        cloudDtos.map { list ->
            Log.d(TAG, "Cloud reports received: ${list.size}")
            list.associateBy(
                keySelector = { requireNotNull(it.id) },
                valueTransform = {
                    UnifiedReportRow(
                        id = requireNotNull(it.id),
                        clientName = it.clientName,
                        clientLocality = it.clientLocality,
                        jobType = it.jobType,
                        technicianName = it.technicianName,
                        timestamp = it.timestamp,
                        cloudExists = true,
                        pdfPathCloud = it.pdfPath,
                        photoCount = it.photoCount,
                        localPdfPath = null,
                        reportRef = it.reportRef
                    )
                }
            )
        }

    // 2) Local recent (last 30 days) with local PDF path
    private val localRows: Flow<List<UnifiedReportRow>> =
        reportRepository.observeRecentLocalReports().map { locals ->
            Log.d(TAG, "Local recent reports: ${locals.size}")
            locals.map { reportWithDetails ->
                val jobTypeDisplay = if (reportWithDetails.report.isCustomJobType && 
                    !reportWithDetails.report.customJobTypeDisplayName.isNullOrBlank()) {
                    reportWithDetails.report.customJobTypeDisplayName
                } else {
                    reportWithDetails.report.jobType.displayName
                }
                
                UnifiedReportRow(
                    id = reportWithDetails.report.id.toString(),
                    clientName = reportWithDetails.client?.name ?: "Unknown Client",
                    clientLocality = reportWithDetails.client?.locality ?: "",
                    jobType = jobTypeDisplay,
                    technicianName = reportWithDetails.report.technicianName,
                    timestamp = reportWithDetails.report.createdAt.time,
                    cloudExists = false,  // May be overwritten by merge
                    pdfPathCloud = null,
                    photoCount = reportWithDetails.photos.size,
                    localPdfPath = reportWithDetails.report.pdfPath.takeIf { it.isNotEmpty() },
                    reportRef = reportWithDetails.report.reportRef
                )
            }
        }

    // Track which report IDs have ever been in cloud (for deletion detection)
    private val knownCloudReportIds = MutableStateFlow<Set<String>>(emptySet())
    
    // 3) Merge: prefer cloud when present; carry localPdfPath for fallback
    // Also: filter out local-only reports that were deleted from cloud
    val unified: StateFlow<List<UnifiedReportRow>> =
        combine(localRows, cloudRows, knownCloudReportIds) { local, cloudMap, knownIds ->
            val result = mutableListOf<UnifiedReportRow>()

            // Update known cloud IDs (reports that are currently in cloud OR were before)
            val currentCloudIds = cloudMap.keys
            knownCloudReportIds.value = knownCloudReportIds.value + currentCloudIds

            // First, take all locals; if cloud exists for same id, overlay cloud fields
            local.forEach { l ->
                val c = cloudMap[l.id]
                if (c != null) {
                    // Cloud version exists → use it with local PDF / ref fallback
                    result += c.copy(
                        localPdfPath = l.localPdfPath,
                        reportRef = c.reportRef.ifBlank { l.reportRef }
                    )
                } else {
                    // Local-only report (not in cloudMap)
                    // Check if this report was EVER in the cloud before
                    val wasInCloud = knownIds.contains(l.id)
                    
                    if (wasInCloud) {
                        // WAS in cloud BUT not in cloudMap anymore → DELETED elsewhere, filter out
                        Log.w(TAG, "⚠️ Filtering deleted report ${l.id} (was in cloud, now removed)")
                    } else {
                        // Never synced yet → keep it visible regardless of age.
                        // OFFLINE-FIRST: a technician without signal may hold reports
                        // locally for hours/days; hiding them made offline saves look
                        // like failures. They show as "Pending upload" until synced.
                        result += l
                    }
                }
            }

            // Then, add cloud-only items that have no local counterpart (older team reports)
            cloudMap.values.forEach { c ->
                if (result.none { it.id == c.id }) result += c
            }

            // Defensive de-duplication: remove any legacy duplicates by ID
            // This handles cases where parallel upload systems created multiple docs
            val deduped = result.distinctBy { it.id }
            val sorted = deduped.sortedByDescending { it.timestamp }
            
            if (result.size != deduped.size) {
                Log.w(TAG, "Removed ${result.size - deduped.size} duplicate report(s)")
            }
            Log.d(TAG, "Unified list: cloud=${cloudMap.size} local=${local.size} merged=${sorted.size}")
            
            sorted
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    
    // Background cleanup job: Remove stale local reports that were deleted from cloud
    init {
        viewModelScope.launch {
            combine(localRows, cloudRows, knownCloudReportIds) { local, cloudMap, knownIds ->
                val now = System.currentTimeMillis()
                val twoDaysAgo = now - (2 * 24 * 60 * 60 * 1000L) // Use 2 days for cleanup (more aggressive)
                
                local.filter { l ->
                    // Only delete local reports that:
                    // 1. Were KNOWN to exist in cloud at some point (i.e. deleted elsewhere)
                    // 2. Don't exist in cloud anymore
                    // 3. Are older than 2 days
                    // NEVER delete reports that haven't synced yet - a technician may be
                    // offline for days and their reports must survive until upload succeeds.
                    knownIds.contains(l.id) && cloudMap[l.id] == null && l.timestamp < twoDaysAgo
                }.mapNotNull { it.id.toLongOrNull() }
            }.collect { reportsToDelete ->
                if (reportsToDelete.isNotEmpty()) {
                    com.example.fieldtechv20kc.utils.FTLog.i(TAG, "Cleaning up ${reportsToDelete.size} stale local report(s)")
                    reportsToDelete.forEach { reportId ->
                        try {
                            reportRepository.deleteReportById(reportId)
                            com.example.fieldtechv20kc.utils.FTLog.i(TAG, "✅ Cleaned up stale local report: $reportId")
                        } catch (e: Exception) {
                            com.example.fieldtechv20kc.utils.FTLog.e(TAG, "Failed to clean up report $reportId", e)
                        }
                    }
                }
            }
        }
    }

    /**
     * Cleans up old PDF cache files to prevent unbounded growth.
     * Call this periodically (e.g., on app start or when cache gets large).
     */
    fun cleanOldPdfCache(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cacheDir1 = File(context.cacheDir, "cached_reports")
                val cacheDir2 = File(context.cacheDir, "shared_reports")
                
                listOf(cacheDir1, cacheDir2).forEach { dir ->
                    if (dir.exists()) {
                        val files = dir.listFiles() ?: return@forEach
                        val now = System.currentTimeMillis()
                        files.forEach { file ->
                            // Delete files older than 7 days
                            if (now - file.lastModified() > 7 * 24 * 60 * 60 * 1000L) {
                                file.delete()
                                Log.d(TAG, "Deleted old cache file: ${file.name}")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clean PDF cache: ${e.message}")
            }
        }
    }
    
    /**
     * Resolves the URI for opening a report PDF.
     * Priority: cloud PDF (if synced) > local PDF (if not synced yet) > null
     * 
     * CRITICAL: Always prefer cloud PDF for fully synced reports to avoid showing
     * wrong PDFs when multiple devices have local reports with the same ID.
     * Uses FileProvider to ensure compatibility with external PDF viewers.
     */
    suspend fun resolveOpenUri(context: Context, row: UnifiedReportRow): Uri? {
        // If report is fully synced to cloud, ALWAYS use cloud PDF
        // This prevents showing wrong PDFs when report IDs collide across devices
        if (row.isFullySynced && !row.pdfPathCloud.isNullOrBlank()) {
            try {
                Log.d(TAG, "Downloading cloud PDF ${row.id} for viewing")
                val downloadUrl = reportStorage.downloadUrl(row.pdfPathCloud)
                
                // Download to cache directory
                val cacheDir = File(context.cacheDir, "cached_reports")
                cacheDir.mkdirs()
                
                // Use hash of PDF cloud path to create unique cache file
                // This prevents showing wrong PDF if data changes
                val pdfPathHash = row.pdfPathCloud.hashCode().toString().replace("-", "n")
                val cachedFile = File(cacheDir, "report_${row.id}_${pdfPathHash}.pdf")
                
                // Always re-download to ensure we have the correct PDF
                // Previous caching logic was causing technicians to see wrong PDFs
                withContext(Dispatchers.IO) {
                    val connection = java.net.URL(downloadUrl.toString()).openConnection()
                    connection.connect()
                    connection.getInputStream().use { input ->
                        cachedFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                Log.d(TAG, "Cloud PDF downloaded to cache: ${cachedFile.absolutePath}")
                
                return androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    cachedFile
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to download cloud PDF for ${row.id}: ${e.message}", e)
                // Fall through to try local PDF
            }
        }
        
        // Fallback: Use local PDF only if report is NOT fully synced yet
        // This handles reports that are still uploading or offline-only
        if (!row.isFullySynced && !row.localPdfPath.isNullOrBlank()) {
            val localFile = File(row.localPdfPath)
            if (localFile.exists()) {
                Log.d(TAG, "Using local PDF for pending report ${row.id}")
                return androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    localFile
                )
            }
        }
        
        return null
    }

    /**
     * Creates a share intent for a report.
     * ALWAYS shares the actual PDF file (not a URL link).
     * Priority: cloud PDF (if synced) > local PDF (if not synced) > null
     * 
     * CRITICAL: Always prefer cloud PDF for fully synced reports to avoid sharing
     * wrong PDFs when multiple devices have local reports with the same ID.
     */
    suspend fun shareIntent(context: Context, row: UnifiedReportRow): Intent? {
        // If report is fully synced, ALWAYS use cloud PDF
        if (row.isFullySynced && !row.pdfPathCloud.isNullOrBlank()) {
            try {
                val downloadUrl = reportStorage.downloadUrl(row.pdfPathCloud)
                
                // Download PDF to cache directory
                val cacheDir = File(context.cacheDir, "shared_reports")
                cacheDir.mkdirs()
                
                // Use hash of PDF cloud path to create unique cache file
                val pdfPathHash = row.pdfPathCloud.hashCode().toString().replace("-", "n")
                val cachedFile = File(cacheDir, "report_${row.id}_${pdfPathHash}.pdf")
                
                // Always download to ensure we share the correct PDF
                withContext(Dispatchers.IO) {
                    val connection = java.net.URL(downloadUrl.toString()).openConnection()
                    connection.connect()
                    connection.getInputStream().use { input ->
                        cachedFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                
                // Share the downloaded file
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    cachedFile
                )
                return Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Field Service Report")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to download cloud PDF for sharing: ${e.message}", e)
                // Fall through to try local PDF
            }
        }
        
        // Fallback: Use local PDF only if report is NOT fully synced yet
        if (!row.isFullySynced && !row.localPdfPath.isNullOrBlank()) {
            val localFile = File(row.localPdfPath)
            if (localFile.exists()) {
                return shareLocalPdf(context, row.localPdfPath)
            }
        }
        
        return null
    }

    private fun shareLocalPdf(context: Context, localPath: String?): Intent? {
        if (localPath.isNullOrBlank()) return null
        
        return try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                File(localPath)
            )
            Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create local share intent: ${e.message}")
            null
        }
    }

    /**
     * Deletes a report from both local and cloud storage.
     */
    // Trash bin - combine local and cloud deleted reports
    private val localDeletedReports: Flow<List<UnifiedReportRow>> =
        reportRepository.observeDeletedReports().map { deletedReports ->
            deletedReports.map { reportWithDetails ->
                val jobTypeDisplay = if (reportWithDetails.report.isCustomJobType && 
                    !reportWithDetails.report.customJobTypeDisplayName.isNullOrBlank()) {
                    reportWithDetails.report.customJobTypeDisplayName
                } else {
                    reportWithDetails.report.jobType.displayName
                }
                
                UnifiedReportRow(
                    id = reportWithDetails.report.id.toString(),
                    clientName = reportWithDetails.client?.name ?: "Unknown Client",
                    clientLocality = reportWithDetails.client?.locality ?: "",
                    jobType = jobTypeDisplay,
                    technicianName = reportWithDetails.report.technicianName,
                    timestamp = reportWithDetails.report.createdAt.time,
                    cloudExists = false,
                    pdfPathCloud = null,
                    photoCount = 0,
                    localPdfPath = reportWithDetails.report.pdfPath.takeIf { it.isNotEmpty() },
                    reportRef = reportWithDetails.report.reportRef
                )
            }
        }
    
    private val cloudDeletedReports: Flow<List<UnifiedReportRow>> =
        reportsRemote.listenDeleted().map { deletedDtos ->
            deletedDtos.map { dto ->
                UnifiedReportRow(
                    id = requireNotNull(dto.id),
                    clientName = dto.clientName,
                    clientLocality = dto.clientLocality,
                    jobType = dto.jobType,
                    technicianName = dto.technicianName,
                    timestamp = dto.timestamp,
                    cloudExists = true,
                    pdfPathCloud = dto.pdfPath,
                    photoCount = dto.photoCount,
                    localPdfPath = null,
                    reportRef = dto.reportRef
                )
            }
        }
    
    private val trashedReportsFlow: Flow<List<UnifiedReportRow>> =
        combine(localDeletedReports, cloudDeletedReports) { local, cloud ->
            // Merge and deduplicate by id (prefer local if both exist)
            val byId = mutableMapOf<String, UnifiedReportRow>()
            cloud.forEach { byId[it.id] = it }
            local.forEach { byId[it.id] = it }  // Local overwrites cloud
            byId.values.sortedByDescending { it.timestamp }
        }
    
    /**
     * Moves a report to the trash bin (soft delete in database and Firestore).
     * The report is hidden from the main list but can be restored.
     */
    fun moveToTrash(row: UnifiedReportRow) {
        viewModelScope.launch {
            try {
                // Always mark as deleted in Firestore (most reports are cloud-only)
                try {
                    reportsRemote.moveToTrash(row.id)
                    Log.d(TAG, "Moved report ${row.id} to trash (cloud)")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to move report ${row.id} to cloud trash", e)
                }
                
                // Also mark as deleted in local database if it exists
                val reportIdLong = row.id.toLongOrNull()
                if (reportIdLong != null) {
                    try {
                        reportRepository.moveToTrash(reportIdLong)
                        Log.d(TAG, "Moved report ${row.id} to trash (local)")
                    } catch (e: Exception) {
                        // Local report might not exist, that's okay
                        Log.d(TAG, "Local report ${row.id} doesn't exist, cloud-only trash")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to move report ${row.id} to trash", e)
            }
        }
    }
    
    /**
     * Moves multiple reports to the trash bin (soft delete).
     */
    fun moveMultipleToTrash(rows: List<UnifiedReportRow>) {
        viewModelScope.launch {
            rows.forEach { row ->
                try {
                    // Always mark as deleted in Firestore (most reports are cloud-only)
                    try {
                        reportsRemote.moveToTrash(row.id)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to move report ${row.id} to cloud trash", e)
                    }
                    
                    // Also mark as deleted in local database if it exists
                    val reportIdLong = row.id.toLongOrNull()
                    if (reportIdLong != null) {
                        try {
                            reportRepository.moveToTrash(reportIdLong)
                        } catch (e: Exception) {
                            // Local report might not exist, that's okay
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to move report ${row.id} to trash", e)
                }
            }
            Log.d(TAG, "Moved ${rows.size} reports to trash")
        }
    }
    
    /**
     * Restores a report from the trash bin.
     */
    fun restoreFromTrash(reportId: String) {
        viewModelScope.launch {
            try {
                // Always restore in Firestore
                try {
                    reportsRemote.restoreFromTrash(reportId)
                    Log.d(TAG, "Restored report $reportId from trash (cloud)")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to restore report $reportId in cloud", e)
                }
                
                // Also restore in local database if it exists
                val reportIdLong = reportId.toLongOrNull()
                if (reportIdLong != null) {
                    try {
                        reportRepository.restoreFromTrash(reportIdLong)
                        Log.d(TAG, "Restored report $reportId from trash (local)")
                    } catch (e: Exception) {
                        // Local report might not exist, that's okay
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore report $reportId", e)
            }
        }
    }
    
    /**
     * Restores multiple reports from the trash bin.
     */
    fun restoreMultipleFromTrash(reportIds: List<String>) {
        viewModelScope.launch {
            reportIds.forEach { reportId ->
                try {
                    // Always restore in Firestore
                    try {
                        reportsRemote.restoreFromTrash(reportId)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to restore report $reportId in cloud", e)
                    }
                    
                    // Also restore in local database if it exists
                    val reportIdLong = reportId.toLongOrNull()
                    if (reportIdLong != null) {
                        try {
                            reportRepository.restoreFromTrash(reportIdLong)
                        } catch (e: Exception) {
                            // Local report might not exist, that's okay
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to restore report $reportId", e)
                }
            }
            Log.d(TAG, "Restored ${reportIds.size} reports from trash")
        }
    }
    
    /**
     * Gets the current count of items in trash.
     * This is a suspend function because it needs to collect from the flow.
     */
    suspend fun getTrashCount(): Int {
        return trashedReportsFlow.first().size
    }
    
    /**
     * Gets all items currently in the trash bin as a Flow.
     */
    fun observeTrashItems(): Flow<List<UnifiedReportRow>> {
        return trashedReportsFlow
    }
    
    /**
     * Gets all items currently in the trash bin (one-time fetch).
     */
    suspend fun getTrashItems(): List<UnifiedReportRow> {
        return trashedReportsFlow.first()
    }
    
    /**
     * Permanently deletes a report from the trash bin.
     */
    suspend fun permanentlyDelete(reportId: String) {
        Log.d(TAG, "Permanently deleting report id=$reportId")
        
        // Local delete always
        val reportIdLong = reportId.toLongOrNull()
        if (reportIdLong != null) {
            try {
                reportRepository.deleteReportById(reportIdLong)
                Log.d(TAG, "Local delete ok id=$reportId")
            } catch (e: Exception) {
                Log.e(TAG, "Local delete failed id=$reportId", e)
            }
        }
        
        // Cloud delete
        try {
            reportsRemote.deleteReport(reportId)
            reportStorage.deleteReportBundle(reportId)
            Log.d(TAG, "Cloud delete ok id=$reportId")
        } catch (t: Throwable) {
            Log.e(TAG, "Cloud delete failed id=$reportId", t)
            // Optional: enqueue outbox jobs to delete later
        }
    }
    
    /**
     * Permanently deletes multiple reports from the trash bin.
     */
    suspend fun permanentlyDeleteMultiple(reportIds: List<String>) {
        reportIds.forEach { reportId ->
            permanentlyDelete(reportId)
        }
    }
    
    /**
     * Empties the entire trash bin (permanently deletes all trashed reports).
     */
    suspend fun emptyTrash() {
        val trashedReports = getTrashItems()
        val reportIds = trashedReports.map { it.id }
        Log.d(TAG, "Emptying trash - ${reportIds.size} reports")
        permanentlyDeleteMultiple(reportIds)
    }
    
    /**
     * Immediately and permanently deletes a report (legacy method - use mark + confirm instead).
     */
    suspend fun deleteReport(row: UnifiedReportRow) {
        Log.d(TAG, "Deleting report id=${row.id} cloudExists=${row.cloudExists}")
        
        // Local delete always
        val reportId = row.id.toLongOrNull()
        if (reportId != null) {
            try {
                reportRepository.deleteReportById(reportId)
                Log.d(TAG, "Local delete ok id=${row.id}")
            } catch (e: Exception) {
                Log.e(TAG, "Local delete failed id=${row.id}", e)
            }
        }
        
        // Cloud delete if exists
        if (row.cloudExists) {
            try {
                reportsRemote.deleteReport(row.id)
                reportStorage.deleteReportBundle(row.id)
                Log.d(TAG, "Cloud delete ok id=${row.id}")
            } catch (t: Throwable) {
                Log.e(TAG, "Cloud delete failed id=${row.id}", t)
                // Optional: enqueue outbox jobs to delete later
            }
        }
    }
    

    /**
     * Triggers a retry of all pending uploads.
     */
    fun retryAllNow(context: android.content.Context) {
        Log.d(TAG, "Retry all triggered")
        com.example.fieldtechv20kc.utils.OutboxWorkHelpers.kickNow(context)
    }

    /**
     * Triggers a retry for a specific report.
     */
    fun retryForReportId(reportId: String, context: android.content.Context) {
        Log.d(TAG, "Retry triggered for report $reportId")
        
        viewModelScope.launch {
            try {
                // Get the local report to check if PDF exists
                val reportIdLong = reportId.toLongOrNull()
                if (reportIdLong != null) {
                    val localReport = reportRepository.getReportWithDetailsById(reportIdLong)
                    val pdfPath = localReport?.report?.pdfPath
                    
                    if (!pdfPath.isNullOrBlank() && java.io.File(pdfPath).exists()) {
                        // Re-enqueue both metadata and PDF uploads
                        Log.d(TAG, "Re-enqueueing uploads for report $reportIdLong")
                        outbox.enqueueUpsertReport(reportIdLong)
                        outbox.enqueueUploadPdf(reportIdLong, pdfPath)
                    } else {
                        Log.w(TAG, "Cannot retry report $reportIdLong - PDF file not found at: $pdfPath")
                    }
                }
                
                // Kick the worker regardless
                com.example.fieldtechv20kc.utils.OutboxWorkHelpers.kickNow(context)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to retry report $reportId", e)
            }
        }
    }
}
