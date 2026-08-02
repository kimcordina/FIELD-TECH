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

class SimplePhotoCapture : ActivityResultContract<Unit, String?>() {
    private var currentPhotoPath: String? = null
    
    override fun createIntent(context: Context, input: Unit): Intent {
        println("DEBUG: SimplePhotoCapture - createIntent called")
        
        // Create a simple file in the app's cache directory
        val photoFile = createImageFile(context)
        currentPhotoPath = photoFile.absolutePath
        
        println("DEBUG: SimplePhotoCapture - Created file: ${photoFile.absolutePath}")
        
        val photoURI = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            photoFile
        )
        
        println("DEBUG: SimplePhotoCapture - Created URI: $photoURI")
        
        return Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    
    override fun parseResult(resultCode: Int, intent: Intent?): String? {
        println("DEBUG: SimplePhotoCapture - parseResult: $resultCode")
        
        return if (resultCode == android.app.Activity.RESULT_OK) {
            println("DEBUG: SimplePhotoCapture - Success, path: $currentPhotoPath")
            
            // Verify the file exists and has content
            currentPhotoPath?.let { path ->
                val file = File(path)
                if (file.exists() && file.length() > 0) {
                    println("DEBUG: SimplePhotoCapture - File verified: ${file.length()} bytes")
                    path
                } else {
                    println("DEBUG: SimplePhotoCapture - File verification failed")
                    null
                }
            }
        } else {
            println("DEBUG: SimplePhotoCapture - Failed or cancelled")
            null
        }
    }
    
    private fun createImageFile(context: Context): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        
        // Use cache directory instead of external files
        val storageDir = File(context.cacheDir, "photos")
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }
        
        val photoFile = File(storageDir, "IMG_${timeStamp}.jpg")
        println("DEBUG: SimplePhotoCapture - Creating file: ${photoFile.absolutePath}")
        
        return photoFile
    }
}





