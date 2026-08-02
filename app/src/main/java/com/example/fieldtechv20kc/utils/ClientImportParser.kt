package com.example.fieldtechv20kc.utils

import android.content.Context
import android.net.Uri
import com.example.fieldtechv20kc.data.model.ClientInput
import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.*
import java.io.InputStream

data class ImportPreviewRow(
    val rowNumber: Int,
    val clientInput: ClientInput,
    val errors: List<String>,
    val warnings: List<String>,
    val isValid: Boolean
)

object ClientImportParser {
    
    /**
     * Parse CSV or XLSX file and return preview rows
     */
    suspend fun parseFile(context: Context, uri: Uri): Result<List<ImportPreviewRow>> = withContext(Dispatchers.IO) {
        try {
            val mimeType = context.contentResolver.getType(uri)
            val fileName = uri.lastPathSegment ?: ""
            
            val rows = when {
                mimeType?.contains("csv") == true || fileName.endsWith(".csv", ignoreCase = true) -> {
                    parseCsv(context, uri)
                }
                mimeType?.contains("spreadsheet") == true || 
                fileName.endsWith(".xlsx", ignoreCase = true) || 
                fileName.endsWith(".xls", ignoreCase = true) -> {
                    parseXlsx(context, uri)
                }
                else -> {
                    return@withContext Result.failure(Exception("Unsupported file format. Please use CSV or XLSX."))
                }
            }
            
            Result.success(rows)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Parse CSV file
     */
    private fun parseCsv(context: Context, uri: Uri): List<ImportPreviewRow> {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw Exception("Cannot open file")
        
        val rows = mutableListOf<ImportPreviewRow>()
        
        inputStream.use { stream ->
            val csvData = csvReader().readAllWithHeader(stream)
            
            csvData.forEachIndexed { index, row ->
                val previewRow = parseRow(index + 2, row) // +2 because row 1 is header, index is 0-based
                rows.add(previewRow)
            }
        }
        
        return rows
    }
    
    /**
     * Parse XLSX file
     */
    private fun parseXlsx(context: Context, uri: Uri): List<ImportPreviewRow> {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw Exception("Cannot open file")
        
        val rows = mutableListOf<ImportPreviewRow>()
        
        inputStream.use { stream ->
            val workbook = WorkbookFactory.create(stream)
            val sheet = workbook.getSheetAt(0)
            
            // Read header row
            val headerRow = sheet.getRow(0) ?: throw Exception("Empty spreadsheet")
            val headers = mutableMapOf<String, Int>()
            for (i in 0 until headerRow.lastCellNum) {
                val cell = headerRow.getCell(i)
                if (cell != null) {
                    headers[cell.stringCellValue.lowercase().trim()] = i
                }
            }
            
            // Read data rows
            for (i in 1..sheet.lastRowNum) {
                val dataRow = sheet.getRow(i) ?: continue
                
                val rowMap = mutableMapOf<String, String>()
                headers.forEach { (header, colIndex) ->
                    val cell = dataRow.getCell(colIndex)
                    if (cell != null) {
                        val value = when (cell.cellType) {
                            CellType.STRING -> cell.stringCellValue
                            CellType.NUMERIC -> cell.numericCellValue.toString()
                            CellType.BOOLEAN -> cell.booleanCellValue.toString()
                            else -> ""
                        }
                        rowMap[header] = value
                    }
                }
                
                val previewRow = parseRow(i + 1, rowMap)
                rows.add(previewRow)
            }
            
            workbook.close()
        }
        
        return rows
    }
    
    /**
     * Parse a single row into ClientInput with validation
     */
    private fun parseRow(rowNumber: Int, data: Map<String, String>): ImportPreviewRow {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        // Get values (case-insensitive)
        fun getValue(vararg keys: String): String? {
            for (key in keys) {
                val value = data.entries.find { it.key.lowercase() == key.lowercase() }?.value
                if (!value.isNullOrBlank()) return value.trim()
            }
            return null
        }
        
        // New column structure:
        // A: clientCode (optional)
        // B: name (required)
        // C: locality (required)
        // D: googleMapsUrl (optional)
        
        val clientCode = getValue("client code", "clientcode", "client_code", "code")
        val name = getValue("name", "client name", "client_name")
        val locality = getValue("locality", "location", "town", "city", "area")
        val googleMapsUrl = getValue("google maps url", "google maps", "maps url", "location url", "url", "link", "google_maps_url")
        
        // Legacy fields (still supported but optional)
        val address = getValue("address", "street", "street address")
        val latitudeStr = getValue("latitude", "lat")
        val longitudeStr = getValue("longitude", "lng", "lon", "long")
        val hasPumpStr = getValue("has pump", "pump", "haspump", "has_pump")
        val pumpModel = getValue("pump model", "pumpmodel", "pump_model", "model")
        val installDateStr = getValue("install date", "installdate", "install_date", "installation date")
        val lastServiceStr = getValue("last service", "lastservice", "last_service", "service date")
        val notes = getValue("notes", "comments", "remarks")
        
        // Validate name (required)
        if (name.isNullOrBlank()) {
            errors.add("Name is required")
        } else if (name.length < 2) {
            errors.add("Name must be at least 2 characters")
        }
        
        // Validate locality (required)
        if (locality.isNullOrBlank()) {
            errors.add("Locality is required")
        } else if (locality.length < 2) {
            errors.add("Locality must be at least 2 characters")
        }
        
        // Parse GPS coordinates and Maps URL
        // Priority: 1) Google Maps URL, 2) latitude/longitude columns
        var latitude: Double? = null
        var longitude: Double? = null
        var addressFromUrl: String? = null
        var finalMapsUrl: String? = null
        
        if (!googleMapsUrl.isNullOrBlank()) {
            // Store the Maps URL regardless of whether it has coordinates
            finalMapsUrl = googleMapsUrl.trim()
            
            // Try to extract coordinates from Google Maps URL
            val coords = parseGoogleMapsUrl(googleMapsUrl)
            if (coords != null) {
                latitude = coords.first
                longitude = coords.second
            } else {
                // Try to extract place name from search query URLs
                addressFromUrl = extractPlaceNameFromSearchUrl(googleMapsUrl)
                if (addressFromUrl != null) {
                    // Don't show warning - URL will work for opening Maps app
                } else {
                    warnings.add("Could not extract location info from Google Maps URL")
                }
            }
        }
        
        // Fall back to explicit lat/lng columns if Google Maps URL didn't work
        if (latitude == null || longitude == null) {
            latitude = ValidationUtils.parseDouble(latitudeStr)
            longitude = ValidationUtils.parseDouble(longitudeStr)
        }
        
        val latError = ValidationUtils.validateLatitude(latitude)
        if (latError != null) errors.add(latError)
        
        val lngError = ValidationUtils.validateLongitude(longitude)
        if (lngError != null) errors.add(lngError)
        
        // Parse has pump
        val hasPump = ValidationUtils.parseBoolean(hasPumpStr ?: "true")
        
        // Parse dates
        val installDate = ValidationUtils.parseDate(installDateStr)
        val lastServiceDate = ValidationUtils.parseDate(lastServiceStr)
        
        val installDateError = ValidationUtils.validateDateNotFuture(installDate)
        if (installDateError != null) errors.add("Install date: $installDateError")
        
        val serviceDateError = ValidationUtils.validateDateNotFuture(lastServiceDate)
        if (serviceDateError != null) errors.add("Service date: $serviceDateError")
        
        // Validate notes
        val notesError = ValidationUtils.validateNotes(notes)
        if (notesError != null) errors.add(notesError)
        
        // Warnings
        if (latitude == null && longitude == null && !address.isNullOrBlank()) {
            warnings.add("Consider adding GPS coordinates for precise location")
        }
        
        // Use address from URL if available and no explicit address provided
        val finalAddress = if (address.isNullOrBlank() && !addressFromUrl.isNullOrBlank()) {
            addressFromUrl
        } else {
            address
        }
        
        val clientInput = ClientInput(
            name = name ?: "",
            clientCode = clientCode,
            locality = locality ?: "",
            address = finalAddress,
            hasPump = hasPump,
            pumpModel = if (hasPump) pumpModel else null,
            installDate = installDate,
            lastServiceDate = lastServiceDate,
            latitude = latitude,
            longitude = longitude,
            mapsUrl = finalMapsUrl,
            notes = notes
        )
        
        return ImportPreviewRow(
            rowNumber = rowNumber,
            clientInput = clientInput,
            errors = errors,
            warnings = warnings,
            isValid = errors.isEmpty()
        )
    }
    
    /**
     * Extract place name from Google Maps search URLs
     * Supports: https://www.google.com/maps/search/?api=1&query=PLACE+NAME
     */
    private fun extractPlaceNameFromSearchUrl(url: String): String? {
        try {
            // Pattern for search API URLs: ?query=...
            val searchPattern = Regex("""[?&]query=([^&]+)""")
            searchPattern.find(url)?.let {
                val encodedQuery = it.groupValues[1]
                // Decode URL encoding: replace + with space, %20 with space
                val decodedQuery = encodedQuery
                    .replace("+", " ")
                    .replace("%20", " ")
                    .replace("%2C", ",")
                    .replace("%2F", "/")
                    .trim()
                
                return if (decodedQuery.isNotBlank()) decodedQuery else null
            }
            
            return null
        } catch (e: Exception) {
            return null
        }
    }
    
    /**
     * Parse Google Maps URL to extract latitude and longitude
     * Supports various Google Maps URL formats:
     * - https://maps.google.com/?q=35.8989,14.5146
     * - https://www.google.com/maps/place/@35.8989,14.5146,17z
     * - https://goo.gl/maps/... (short links)
     * - https://maps.app.goo.gl/...
     */
    private fun parseGoogleMapsUrl(url: String): Pair<Double, Double>? {
        try {
            // Pattern 1: ?q=lat,lng
            val qPattern = Regex("""[?&]q=(-?\d+\.?\d*),(-?\d+\.?\d*)""")
            qPattern.find(url)?.let {
                val lat = it.groupValues[1].toDoubleOrNull()
                val lng = it.groupValues[2].toDoubleOrNull()
                if (lat != null && lng != null) {
                    return Pair(lat, lng)
                }
            }
            
            // Pattern 2: @lat,lng,zoom
            val atPattern = Regex("""@(-?\d+\.?\d*),(-?\d+\.?\d*),\d+""")
            atPattern.find(url)?.let {
                val lat = it.groupValues[1].toDoubleOrNull()
                val lng = it.groupValues[2].toDoubleOrNull()
                if (lat != null && lng != null) {
                    return Pair(lat, lng)
                }
            }
            
            // Pattern 3: /place/name/@lat,lng
            val placePattern = Regex("""/place/[^/]+/@(-?\d+\.?\d*),(-?\d+\.?\d*)""")
            placePattern.find(url)?.let {
                val lat = it.groupValues[1].toDoubleOrNull()
                val lng = it.groupValues[2].toDoubleOrNull()
                if (lat != null && lng != null) {
                    return Pair(lat, lng)
                }
            }
            
            // Pattern 4: ll=lat,lng
            val llPattern = Regex("""[?&]ll=(-?\d+\.?\d*),(-?\d+\.?\d*)""")
            llPattern.find(url)?.let {
                val lat = it.groupValues[1].toDoubleOrNull()
                val lng = it.groupValues[2].toDoubleOrNull()
                if (lat != null && lng != null) {
                    return Pair(lat, lng)
                }
            }
            
            return null
        } catch (e: Exception) {
            return null
        }
    }
}

