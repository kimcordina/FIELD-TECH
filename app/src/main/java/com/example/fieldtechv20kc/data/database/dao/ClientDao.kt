package com.example.fieldtechv20kc.data.database.dao

import androidx.room.*
import com.example.fieldtechv20kc.data.model.Client
import kotlinx.coroutines.flow.Flow

@Dao
interface ClientDao {
    
    // Get all non-deleted clients ordered by name
    @Query("SELECT * FROM clients WHERE deleted = 0 ORDER BY name ASC")
    fun getAllClients(): Flow<List<Client>>
    
    // Search clients by name or address (non-deleted only)
    @Query("""
        SELECT * FROM clients 
        WHERE deleted = 0 AND (
            name LIKE '%' || :query || '%' OR 
            address LIKE '%' || :query || '%'
        )
        ORDER BY name ASC
    """)
    fun searchClients(query: String): Flow<List<Client>>
    
    // Get clients with pump filter
    @Query("""
        SELECT * FROM clients 
        WHERE deleted = 0 AND hasPump = :hasPump
        ORDER BY name ASC
    """)
    fun getClientsWithPump(hasPump: Boolean): Flow<List<Client>>
    
    // Search with pump filter
    @Query("""
        SELECT * FROM clients 
        WHERE deleted = 0 
            AND hasPump = :hasPump
            AND (
                name LIKE '%' || :query || '%' OR 
                address LIKE '%' || :query || '%'
            )
        ORDER BY name ASC
    """)
    fun searchClientsWithPump(query: String, hasPump: Boolean): Flow<List<Client>>
    
    // Get clients sorted by last service date (most recent first)
    @Query("SELECT * FROM clients WHERE deleted = 0 ORDER BY lastServiceDate DESC")
    fun getClientsByLastService(): Flow<List<Client>>
    
    // Get clients sorted by name descending
    @Query("SELECT * FROM clients WHERE deleted = 0 ORDER BY name DESC")
    fun getClientsByNameDesc(): Flow<List<Client>>
    
    // Get single client by ID (observe)
    @Query("SELECT * FROM clients WHERE id = :id LIMIT 1")
    fun observeClientById(id: String): Flow<Client?>
    
    // Get single client by ID (suspend)
    @Query("SELECT * FROM clients WHERE id = :id LIMIT 1")
    suspend fun getClientById(id: String): Client?
    
    // Get single client by ID once (for sync comparison)
    @Query("SELECT * FROM clients WHERE id = :id LIMIT 1")
    suspend fun getByIdOnce(id: String): Client?
    
    // Find client by client code (non-deleted only)
    @Query("SELECT * FROM clients WHERE deleted = 0 AND clientCode = :clientCode LIMIT 1")
    suspend fun findByClientCode(clientCode: String): Client?
    
    // Find client by name and locality (fallback for clients without code)
    @Query("SELECT * FROM clients WHERE deleted = 0 AND LOWER(name) = LOWER(:name) AND LOWER(locality) = LOWER(:locality) LIMIT 1")
    suspend fun findByNameAndLocality(name: String, locality: String): Client?
    
    // Upsert client (insert or replace)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(client: Client)
    
    // Update client
    @Update
    suspend fun update(client: Client)
    
    // Update last service date
    @Query("UPDATE clients SET lastServiceDate = :lastServiceDate, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateLastServiceDate(id: String, lastServiceDate: Long, updatedAt: Long = System.currentTimeMillis())
    
    // Soft delete client
    @Query("UPDATE clients SET deleted = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: String, updatedAt: Long = System.currentTimeMillis())
    
    // Restore soft-deleted client
    @Query("UPDATE clients SET deleted = 0, updatedAt = :updatedAt WHERE id = :id")
    suspend fun restore(id: String, updatedAt: Long = System.currentTimeMillis())
    
    // Hard delete (for testing/cleanup)
    @Delete
    suspend fun delete(client: Client)
    
    // Get all clients including deleted (for admin/debug)
    @Query("SELECT * FROM clients ORDER BY name ASC")
    fun getAllClientsIncludingDeleted(): Flow<List<Client>>
    
    // Batch insert for import
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(clients: List<Client>)
    
    // Locality-aware queries for grouping
    @Query("""
        SELECT * FROM clients
        WHERE deleted = 0
        AND (:locality IS NULL OR LOWER(locality) = LOWER(:locality))
        AND (:q IS NULL OR name LIKE '%' || :q || '%' OR locality LIKE '%' || :q || '%' OR address LIKE '%' || :q || '%')
        ORDER BY CASE WHEN locality IS NULL THEN 1 ELSE 0 END, locality, name
    """)
    fun observeByLocality(q: String?, locality: String?): Flow<List<Client>>
    
    // Get distinct localities for filter dropdown
    @Query("SELECT DISTINCT locality FROM clients WHERE deleted = 0 AND locality IS NOT NULL ORDER BY locality")
    fun observeLocalities(): Flow<List<String>>
    
    // Get clients grouped by locality (for section headers)
    @Query("""
        SELECT * FROM clients 
        WHERE deleted = 0
        ORDER BY CASE WHEN locality IS NULL THEN 1 ELSE 0 END, locality, name
    """)
    fun getAllClientsGroupedByLocality(): Flow<List<Client>>
}

