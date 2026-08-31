package com.example.fieldtechv20kc.data.remote.firestore

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.example.fieldtechv20kc.data.model.Client
import com.example.fieldtechv20kc.BuildConfig
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class FirestoreClientsDataSource(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val companyId: String = BuildConfig.COMPANY_ID
) {

    private fun col() =
        db.collection("companies").document(companyId).collection("clients")

    fun listenAll(): Flow<List<ClientDto>> = callbackFlow {
        val reg = col().addSnapshotListener { snap, err ->
            if (err != null) {
                trySend(emptyList())
                return@addSnapshotListener
            }
            val list = snap?.documents?.mapNotNull {
                it.toObject(ClientDto::class.java)?.copy(id = it.id)
            } ?: emptyList()
            trySend(list)
        }
        awaitClose { reg.remove() }
    }

    suspend fun upsert(client: Client) {
        val dto = ClientDto.fromClient(client)
        col().document(client.id).set(dto.toMap(), SetOptions.merge()).awaitKtx()
    }

    suspend fun softDelete(id: String, now: Long) {
        col().document(id).set(
            mapOf("deleted" to true, "updatedAt" to now),
            SetOptions.merge()
        ).awaitKtx()
    }
}

// DTO keeps Firestore schema decoupled
data class ClientDto(
    var id: String? = null,
    var name: String = "",
    var clientCode: String? = null,
    var locality: String? = null,
    var address: String = "",
    var hasPump: Boolean = true,
    var pumpModel: String? = null,
    var installDate: Long? = null,
    var lastServiceDate: Long? = null,
    var mapsUrl: String? = null,
    var notes: String? = null,
    var productsEquipment: String? = null,
    var salesman: String? = null,
    var priorityStarred: Boolean = false,
    var serviceAlertsSilenced: Boolean = false,
    var updatedAt: Long = System.currentTimeMillis(),
    var deleted: Boolean = false
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "name" to name,
        "clientCode" to clientCode,
        "locality" to locality,
        "address" to address,
        "hasPump" to hasPump,
        "pumpModel" to pumpModel,
        "installDate" to installDate,
        "lastServiceDate" to lastServiceDate,
        "mapsUrl" to mapsUrl,
        "notes" to notes,
        "productsEquipment" to productsEquipment,
        "salesman" to salesman,
        "priorityStarred" to priorityStarred,
        "serviceAlertsSilenced" to serviceAlertsSilenced,
        "updatedAt" to updatedAt,
        "deleted" to deleted
    )

    fun toClient(): Client = Client(
        id = requireNotNull(id),
        companyId = BuildConfig.COMPANY_ID,
        name = name,
        clientCode = clientCode,
        locality = locality,
        address = address,
        hasPump = hasPump,
        pumpModel = pumpModel,
        installDate = installDate,
        lastServiceDate = lastServiceDate,
        mapsUrl = mapsUrl,
        notes = notes,
        productsEquipment = productsEquipment,
        salesman = salesman,
        priorityStarred = priorityStarred,
        serviceAlertsSilenced = serviceAlertsSilenced,
        updatedAt = updatedAt,
        deleted = deleted
    )

    companion object {
        fun fromClient(c: Client) = ClientDto(
            id = c.id,
            name = c.name,
            clientCode = c.clientCode,
            locality = c.locality,
            address = c.address,
            hasPump = c.hasPump,
            pumpModel = c.pumpModel,
            installDate = c.installDate,
            lastServiceDate = c.lastServiceDate,
            mapsUrl = c.mapsUrl,
            notes = c.notes,
            productsEquipment = c.productsEquipment,
            salesman = c.salesman,
            priorityStarred = c.priorityStarred,
            serviceAlertsSilenced = c.serviceAlertsSilenced,
            updatedAt = c.updatedAt,
            deleted = c.deleted
        )
    }
}

