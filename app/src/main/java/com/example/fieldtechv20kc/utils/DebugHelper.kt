package com.example.fieldtechv20kc.utils

import android.util.Log

object DebugHelper {
    private const val TAG = "FieldTechDebug"
    
    fun log(message: String) {
        Log.d(TAG, message)
    }
    
    fun logError(message: String, throwable: Throwable? = null) {
        Log.e(TAG, message, throwable)
    }
}





