package com.example.fieldtechv20kc.data.repository

import com.example.fieldtechv20kc.BuildConfig
import com.example.fieldtechv20kc.data.database.dao.ClientPinsDao
import com.example.fieldtechv20kc.data.model.ClientPinEntity
import com.example.fieldtechv20kc.data.model.ClientPinInput
import com.example.fieldtechv20kc.data.model.PinStatus
import com.example.fieldtechv20kc.data.remote.firestore.ClientPinDto
import com.example.fieldtechv20kc.data.remote.firestore.FirestorePinsDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.UUID

class ClientPinsRepository(
    private val dao: ClientPinsDao,
    private val remote: FirestorePinsDataSource
) {
    
    /**
     * Start two-way sync with Firestore
     * Call once on app startup after user is signed in
     */
    fun startSync(scope: CoroutineScope) {
        android.util.Log.d("ClientPinsRepository", "🔄 Starting pins sync...")
        
        scope.launch {
            remote.listenAllPins().collect { remoteList ->
                android.util.Log.d("ClientPinsRepository", "🔄 Sync received ${remoteList.size} remote pins")
                
                // Apply remote → local if newer
                remoteList.forEach { dto ->
                    val id = requireNotNull(dto.id)
                    val local = dao.getByIdOnce(id)
                    val remoteNewer = local == null || dto.updatedAt > local.updatedAt
                    
                    android.util.Log.d("ClientPinsRepository", "🔍 Pin $id: local=${local != null}, remoteNewer=$remoteNewer")
                    
                    if (remoteNewer) {
                        val entity = ClientPinEntity(
                            id = id,
                            clientId = dto.clientId,
                            label = dto.label,
                            latitude = dto.latitude,
                            longitude = dto.longitude,
                            isPrimary = dto.isPrimary,
                            status = PinStatus.valueOf(dto.status),
                            sourceUrl = dto.sourceUrl,
                            createdBy = local?.createdBy, // keep existing if present
                            createdAt = local?.createdAt ?: System.currentTimeMillis(),
                            updatedAt = dto.updatedAt,
                            deleted = dto.deleted
                        )
                        dao.upsert(entity)
                        android.util.Log.d("ClientPinsRepository", "✅ Pin $id synced to local database")
                    } else {
                        android.util.Log.d("ClientPinsRepository", "⏭️ Pin $id skipped (local is newer)")
                    }
                }
                
                android.util.Log.d("ClientPinsRepository", "✅ Pins sync batch complete")
            }
        }
    }
    
    fun observePins(clientId: String): Flow<List<ClientPinEntity>> {
        return dao.observeForClient(clientId)
    }
    
    suspend fun addOrUpdate(input: ClientPinInput): String {
        val now = System.currentTimeMillis()
        val pinId = input.id ?: UUID.randomUUID().toString()
        
        android.util.Log.d("ClientPinsRepository", "📌 addOrUpdate called with pinId=$pinId, clientId=${input.clientId}")
        
        val pin = ClientPinEntity(
            id = pinId,
            clientId = input.clientId,
            label = input.label,
            latitude = input.latitude,
            longitude = input.longitude,
            isPrimary = false, // Never auto-set primary
            status = input.status,
            sourceUrl = input.sourceUrl,
            createdAt = now,
            updatedAt = now,
            deleted = false
        )
        
        android.util.Log.d("ClientPinsRepository", "💾 Saving pin to local database...")
        dao.upsert(pin)
        android.util.Log.d("ClientPinsRepository", "✅ Pin saved locally")
        
        // Push to Firestore
        try {
            android.util.Log.d("ClientPinsRepository", "☁️ Pushing pin to Firestore...")
            remote.upsert(input.clientId, pin.toDto())
            android.util.Log.d("ClientPinsRepository", "✅ Pin pushed to Firestore successfully")
        } catch (e: Exception) {
            android.util.Log.e("ClientPinsRepository", "❌ Failed to push pin to Firestore: ${e.message}", e)
            e.printStackTrace()
        }
        
        return pinId
    }
    
    suspend fun setPrimary(clientId: String, pinId: String) {
        val now = System.currentTimeMillis()
        // Local: ensure only one primary
        dao.clearPrimaryLocal(clientId, now)
        dao.markPrimary(pinId, now)
        
        // Remote: transactional clear/set
        try {
            remote.setPrimaryTransactional(clientId, pinId, now)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    suspend fun delete(pinId: String, clientId: String) {
        val now = System.currentTimeMillis()
        dao.softDelete(pinId, now)
        
        // Push to Firestore
        try {
            remote.softDelete(clientId, pinId, now)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    suspend fun getPrimary(clientId: String): ClientPinEntity? {
        return dao.getPrimary(clientId)
    }
    
    suspend fun getFirstPin(clientId: String): ClientPinEntity? {
        return dao.getFirstPin(clientId)
    }
    
    companion object {
        /**
         * Parse various input formats into lat/lng coordinates
         * Accepted formats:
         * - Plain coords: "35.8989, 14.5146"
         * - Geo URI: "geo:35.8989,14.5146"
         * - Google Maps URLs with @lat,lng or ?q=lat,lng
         * - Short URLs (maps.app.goo.gl) → returns null (URL-only pin)
         */
        fun parseLocationInput(input: String): Pair<Double?, Double?>? {
            val trimmed = input.trim()
            
            // Try plain coordinates first: "35.8989, 14.5146"
            val plainCoordRegex = Regex("""^(-?\d+\.?\d*)\s*,\s*(-?\d+\.?\d*)$""")
            plainCoordRegex.find(trimmed)?.let { match ->
                val lat = match.groupValues[1].toDoubleOrNull()
                val lng = match.groupValues[2].toDoubleOrNull()
                if (lat != null && lng != null && isValidCoordinate(lat, lng)) {
                    return Pair(lat, lng)
                }
            }
            
            // Try geo: URI format
            if (trimmed.startsWith("geo:")) {
                val geoRegex = Regex("""geo:(-?\d+\.?\d*),(-?\d+\.?\d*)""")
                geoRegex.find(trimmed)?.let { match ->
                    val lat = match.groupValues[1].toDoubleOrNull()
                    val lng = match.groupValues[2].toDoubleOrNull()
                    if (lat != null && lng != null && isValidCoordinate(lat, lng)) {
                        return Pair(lat, lng)
                    }
                }
            }
            
            // Try Google Maps URL with @lat,lng
            val atRegex = Regex("""@(-?\d+\.?\d*),(-?\d+\.?\d*)""")
            atRegex.find(trimmed)?.let { match ->
                val lat = match.groupValues[1].toDoubleOrNull()
                val lng = match.groupValues[2].toDoubleOrNull()
                if (lat != null && lng != null && isValidCoordinate(lat, lng)) {
                    return Pair(lat, lng)
                }
            }
            
            // Try Google Maps URL with ?q=lat,lng
            val qRegex = Regex("""[?&]q=(-?\d+\.?\d*),(-?\d+\.?\d*)""")
            qRegex.find(trimmed)?.let { match ->
                val lat = match.groupValues[1].toDoubleOrNull()
                val lng = match.groupValues[2].toDoubleOrNull()
                if (lat != null && lng != null && isValidCoordinate(lat, lng)) {
                    return Pair(lat, lng)
                }
            }
            
            // If it looks like a URL but we couldn't extract coords, return null (URL-only pin)
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                return Pair(null, null)
            }
            
            // Invalid input
            return null
        }
        
        private fun isValidCoordinate(lat: Double, lng: Double): Boolean {
            return lat in -90.0..90.0 && lng in -180.0..180.0
        }
    }
    
    /**
     * Convert ClientPinEntity to Firestore DTO
     */
    private fun ClientPinEntity.toDto() = ClientPinDto(
        id = id,
        clientId = clientId,
        label = label,
        latitude = latitude,
        longitude = longitude,
        isPrimary = isPrimary,
        status = status.name,
        sourceUrl = sourceUrl,
        updatedAt = updatedAt,
        deleted = deleted,
        companyId = BuildConfig.COMPANY_ID
    )
}
