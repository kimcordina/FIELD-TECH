package com.example.fieldtechv20kc.data.repository

import com.example.fieldtechv20kc.BuildConfig
import com.example.fieldtechv20kc.data.database.dao.ClientDao
import com.example.fieldtechv20kc.data.model.Client
import com.example.fieldtechv20kc.data.model.ClientInput
import com.example.fieldtechv20kc.data.model.ClientPinInput
import com.example.fieldtechv20kc.data.model.ClientSort
import com.example.fieldtechv20kc.data.model.ImportError
import com.example.fieldtechv20kc.data.model.ImportResult
import com.example.fieldtechv20kc.data.model.PinStatus
import com.example.fieldtechv20kc.data.remote.firestore.FirestoreClientsDataSource
import com.example.fieldtechv20kc.utils.ValidationUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.UUID

class ClientsRepository(
    private val clientDao: ClientDao,
    private val remote: FirestoreClientsDataSource,
    private val pinsRepository: ClientPinsRepository? = null
) {
    
    /**
     * Start two-way sync with Firestore
     * Call once on app startup after user is signed in
     */
    fun startSync(scope: CoroutineScope) {
        scope.launch {
            remote.listenAll().collect { remoteList ->
                // For each remote doc, compare updatedAt vs local and upsert if newer
                remoteList.forEach { dto ->
                    try {
                        val remoteClient = dto.toClient()
                        val local = clientDao.getByIdOnce(remoteClient.id)
                        
                        // Last-write-wins: only apply if remote is newer (or local doesn't exist)
                        if (local == null || remoteClient.updatedAt > local.updatedAt) {
                            clientDao.upsert(remoteClient.copy(companyId = BuildConfig.COMPANY_ID))
                        }
                    } catch (e: Exception) {
                        // Log error but continue syncing other clients
                        e.printStackTrace()
                    }
                }
            }
        }
    }
    
    /**
     * Observe clients with optional filtering and sorting
     */
    fun observeClients(
        query: String? = null,
        hasPump: Boolean? = null,
        sort: ClientSort = ClientSort.NAME_ASC
    ): Flow<List<Client>> {
        return when {
            // Search + filter
            !query.isNullOrBlank() && hasPump != null -> {
                clientDao.searchClientsWithPump(query, hasPump)
            }
            // Search only
            !query.isNullOrBlank() -> {
                clientDao.searchClients(query)
            }
            // Filter only with custom sort
            hasPump != null -> {
                // Note: All sort options return same query for pump filter (DESC sorting is low priority optimization)
                clientDao.getClientsWithPump(hasPump)
            }
            // Sort only
            else -> {
                when (sort) {
                    ClientSort.NAME_ASC -> clientDao.getAllClients()
                    ClientSort.NAME_DESC -> clientDao.getClientsByNameDesc()
                    ClientSort.LOCALITY_ASC -> clientDao.getAllClients()
                    ClientSort.LOCALITY_DESC -> clientDao.getAllClients()
                    ClientSort.LAST_SERVICE_DESC -> clientDao.getClientsByLastService()
                    ClientSort.RECENTLY_ADDED -> clientDao.getAllClients()
                }
            }
        }
    }
    
    /**
     * Observe single client by ID
     */
    fun observeClient(id: String): Flow<Client?> {
        return clientDao.observeClientById(id)
    }
    
    suspend fun getClientById(id: String): Client? {
        return clientDao.getClientById(id)
    }
    
    /**
     * Get single client by ID (suspend)
     */
    suspend fun getClient(id: String): Client? {
        return clientDao.getClientById(id)
    }
    
    /**
     * Observe all localities for filter dropdown
     */
    fun observeLocalities(): Flow<List<String>> {
        return clientDao.observeLocalities()
    }
    
    /**
     * Observe clients grouped by locality (with section headers)
     */
    fun observeClientsGroupedByLocality(): Flow<List<Client>> {
        return clientDao.getAllClientsGroupedByLocality()
    }
    
    /**
     * Observe clients by locality with optional search query
     */
    fun observeByLocality(q: String?, locality: String?): Flow<List<Client>> {
        return clientDao.observeByLocality(q, locality)
    }
    
    /**
     * Add or update a client
     * Returns the client ID (generated if new)
     */
    suspend fun addOrUpdateClient(input: ClientInput): Result<String> {
        // Validate input
        val errors = ValidationUtils.validateClient(
            name = input.name,
            email = null,
            phone = null,
            latitude = input.latitude,
            longitude = input.longitude,
            installDate = input.installDate,
            lastServiceDate = input.lastServiceDate,
            notes = input.notes
        )
        
        if (errors.isNotEmpty()) {
            return Result.failure(IllegalArgumentException(errors.values.joinToString(", ")))
        }
        
        // Create or update client entity
        val now = System.currentTimeMillis()
        val clientId = input.id ?: UUID.randomUUID().toString()
        val client = Client(
            id = clientId,
            companyId = BuildConfig.COMPANY_ID,
            name = input.name.trim(),
            clientCode = input.clientCode?.trim(),
            locality = input.locality.trim(),
            legalName = input.legalName.trim(),
            companyNumber = input.companyNumber.trim(),
            address = input.address?.trim() ?: "",
            hasPump = input.hasPump,
            pumpModel = input.pumpModel?.trim(),
            installDate = input.installDate,
            lastServiceDate = input.lastServiceDate,
            latitude = input.latitude,
            longitude = input.longitude,
            mapsUrl = input.mapsUrl?.trim(),
            notes = input.notes?.trim(),
            productsEquipment = input.productsEquipment?.trim(),
            salesman = input.salesman?.trim(),
            updatedAt = now,
            deleted = false
        )
        
        // Save locally first
        clientDao.upsert(client)
        
        // Push to Firestore (fire-and-forget, will queue if offline)
        try {
            remote.upsert(client)
        } catch (e: Exception) {
            // Log but don't fail the operation
            e.printStackTrace()
        }
        
        // Auto-create pin from mapsUrl if provided
        if (!input.mapsUrl.isNullOrBlank()) {
            try {
                autoCreatePinFromMapsUrl(clientId, input.mapsUrl!!)
            } catch (e: Exception) {
                android.util.Log.e("ClientsRepository", "❌ Failed to create pin for client $clientId: ${e.message}", e)
            }
        }
        
        return Result.success(clientId)
    }
    
    /**
     * Soft delete a client
     */
    suspend fun softDelete(id: String) {
        val now = System.currentTimeMillis()
        clientDao.softDelete(id, now)
        
        // Push delete to Firestore
        try {
            remote.softDelete(id, now)
        } catch (e: Exception) {
            // Log but don't fail the operation
            e.printStackTrace()
        }
    }
    
    /**
     * Restore a soft-deleted client
     */
    suspend fun restore(id: String) {
        clientDao.restore(id)
    }
    
    /**
     * Update the last service date for a client (called when a report is created)
     */
    suspend fun updateLastServiceDate(clientId: String, serviceDate: Long = System.currentTimeMillis()) {
        val now = System.currentTimeMillis()
        clientDao.updateLastServiceDate(clientId, serviceDate, now)
        
        // Sync to Firestore in background (non-blocking, fire-and-forget)
        // This prevents blocking when offline
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val client = clientDao.getClientById(clientId)
                if (client != null) {
                    kotlinx.coroutines.withTimeout(5000) { // 5 second timeout
                        remote.upsert(client.copy(lastServiceDate = serviceDate, updatedAt = now))
                    }
                }
            } catch (e: Exception) {
                // Log but don't fail the operation
                android.util.Log.e("ClientsRepository", "Failed to sync lastServiceDate to Firestore", e)
            }
        }
    }
    
    /**
     * Import clients from a list of inputs
     * Performs dedupe based on email (primary), then name+phone, then name
     */
    suspend fun importClients(rows: List<ClientInput>): ImportResult {
        var inserted = 0
        var updated = 0
        val failed = mutableListOf<ImportError>()
        
        rows.forEachIndexed { index, input ->
            try {
                // Validate
                val errors = ValidationUtils.validateClient(
                    name = input.name,
                    email = null,
                    phone = null,
                    latitude = input.latitude,
                    longitude = input.longitude,
                    installDate = input.installDate,
                    lastServiceDate = input.lastServiceDate,
                    notes = input.notes
                )
                
                if (errors.isNotEmpty()) {
                    failed.add(
                        ImportError(
                            row = index + 1,
                            reason = errors.values.joinToString(", "),
                            data = mapOf("name" to input.name)
                        )
                    )
                    return@forEachIndexed
                }
                
                // Dedupe logic: check if client exists
                val existingClient = findExistingClient(input)
                
                val now = System.currentTimeMillis()
                
                if (existingClient != null) {
                    // Update existing
                    val updatedClient = existingClient.copy(
                        name = input.name.trim(),
                        clientCode = input.clientCode?.trim(),
                        locality = input.locality.trim(),
                        legalName = input.legalName.trim(),
                        companyNumber = input.companyNumber.trim(),
                        address = input.address?.trim() ?: "",
                        hasPump = input.hasPump,
                        pumpModel = input.pumpModel?.trim(),
                        installDate = input.installDate,
                        lastServiceDate = input.lastServiceDate,
                        latitude = input.latitude,
                        longitude = input.longitude,
                        mapsUrl = input.mapsUrl?.trim(),
                        notes = input.notes?.trim(),
                        productsEquipment = input.productsEquipment?.trim(),
                        salesman = input.salesman?.trim(),
                        updatedAt = now
                    )
                    clientDao.upsert(updatedClient)
                    
                    // Push to Firestore
                    try {
                        remote.upsert(updatedClient)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    
                    // Auto-create pin from mapsUrl if provided
                    if (!input.mapsUrl.isNullOrBlank()) {
                        try {
                            autoCreatePinFromMapsUrl(existingClient.id, input.mapsUrl!!)
                        } catch (e: Exception) {
                            android.util.Log.e("ClientsRepository", "❌ Failed to create pin for existing client ${existingClient.id}: ${e.message}", e)
                        }
                    }
                    
                    updated++
                } else {
                    // Insert new
                    val newClient = Client(
                        id = UUID.randomUUID().toString(),
                        companyId = BuildConfig.COMPANY_ID,
                        name = input.name.trim(),
                        clientCode = input.clientCode?.trim(),
                        locality = input.locality.trim(),
                        legalName = input.legalName.trim(),
                        companyNumber = input.companyNumber.trim(),
                        address = input.address?.trim() ?: "",
                        hasPump = input.hasPump,
                        pumpModel = input.pumpModel?.trim(),
                        installDate = input.installDate,
                        lastServiceDate = input.lastServiceDate,
                        latitude = input.latitude,
                        longitude = input.longitude,
                        mapsUrl = input.mapsUrl?.trim(),
                        notes = input.notes?.trim(),
                        productsEquipment = input.productsEquipment?.trim(),
                        salesman = input.salesman?.trim(),
                        updatedAt = now,
                        deleted = false
                    )
                    clientDao.upsert(newClient)
                    
                    // Push to Firestore
                    try {
                        remote.upsert(newClient)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    
                    // Auto-create pin from mapsUrl if provided
                    if (!input.mapsUrl.isNullOrBlank()) {
                        try {
                            autoCreatePinFromMapsUrl(newClient.id, input.mapsUrl!!)
                        } catch (e: Exception) {
                            android.util.Log.e("ClientsRepository", "❌ Failed to create pin for new client ${newClient.id}: ${e.message}", e)
                        }
                    }
                    
                    inserted++
                }
            } catch (e: Exception) {
                failed.add(
                    ImportError(
                        row = index + 1,
                        reason = e.message ?: "Unknown error",
                        data = mapOf("name" to input.name)
                    )
                )
            }
        }
        
        return ImportResult(inserted, updated, failed)
    }
    
    /**
     * Find existing client to avoid duplicates during import
     * Priority:
     * 1. Match by clientCode (if provided and not blank)
     * 2. Match by name + locality (fallback)
     */
    private suspend fun findExistingClient(input: ClientInput): Client? {
        // Priority 1: Check by client code (unique identifier)
        if (!input.clientCode.isNullOrBlank()) {
            val byCode = clientDao.findByClientCode(input.clientCode.trim())
            if (byCode != null) {
                return byCode
            }
        }
        
        // Priority 2: Fallback to name + locality match
        // (for clients without client code)
        if (input.name.isNotBlank() && input.locality.isNotBlank()) {
            val byNameAndLocality = clientDao.findByNameAndLocality(
                name = input.name.trim(),
                locality = input.locality.trim()
            )
            if (byNameAndLocality != null) {
                return byNameAndLocality
            }
        }
        
        return null
    }
    
    /**
     * Auto-create or update location pin from client's mapsUrl
     * Called after client is created/updated with a mapsUrl
     */
    private suspend fun autoCreatePinFromMapsUrl(clientId: String, mapsUrl: String) {
        android.util.Log.d("ClientsRepository", "🔵 autoCreatePinFromMapsUrl called for clientId=$clientId, mapsUrl=${mapsUrl.take(50)}...")
        
        if (pinsRepository == null) {
            android.util.Log.e("ClientsRepository", "❌ pinsRepository is NULL! Pin not created.")
            return
        }
        
        try {
            val pins = pinsRepository ?: run {
                android.util.Log.e("ClientsRepository", "❌ pinsRepository is NULL after check! Pin not created.")
                return
            }
            
            android.util.Log.d("ClientsRepository", "📍 Parsing location input from URL...")
            // Try to extract coordinates from the URL
            val coords = ClientPinsRepository.parseLocationInput(mapsUrl)
            
            android.util.Log.d("ClientsRepository", "📍 Parsed coords: $coords")
            
            // Create pin regardless of whether we have coordinates
            // The pin can work with just the sourceUrl for opening Maps
            val pinInput = ClientPinInput(
                clientId = clientId,
                label = "Location", // Default label
                latitude = coords?.first,
                longitude = coords?.second,
                status = PinStatus.SEEDED,
                sourceUrl = mapsUrl
            )
            
            android.util.Log.d("ClientsRepository", "✅ Creating pin: clientId=$clientId, hasCoords=${coords != null}, url=${mapsUrl.take(50)}...")
            val pinId = pins.addOrUpdate(pinInput)
            android.util.Log.d("ClientsRepository", "✅ Pin created successfully with ID: $pinId")
        } catch (e: Exception) {
            // Log error - pin creation failure should be visible
            android.util.Log.e("ClientsRepository", "❌ Error creating pin: ${e.message}", e)
            e.printStackTrace()
        }
    }
}

