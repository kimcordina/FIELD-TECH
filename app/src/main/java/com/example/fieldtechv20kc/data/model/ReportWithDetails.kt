package com.example.fieldtechv20kc.data.model

data class ReportWithDetails(
    val report: Report,
    val client: Client?,  // Nullable for legacy reports without clients
    val photos: List<Photo>
)
