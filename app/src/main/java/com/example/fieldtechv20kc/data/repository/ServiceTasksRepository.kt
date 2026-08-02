package com.example.fieldtechv20kc.data.repository

import android.net.Uri
import com.example.fieldtechv20kc.data.database.dao.ServiceTasksDao
import com.example.fieldtechv20kc.data.model.ServiceTask
import com.example.fieldtechv20kc.data.model.ServiceTaskWithClient
import com.example.fieldtechv20kc.data.model.TaskStatus
import com.example.fieldtechv20kc.data.remote.firestore.FirestoreTasksDataSource
import com.example.fieldtechv20kc.data.remote.firestore.TaskDto
import com.example.fieldtechv20kc.data.remote.storage.FirebaseStorageService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class ServiceTasksRepository(
    private val dao: ServiceTasksDao,
    private val remote: FirestoreTasksDataSource,
    private val storage: FirebaseStorageService
) {

    /** Optional hook: fired after a task is marked DONE (report complete or manual). */
    private var onTaskDone: (suspend (taskId: String) -> Unit)? = null

    fun setOnTaskDoneListener(listener: suspend (taskId: String) -> Unit) {
        onTaskDone = listener
    }

    private suspend fun notifyTaskDone(taskId: String) {
        try {
            onTaskDone?.invoke(taskId)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Start two-way sync with Firestore
     * Call once on app startup after user is signed in
     */
    fun startSync(scope: CoroutineScope) {
        scope.launch {
            remote.listenAll().collect { list ->
                println("📥 TASK SYNC: Received ${list.size} tasks from Firestore")
                list.forEach { dto ->
                    try {
                        val id = requireNotNull(dto.id)
                        val local = dao.getByIdOnce(id)
                        val remoteNewer = local == null || dto.updatedAt > local.updatedAt
                        
                        println("📥 TASK SYNC: Processing task $id - remoteNewer=$remoteNewer, voicePath=${dto.voicePath}, photoCount=${dto.photoCount}, photoPaths=${dto.photoPaths}")
                        
                        if (remoteNewer) {
                            val entity = ServiceTask(
                                id = id,
                                clientId = dto.clientId,
                                title = dto.title,
                                assignedToName = dto.assignedToName,
                                scheduledDate = dto.scheduledDate,
                                status = TaskStatus.valueOf(dto.status),
                                notes = dto.notes,
                                linkedReportId = dto.linkedReportId,
                                voiceNoteUri = dto.voicePath, // Use voicePath from Firestore for cross-device sync
                                photoUris = dto.photoPaths, // Use photoPaths from Firestore for cross-device sync
                                deletedByName = dto.deletedByName,
                                deletedAt = dto.deletedAt,
                                updatedAt = dto.updatedAt,
                                deleted = dto.deleted,
                                createdAt = local?.createdAt ?: System.currentTimeMillis()
                            )
                            dao.upsert(entity)
                            println("✅ TASK SYNC: Updated local DB for task $id with voiceNoteUri=${entity.voiceNoteUri}, photoUris=${entity.photoUris}")
                        }
                    } catch (e: Exception) {
                        // Log error but continue syncing other tasks
                        println("❌ TASK SYNC ERROR: Failed to sync task ${dto.id}: ${e.message}")
                        e.printStackTrace()
                    }
                }
            }
        }
    }
    
    /**
     * Observe tasks with optional filters
     */
    fun observe(
        assignee: String? = null,
        fromDate: Long? = null,
        toDate: Long? = null,
        status: TaskStatus? = null
    ): Flow<List<ServiceTask>> {
        return dao.observe(
            assignee = assignee,
            fromDate = fromDate,
            toDate = toDate,
            status = status?.name
        )
    }
    
    /**
     * Observe tasks with client details
     */
    fun observeWithClients(
        assignee: String? = null,
        fromDate: Long? = null,
        toDate: Long? = null,
        status: TaskStatus? = null
    ): Flow<List<ServiceTaskWithClient>> {
        return dao.observeWithClients(
            assignee = assignee,
            fromDate = fromDate,
            toDate = toDate,
            status = status?.name
        )
    }
    
    /**
     * Observe tasks for a specific client
     */
    fun observeForClient(clientId: String): Flow<List<ServiceTask>> {
        return dao.observeForClient(clientId)
    }
    
    /**
     * Get pending task for a client (for color coding)
     */
    suspend fun getPendingTaskForClient(clientId: String): ServiceTask? {
        return dao.getPendingTaskForClient(clientId)
    }
    
    /**
     * Get ALL pending tasks for a client (for task selection popup)
     */
    suspend fun getPendingByClientOnce(clientId: String): List<ServiceTask> {
        return dao.getPendingByClientOnce(clientId)
    }
    
    /**
     * Get single task by ID
     */
    suspend fun getById(id: String): ServiceTask? {
        return dao.getById(id)
    }
    
    /**
     * Observe single task by ID
     */
    fun observeById(id: String): Flow<ServiceTask?> {
        return dao.observeById(id)
    }
    
    /**
     * Create or update a task
     */
    suspend fun upsert(task: ServiceTask) {
        println("🆕 TASK REPOSITORY: upsert() called with taskId=${task.id}, voiceNoteUri=${task.voiceNoteUri}, photoUris=${task.photoUris}")
        val now = System.currentTimeMillis()
        val updated = task.copy(updatedAt = now)
        dao.upsert(updated)
        println("🆕 TASK REPOSITORY: Saved to local DB, now processing uploads in background")
        
        // Launch Firebase uploads in background (non-blocking, fire-and-forget)
        // This prevents blocking when offline
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                kotlinx.coroutines.withTimeout(10000) { // 10 second timeout for all uploads
                    // Upload voice note to Storage if present and not already uploaded
                    var voicePath: String? = null
                    if (!task.voiceNoteUri.isNullOrBlank()) {
                        android.util.Log.d("FT/TASK", "🎤 TASK VOICE UPLOAD: Starting upload for task ${task.id}, voiceNoteUri=${task.voiceNoteUri}")
                        try {
                            val fileName = "voice_${now}.m4a"
                            val ref = storage.taskAudioRef(task.id, fileName)
                            
                            // Check if it's a file path or content URI
                            voicePath = if (task.voiceNoteUri.startsWith("content://") || task.voiceNoteUri.startsWith("file://")) {
                                // It's a URI - use uploadFromUri
                                storage.uploadFromUri(ref, Uri.parse(task.voiceNoteUri))
                            } else {
                                // It's a file path - use uploadFromFile
                                val file = File(task.voiceNoteUri)
                                storage.uploadFromFile(ref, file)
                            }
                            
                            android.util.Log.d("FT/TASK", "✅ TASK VOICE UPLOAD: Successfully uploaded to path: $voicePath")
                        } catch (e: Exception) {
                            // Log but don't fail; can retry later
                            android.util.Log.e("FT/TASK", "❌ TASK VOICE UPLOAD ERROR: Failed to upload voice for task ${task.id}: ${e.message}", e)
                            e.printStackTrace()
                        }
                    } else {
                        android.util.Log.d("FT/TASK", "🎤 TASK VOICE UPLOAD: No voice note to upload for task ${task.id}")
                    }
                    
                    // Upload photos to Storage if present and get download URLs
                    var photoCount = 0
                    val photoDownloadUrls = mutableListOf<String>()
                    if (!task.photoUris.isNullOrBlank()) {
                        val photoList = task.photoUris.split(",").filter { it.isNotBlank() }
                        println("📸 TASK PHOTO UPLOAD: Starting upload of ${photoList.size} photos for task ${task.id}")
                        photoList.forEachIndexed { index, photoUri ->
                            try {
                                println("📸 TASK PHOTO UPLOAD: Uploading photo $index from URI: $photoUri")
                                val fileName = "photo_${now}_${index}.jpg"
                                val ref = storage.taskPhotoRef(task.id, fileName)
                                val storagePath = storage.uploadFromUri(ref, Uri.parse(photoUri))
                                println("📸 TASK PHOTO UPLOAD: Photo uploaded to storage path: $storagePath")
                                // Get download URL for cross-device access
                                val downloadUrl = storage.downloadUrl(storagePath).toString()
                                println("📸 TASK PHOTO UPLOAD: Got download URL: $downloadUrl")
                                photoDownloadUrls.add(downloadUrl)
                                photoCount++
                            } catch (e: Exception) {
                                // Log but don't fail; continue with other photos
                                println("❌ TASK PHOTO UPLOAD ERROR: Failed to upload photo $index: ${e.message}")
                                e.printStackTrace()
                            }
                        }
                        println("📸 TASK PHOTO UPLOAD: Successfully uploaded $photoCount photos. URLs: ${photoDownloadUrls.joinToString()}")
                    }
                    
                    // Push to Firestore with photo download URLs
                    try {
                        val photoPaths = if (photoDownloadUrls.isNotEmpty()) photoDownloadUrls.joinToString(",") else null
                        println("📤 TASK FIRESTORE SYNC: Syncing task ${task.id} to Firestore with voicePath=$voicePath, photoCount=$photoCount, photoPaths=$photoPaths")
                        remote.upsert(
                            TaskDto(
                                id = updated.id,
                                clientId = updated.clientId,
                                title = updated.title,
                                assignedToName = updated.assignedToName,
                                scheduledDate = updated.scheduledDate,
                                status = updated.status.name,
                                notes = updated.notes,
                                voicePath = voicePath,
                                photoCount = photoCount,
                                photoPaths = photoPaths,
                                linkedReportId = updated.linkedReportId,
                                updatedAt = updated.updatedAt,
                        deleted = updated.deleted
                    )
                )
                println("✅ TASK FIRESTORE SYNC: Successfully synced task ${task.id} to Firestore")
            } catch (e: Exception) {
                // Log but don't fail the operation
                println("❌ TASK FIRESTORE SYNC ERROR: Failed to sync task ${task.id}: ${e.message}")
                e.printStackTrace()
            }
                }
            } catch (e: Exception) {
                // Timeout or other error - log but don't fail the local save
                android.util.Log.e("FT/TASK", "Background upload failed or timed out for task ${task.id}: ${e.message}", e)
            }
        }
    }
    
    /**
     * Update task status
     */
    suspend fun setStatus(id: String, status: TaskStatus) {
        val local = dao.getByIdOnce(id) ?: return
        upsert(local.copy(status = status))
        if (status == TaskStatus.DONE) notifyTaskDone(id)
    }
    
    /**
     * Delete a job (mark as DELETED, track who deleted it)
     * No notifications are sent for deleted jobs
     */
    suspend fun deleteJob(id: String, deletedBy: String) {
        val local = dao.getByIdOnce(id) ?: return
        val now = System.currentTimeMillis()
        val updated = local.copy(
            status = TaskStatus.DELETED,
            deletedByName = deletedBy,
            deletedAt = now,
            updatedAt = now
        )
        dao.upsert(updated)
        
        // Push to Firestore (no notification trigger)
        try {
            remote.upsert(
                TaskDto(
                    id = updated.id,
                    clientId = updated.clientId,
                    title = updated.title,
                    assignedToName = updated.assignedToName,
                    scheduledDate = updated.scheduledDate,
                    status = updated.status.name,
                    notes = updated.notes,
                    voicePath = null, // Not relevant for deleted jobs
                    linkedReportId = updated.linkedReportId,
                    updatedAt = updated.updatedAt,
                    deleted = updated.deleted
                )
            )
        } catch (e: Exception) {
            // Log but don't fail
            e.printStackTrace()
        }
    }
    
    /**
     * Link task to report and mark as DONE
     */
    suspend fun linkReportAndComplete(id: String, reportId: String) {
        val local = dao.getByIdOnce(id) ?: return
        upsert(local.copy(status = TaskStatus.DONE, linkedReportId = reportId))
        notifyTaskDone(id)
    }
    
    /**
     * Soft delete task
     */
    suspend fun delete(id: String) {
        val now = System.currentTimeMillis()
        dao.softDelete(id, now)
        
        // Push delete to Firestore
        try {
            remote.softDelete(id, now)
        } catch (e: Exception) {
            // Log but don't fail the operation
            e.printStackTrace()
        }
    }
    
    /**
     * Update entire task
     */
    suspend fun update(task: ServiceTask) {
        dao.update(task.copy(updatedAt = System.currentTimeMillis()))
    }
    
    /**
     * Attach or replace voice note for an existing task
     */
    suspend fun attachVoice(taskId: String, voiceUri: String) {
        val now = System.currentTimeMillis()
        val fileName = "voice_${now}.m4a"
        
        // Upload to Storage
        var voicePath: String? = null
        try {
            val ref = storage.taskAudioRef(taskId, fileName)
            voicePath = storage.uploadFromUri(ref, Uri.parse(voiceUri))
        } catch (e: Exception) {
            e.printStackTrace()
            return // Can't proceed without upload
        }
        
        // Update local
        val local = dao.getByIdOnce(taskId) ?: return
        val updated = local.copy(voiceNoteUri = voiceUri, updatedAt = now)
        dao.upsert(updated)
        
        // Update Firestore
        try {
            remote.upsert(
                TaskDto(
                    id = taskId,
                    clientId = updated.clientId,
                    title = updated.title,
                    assignedToName = updated.assignedToName,
                    scheduledDate = updated.scheduledDate,
                    status = updated.status.name,
                    notes = updated.notes,
                    voicePath = voicePath,
                    linkedReportId = updated.linkedReportId,
                    updatedAt = now,
                    deleted = updated.deleted
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Get old tasks that are completed, cancelled, or deleted
     * Used for cleanup of items older than the specified cutoff time
     */
    suspend fun getOldCompletedOrDeleted(cutoffTime: Long): List<ServiceTask> {
        return dao.getOldCompletedOrDeleted(cutoffTime)
    }
    
    /**
     * Permanently delete a task from both Firestore and local database
     * This is used by the cleanup process and cannot be undone
     */
    suspend fun permanentlyDelete(id: String) {
        // Delete from Firestore first
        try {
            remote.permanentlyDelete(id)
        } catch (e: Exception) {
            android.util.Log.w("FT/CLEANUP", "Failed to delete task $id from Firestore: ${e.message}")
        }
        
        // Delete from local database
        dao.permanentlyDelete(id)
    }
}



