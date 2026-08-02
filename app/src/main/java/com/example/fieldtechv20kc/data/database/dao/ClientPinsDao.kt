package com.example.fieldtechv20kc.data.database.dao

import androidx.room.*
import com.example.fieldtechv20kc.data.model.ClientPinEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ClientPinsDao {
    @Query("""
        SELECT * FROM client_pins
        WHERE clientId = :clientId AND deleted = 0
        ORDER BY isPrimary DESC, updatedAt DESC
    """)
    fun observeForClient(clientId: String): Flow<List<ClientPinEntity>>

    @Query("""
        SELECT * FROM client_pins
        WHERE clientId = :clientId AND isPrimary = 1 AND deleted = 0
        LIMIT 1
    """)
    suspend fun getPrimary(clientId: String): ClientPinEntity?

    @Query("""
        SELECT * FROM client_pins
        WHERE clientId = :clientId AND deleted = 0
        ORDER BY updatedAt DESC
        LIMIT 1
    """)
    suspend fun getFirstPin(clientId: String): ClientPinEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(pin: ClientPinEntity)

    @Query("UPDATE client_pins SET deleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("UPDATE client_pins SET isPrimary = 0, updatedAt = :now WHERE clientId = :clientId")
    suspend fun clearPrimary(clientId: String, now: Long)
    
    @Query("UPDATE client_pins SET isPrimary = 0, updatedAt = :now WHERE clientId = :clientId")
    suspend fun clearPrimaryLocal(clientId: String, now: Long)

    @Query("UPDATE client_pins SET isPrimary = 1, updatedAt = :now WHERE id = :pinId")
    suspend fun markPrimary(pinId: String, now: Long)
    
    @Query("SELECT * FROM client_pins WHERE id = :pinId LIMIT 1")
    suspend fun getById(pinId: String): ClientPinEntity?
    
    @Query("SELECT * FROM client_pins WHERE id = :id LIMIT 1")
    suspend fun getByIdOnce(id: String): ClientPinEntity?
    
    @Query("""
        SELECT * FROM client_pins
        WHERE clientId = :clientId AND isPrimary = 1 AND deleted = 0
        LIMIT 1
    """)
    suspend fun getPrimaryOnce(clientId: String): ClientPinEntity?
    
    @Query("""
        SELECT * FROM client_pins
        WHERE clientId = :clientId
    """)
    suspend fun getAllPinsForClient(clientId: String): List<ClientPinEntity>
}
