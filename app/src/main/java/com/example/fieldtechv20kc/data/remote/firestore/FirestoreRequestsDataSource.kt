package com.example.fieldtechv20kc.data.remote.firestore

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.example.fieldtechv20kc.BuildConfig
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class FirestoreRequestsDataSource(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val companyId: String = BuildConfig.COMPANY_ID
) {
    private fun col() = db.collection("companies").document(companyId).collection("requests")

    fun listenAll(): Flow<List<RequestDto>> = callbackFlow {
        val reg = col().addSnapshotListener { snap, err ->
            if (err != null) {
                trySend(emptyList())
                return@addSnapshotListener
            }
            val list = snap?.documents?.mapNotNull {
                it.toObject(RequestDto::class.java)?.copy(id = it.id)
            } ?: emptyList()
            trySend(list)
        }
        awaitClose { reg.remove() }
    }

    suspend fun upsert(dto: RequestDto) {
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

data class RequestDto(
    var id: String? = null,
    var clientId: String = "",
    var notes: String? = null,
    // voiceUri is LOCAL ONLY; do not sync device-specific content URIs.
    // We'll add Storage upload later and replace with voicePath.
    var voicePath: String? = null, // reserved for Stage 2.3
    var photoCount: Int = 0, // Number of photos uploaded to Storage
    var photoPaths: String? = null, // Comma-separated Firebase Storage paths
    var status: String = "OPEN", // OPEN | ASSIGNED | DONE | CANCELED
    var linkedTaskId: String? = null,
    var requestedByName: String? = null,
    var cancelledByName: String? = null,
    var requestedAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    var deleted: Boolean = false
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "clientId" to clientId,
        "notes" to notes,
        "voicePath" to voicePath,
        "photoCount" to photoCount,
        "photoPaths" to photoPaths,
        "status" to status,
        "linkedTaskId" to linkedTaskId,
        "requestedByName" to requestedByName,
        "cancelledByName" to cancelledByName,
        "requestedAt" to requestedAt,
        "updatedAt" to updatedAt,
        "deleted" to deleted
    )
}

