package com.example.fieldtechv20kc.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "client_pins",
    indices = [
        Index("clientId"),
        Index(value = ["clientId", "isPrimary"])
    ]
)
data class ClientPinEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val clientId: String,                  // logical FK to clients.id
    val label: String,                     // e.g., "Front door"
    val latitude: Double?,                 // nullable to allow URL-only pins
    val longitude: Double?,                // nullable to allow URL-only pins
    val isPrimary: Boolean = false,
    val status: PinStatus = PinStatus.SEEDED, // SEEDED or VERIFIED
    val sourceUrl: String? = null,         // original pasted URL
    val createdBy: String? = null,         // reserved for future Firebase
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false
)

enum class PinStatus {
    SEEDED,
    VERIFIED
}

data class ClientPinInput(
    val id: String? = null,
    val clientId: String,
    val label: String,
    val latitude: Double?,
    val longitude: Double?,
    val status: PinStatus,
    val sourceUrl: String? = null
)
