package com.example.fieldtechv20kc.data.repository

import com.example.fieldtechv20kc.data.database.AppDatabase
import com.example.fieldtechv20kc.data.database.dao.OutboxDao
import com.example.fieldtechv20kc.data.model.OutboxJob
import kotlinx.coroutines.flow.Flow

class OutboxRepository private constructor(
    private val dao: OutboxDao
) {
    companion object {
        @Volatile private var INSTANCE: OutboxRepository? = null
        
        fun init(db: AppDatabase) { 
            INSTANCE = OutboxRepository(db.outboxDao()) 
        }
        
        fun get(): OutboxRepository = requireNotNull(INSTANCE) { 
            "OutboxRepository not initialized" 
        }
    }

    fun observePendingCount(): Flow<Int> = dao.observeCount()
    
    suspend fun getPendingCount(): Int = dao.getCount()

    suspend fun enqueueUploadPdf(reportId: Long, localPath: String) {
        dao.insert(OutboxJob(type = "UPLOAD_PDF", reportId = reportId, payload = localPath))
    }

    suspend fun enqueueUploadPhoto(reportId: Long, photoId: Long, localPath: String) {
        val payload = """{"photoId":$photoId,"path":"$localPath"}"""
        dao.insert(OutboxJob(type = "UPLOAD_PHOTO", reportId = reportId, payload = payload))
    }

    suspend fun enqueueUpsertReport(reportId: Long) {
        dao.insert(OutboxJob(type = "UPSERT_REPORT", reportId = reportId))
    }
    
    suspend fun getAllJobs(): List<OutboxJob> = dao.getAllJobs()
    
    suspend fun deleteJob(job: OutboxJob) = dao.delete(job)
    
    suspend fun updateJob(job: OutboxJob) = dao.update(job)
}
