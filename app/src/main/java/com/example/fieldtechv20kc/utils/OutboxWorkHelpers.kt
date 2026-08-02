package com.example.fieldtechv20kc.utils

import android.content.Context
import androidx.work.*
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class OutboxWorkHelpers {
    companion object {
        private const val TAG = "WORKER"
        private const val UNIQUE_WORK_NAME = "outbox_drain"
        
        /**
         * Kick outbox worker immediately with exponential backoff and constraints
         * Uses REPLACE policy to ensure fresh worker runs when network becomes available
         */
        fun kickNow(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)  // Require network
                .build()
            
            val req = OneTimeWorkRequestBuilder<com.example.fieldtechv20kc.workers.OutboxWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    Duration.ofSeconds(30)  // Start with 30s, doubles each retry with jitter
                )
                .addTag(UNIQUE_WORK_NAME)
                .build()
            
            // Use REPLACE to ensure a fresh worker runs when called
            // This is critical for offline→online transitions
            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    UNIQUE_WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    req
                )
            
            FTLog.i(TAG, "Outbox worker enqueued with REPLACE policy")
        }
        
        /**
         * Schedule periodic outbox drain (every 15 minutes)
         * Good for background reliability
         */
        fun schedulePeriodicDrain(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            
            val periodicWork = PeriodicWorkRequestBuilder<com.example.fieldtechv20kc.workers.OutboxWorker>(
                15, TimeUnit.MINUTES  // Run every 15 minutes
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    Duration.ofSeconds(60)
                )
                .addTag("outbox_periodic")
                .build()
            
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    "outbox_periodic_drain",
                    ExistingPeriodicWorkPolicy.KEEP,
                    periodicWork
                )
            
            FTLog.i(TAG, "Periodic outbox drain scheduled (15min)")
        }
    }
}



