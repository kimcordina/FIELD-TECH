package com.example.fieldtechv20kc.data.model

data class ClientInput(
    val id: String? = null,
    val name: String,
    val clientCode: String? = null,
    val locality: String, // Required field for import
    val address: String? = null, // Optional for import
    // Legacy fields (not used in import)
    val legalName: String = "",
    val companyNumber: String = "",
    val hasPump: Boolean = true,
    val pumpModel: String? = null,
    val installDate: Long? = null,
    val lastServiceDate: Long? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val mapsUrl: String? = null,
    val notes: String? = null,
    val productsEquipment: String? = null,
    val salesman: String? = null
)

data class ImportResult(
    val inserted: Int,
    val updated: Int,
    val failed: List<ImportError>
)

data class ImportError(
    val row: Int,
    val reason: String,
    val isWarning: Boolean = false,
    val data: Map<String, String> = emptyMap()
)

