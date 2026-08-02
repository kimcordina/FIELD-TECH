package com.example.fieldtechv20kc.utils

import android.util.Patterns

object ValidationUtils {
    
    /**
     * Validate client name (min 2 chars required)
     */
    fun validateName(name: String): String? {
        return when {
            name.isBlank() -> "Name is required"
            name.trim().length < 2 -> "Name must be at least 2 characters"
            else -> null
        }
    }
    
    /**
     * Validate email (optional, but must be valid format if provided)
     */
    fun validateEmail(email: String?): String? {
        if (email.isNullOrBlank()) return null
        
        return if (Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            null
        } else {
            "Invalid email format"
        }
    }
    
    /**
     * Validate phone (optional, but must be valid format if provided)
     */
    fun validatePhone(phone: String?): String? {
        if (phone.isNullOrBlank()) return null
        
        return if (Patterns.PHONE.matcher(phone).matches()) {
            null
        } else {
            "Invalid phone number format"
        }
    }
    
    /**
     * Validate latitude (-90 to 90)
     */
    fun validateLatitude(lat: Double?): String? {
        if (lat == null) return null
        
        return if (lat in -90.0..90.0) {
            null
        } else {
            "Latitude must be between -90 and 90"
        }
    }
    
    /**
     * Validate longitude (-180 to 180)
     */
    fun validateLongitude(lng: Double?): String? {
        if (lng == null) return null
        
        return if (lng in -180.0..180.0) {
            null
        } else {
            "Longitude must be between -180 and 180"
        }
    }
    
    /**
     * Validate date is not in the future
     */
    fun validateDateNotFuture(dateMillis: Long?): String? {
        if (dateMillis == null) return null
        
        return if (dateMillis <= System.currentTimeMillis()) {
            null
        } else {
            "Date cannot be in the future"
        }
    }
    
    /**
     * Validate notes length (max 10,000 chars)
     */
    fun validateNotes(notes: String?): String? {
        if (notes.isNullOrBlank()) return null
        
        return if (notes.length <= 10_000) {
            null
        } else {
            "Notes cannot exceed 10,000 characters"
        }
    }
    
    /**
     * Parse double from string, returns null if invalid
     */
    fun parseDouble(value: String?): Double? {
        if (value.isNullOrBlank()) return null
        return value.toDoubleOrNull()
    }
    
    /**
     * Parse boolean from various formats
     */
    fun parseBoolean(value: String?): Boolean {
        if (value.isNullOrBlank()) return true // default to true for hasPump
        
        return when (value.trim().lowercase()) {
            "true", "yes", "1", "y" -> true
            "false", "no", "0", "n" -> false
            else -> true
        }
    }
    
    /**
     * Parse date from string (supports YYYY-MM-DD format)
     * Returns epoch millis at local midnight
     */
    fun parseDate(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        
        return try {
            val parts = value.split("-")
            if (parts.size == 3) {
                val year = parts[0].toInt()
                val month = parts[1].toInt()
                val day = parts[2].toInt()
                
                // Create epoch millis (simplified - doesn't handle timezone properly)
                val calendar = java.util.Calendar.getInstance()
                calendar.set(year, month - 1, day, 0, 0, 0)
                calendar.set(java.util.Calendar.MILLISECOND, 0)
                calendar.timeInMillis
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Validate locality (required, min 2 chars)
     */
    fun validateLocality(locality: String): String? {
        return when {
            locality.isBlank() -> "Locality is required"
            locality.trim().length < 2 -> "Locality must be at least 2 characters"
            else -> null
        }
    }
    
    /**
     * Validate all client fields
     */
    fun validateClient(
        name: String,
        email: String?,
        phone: String?,
        latitude: Double?,
        longitude: Double?,
        installDate: Long?,
        lastServiceDate: Long?,
        notes: String?
    ): Map<String, String> {
        val errors = mutableMapOf<String, String>()
        
        validateName(name)?.let { errors["name"] = it }
        validateEmail(email)?.let { errors["email"] = it }
        validatePhone(phone)?.let { errors["phone"] = it }
        validateLatitude(latitude)?.let { errors["latitude"] = it }
        validateLongitude(longitude)?.let { errors["longitude"] = it }
        validateDateNotFuture(installDate)?.let { errors["installDate"] = it }
        validateDateNotFuture(lastServiceDate)?.let { errors["lastServiceDate"] = it }
        validateNotes(notes)?.let { errors["notes"] = it }
        
        return errors
    }
    
    /**
     * Validate client for import (relaxed validation)
     * Only name and locality are required; phone/email issues are warnings
     */
    fun validateClientForImport(
        name: String,
        locality: String,
        email: String?,
        phone: String?
    ): Pair<Map<String, String>, Map<String, String>> {
        val errors = mutableMapOf<String, String>()
        val warnings = mutableMapOf<String, String>()
        
        // Required fields (errors)
        validateName(name)?.let { errors["name"] = it }
        validateLocality(locality)?.let { errors["locality"] = it }
        
        // Optional fields (warnings only)
        validateEmail(email)?.let { warnings["email"] = it }
        validatePhone(phone)?.let { warnings["phone"] = it }
        
        return Pair(errors, warnings)
    }
}

