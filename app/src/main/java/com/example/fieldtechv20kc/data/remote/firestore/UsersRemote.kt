package com.example.fieldtechv20kc.data.remote.firestore

import com.example.fieldtechv20kc.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class UsersRemote(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val companyId: String = BuildConfig.COMPANY_ID
) {
    private fun userDoc() =
        db.collection("companies").document(companyId)
            .collection("users").document(requireNotNull(auth.currentUser?.uid))

    suspend fun upsertProfile(
        displayName: String?,
        assignedToName: String?,          // "Jenson" | "Abubakar" | null
        role: String,                     // "TECH" | "MANAGER" | "REQUESTER" | "NONE"
        notificationsEnabled: Boolean? = null  // If null, use role-based default
    ) {
        val map = mutableMapOf<String, Any?>(
            "displayName" to displayName,
            "role" to role,
            "updatedAt" to System.currentTimeMillis()
        )
        
        // Only include assignedToName if role is TECH
        if (role == "TECH" && assignedToName != null) {
            map["assignedToName"] = assignedToName
        } else if (role != "TECH") {
            // Clear assignedToName if not TECH
            map["assignedToName"] = null
        }
        
        // Set notificationsEnabled based on role if not explicitly provided
        if (notificationsEnabled != null) {
            map["notificationsEnabled"] = notificationsEnabled
        } else {
            // Default: TECH/MANAGER = true, REQUESTER/NONE = false
            map["notificationsEnabled"] = when (role) {
                "TECH", "MANAGER" -> true
                else -> false
            }
        }
        
        userDoc().set(map, SetOptions.merge()).await()
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        userDoc().set(
            mapOf(
                "notificationsEnabled" to enabled,
                "updatedAt" to System.currentTimeMillis()
            ),
            SetOptions.merge()
        ).await()
    }

    suspend fun addToken(token: String, platform: String = "android") {
        userDoc().collection("tokens").document(token).set(
            mapOf(
                "token" to token,
                "platform" to platform,
                "active" to true,
                "createdAt" to System.currentTimeMillis()
            ),
            SetOptions.merge()
        ).await()
    }

    suspend fun deactivateToken(token: String) {
        userDoc().collection("tokens").document(token).set(
            mapOf("active" to false),
            SetOptions.merge()
        ).await()
    }
    
    suspend fun getProfile(): UserProfile? {
        val snapshot = userDoc().get().await()
        return if (snapshot.exists()) {
            UserProfile(
                role = snapshot.getString("role") ?: "NONE",
                assignedToName = snapshot.getString("assignedToName"),
                notificationsEnabled = snapshot.getBoolean("notificationsEnabled") ?: false,
                displayName = snapshot.getString("displayName") ?: "",
                updatedAt = snapshot.getLong("updatedAt") ?: 0L
            )
        } else null
    }
    
    suspend fun getActiveTokenCount(): Int {
        val snapshot = userDoc().collection("tokens")
            .whereEqualTo("active", true)
            .get()
            .await()
        return snapshot.size()
    }
}

data class UserProfile(
    val role: String,
    val assignedToName: String?,
    val notificationsEnabled: Boolean,
    val displayName: String,
    val updatedAt: Long
)

