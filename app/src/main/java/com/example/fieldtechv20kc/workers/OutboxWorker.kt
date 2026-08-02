package com.example.fieldtechv20kc.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.fieldtechv20kc.data.database.AppDatabase
import com.example.fieldtechv20kc.data.model.OutboxJob
import com.example.fieldtechv20kc.data.remote.firestore.ReportCloudDto
import com.example.fieldtechv20kc.data.remote.firestore.ReportsRemote
import com.example.fieldtechv20kc.data.remote.storage.ReportStorage
import com.example.fieldtechv20kc.data.repository.OutboxRepository
import com.example.fieldtechv20kc.utils.FTLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class OutboxWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "OUTBOX"
        private const val MAX_RETRIES = 10  // Retry budget before quarantine
        // Per-job timeout: on "zombie" networks (connected but no real internet,
        // common in low-signal field areas) Firestore/Storage calls can hang
        // indefinitely. Fail fast so WorkManager retries with backoff instead.
        private const val JOB_TIMEOUT_MS = 120_000L
    }

    private val db = AppDatabase.getDatabase(appContext)
    private val outbox = OutboxRepository.get()
    private val reportsRemote = ReportsRemote()
    private val reportStorage = ReportStorage()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        FTLog.i(TAG, "OutboxWorker started (attempt ${runAttemptCount + 1})")
        
        try {
            // Only process non-quarantined jobs
            val jobs = db.outboxDao().getAllActiveJobs()
            FTLog.i(TAG, "Processing ${jobs.size} active jobs")
            
            if (jobs.isEmpty()) {
                FTLog.i(TAG, "No jobs to process")
                return@withContext Result.success()
            }
            
            var successCount = 0
            var failCount = 0
            var quarantinedCount = 0
            
            for (job in jobs) {
                // Check if we're being stopped (network lost, etc.)
                if (isStopped) {
                    FTLog.w(TAG, "Worker stopped mid-flight, will retry remaining ${jobs.size - successCount - failCount} jobs")
                    return@withContext Result.retry()
                }
                
                if (job.type !in setOf("UPLOAD_PDF", "UPLOAD_PHOTO", "UPSERT_REPORT")) {
                    FTLog.w(TAG, "Unknown job type: ${job.type}, deleting")
                    outbox.deleteJob(job)
                    continue
                }
                
                try {
                    kotlinx.coroutines.withTimeout(JOB_TIMEOUT_MS) {
                        when (job.type) {
                            "UPLOAD_PDF" -> processUploadPdf(job)
                            "UPLOAD_PHOTO" -> processUploadPhoto(job)
                            "UPSERT_REPORT" -> processUpsertReport(job)
                        }
                    }
                    
                    // Success - delete job
                    outbox.deleteJob(job)
                    successCount++
                    FTLog.i(TAG, "✅ Job ${job.id} (${job.type}) succeeded")
                    
                } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                    // Job hung (zombie network) - count as a normal failure so it retries
                    failCount++
                    val errorMsg = "Timed out after ${JOB_TIMEOUT_MS / 1000}s (poor connection?)"
                    FTLog.e(TAG, "⏱️ Job ${job.id} (${job.type}) $errorMsg")
                    
                    val updatedJob = job.copy(
                        attempts = job.attempts + 1,
                        lastError = errorMsg,
                        lastAttemptAt = System.currentTimeMillis()
                    )
                    if (updatedJob.attempts >= MAX_RETRIES) {
                        db.outboxDao().quarantine(job.id, errorMsg)
                        quarantinedCount++
                        FTLog.w(TAG, "Job ${job.id} quarantined after $MAX_RETRIES attempts")
                    } else {
                        outbox.updateJob(updatedJob)
                    }
                    
                } catch (e: kotlinx.coroutines.CancellationException) {
                    // Worker was cancelled (network lost, etc.) - don't update job state, just bail
                    FTLog.w(TAG, "Job ${job.id} cancelled due to worker cancellation, will retry later")
                    throw e  // Re-throw to stop processing
                    
                } catch (e: Exception) {
                    failCount++
                    val errorMsg = "${e.javaClass.simpleName}: ${e.message}"
                    FTLog.e(TAG, "❌ Job ${job.id} (${job.type}) failed: $errorMsg", e)
                    
                    // Update attempts and error
                    val updatedJob = job.copy(
                        attempts = job.attempts + 1,
                        lastError = errorMsg,
                        lastAttemptAt = System.currentTimeMillis()
                    )
                    
                    if (updatedJob.attempts >= MAX_RETRIES) {
                        // Quarantine job - stop auto-retrying
                        db.outboxDao().quarantine(job.id, errorMsg)
                        quarantinedCount++
                        FTLog.w(TAG, "Job ${job.id} quarantined after $MAX_RETRIES attempts")
                    } else {
                        // Update job for next retry
                        outbox.updateJob(updatedJob)
                        FTLog.w(TAG, "Job ${job.id} will retry (attempt ${updatedJob.attempts}/$MAX_RETRIES)")
                    }
                }
            }
            
            val summary = "Finished: $successCount succeeded, $failCount failed, $quarantinedCount quarantined"
            FTLog.i(TAG, summary)
            
            // Return success even if some jobs failed (they'll retry later)
            Result.success()
            
        } catch (e: kotlinx.coroutines.CancellationException) {
            FTLog.w(TAG, "OutboxWorker was cancelled (likely network constraint lost), will retry when network returns")
            // Don't call Result.retry() - let WorkManager handle it with constraints
            throw e  // Re-throw CancellationException to properly signal cancellation
            
        } catch (e: Exception) {
            FTLog.e(TAG, "OutboxWorker critical error", e)
            // Retry the entire worker (exponential backoff handled by WorkManager)
            Result.retry()
        }
    }

    private suspend fun processUploadPdf(job: OutboxJob) {
        val localPath = job.payload
        val file = File(localPath)
        
        if (!file.exists()) {
            FTLog.e(TAG, "PDF file not found: $localPath")
            throw java.io.FileNotFoundException("PDF file not found: $localPath")
        }
        
        FTLog.i(TAG, "Uploading PDF for report ${job.reportId} (${file.length() / 1024}KB)")
        val storagePath = reportStorage.uploadPdf(job.reportId, file)
        
        // Patch the pdfPath in Firestore
        reportsRemote.patchPdfPath(job.reportId.toString(), storagePath)
        FTLog.i(TAG, "PDF uploaded and patched: $storagePath")
    }

    private suspend fun processUploadPhoto(job: OutboxJob) {
        val json = JSONObject(job.payload)
        val photoId = json.getLong("photoId")
        val localPath = json.getString("path")
        val file = File(localPath)
        
        if (!file.exists()) {
            FTLog.e(TAG, "Photo file not found: $localPath")
            throw java.io.FileNotFoundException("Photo file not found: $localPath")
        }
        
        FTLog.i(TAG, "Uploading photo $photoId for report ${job.reportId} (${file.length() / 1024}KB)")
        val storagePath = reportStorage.uploadPhoto(job.reportId, photoId, file)
        
        // Increment photo count in Firestore
        reportsRemote.incrementPhotoCount(job.reportId.toString())
        FTLog.i(TAG, "Photo uploaded: $storagePath")
    }

    private suspend fun processUpsertReport(job: OutboxJob) {
        val reportId = job.reportId
        
        // Get report from local DB
        val report = db.reportDao().getReportById(reportId)
        if (report == null) {
            FTLog.w(TAG, "Report $reportId not found in local DB, skipping")
            return
        }
        
        FTLog.i(TAG, "Upserting report metadata for report $reportId")
        
        val client = report.clientId?.let { db.clientDao().getClientById(it) }
        val photos = db.photoDao().getPhotosByReportIdSync(reportId)
        
        val jobTypeDisplay = if (report.isCustomJobType && !report.customJobTypeDisplayName.isNullOrBlank()) {
            report.customJobTypeDisplayName
        } else {
            report.jobType.displayName
        }
        
        val dto = ReportCloudDto(
            id = reportId.toString(),
            clientId = client?.id,
            clientName = client?.name ?: "Unknown Client",
            clientLocality = client?.locality ?: "",
            technicianName = report.technicianName,
            jobType = jobTypeDisplay,
            timestamp = report.createdAt.time,
            pdfPath = null,  // Will be patched by UPLOAD_PDF job
            photoCount = photos.size,
            updatedAt = System.currentTimeMillis(),
            deleted = false,
            reportRef = report.reportRef
        )
        
        Log.d(TAG, "Upserting report metadata for ${reportId}")
        reportsRemote.upsertReport(dto)
        Log.d(TAG, "Report metadata upserted")
    }
}



