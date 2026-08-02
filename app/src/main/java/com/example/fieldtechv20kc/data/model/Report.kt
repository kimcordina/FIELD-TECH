package com.example.fieldtechv20kc.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

@Entity(
    tableName = "reports",
    indices = [
        Index(value = ["clientId"]),
        Index(value = ["deleted"])
    ]
)
data class Report(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val clientId: String? = null, // Updated to String UUID, nullable for legacy compatibility
    val jobType: JobType,
    val equipmentInstalledRepaired: String,
    val serialNumbers: String,
    val workCarriedOut: String,
    val technicianName: String,
    val findings: String,
    val signerName: String,
    val signatureData: String, // Base64 encoded signature
    val pdfPath: String,
    val createdAt: Date = Date(),
    val isCompleted: Boolean = false,
    // Custom job type fields
    val isCustomJobType: Boolean = false,
    val customJobTypeId: String? = null,
    val customJobTypeDisplayName: String? = null,
    val customJobTypeLegalTitle: String? = null,
    val customJobTypeLegalText: String? = null,
    // Time tracking fields
    val timeStarted: String? = null,
    val timeCompleted: String? = null,
    // Internal notes (not included in PDF)
    val internalNotes: String? = null,
    // Soft delete flag for trash bin
    val deleted: Boolean = false,
    /** Human-facing unique ref, e.g. NC-0132-26. Empty on legacy rows. */
    val reportRef: String = ""
)
