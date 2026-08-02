package com.example.fieldtechv20kc.data.repository

import android.net.Uri
import com.example.fieldtechv20kc.data.database.dao.ServiceRequestsDao
import com.example.fieldtechv20kc.data.model.RequestStatus
import com.example.fieldtechv20kc.data.model.ServiceRequest
import com.example.fieldtechv20kc.data.remote.firestore.FirestoreRequestsDataSource
import com.example.fieldtechv20kc.data.remote.firestore.RequestDto
import com.example.fieldtechv20kc.data.remote.storage.FirebaseStorageService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class ServiceRequestsRepository(
    private val dao: ServiceRequestsDao,
    private val remote: FirestoreRequestsDataSource,
    private val storage: FirebaseStorageService
) {
    
    /**
     * Start two-way sync with Firestore
     * Call once on app startup after user is signed in
     */
    fun startSync(scope: CoroutineScope) {
        scope.launch {
            remote.listenAll().collect { list ->
                println("📥 REQUEST SYNC: Received ${list.size} requests from Firestore")
                list.forEach { dto ->
                    try {
                        val id = requireNotNull(dto.id)
                        val local = dao.getByIdOnce(id)
                        val remoteNewer = local == null || dto.updatedAt > local.updatedAt
                        
                        println("📥 REQUEST SYNC: Processing request $id - remoteNewer=$remoteNewer, voicePath=${dto.voicePath}, photoCount=${dto.photoCount}, photoPaths=${dto.photoPaths}")
                        
                        if (remoteNewer) {
                            val entity = ServiceRequest(
                                id = id,
                                clientId = dto.clientId,
                                notes = dto.notes,
                                // Use voicePath from Firestore for cross-device sync
                                voiceUri = dto.voicePath,
                                // Use photoPaths from Firestore (Storage paths) for cross-device sync
                                photoUris = dto.photoPaths,
                                status = RequestStatus.valueOf(dto.status),
                                linkedTaskId = dto.linkedTaskId,
                                requestedByName = dto.requestedByName,
                                cancelledByName = dto.cancelledByName,
                                requestedAt = dto.requestedAt,
                                updatedAt = dto.updatedAt,
                                deleted = dto.deleted
                            )
                            dao.upsert(entity)
                            println("✅ REQUEST SYNC: Updated local DB for request $id with voiceUri=${entity.voiceUri}, photoUris=${entity.photoUris}")
                        }
                    } catch (e: Exception) {
                        // Log error but continue syncing other requests
                        println("❌ REQUEST SYNC ERROR: Failed to sync request ${dto.id}: ${e.message}")
                        e.printStackTrace()
                    }
                }
            }
        }
    }
    
    fun observe(status: RequestStatus?, locality: String?, q: String?): Flow<List<ServiceRequest>> {
        val statusStr = status?.name
        return dao.observe(statusStr, locality, q)
    }
    
    fun observeById(id: String): Flow<ServiceRequest?> {
        return dao.observeById(id)
    }
    
    suspend fun getById(id: String): ServiceRequest? {
        return dao.getById(id)
    }
    
    suspend fun create(
        clientId: String,
        notes: String?,
        voiceUri: String?,
        photoUris: String? = null,
        requestedBy: String?
    ): String {
        android.util.Log.d("FT/REQUEST", "🆕 REQUEST REPOSITORY: create() called with clientId=$clientId, voiceUri=$voiceUri, photoUris=$photoUris")
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        android.util.Log.d("FT/REQUEST", "🆕 REQUEST REPOSITORY: Generated new request ID: $id")
        
        // 1) Save locally first
        val request = ServiceRequest(
            id = id,
            clientId = clientId,
            notes = notes,
            voiceUri = voiceUri, // Keep local URI
            photoUris = photoUris, // Keep local photo URIs
            status = RequestStatus.OPEN,
            requestedByName = requestedBy,
            requestedAt = now,
            updatedAt = now
        )
        dao.upsert(request)
        
        // 2) Upload voice note to Storage if present
        var voicePath: String? = null
        if (!voiceUri.isNullOrBlank()) {
            android.util.Log.d("FT/REQUEST", "🎤 REQUEST VOICE UPLOAD: Starting upload for request $id, voiceUri=$voiceUri")
            try {
                val fileName = "voice_${now}.m4a"
                val ref = storage.requestAudioRef(id, fileName)
                
                // Check if it's a file path or content URI
                voicePath = if (voiceUri.startsWith("content://") || voiceUri.startsWith("file://")) {
                    // It's a URI - use uploadFromUri
                    storage.uploadFromUri(ref, Uri.parse(voiceUri))
                } else {
                    // It's a file path - use uploadFromFile
                    val file = File(voiceUri)
                    storage.uploadFromFile(ref, file)
                }
                
                android.util.Log.d("FT/REQUEST", "✅ REQUEST VOICE UPLOAD: Successfully uploaded to path: $voicePath")
            } catch (e: Exception) {
                // Log but don't fail; can retry later
                android.util.Log.e("FT/REQUEST", "❌ REQUEST VOICE UPLOAD ERROR: Failed to upload voice for request $id: ${e.message}", e)
                e.printStackTrace()
            }
        } else {
            android.util.Log.d("FT/REQUEST", "🎤 REQUEST VOICE UPLOAD: No voice note to upload for request $id")
        }
        
        // 3) Upload photos to Storage if present and get download URLs
        var photoCount = 0
        val photoDownloadUrls = mutableListOf<String>()
        if (!photoUris.isNullOrBlank()) {
            val photoList = photoUris.split(",").filter { it.isNotBlank() }
            println("📸 REQUEST PHOTO UPLOAD: Starting upload of ${photoList.size} photos for request $id")
            photoList.forEachIndexed { index, photoUri ->
                try {
                    println("📸 REQUEST PHOTO UPLOAD: Uploading photo $index from URI: $photoUri")
                    val fileName = "photo_${now}_${index}.jpg"
                    val ref = storage.requestPhotoRef(id, fileName)
                    val storagePath = storage.uploadFromUri(ref, Uri.parse(photoUri))
                    println("📸 REQUEST PHOTO UPLOAD: Photo uploaded to storage path: $storagePath")
                    // Get download URL for cross-device access
                    val downloadUrl = storage.downloadUrl(storagePath).toString()
                    println("📸 REQUEST PHOTO UPLOAD: Got download URL: $downloadUrl")
                    photoDownloadUrls.add(downloadUrl)
                    photoCount++
                } catch (e: Exception) {
                    // Log but don't fail; continue with other photos
                    println("❌ REQUEST PHOTO UPLOAD ERROR: Failed to upload photo $index: ${e.message}")
                    e.printStackTrace()
                }
            }
            println("📸 REQUEST PHOTO UPLOAD: Successfully uploaded $photoCount photos. URLs: ${photoDownloadUrls.joinToString()}")
        }
        
        // 4) Push to Firestore with voicePath, photoCount, and photoPaths (download URLs)
        try {
            val photoPaths = if (photoDownloadUrls.isNotEmpty()) photoDownloadUrls.joinToString(",") else null
            android.util.Log.d("FT/REQUEST", "📤 REQUEST FIRESTORE SYNC: Syncing request $id to Firestore with voicePath=$voicePath, photoCount=$photoCount, photoPaths=$photoPaths")
            remote.upsert(
                RequestDto(
                    id = id,
                    clientId = clientId,
                    notes = notes,
                    voicePath = voicePath,
                    photoCount = photoCount,
                    photoPaths = photoPaths,
                    status = "OPEN",
                    linkedTaskId = null,
                    requestedByName = requestedBy,
                    requestedAt = now,
                    updatedAt = now,
                    deleted = false
                )
            )
            println("✅ REQUEST FIRESTORE SYNC: Successfully synced request $id to Firestore")
        } catch (e: Exception) {
            // Log but don't fail the operation
            println("❌ REQUEST FIRESTORE SYNC ERROR: Failed to sync request $id: ${e.message}")
            e.printStackTrace()
        }
        
        return id
    }
    
    suspend fun setStatus(id: String, status: RequestStatus, cancelledBy: String? = null) {
        val local = dao.getByIdOnce(id) ?: return
        val now = System.currentTimeMillis()
        val updated = local.copy(
            status = status, 
            cancelledByName = if (status == RequestStatus.CANCELED) cancelledBy else local.cancelledByName,
            updatedAt = now
        )
        dao.upsert(updated)
        
        // Push to Firestore
        try {
            remote.upsert(
                RequestDto(
                    id = id,
                    clientId = updated.clientId,
                    notes = updated.notes,
                    voicePath = null, // Reserved for Storage upload
                    status = status.name,
                    linkedTaskId = updated.linkedTaskId,
                    requestedByName = updated.requestedByName,
                    cancelledByName = updated.cancelledByName,
                    requestedAt = updated.requestedAt,
                    updatedAt = updated.updatedAt,
                    deleted = updated.deleted
                )
            )
        } catch (e: Exception) {
            // Log but don't fail the operation
            e.printStackTrace()
        }
    }
    
    /**
     * When a linked job is completed, auto-close the parent request so the
     * unified Jobs inbox stays coherent.
     */
    suspend fun markDoneByLinkedTaskId(taskId: String) {
        val local = dao.getByLinkedTaskId(taskId) ?: return
        if (local.status == RequestStatus.DONE || local.status == RequestStatus.CANCELED) return
        setStatus(local.id, RequestStatus.DONE)
    }

    suspend fun linkTask(id: String, taskId: String) {
        val local = dao.getByIdOnce(id) ?: return
        val now = System.currentTimeMillis()
        val updated = local.copy(
            linkedTaskId = taskId,
            status = RequestStatus.ASSIGNED,
            updatedAt = now
        )
        dao.upsert(updated)
        
        // Push to Firestore
        try {
            remote.upsert(
                RequestDto(
                    id = id,
                    clientId = updated.clientId,
                    notes = updated.notes,
                    voicePath = null,
                    status = "ASSIGNED",
                    linkedTaskId = taskId,
                    requestedByName = updated.requestedByName,
                    requestedAt = updated.requestedAt,
                    updatedAt = updated.updatedAt,
                    deleted = updated.deleted
                )
            )
        } catch (e: Exception) {
            // Log but don't fail the operation
            e.printStackTrace()
        }
    }
    
    /**
     * Attach or replace voice note for an existing request
     */
    suspend fun attachVoice(requestId: String, voiceUri: String) {
        val now = System.currentTimeMillis()
        val fileName = "voice_${now}.m4a"
        
        // Upload to Storage
        var voicePath: String? = null
        try {
            val ref = storage.requestAudioRef(requestId, fileName)
            voicePath = storage.uploadFromUri(ref, Uri.parse(voiceUri))
        } catch (e: Exception) {
            e.printStackTrace()
            return // Can't proceed without upload
        }
        
        // Update local
        val local = dao.getByIdOnce(requestId) ?: return
        val updated = local.copy(voiceUri = voiceUri, updatedAt = now)
        dao.upsert(updated)
        
        // Update Firestore
        try {
            remote.upsert(
                RequestDto(
                    id = requestId,
                    clientId = local.clientId,
                    notes = local.notes,
                    voicePath = voicePath,
                    status = local.status.name,
                    linkedTaskId = local.linkedTaskId,
                    requestedByName = local.requestedByName,
                    requestedAt = local.requestedAt,
                    updatedAt = now,
                    deleted = local.deleted
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
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
     * Get old requests that are completed, cancelled, or deleted
     * Used for cleanup of items older than the specified cutoff time
     */
    suspend fun getOldCompletedOrDeleted(cutoffTime: Long): List<ServiceRequest> {
        return dao.getOldCompletedOrDeleted(cutoffTime)
    }
    
    /**
     * Permanently delete a request from both Firestore and local database
     * This is used by the cleanup process and cannot be undone
     */
    suspend fun permanentlyDelete(id: String) {
        // Delete from Firestore first
        try {
            remote.permanentlyDelete(id)
        } catch (e: Exception) {
            android.util.Log.w("FT/CLEANUP", "Failed to delete request $id from Firestore: ${e.message}")
        }
        
        // Delete from local database
        dao.permanentlyDelete(id)
    }
}
