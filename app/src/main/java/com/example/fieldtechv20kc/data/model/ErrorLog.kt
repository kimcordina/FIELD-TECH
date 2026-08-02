package com.example.fieldtechv20kc.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Error log entry for diagnostics
 */
@Entity(
    tableName = "error_logs",
    indices = [Index(value = ["timestamp"], orders = [Index.Order.DESC])]
)
data class ErrorLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val level: String,              // "Info", "Warning", "Error"
    val tag: String,                // e.g., "OUTBOX", "UPLOAD", "FCM"
    val message: String,            // Human-readable error message
    val stackTrace: String? = null, // Optional stack trace
    val timestamp: Long             // Epoch millis
)

