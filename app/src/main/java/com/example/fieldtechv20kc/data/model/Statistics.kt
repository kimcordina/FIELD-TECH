package com.example.fieldtechv20kc.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "statistics")
data class Statistics(
    @PrimaryKey
    val id: Int = 1, // Single row for global statistics
    val totalReports: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Entity(tableName = "locality_statistics")
data class LocalityStatistics(
    @PrimaryKey
    val locality: String,
    val reportCount: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis()
)

