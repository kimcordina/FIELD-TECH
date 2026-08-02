package com.example.fieldtechv20kc.data.model

data class CustomJobType(
    val id: String, // Unique identifier
    val displayName: String,
    val legalTitle: String,
    val legalText: String,
    val isDefault: Boolean = false // True for built-in job types
)



