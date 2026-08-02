package com.example.fieldtechv20kc.data.remote.storage

import android.net.Uri
import com.example.fieldtechv20kc.BuildConfig
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import java.io.File

class ReportStorage(
    private val storage: FirebaseStorage = FirebaseStorage.getInstance(),
    private val companyId: String = BuildConfig.COMPANY_ID
) {
    private fun root(): StorageReference =
        storage.reference.child("companies").child(companyId).child("reports")

    fun pdfRef(reportId: Long): StorageReference =
        root().child(reportId.toString()).child("pdf").child("report_${reportId}.pdf")

    fun photoRef(reportId: Long, photoId: Long): StorageReference =
        root().child(reportId.toString()).child("photos").child("photo_${photoId}.jpg")

    suspend fun uploadPdf(reportId: Long, file: File): String {
        val ref = pdfRef(reportId)
        ref.putFile(Uri.fromFile(file)).awaitKtx()
        return ref.path
    }

    suspend fun uploadPhoto(reportId: Long, photoId: Long, file: File): String {
        val ref = photoRef(reportId, photoId)
        ref.putFile(Uri.fromFile(file)).awaitKtx()
        return ref.path
    }

    suspend fun downloadUrl(path: String): Uri =
        storage.getReference(path).downloadUrl.awaitKtx()
    
    suspend fun deleteReportBundle(reportId: String) {
        // Delete the entire report folder (PDF + photos)
        val reportRef = root().child(reportId)
        
        try {
            // List all files in the report folder
            val listResult = reportRef.listAll().awaitKtx()
            
            // Delete all files
            listResult.items.forEach { item ->
                try {
                    item.delete().awaitKtx()
                } catch (e: Exception) {
                    // Continue even if individual file delete fails
                    android.util.Log.e("ReportStorage", "Failed to delete ${item.path}", e)
                }
            }
            
            // Delete subdirectories (pdf, photos)
            listResult.prefixes.forEach { prefix ->
                try {
                    val subList = prefix.listAll().awaitKtx()
                    subList.items.forEach { item ->
                        item.delete().awaitKtx()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ReportStorage", "Failed to delete folder ${prefix.path}", e)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ReportStorage", "Failed to delete report bundle $reportId", e)
            throw e
        }
    }
}

