package com.example.fieldtechv20kc.data.remote.firestore

import com.example.fieldtechv20kc.BuildConfig
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestorePinsDataSource(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val companyId: String = BuildConfig.COMPANY_ID
) {
    private fun clientPinsCol(clientId: String) =
        db.collection("companies").document(companyId)
            .collection("clients").document(clientId)
            .collection("pins")

    // Listen to ALL pins across ALL clients (collection group)
    fun listenAllPins(): Flow<List<ClientPinDto>> = callbackFlow {
        android.util.Log.d("FirestorePinsDataSource", "🎯 Starting listenAllPins collection group listener for company: $companyId")
        
        val reg = db.collectionGroup("pins")
            // Don't filter deleted pins here - we need to receive deletion updates!
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    android.util.Log.e("FirestorePinsDataSource", "❌ ❌ ❌ CRITICAL ERROR in pins listener: ${err.message}", err)
                    android.util.Log.e("FirestorePinsDataSource", "❌ Error details: ${err.javaClass.simpleName}")
                    android.util.Log.e("FirestorePinsDataSource", "❌ This is likely a MISSING FIRESTORE INDEX error!")
                    android.util.Log.e("FirestorePinsDataSource", "❌ Check FIRESTORE_INDEXES_REQUIRED.md for instructions")
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                
                val totalDocs = snap?.size() ?: 0
                android.util.Log.d("FirestorePinsDataSource", "📥 Received $totalDocs documents from Firestore collection group query")
                
                val list = snap?.documents?.mapNotNull { d ->
                    val path = d.reference.path
                    android.util.Log.d("FirestorePinsDataSource", "🔍 Processing pin: path=$path")
                    
                    // Only include pins from our company's path
                    if (!path.startsWith("companies/$companyId/")) {
                        android.util.Log.d("FirestorePinsDataSource", "⏭️ Skipping pin from different company: $path")
                        return@mapNotNull null
                    }
                    
                    val dto = d.toObject(ClientPinDto::class.java)
                    if (dto == null) {
                        android.util.Log.w("FirestorePinsDataSource", "⚠️ Failed to parse pin document: ${d.id}")
                        return@mapNotNull null
                    }
                    
                    // parent path contains clientId; also stored in field for safety
                    val clientId = d.reference.parent.parent?.id ?: dto.clientId
                    
                    android.util.Log.d("FirestorePinsDataSource", "✅ Pin received: id=${d.id}, clientId=$clientId, label=${dto.label}, deleted=${dto.deleted}")
                    
                    dto.copy(
                        id = d.id,
                        clientId = clientId
                    )
                } ?: emptyList()
                
                android.util.Log.d("FirestorePinsDataSource", "📊 Filtered to ${list.size} pins for company $companyId")
                trySend(list)
            }
        awaitClose { 
            android.util.Log.d("FirestorePinsDataSource", "🔚 Pins listener closed")
            reg.remove() 
        }
    }

    suspend fun upsert(clientId: String, dto: ClientPinDto) {
        clientPinsCol(clientId).document(requireNotNull(dto.id))
            .set(dto.toMap(), SetOptions.merge()).await()
    }

    suspend fun softDelete(clientId: String, pinId: String, now: Long) {
        clientPinsCol(clientId).document(pinId)
            .set(mapOf("deleted" to true, "updatedAt" to now), SetOptions.merge()).await()
    }

    // Ensure single Primary per client via transaction
    suspend fun setPrimaryTransactional(clientId: String, pinId: String, now: Long) {
        // First, get all current primary pins outside the transaction
        val colRef = clientPinsCol(clientId)
        val currentPrimaries = colRef.whereEqualTo("isPrimary", true).get().await()
        
        // Then run transaction to update them
        db.runTransaction { txn ->
            // Clear all existing primaries
            currentPrimaries.documents.forEach { doc ->
                txn.update(doc.reference, mapOf("isPrimary" to false, "updatedAt" to now))
            }
            // Set the new primary
            val targetRef = colRef.document(pinId)
            txn.update(targetRef, mapOf("isPrimary" to true, "updatedAt" to now))
            null // Transaction must return a value
        }.await()
    }
}

data class ClientPinDto(
    var id: String? = null,
    var clientId: String = "",
    var label: String = "",
    var latitude: Double? = null,
    var longitude: Double? = null,
    var isPrimary: Boolean = false,
    var status: String = "SEEDED", // SEEDED | VERIFIED
    var sourceUrl: String? = null,
    var updatedAt: Long = System.currentTimeMillis(),
    var deleted: Boolean = false,
    // helpful for collectionGroup filter; set to BuildConfig.COMPANY_ID
    var companyId: String = BuildConfig.COMPANY_ID
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "clientId" to clientId,
        "label" to label,
        "latitude" to latitude,
        "longitude" to longitude,
        "isPrimary" to isPrimary,
        "status" to status,
        "sourceUrl" to sourceUrl,
        "updatedAt" to updatedAt,
        "deleted" to deleted,
        "companyId" to companyId
    )
}

