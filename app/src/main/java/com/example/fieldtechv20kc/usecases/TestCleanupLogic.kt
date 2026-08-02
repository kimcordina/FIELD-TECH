package com.example.fieldtechv20kc.usecases

import android.util.Log
import com.example.fieldtechv20kc.data.repository.ServiceRequestsRepository
import com.example.fieldtechv20kc.data.repository.ServiceTasksRepository
import java.util.concurrent.TimeUnit

/**
 * Test utility to verify cleanup logic without actually deleting anything
 * Call this from settings screen to test what would be deleted
 */
class TestCleanupLogic(
    private val requestsRepository: ServiceRequestsRepository,
    private val tasksRepository: ServiceTasksRepository
) {
    companion object {
        private const val TAG = "FT/CLEANUP_TEST"
    }

    /**
     * Simulate cleanup and report what would be deleted
     * Does NOT actually delete anything - just shows what would be deleted
     */
    suspend fun simulateCleanup(retentionDays: Int = 14): String {
        val cutoffTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays.toLong())
        val now = System.currentTimeMillis()
        val cutoffDaysAgo = TimeUnit.MILLISECONDS.toDays(now - cutoffTime)
        
        val report = StringBuilder()
        report.appendLine("=== CLEANUP SIMULATION ===")
        report.appendLine("Retention period: $retentionDays days")
        report.appendLine("Cutoff date: $cutoffDaysAgo days ago")
        report.appendLine("Cutoff timestamp: $cutoffTime")
        report.appendLine()
        
        try {
            // Check requests
            val oldRequests = requestsRepository.getOldCompletedOrDeleted(cutoffTime)
            report.appendLine("REQUESTS TO DELETE: ${oldRequests.size}")
            
            if (oldRequests.isNotEmpty()) {
                report.appendLine()
                oldRequests.forEach { request ->
                    val daysAgo = TimeUnit.MILLISECONDS.toDays(now - request.updatedAt)
                    report.appendLine("  Request ${request.id.take(8)}...")
                    report.appendLine("    Status: ${request.status}")
                    report.appendLine("    Updated: $daysAgo days ago")
                    report.appendLine("    Has voice: ${!request.voiceUri.isNullOrBlank()}")
                    report.appendLine("    Has photos: ${!request.photoUris.isNullOrBlank()}")
                    report.appendLine()
                }
            }
            
            report.appendLine()
            
            // Check tasks
            val oldTasks = tasksRepository.getOldCompletedOrDeleted(cutoffTime)
            report.appendLine("TASKS TO DELETE: ${oldTasks.size}")
            
            if (oldTasks.isNotEmpty()) {
                report.appendLine()
                oldTasks.forEach { task ->
                    val daysAgo = TimeUnit.MILLISECONDS.toDays(now - task.updatedAt)
                    report.appendLine("  Task ${task.id.take(8)}...")
                    report.appendLine("    Status: ${task.status}")
                    report.appendLine("    Updated: $daysAgo days ago")
                    report.appendLine("    Has voice: ${!task.voiceNoteUri.isNullOrBlank()}")
                    report.appendLine("    Has photos: ${!task.photoUris.isNullOrBlank()}")
                    report.appendLine()
                }
            }
            
            // Summary
            report.appendLine()
            report.appendLine("=== SUMMARY ===")
            report.appendLine("Total items that would be deleted: ${oldRequests.size + oldTasks.size}")
            report.appendLine()
            report.appendLine("NOTE: This is a SIMULATION ONLY")
            report.appendLine("Nothing has been deleted")
            
        } catch (e: Exception) {
            report.appendLine()
            report.appendLine("ERROR: ${e.message}")
            Log.e(TAG, "Simulation failed", e)
        }
        
        val result = report.toString()
        Log.d(TAG, result)
        return result
    }
}








