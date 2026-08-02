package com.example.fieldtechv20kc.data.remote.firestore

import com.example.fieldtechv20kc.BuildConfig
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class ReportsRemote(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val companyId: String = BuildConfig.COMPANY_ID
) {
    private fun reportsCol() =
        db.collection("companies").document(companyId).collection("reports")

    fun listenAll(): Flow<List<ReportCloudDto>> = callbackFlow {
        val reg = reportsCol()
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull {
                    it.toObject(ReportCloudDto::class.java)?.copy(id = it.id)
                }?.filter { !it.deleted } ?: emptyList()  // Filter deleted in code (handles missing field)
                trySend(list)
            }
        awaitClose { reg.remove() }
    }
    
    fun listenDeleted(): Flow<List<ReportCloudDto>> = callbackFlow {
        val reg = reportsCol()
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull {
                    it.toObject(ReportCloudDto::class.java)?.copy(id = it.id)
                }?.filter { it.deleted } ?: emptyList()  // Only get deleted reports
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    suspend fun upsertReport(dto: ReportCloudDto) {
        val id = requireNotNull(dto.id) { "Report ID must not be null" }
        reportsCol().document(id)
            .set(dto.toMap(), SetOptions.merge())
            .awaitKtx()
    }

    suspend fun upsertPhoto(reportId: Long, photo: ReportPhotoDto) {
        val sub = reportsCol().document(reportId.toString()).collection("photos")
        sub.document(photo.id.toString()).set(photo.toMap(), SetOptions.merge()).awaitKtx()
    }
    
    suspend fun exists(id: String): Boolean {
        return reportsCol().document(id).get().awaitKtx().exists()
    }
    
    suspend fun deleteReport(id: String) {
        // Delete the report document (photos subcollection will be orphaned but that's ok)
        // In production, you'd use a Cloud Function to delete subcollections
        reportsCol().document(id).delete().awaitKtx()
    }
    
    suspend fun moveToTrash(reportId: String) {
        reportsCol().document(reportId)
            .set(mapOf("deleted" to true, "updatedAt" to System.currentTimeMillis()), SetOptions.merge())
            .awaitKtx()
    }
    
    suspend fun restoreFromTrash(reportId: String) {
        reportsCol().document(reportId)
            .set(mapOf("deleted" to false, "updatedAt" to System.currentTimeMillis()), SetOptions.merge())
            .awaitKtx()
    }
    
    suspend fun patchPdfPath(reportId: String, pdfPath: String) {
        reportsCol().document(reportId)
            .set(mapOf("pdfPath" to pdfPath, "updatedAt" to System.currentTimeMillis()), SetOptions.merge())
            .awaitKtx()
    }
    
    suspend fun incrementPhotoCount(reportId: String) {
        val docRef = reportsCol().document(reportId)
        db.runTransaction { transaction ->
            val snapshot = transaction.get(docRef)
            val currentCount = snapshot.getLong("photoCount") ?: 0
            transaction.update(docRef, mapOf(
                "photoCount" to currentCount + 1,
                "updatedAt" to System.currentTimeMillis()
            ))
        }.awaitKtx()
    }
    
    fun listenPhotos(reportId: Long): Flow<List<ReportPhotoDto>> = callbackFlow {
        val reg = reportsCol()
            .document(reportId.toString())
            .collection("photos")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull {
                    it.toObject(ReportPhotoDto::class.java)?.copy(id = it.id.toLongOrNull() ?: 0L)
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }
}

data class ReportCloudDto(
    var id: String? = null,               // reportId as string
    var clientId: String? = null,
    var clientName: String = "",
    var clientLocality: String = "",
    var technicianName: String = "",
    var jobType: String = "",
    var timestamp: Long = System.currentTimeMillis(),
    var pdfPath: String? = null,          // Storage path
    var photoCount: Int = 0,
    var updatedAt: Long = System.currentTimeMillis(),
    var deleted: Boolean = false,
    var reportRef: String = ""            // e.g. NC-0132-26
) {
    fun toMap(): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>(
            "clientId" to clientId,
            "clientName" to clientName,
            "clientLocality" to clientLocality,
            "technicianName" to technicianName,
            "jobType" to jobType,
            "timestamp" to timestamp,
            "photoCount" to photoCount,
            "updatedAt" to updatedAt,
            "deleted" to deleted,
            "reportRef" to reportRef
        )
        // Only include pdfPath if it's not null, to avoid overwriting existing values
        if (pdfPath != null) {
            map["pdfPath"] = pdfPath
        }
        return map
    }
}

data class ReportPhotoDto(
    var id: Long = 0L,                   // photoId (Room PK)
    var path: String = "",               // Storage path
    var description: String? = null,
    var timestamp: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "path" to path,
        "description" to description,
        "timestamp" to timestamp
    )
}

