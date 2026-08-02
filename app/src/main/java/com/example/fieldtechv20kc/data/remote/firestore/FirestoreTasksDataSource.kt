package com.example.fieldtechv20kc.data.remote.firestore

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.example.fieldtechv20kc.BuildConfig
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class FirestoreTasksDataSource(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val companyId: String = BuildConfig.COMPANY_ID
) {
    private fun col() = db.collection("companies").document(companyId).collection("tasks")

    fun listenAll(): Flow<List<TaskDto>> = callbackFlow {
        val reg = col().addSnapshotListener { snap, err ->
            if (err != null) {
                trySend(emptyList())
                return@addSnapshotListener
            }
            val list = snap?.documents?.mapNotNull {
                it.toObject(TaskDto::class.java)?.copy(id = it.id)
            } ?: emptyList()
            trySend(list)
        }
        awaitClose { reg.remove() }
    }

    suspend fun upsert(dto: TaskDto) {
        col().document(requireNotNull(dto.id)).set(dto.toMap(), SetOptions.merge()).awaitKtx()
    }

    suspend fun softDelete(id: String, now: Long) {
        col().document(id).set(
            mapOf("deleted" to true, "updatedAt" to now),
            SetOptions.merge()
        ).awaitKtx()
    }
    
    suspend fun permanentlyDelete(id: String) {
        col().document(id).delete().awaitKtx()
    }
}

data class TaskDto(
    var id: String? = null,
    var clientId: String = "",
    var title: String = "Service visit",
    var assignedToName: String = "",
    var scheduledDate: Long = 0L,
    var status: String = "PENDING", // PENDING | DONE | CANCELED | DELETED
    var notes: String? = null,
    var voicePath: String? = null, // Storage path for voice notes
    var photoCount: Int = 0, // Number of photos uploaded to Storage
    var photoPaths: String? = null, // Comma-separated Firebase Storage paths
    var linkedReportId: String? = null,
    var deletedByName: String? = null, // Who deleted the job
    var deletedAt: Long? = null, // When the job was deleted
    var updatedAt: Long = System.currentTimeMillis(),
    var deleted: Boolean = false
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "clientId" to clientId,
        "title" to title,
        "assignedToName" to assignedToName,
        "scheduledDate" to scheduledDate,
        "status" to status,
        "notes" to notes,
        "voicePath" to voicePath,
        "photoCount" to photoCount,
        "photoPaths" to photoPaths,
        "linkedReportId" to linkedReportId,
        "deletedByName" to deletedByName,
        "deletedAt" to deletedAt,
        "updatedAt" to updatedAt,
        "deleted" to deleted
    )
}

