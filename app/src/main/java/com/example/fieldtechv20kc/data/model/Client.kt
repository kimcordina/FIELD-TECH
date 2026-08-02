package com.example.fieldtechv20kc.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "clients",
    indices = [
        Index(value = ["name"]),
        Index(value = ["deleted", "name"]),
        Index(value = ["locality", "name"]),
        Index(value = ["lastServiceDate"]),
        Index(value = ["clientCode"])
    ]
)
data class Client(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val companyId: String = "local", // Phase 2: multi-tenant support
    val name: String,
    val clientCode: String? = null, // Unique client code
    val locality: String? = null, // Made nullable for migration safety; required in UI
    val legalName: String = "",
    val companyNumber: String = "",
    val address: String = "",
    val hasPump: Boolean = true,
    val pumpModel: String? = null,
    val installDate: Long? = null,          // epoch millis
    val lastServiceDate: Long? = null,      // epoch millis
    val latitude: Double? = null,           // GPS coordinates
    val longitude: Double? = null,          // GPS coordinates
    val mapsUrl: String? = null,            // Google Maps URL for location
    val notes: String? = null,              // Free-form notes (max 10,000 chars)
    val productsEquipment: String? = null,  // Comma-separated list: 2IN1,RMW,RINSE,ECOMIX,LAUNDRY,HA25,POOL,OTHER
    val salesman: String? = null,           // Sales representative name
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false            // Soft delete flag
)
