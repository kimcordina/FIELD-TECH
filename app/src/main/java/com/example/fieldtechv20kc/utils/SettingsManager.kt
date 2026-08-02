package com.example.fieldtechv20kc.utils

import android.content.Context
import android.content.SharedPreferences
import com.example.fieldtechv20kc.data.model.ReportSettings
import com.example.fieldtechv20kc.data.model.CustomJobType
import com.example.fieldtechv20kc.data.model.JobType
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsManager private constructor(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("report_settings", Context.MODE_PRIVATE)
    private val gson = Gson()
    
    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<ReportSettings> = _settings.asStateFlow()
    
    companion object {
        @Volatile
        private var INSTANCE: SettingsManager? = null
        
        fun getInstance(context: Context): SettingsManager {
            return INSTANCE ?: synchronized(this) {
                val instance = SettingsManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
    
    private fun loadSettings(): ReportSettings {
        return ReportSettings(
            // App Theme Settings
            isDarkMode = prefs.getBoolean("is_dark_mode", false),
            accentColor = prefs.getString("accent_color", "#1976D2") ?: "#1976D2",
            
            // Client Info Settings
            clientLegalNameEnabled = prefs.getBoolean("client_legal_name_enabled", false),
            clientLegalName = prefs.getString("client_legal_name", "") ?: "",
            clientCompanyNumberEnabled = prefs.getBoolean("client_company_number_enabled", false),
            clientCompanyNumber = prefs.getString("client_company_number", "") ?: "",
            clientAddressEnabled = prefs.getBoolean("client_address_enabled", false),
            clientAddress = prefs.getString("client_address", "") ?: "",
            customJobTypes = loadCustomJobTypes(),
            defaultJobTypeTitles = loadJobTypeTitles(),
            defaultJobTypeLegalTitles = loadJobTypeLegalTitles(),
            defaultJobTypeLegalTexts = loadJobTypeLegalTexts(),
            // Job Documentation Settings
            defaultEquipment = loadDefaultEquipment(),
            defaultTechnicianName = prefs.getString("default_technician_name", "") ?: "",
            serialNumbersEnabled = prefs.getBoolean("serial_numbers_enabled", true),
            timeStartedEnabled = prefs.getBoolean("time_started_enabled", false),
            timeCompletedEnabled = prefs.getBoolean("time_completed_enabled", false),
            // Email Settings
            autoEmailReportsEnabled = prefs.getBoolean("auto_email_reports_enabled", false),
            reportEmailRecipient = prefs.getString("report_email_recipient", "") ?: ""
        )
    }
    
    private fun loadCustomJobTypes(): List<CustomJobType> {
        val json = prefs.getString("custom_job_types", null)
        return if (json != null) {
            try {
                val type = object : TypeToken<List<CustomJobType>>() {}.type
                gson.fromJson(json, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }
    
    private fun loadJobTypeTitles(): Map<String, String> {
        val json = prefs.getString("job_type_titles", null)
        return if (json != null) {
            try {
                val type = object : TypeToken<Map<String, String>>() {}.type
                gson.fromJson(json, type) ?: emptyMap()
            } catch (e: Exception) {
                emptyMap()
            }
        } else {
            emptyMap()
        }
    }
    
    private fun loadJobTypeLegalTitles(): Map<String, String> {
        val json = prefs.getString("job_type_legal_titles", null)
        return if (json != null) {
            try {
                val type = object : TypeToken<Map<String, String>>() {}.type
                gson.fromJson(json, type) ?: emptyMap()
            } catch (e: Exception) {
                emptyMap()
            }
        } else {
            emptyMap()
        }
    }
    
    private fun loadJobTypeLegalTexts(): Map<String, String> {
        val json = prefs.getString("job_type_legal_texts", null)
        return if (json != null) {
            try {
                val type = object : TypeToken<Map<String, String>>() {}.type
                gson.fromJson(json, type) ?: emptyMap()
            } catch (e: Exception) {
                emptyMap()
            }
        } else {
            emptyMap()
        }
    }
    
    private fun loadDefaultEquipment(): List<String> {
        val json = prefs.getString("default_equipment", null)
        return if (json != null) {
            try {
                val type = object : TypeToken<List<String>>() {}.type
                gson.fromJson(json, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }
    
    fun updateClientLegalNameEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("client_legal_name_enabled", enabled).apply()
        _settings.value = _settings.value.copy(clientLegalNameEnabled = enabled)
    }
    
    fun updateClientLegalName(name: String) {
        prefs.edit().putString("client_legal_name", name).apply()
        _settings.value = _settings.value.copy(clientLegalName = name)
    }
    
    fun updateClientCompanyNumberEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("client_company_number_enabled", enabled).apply()
        _settings.value = _settings.value.copy(clientCompanyNumberEnabled = enabled)
    }
    
    fun updateClientCompanyNumber(number: String) {
        prefs.edit().putString("client_company_number", number).apply()
        _settings.value = _settings.value.copy(clientCompanyNumber = number)
    }
    
    fun updateClientAddressEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("client_address_enabled", enabled).apply()
        _settings.value = _settings.value.copy(clientAddressEnabled = enabled)
    }
    
    fun updateClientAddress(address: String) {
        prefs.edit().putString("client_address", address).apply()
        _settings.value = _settings.value.copy(clientAddress = address)
    }
    
    // Job Type Management
    fun addCustomJobType(customJobType: CustomJobType) {
        val currentTypes = _settings.value.customJobTypes.toMutableList()
        currentTypes.add(customJobType)
        saveCustomJobTypes(currentTypes)
    }
    
    fun updateCustomJobType(customJobType: CustomJobType) {
        val currentTypes = _settings.value.customJobTypes.toMutableList()
        val index = currentTypes.indexOfFirst { it.id == customJobType.id }
        if (index != -1) {
            currentTypes[index] = customJobType
            saveCustomJobTypes(currentTypes)
        }
    }
    
    fun deleteCustomJobType(jobTypeId: String) {
        val currentTypes = _settings.value.customJobTypes.toMutableList()
        currentTypes.removeAll { it.id == jobTypeId }
        saveCustomJobTypes(currentTypes)
    }
    
    private fun saveCustomJobTypes(types: List<CustomJobType>) {
        val json = gson.toJson(types)
        prefs.edit().putString("custom_job_types", json).apply()
        _settings.value = _settings.value.copy(customJobTypes = types)
    }
    
    fun updateDefaultJobTypeTitle(jobType: JobType, title: String) {
        val currentTitles = _settings.value.defaultJobTypeTitles.toMutableMap()
        currentTitles[jobType.name] = title
        saveJobTypeTitles(currentTitles)
    }
    
    fun updateDefaultJobTypeLegalTitle(jobType: JobType, legalTitle: String) {
        val currentTitles = _settings.value.defaultJobTypeLegalTitles.toMutableMap()
        currentTitles[jobType.name] = legalTitle
        saveJobTypeLegalTitles(currentTitles)
    }
    
    fun updateDefaultJobTypeLegalText(jobType: JobType, legalText: String) {
        val currentTexts = _settings.value.defaultJobTypeLegalTexts.toMutableMap()
        currentTexts[jobType.name] = legalText
        saveJobTypeLegalTexts(currentTexts)
    }
    
    private fun saveJobTypeTitles(titles: Map<String, String>) {
        val json = gson.toJson(titles)
        prefs.edit().putString("job_type_titles", json).apply()
        _settings.value = _settings.value.copy(defaultJobTypeTitles = titles)
    }
    
    private fun saveJobTypeLegalTitles(titles: Map<String, String>) {
        val json = gson.toJson(titles)
        prefs.edit().putString("job_type_legal_titles", json).apply()
        _settings.value = _settings.value.copy(defaultJobTypeLegalTitles = titles)
    }
    
    private fun saveJobTypeLegalTexts(texts: Map<String, String>) {
        val json = gson.toJson(texts)
        prefs.edit().putString("job_type_legal_texts", json).apply()
        _settings.value = _settings.value.copy(defaultJobTypeLegalTexts = texts)
    }
    
    // Job Documentation Settings Management
    fun updateDefaultEquipment(equipment: List<String>) {
        val json = gson.toJson(equipment)
        prefs.edit().putString("default_equipment", json).apply()
        _settings.value = _settings.value.copy(defaultEquipment = equipment)
    }
    
    fun updateDefaultTechnicianName(name: String) {
        prefs.edit().putString("default_technician_name", name).apply()
        _settings.value = _settings.value.copy(defaultTechnicianName = name)
    }
    
    fun updateSerialNumbersEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("serial_numbers_enabled", enabled).apply()
        _settings.value = _settings.value.copy(serialNumbersEnabled = enabled)
    }
    
    fun updateTimeStartedEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("time_started_enabled", enabled).apply()
        _settings.value = _settings.value.copy(timeStartedEnabled = enabled)
    }
    
    fun updateTimeCompletedEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("time_completed_enabled", enabled).apply()
        _settings.value = _settings.value.copy(timeCompletedEnabled = enabled)
    }
    
    // App Theme Settings Management
    fun updateDarkMode(isDarkMode: Boolean) {
        prefs.edit().putBoolean("is_dark_mode", isDarkMode).apply()
        _settings.value = _settings.value.copy(isDarkMode = isDarkMode)
    }
    
    fun updateAccentColor(color: String) {
        prefs.edit().putString("accent_color", color).apply()
        _settings.value = _settings.value.copy(accentColor = color)
    }
    
    // Email Settings Management
    fun updateAutoEmailReportsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("auto_email_reports_enabled", enabled).apply()
        _settings.value = _settings.value.copy(autoEmailReportsEnabled = enabled)
    }
    
    fun updateReportEmailRecipient(email: String) {
        prefs.edit().putString("report_email_recipient", email).apply()
        _settings.value = _settings.value.copy(reportEmailRecipient = email)
    }
}
