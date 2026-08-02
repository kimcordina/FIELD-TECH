package com.example.fieldtechv20kc.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "service_requests",
    indices = [
        Index(value = ["status"]),
        Index(value = ["requestedAt"]),
        Index(value = ["clientId", "status"])
    ]
)
data class ServiceRequest(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val clientId: String,
    val notes: String? = null,
    val voiceUri: String? = null,
    val photoUris: String? = null, // Comma-separated list of photo URIs
    val status: RequestStatus = RequestStatus.OPEN,
    val linkedTaskId: String? = null,
    val requestedByName: String? = null,
    val cancelledByName: String? = null,
    val requestedAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false
)

enum class RequestStatus {
    OPEN,
    ASSIGNED,
    DONE,
    CANCELED
}

// Data class for requests with client details
data class ServiceRequestWithClient(
    val request: ServiceRequest,
    val client: Client?
)
