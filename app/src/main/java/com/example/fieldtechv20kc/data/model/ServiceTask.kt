package com.example.fieldtechv20kc.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "service_tasks",
    indices = [
        Index(value = ["assignedToName"]),
        Index(value = ["scheduledDate"]),
        Index(value = ["status"]),
        Index(value = ["clientId", "status"])
    ]
)
data class ServiceTask(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val clientId: String,                       // FK to clients
    val title: String = "Service visit",
    val assignedToName: String,                 // "Jenson" or "Abubakar"
    val scheduledDate: Long,                    // epoch millis at local midnight for chosen day
    val status: TaskStatus = TaskStatus.PENDING,
    val notes: String? = null,
    val voiceNoteUri: String? = null,           // voice note content URI
    val photoUris: String? = null,              // comma-separated photo URIs
    val linkedReportId: String? = null,         // set when report completes
    val createdByName: String? = null,          // user who created the job
    val cancelledByName: String? = null,        // user who cancelled the job
    val deletedByName: String? = null,          // user who deleted the job
    val deletedAt: Long? = null,                // when the job was deleted
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false
)

enum class TaskStatus {
    PENDING,
    DONE,
    CANCELED,
    DELETED
}

// Fixed technician names and their colors
object Technicians {
    const val JENSON = "Jenson"
    const val ABUBAKAR = "Abubakar"
    
    val ALL = listOf(JENSON, ABUBAKAR)
    
    fun getColorForTechnician(name: String): androidx.compose.ui.graphics.Color {
        return when (name) {
            JENSON -> androidx.compose.ui.graphics.Color(0xFFADD8E6) // Light Blue
            ABUBAKAR -> androidx.compose.ui.graphics.Color(0xFFFFCCCB) // Light Red
            else -> androidx.compose.ui.graphics.Color.LightGray
        }
    }
}

/**
 * ServiceTask (Job) with linked client details
 */
data class ServiceTaskWithClient(
    @androidx.room.Embedded val task: ServiceTask,
    @androidx.room.Relation(
        parentColumn = "clientId",
        entityColumn = "id"
    )
    val client: Client?
)



