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

class CameraCapture : ActivityResultContract<Unit, Uri?>() {
    private var currentPhotoUri: Uri? = null
    
    override fun createIntent(context: Context, input: Unit): Intent {
        println("DEBUG: CameraCapture - createIntent called")
        
        // Create a photo file
        val photoFile = createImageFile(context)
        currentPhotoUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            photoFile
        )
        
        println("DEBUG: CameraCapture - Created photo URI: $currentPhotoUri")
        println("DEBUG: CameraCapture - Photo file: ${photoFile.absolutePath}")
        
        return Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, currentPhotoUri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    
    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        println("DEBUG: CameraCapture - parseResult called with resultCode: $resultCode")
        
        return if (resultCode == android.app.Activity.RESULT_OK) {
            println("DEBUG: CameraCapture - Photo capture successful")
            currentPhotoUri
        } else {
            println("DEBUG: CameraCapture - Photo capture failed or cancelled")
            null
        }
    }
    
    private fun createImageFile(context: Context): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = File(context.getExternalFilesDir(null), "FieldTechPhotos")
        
        if (!storageDir.exists()) {
            val created = storageDir.mkdirs()
            println("DEBUG: CameraCapture - Created storage directory: $created")
        }
        
        val photoFile = File(storageDir, "IMG_${timeStamp}.jpg")
        println("DEBUG: CameraCapture - Creating photo file: ${photoFile.absolutePath}")
        
        return photoFile
    }
}





