package com.example.fieldtechv20kc.notifications

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.fieldtechv20kc.data.remote.firestore.UsersRemote
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

object NotificationHelper {
    private const val TAG = "FT/NOTIFICATIONS"
    
    /**
     * Check if notification permission is granted (Android 13+)
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // No permission needed on older versions
        }
    }
    
    /**
     * Get the current FCM token
     */
    suspend fun getCurrentToken(): String? {
        return try {
            val token = FirebaseMessaging.getInstance().token.await()
            Log.d(TAG, "FCM token retrieved: ${token.take(20)}...")
            token
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get FCM token", e)
            null
        }
    }
    
    /**
     * Register the current device token with Firestore
     */
    suspend fun registerToken(): Result<String> {
        return try {
            val token = getCurrentToken()
            if (token != null) {
                val usersRemote = UsersRemote()
                usersRemote.addToken(token, "android")
                Log.d(TAG, "Token registered successfully")
                Result.success(token)
            } else {
                Result.failure(Exception("Failed to retrieve FCM token"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register token", e)
            Result.failure(e)
        }
    }
    
    /**
     * Deactivate the current device token in Firestore
     */
    suspend fun deactivateCurrentToken() {
        try {
            val token = getCurrentToken()
            if (token != null) {
                val usersRemote = UsersRemote()
                usersRemote.deactivateToken(token)
                Log.d(TAG, "Token deactivated successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to deactivate token", e)
        }
    }
}










