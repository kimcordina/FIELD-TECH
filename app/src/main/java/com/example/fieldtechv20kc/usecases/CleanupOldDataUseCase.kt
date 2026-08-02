package com.example.fieldtechv20kc.usecases

import android.util.Log
import com.example.fieldtechv20kc.data.model.RequestStatus
import com.example.fieldtechv20kc.data.model.TaskStatus
import com.example.fieldtechv20kc.data.repository.ServiceRequestsRepository
import com.example.fieldtechv20kc.data.repository.ServiceTasksRepository
import com.example.fieldtechv20kc.data.remote.storage.FirebaseStorageService
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/**
 * Use case to clean up old completed/cancelled/deleted requests and tasks
 * Deletes items older than 14 days along with their associated files (voice notes, photos)
 */
class CleanupOldDataUseCase(
    private val requestsRepository: ServiceRequestsRepository,
    private val tasksRepository: ServiceTasksRepository,
    private val storage: FirebaseStorageService
) {
    companion object {
        private const val TAG = "FT/CLEANUP"
        private const val RETENTION_DAYS = 14
        private val RETENTION_MILLIS = TimeUnit.DAYS.toMillis(RETENTION_DAYS.toLong())
    }

    data class CleanupResult(
        val requestsDeleted: Int,
        val tasksDeleted: Int,
        val voiceNotesDeleted: Int,
        val photosDeleted: Int,
        val errors: Int
    )

    suspend fun execute(): CleanupResult {
        Log.d(TAG, "========================================")
        Log.d(TAG, "Starting cleanup of old data (>$RETENTION_DAYS days)")
        Log.d(TAG, "========================================")
        
        val cutoffTime = System.currentTimeMillis() - RETENTION_MILLIS
        Log.d(TAG, "Cutoff time: $cutoffTime (${RETENTION_DAYS} days ago)")
        
        var requestsDeleted = 0
        var tasksDeleted = 0
        var voiceNotesDeleted = 0
        var photosDeleted = 0
        var errors = 0
        
        try {
            // Clean up old requests
            val oldRequests = requestsRepository.getOldCompletedOrDeleted(cutoffTime)
            Log.d(TAG, "Found ${oldRequests.size} old requests to delete")
            
            for (request in oldRequests) {
                try {
                    Log.d(TAG, "Deleting request ${request.id} (status=${request.status}, updatedAt=${request.updatedAt})")
                    
                    // Delete voice note from Storage if exists
                    if (!request.voiceUri.isNullOrBlank() && request.voiceUri.startsWith("companies/")) {
                        try {
                            val voicePath = request.voiceUri.removePrefix("/")
                            FirebaseStorage.getInstance().getReference(voicePath).delete().await()
                            voiceNotesDeleted++
                            Log.d(TAG, "  ✓ Deleted voice note: $voicePath")
                        } catch (e: Exception) {
                            Log.w(TAG, "  ⚠ Failed to delete voice note: ${e.message}")
                            errors++
                        }
                    }
                    
                    // Delete photos from Storage if exist
                    if (!request.photoUris.isNullOrBlank()) {
                        val photoList = request.photoUris.split(",").filter { it.isNotBlank() && it.startsWith("http") }
                        for (photoUrl in photoList) {
                            try {
                                // Extract storage path from download URL
                                val path = extractStoragePathFromUrl(photoUrl)
                                if (path != null) {
                                    FirebaseStorage.getInstance().getReference(path).delete().await()
                                    photosDeleted++
                                    Log.d(TAG, "  ✓ Deleted photo: $path")
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "  ⚠ Failed to delete photo: ${e.message}")
                                errors++
                            }
                        }
                    }
                    
                    // Delete from Firestore and local DB
                    requestsRepository.permanentlyDelete(request.id)
                    requestsDeleted++
                    Log.d(TAG, "  ✓ Permanently deleted request ${request.id}")
                    
                } catch (e: Exception) {
                    Log.e(TAG, "  ✗ Error deleting request ${request.id}: ${e.message}", e)
                    errors++
                }
            }
            
            // Clean up old tasks
            val oldTasks = tasksRepository.getOldCompletedOrDeleted(cutoffTime)
            Log.d(TAG, "Found ${oldTasks.size} old tasks to delete")
            
            for (task in oldTasks) {
                try {
                    Log.d(TAG, "Deleting task ${task.id} (status=${task.status}, updatedAt=${task.updatedAt})")
                    
                    // Delete voice note from Storage if exists
                    if (!task.voiceNoteUri.isNullOrBlank() && task.voiceNoteUri.startsWith("companies/")) {
                        try {
                            val voicePath = task.voiceNoteUri.removePrefix("/")
                            FirebaseStorage.getInstance().getReference(voicePath).delete().await()
                            voiceNotesDeleted++
                            Log.d(TAG, "  ✓ Deleted voice note: $voicePath")
                        } catch (e: Exception) {
                            Log.w(TAG, "  ⚠ Failed to delete voice note: ${e.message}")
                            errors++
                        }
                    }
                    
                    // Delete photos from Storage if exist
                    if (!task.photoUris.isNullOrBlank()) {
                        val photoList = task.photoUris.split(",").filter { it.isNotBlank() && it.startsWith("http") }
                        for (photoUrl in photoList) {
                            try {
                                val path = extractStoragePathFromUrl(photoUrl)
                                if (path != null) {
                                    FirebaseStorage.getInstance().getReference(path).delete().await()
                                    photosDeleted++
                                    Log.d(TAG, "  ✓ Deleted photo: $path")
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "  ⚠ Failed to delete photo: ${e.message}")
                                errors++
                            }
                        }
                    }
                    
                    // Delete from Firestore and local DB
                    tasksRepository.permanentlyDelete(task.id)
                    tasksDeleted++
                    Log.d(TAG, "  ✓ Permanently deleted task ${task.id}")
                    
                } catch (e: Exception) {
                    Log.e(TAG, "  ✗ Error deleting task ${task.id}: ${e.message}", e)
                    errors++
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Fatal error during cleanup: ${e.message}", e)
            errors++
        }
        
        val result = CleanupResult(
            requestsDeleted = requestsDeleted,
            tasksDeleted = tasksDeleted,
            voiceNotesDeleted = voiceNotesDeleted,
            photosDeleted = photosDeleted,
            errors = errors
        )
        
        Log.d(TAG, "========================================")
        Log.d(TAG, "Cleanup complete:")
        Log.d(TAG, "  Requests deleted: $requestsDeleted")
        Log.d(TAG, "  Tasks deleted: $tasksDeleted")
        Log.d(TAG, "  Voice notes deleted: $voiceNotesDeleted")
        Log.d(TAG, "  Photos deleted: $photosDeleted")
        Log.d(TAG, "  Errors: $errors")
        Log.d(TAG, "========================================")
        
        return result
    }
    
    /**
     * Extract storage path from a Firebase Storage download URL
     * e.g., https://firebasestorage.googleapis.com/.../o/companies%2F...%2Fphoto.jpg?...
     * returns: companies/NCORDINA/requests/xyz/photos/photo.jpg
     */
    private fun extractStoragePathFromUrl(url: String): String? {
        return try {
            if (!url.contains("/o/")) return null
            val pathEncoded = url.substringAfter("/o/").substringBefore("?")
            java.net.URLDecoder.decode(pathEncoded, "UTF-8")
        } catch (e: Exception) {
            null
        }
    }
}

