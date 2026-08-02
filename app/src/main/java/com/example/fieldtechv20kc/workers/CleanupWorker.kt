package com.example.fieldtechv20kc.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.fieldtechv20kc.data.database.AppDatabase
import com.example.fieldtechv20kc.data.remote.firestore.FirestoreRequestsDataSource
import com.example.fieldtechv20kc.data.remote.firestore.FirestoreTasksDataSource
import com.example.fieldtechv20kc.data.remote.storage.FirebaseStorageService
import com.example.fieldtechv20kc.data.repository.ServiceRequestsRepository
import com.example.fieldtechv20kc.data.repository.ServiceTasksRepository
import com.example.fieldtechv20kc.usecases.CleanupOldDataUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Background worker that runs daily to clean up old completed/cancelled/deleted requests and tasks
 * Deletes items older than 14 days along with their associated files
 */
class CleanupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "FT/CLEANUP_WORKER"
        const val WORK_NAME = "cleanup_old_data"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "CleanupWorker starting...")
            
            // Initialize dependencies
            val database = AppDatabase.getDatabase(applicationContext)
            val storage = FirebaseStorageService()
            
            val requestsRepository = ServiceRequestsRepository(
                dao = database.serviceRequestsDao(),
                remote = FirestoreRequestsDataSource(),
                storage = storage
            )
            
            val tasksRepository = ServiceTasksRepository(
                dao = database.serviceTasksDao(),
                remote = FirestoreTasksDataSource(),
                storage = storage
            )
            
            val cleanupUseCase = CleanupOldDataUseCase(
                requestsRepository = requestsRepository,
                tasksRepository = tasksRepository,
                storage = storage
            )
            
            // Execute cleanup
            val result = cleanupUseCase.execute()
            
            Log.d(TAG, "CleanupWorker completed successfully")
            Log.d(TAG, "  Requests deleted: ${result.requestsDeleted}")
            Log.d(TAG, "  Tasks deleted: ${result.tasksDeleted}")
            Log.d(TAG, "  Voice notes deleted: ${result.voiceNotesDeleted}")
            Log.d(TAG, "  Photos deleted: ${result.photosDeleted}")
            Log.d(TAG, "  Errors encountered: ${result.errors}")
            
            if (result.errors > 0) {
                // Return success even with errors - we'll try again next time
                Log.w(TAG, "Cleanup completed with ${result.errors} errors")
            }
            
            Result.success()
            
        } catch (e: Exception) {
            Log.e(TAG, "CleanupWorker failed: ${e.message}", e)
            // Retry on failure
            Result.retry()
        }
    }
}








