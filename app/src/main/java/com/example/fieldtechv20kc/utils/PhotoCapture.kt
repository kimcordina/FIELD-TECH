package com.example.fieldtechv20kc.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class PhotoCaptureContract : ActivityResultContract<Unit, String?>() {
    private var currentPhotoUri: Uri? = null
    private var currentPhotoFile: File? = null
    
    override fun createIntent(context: Context, input: Unit): Intent {
        try {
            val photoFile = createImageFile(context)
            currentPhotoFile = photoFile
            
            val photoURI = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                photoFile
            )
            currentPhotoUri = photoURI
            
            println("DEBUG: Created photo URI: $photoURI")
            println("DEBUG: Photo file path: ${photoFile.absolutePath}")
            
            return Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (e: Exception) {
            println("DEBUG: Error creating camera intent: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }
    
    override fun parseResult(resultCode: Int, intent: Intent?): String? {
        println("DEBUG: parseResult called with resultCode: $resultCode")
        return if (resultCode == android.app.Activity.RESULT_OK) {
            val uriString = currentPhotoUri?.toString()
            println("DEBUG: Photo capture successful, returning URI: $uriString")
            println("DEBUG: File exists: ${currentPhotoFile?.exists()}")
            uriString
        } else {
            println("DEBUG: Photo capture failed or cancelled")
            null
        }
    }
    
    private fun createImageFile(context: Context): File {
        try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val storageDir = File(context.getExternalFilesDir(null), "FieldTechPhotos")
            if (!storageDir.exists()) {
                val created = storageDir.mkdirs()
                println("DEBUG: Created storage directory: $created")
            }
            val photoFile = File.createTempFile(
                "JPEG_${timeStamp}_",
                ".jpg",
                storageDir
            )
            println("DEBUG: Created photo file: ${photoFile.absolutePath}")
            println("DEBUG: File exists: ${photoFile.exists()}")
            println("DEBUG: File can write: ${photoFile.canWrite()}")
            return photoFile
        } catch (e: Exception) {
            println("DEBUG: Error creating image file: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }
}