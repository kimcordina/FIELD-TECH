package com.example.fieldtechv20kc.data.model

data class ReportSettings(
    // App Theme Settings
    val isDarkMode: Boolean = false, // false = light mode, true = dark mode
    val accentColor: String = "#1976D2", // Default Material Blue
    
    // Client Info Settings
    val clientLegalNameEnabled: Boolean = false,
    val clientLegalName: String = "",
    val clientCompanyNumberEnabled: Boolean = false,
    val clientCompanyNumber: String = "",
    val clientAddressEnabled: Boolean = false,
    val clientAddress: String = "",
    
    // Job Type Settings
    val customJobTypes: List<CustomJobType> = emptyList(),
    val defaultJobTypeTitles: Map<String, String> = emptyMap(), // JobType.name -> custom title
    val defaultJobTypeLegalTitles: Map<String, String> = emptyMap(), // JobType.name -> custom legal title
    val defaultJobTypeLegalTexts: Map<String, String> = emptyMap(), // JobType.name -> custom legal text
    
    // Job Documentation Settings
    val defaultEquipment: List<String> = emptyList(),
    val defaultTechnicianName: String = "",
    val serialNumbersEnabled: Boolean = true,
    val timeStartedEnabled: Boolean = false,
    val timeCompletedEnabled: Boolean = false,
    
    // Email Settings
    val autoEmailReportsEnabled: Boolean = false,
    val reportEmailRecipient: String = "" // Gmail address to send reports to
)
