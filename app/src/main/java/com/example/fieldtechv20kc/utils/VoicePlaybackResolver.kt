package com.example.fieldtechv20kc.utils

import android.net.Uri
import com.example.fieldtechv20kc.data.remote.storage.FirebaseStorageService

object VoicePlaybackResolver {
    
    /**
     * Resolve voice URI for playback with local-first, cloud fallback strategy
     * 
     * @param localUri Local content:// URI (may be null on other devices)
     * @param cloudPath Firestore voicePath (Storage path)
     * @param storage FirebaseStorageService instance
     * @return Uri for playback, or null if neither source is available
     */
    suspend fun resolveVoiceForPlayback(
        localUri: String?,
        cloudPath: String?,
        storage: FirebaseStorageService
    ): Uri? {
        // Prefer local URI (immediate playback, no download)
        if (!localUri.isNullOrBlank()) {
            return Uri.parse(localUri)
        }
        
        // Fallback to cloud download URL
        if (!cloudPath.isNullOrBlank()) {
            return try {
                storage.downloadUrl(cloudPath)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
        
        // No voice available
        return null
    }
}

