package com.example.fieldtechv20kc.usecases

import android.util.Log
import com.example.fieldtechv20kc.data.repository.OutboxRepository
import com.example.fieldtechv20kc.data.repository.ReportRepository

class EnqueueUnsyncedReports(
    private val reportRepo: ReportRepository,
    private val outbox: OutboxRepository
) {
    data class ResultSummary(
        val pdfJobs: Int,
        val metaJobs: Int,
        val scannedReports: Int
    )

    suspend fun run(days: Int = 365): ResultSummary {
        val recent = reportRepo.getRecentLocalReportsBlocking(days)
        var pdfJobs = 0
        var metaJobs = 0

        for (r in recent) {
            // Enqueue PDF upload if PDF exists and file is accessible
            val pdfPath = r.report.pdfPath
            if (!pdfPath.isNullOrBlank() && pdfPath.isNotEmpty()) {
                val pdfFile = java.io.File(pdfPath)
                if (pdfFile.exists()) {
                    outbox.enqueueUploadPdf(r.report.id, pdfPath)
                    pdfJobs++
                } else {
                    Log.w("FT/OUTBOX", "PDF file not found for report ${r.report.id}: $pdfPath")
                }
            }
            
            // Enqueue metadata upsert
            outbox.enqueueUpsertReport(r.report.id)
            metaJobs++

            // Note: Photos are NOT uploaded separately for reports - they're embedded in the PDF
        }
        
        Log.d("FT/OUTBOX", "Backfill enqueue: scanned=${recent.size} pdf=$pdfJobs meta=$metaJobs")
        return ResultSummary(pdfJobs, metaJobs, recent.size)
    }
}



