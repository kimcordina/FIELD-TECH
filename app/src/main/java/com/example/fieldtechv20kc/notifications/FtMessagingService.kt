package com.example.fieldtechv20kc.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.fieldtechv20kc.MainActivity
import com.example.fieldtechv20kc.R
import com.example.fieldtechv20kc.data.remote.firestore.UsersRemote
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FtMessagingService : FirebaseMessagingService() {
    
    companion object {
        private const val TAG = "FT/FCM"
        private const val CHANNEL_ID = "tasks_channel"
    }
    
    override fun onMessageReceived(message: RemoteMessage) {
        Log.d(TAG, "Message received from: ${message.from}")
        Log.d(TAG, "Message data: ${message.data}")
        
        val title = message.notification?.title ?: message.data["title"] ?: "Field Tech"
        val body = message.notification?.body ?: message.data["body"] ?: "You have an update"
        val type = message.data["type"]
        val clickAction = message.data["click_action"]
        
        Log.d(TAG, "Notification - Title: $title, Body: $body, Type: $type, Click Action: $clickAction")

        // Create notification channel for Android O+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Tasks & Updates",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for task assignments and completions"
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }

        // Create intent to open the app with the specified action
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            when (clickAction) {
                "OPEN_TASKS" -> putExtra("navigate_to", "tasks")
                "OPEN_REPORTS" -> putExtra("navigate_to", "reports")
                // Requests tab merged into Jobs — open the unified inbox
                "OPEN_REQUESTS" -> putExtra("navigate_to", "tasks")
            }
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(this)
            .notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
        
        Log.d(TAG, "Notification displayed successfully with click action: $clickAction")
    }
    
    override fun onNewToken(token: String) {
        Log.d(TAG, "New FCM token: $token")
        
        // Track the previous token so we can deactivate it on rotation.
        // Without this, old tokens stay "active" in Firestore and the same
        // device receives duplicate notifications.
        val prefs = getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
        val previousToken = prefs.getString("last_token", null)
        prefs.edit().putString("last_token", token).apply()
        
        // Save the new token to Firestore (and deactivate the old one)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val usersRemote = UsersRemote()
                if (previousToken != null && previousToken != token) {
                    try {
                        usersRemote.deactivateToken(previousToken)
                        Log.d(TAG, "Old token deactivated after rotation")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to deactivate old token", e)
                    }
                }
                usersRemote.addToken(token, "android")
                Log.d(TAG, "Token saved to Firestore successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save token to Firestore", e)
            }
        }
    }
}

