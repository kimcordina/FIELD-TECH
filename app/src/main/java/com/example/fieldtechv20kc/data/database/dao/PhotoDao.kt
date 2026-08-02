package com.example.fieldtechv20kc.data.database.dao

import androidx.room.*
import com.example.fieldtechv20kc.data.model.Photo
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {
    
    @Query("SELECT * FROM photos WHERE reportId = :reportId")
    fun getPhotosByReportId(reportId: Long): Flow<List<Photo>>
    
    @Query("SELECT * FROM photos WHERE reportId = :reportId")
    suspend fun getPhotosByReportIdSync(reportId: Long): List<Photo>
    
    @Query("SELECT * FROM photos WHERE id = :id")
    suspend fun getPhotoById(id: Long): Photo?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(photo: Photo): Long
    
    @Update
    suspend fun updatePhoto(photo: Photo)
    
    @Delete
    suspend fun deletePhoto(photo: Photo)
    
    @Query("DELETE FROM photos WHERE reportId = :reportId")
    suspend fun deletePhotosByReportId(reportId: Long)
}

