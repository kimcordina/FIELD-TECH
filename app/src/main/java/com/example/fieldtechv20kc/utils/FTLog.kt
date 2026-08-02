package com.example.fieldtechv20kc.utils

import android.content.Context
import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.example.fieldtechv20kc.data.database.AppDatabase
import com.example.fieldtechv20kc.data.model.ErrorLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Centralized logging helper for FieldTech
 * 
 * Tags:
 * - FT/OUTBOX: Outbox worker operations
 * - FT/UPLOAD: PDF/photo/audio upload operations
 * - FT/FCM: Firebase Cloud Messaging
 * - FT/FUNCTIONS: Cloud Functions calls
 * - FT/FIRESTORE: Firestore operations
 * - FT/STORAGE: Firebase Storage operations
 * - FT/SYNC: General sync operations
 * - FT/WORKER: WorkManager operations
 * - FT/INTEGRITY: File integrity checks
 * - FT/CLEANUP: Cache cleanup operations
 */
object FTLog {
    
    private const val BASE_TAG = "FieldTech"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Lazy database reference
    private var database: AppDatabase? = null
    
    fun init(context: Context) {
        database = AppDatabase.getDatabase(context)
    }
    
    enum class Level(val priority: Int, val displayName: String) {
        INFO(Log.INFO, "Info"),
        WARN(Log.WARN, "Warning"),
        ERROR(Log.ERROR, "Error")
    }
    
    /**
     * Log an info message
     */
    fun i(tag: String, message: String, throwable: Throwable? = null) {
        log(Level.INFO, tag, message, throwable)
    }
    
    /**
     * Log a warning
     */
    fun w(tag: String, message: String, throwable: Throwable? = null) {
        log(Level.WARN, tag, message, throwable)
    }
    
    /**
     * Log an error
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        log(Level.ERROR, tag, message, throwable)
    }
    
    /**
     * Core logging function
     */
    private fun log(level: Level, tag: String, message: String, throwable: Throwable?) {
        val fullTag = "$BASE_TAG/$tag"
        
        // Log to Logcat
        when (level) {
            Level.INFO -> Log.i(fullTag, message, throwable)
            Level.WARN -> Log.w(fullTag, message, throwable)
            Level.ERROR -> Log.e(fullTag, message, throwable)
        }
        
        // Log to Crashlytics (breadcrumbs for all, non-fatals for errors)
        try {
            val crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.log("[$fullTag] $message")
            
            if (throwable != null && level == Level.ERROR) {
                crashlytics.recordException(throwable)
            }
        } catch (e: Exception) {
            Log.e(BASE_TAG, "Failed to log to Crashlytics", e)
        }
        
        // Store in local Error Tray database (errors and warnings only)
        if (level == Level.ERROR || level == Level.WARN) {
            scope.launch {
                try {
                    database?.errorLogDao()?.insert(
                        ErrorLog(
                            level = level.displayName,
                            tag = tag,
                            message = message,
                            stackTrace = throwable?.stackTraceToString(),
                            timestamp = System.currentTimeMillis()
                        )
                    )
                } catch (e: Exception) {
                    Log.e(BASE_TAG, "Failed to store error log", e)
                }
            }
        }
    }
    
    /**
     * Set custom keys for Crashlytics context
     */
    fun setUserContext(userId: String, role: String) {
        try {
            val crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.setUserId(userId)
            crashlytics.setCustomKey("role", role)
        } catch (e: Exception) {
            Log.e(BASE_TAG, "Failed to set user context", e)
        }
    }
    
    /**
     * Clear user context (on logout)
     */
    fun clearUserContext() {
        try {
            val crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.setUserId("")
            crashlytics.setCustomKey("role", "")
        } catch (e: Exception) {
            Log.e(BASE_TAG, "Failed to clear user context", e)
        }
    }
    
    /**
     * Force send any unsent crash reports
     */
    fun sendUnsentReports() {
        try {
            FirebaseCrashlytics.getInstance().sendUnsentReports()
        } catch (e: Exception) {
            Log.e(BASE_TAG, "Failed to send unsent reports", e)
        }
    }
}

