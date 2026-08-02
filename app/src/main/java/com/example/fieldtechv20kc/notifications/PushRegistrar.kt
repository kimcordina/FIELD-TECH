package com.example.fieldtechv20kc.notifications

import android.Manifest
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.example.fieldtechv20kc.data.remote.firestore.UsersRemote
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

class PushRegistrar(
    private val activity: ComponentActivity,
    private val usersRemote: UsersRemote
) {
    private val requestPermission = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op */ }

    suspend fun ensureRegistered() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val token = FirebaseMessaging.getInstance().token.await()
        usersRemote.addToken(token, "android")
    }
}

