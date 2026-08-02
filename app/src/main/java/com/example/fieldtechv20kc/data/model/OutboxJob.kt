package com.example.fieldtechv20kc.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "outbox_jobs",
    indices = [
        Index(value = ["reportId"]),
        Index(value = ["type"]),
        Index(value = ["quarantined"])
    ]
)
data class OutboxJob(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: String,           // "UPLOAD_PDF", "UPLOAD_PHOTO", "UPSERT_REPORT"
    val reportId: Long,
    val payload: String = "",   // JSON or simple string data
    val createdAt: Long = System.currentTimeMillis(),
    val attempts: Int = 0,
    val lastError: String? = null,
    val quarantined: Boolean = false,  // True if max retries exceeded
    val lastAttemptAt: Long? = null    // Timestamp of last attempt
)

