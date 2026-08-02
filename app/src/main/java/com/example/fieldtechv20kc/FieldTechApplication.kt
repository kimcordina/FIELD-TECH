package com.example.fieldtechv20kc

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.multidex.MultiDex
import com.example.fieldtechv20kc.data.database.AppDatabase
import com.example.fieldtechv20kc.data.remote.firestore.FirestoreClientsDataSource
import com.example.fieldtechv20kc.data.remote.firestore.FirestoreTasksDataSource
import com.example.fieldtechv20kc.data.remote.firestore.FirestoreRequestsDataSource
import com.example.fieldtechv20kc.data.remote.firestore.FirestorePinsDataSource
import com.example.fieldtechv20kc.data.remote.firestore.ReportsRemote
import com.example.fieldtechv20kc.data.remote.storage.FirebaseStorageService
import com.example.fieldtechv20kc.data.repository.ClientsRepository
import com.example.fieldtechv20kc.data.repository.ClientPinsRepository
import com.example.fieldtechv20kc.data.repository.OutboxRepository
import com.example.fieldtechv20kc.data.repository.ReportRepository
import com.example.fieldtechv20kc.data.repository.RouteRepository
import com.example.fieldtechv20kc.data.repository.ServiceTasksRepository
import com.example.fieldtechv20kc.data.repository.ServiceRequestsRepository
import com.example.fieldtechv20kc.usecases.BackfillLocalReportsToCloud
import com.example.fieldtechv20kc.utils.FTLog
import com.example.fieldtechv20kc.utils.ConnectivityObserver
import com.example.fieldtechv20kc.workers.CleanupWorker
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import androidx.work.*
import java.util.concurrent.TimeUnit

class FieldTechApplication : Application() {
    
    val database by lazy { AppDatabase.getDatabase(this) }
    
    // Application-level coroutine scope
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Firebase Storage service
    private val storageService by lazy { FirebaseStorageService() }
    
    // Repositories with Firestore sync and Storage
    val pinsRepository by lazy {
        ClientPinsRepository(
            dao = database.clientPinsDao(),
            remote = FirestorePinsDataSource()
        )
    }
    
    val clientsRepository by lazy {
        ClientsRepository(
            clientDao = database.clientDao(),
            remote = FirestoreClientsDataSource(),
            pinsRepository = pinsRepository
        )
    }
    
    val tasksRepository by lazy {
        ServiceTasksRepository(
            dao = database.serviceTasksDao(),
            remote = FirestoreTasksDataSource(),
            storage = storageService
        )
    }
    
    val requestsRepository by lazy {
        ServiceRequestsRepository(
            dao = database.serviceRequestsDao(),
            remote = FirestoreRequestsDataSource(),
            storage = storageService
        )
    }
    
    val routeRepository by lazy {
        RouteRepository(
            routeDao = database.routeDao(),
            serviceTasksDao = database.serviceTasksDao(),
            clientDao = database.clientDao(),
            clientPinsDao = database.clientPinsDao()
        )
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize centralized logging
        FTLog.init(this)
        FTLog.i("APP", "FieldTech application started")
        
        // Check Google Play Services availability
        checkGooglePlayServices()
        
        // Initialize OutboxRepository
        OutboxRepository.init(database)
        
        // Schedule periodic outbox drain as safety net (every 15 minutes)
        com.example.fieldtechv20kc.utils.OutboxWorkHelpers.schedulePeriodicDrain(this)
        
        // Clean up old error logs (older than 7 days)
        cleanupOldErrors()
        
        // Schedule daily cleanup of old data
        scheduleCleanupWorker()
        
        // Start connectivity observer for auto-kick on reconnect
        val connectivityObserver = ConnectivityObserver(this)
        connectivityObserver.startAutoKickOnReconnect(appScope)
        
        // Start Firestore sync if user is signed in
        initializeFirestoreSync()
    }
    
    /**
     * Check if Google Play Services is available.
     * If not available (e.g., emulator), log a warning and continue in offline mode.
     */
    private fun checkGooglePlayServices() {
        val apiAvailability = GoogleApiAvailability.getInstance()
        val resultCode = apiAvailability.isGooglePlayServicesAvailable(this)
        
        when (resultCode) {
            ConnectionResult.SUCCESS -> {
                FTLog.i("APP", "Google Play Services available ✅")
            }
            ConnectionResult.SERVICE_MISSING -> {
                FTLog.w("APP", "Google Play Services not installed (emulator?). App will work in offline mode.")
            }
            ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED -> {
                FTLog.w("APP", "Google Play Services needs update. Some cloud features may not work.")
            }
            ConnectionResult.SERVICE_DISABLED -> {
                FTLog.w("APP", "Google Play Services disabled. App will work in offline mode.")
            }
            ConnectionResult.SERVICE_INVALID -> {
                FTLog.w("APP", "Google Play Services invalid signature (emulator?). App will work in offline mode.")
            }
            else -> {
                FTLog.w("APP", "Google Play Services unavailable (code: $resultCode). App will work in offline mode.")
            }
        }
    }
    
    private fun initializeFirestoreSync() {
        // When a job is completed, auto-close its linked request (unified Jobs inbox)
        tasksRepository.setOnTaskDoneListener { taskId ->
            requestsRepository.markDoneByLinkedTaskId(taskId)
        }

        appScope.launch {
            try {
                // Wait for auth state and start sync if signed in
                val auth = FirebaseAuth.getInstance()
                if (auth.currentUser != null) {
                    FTLog.i("APP", "User signed in, starting Firestore sync...")
                    clientsRepository.startSync(appScope)
                    tasksRepository.startSync(appScope)
                    requestsRepository.startSync(appScope)
                    pinsRepository.startSync(appScope)
                    routeRepository.startSync(appScope)
                    
                    // Run one-time backfill of local reports to cloud
                    runBackfillIfNeeded()
                    
                    FTLog.i("APP", "Firestore sync initialized successfully ✅")
                } else {
                    FTLog.i("APP", "No user signed in, skipping Firestore sync")
                }
                
                // Listen for auth state changes to start/stop sync
                auth.addAuthStateListener { firebaseAuth ->
                    if (firebaseAuth.currentUser != null) {
                        FTLog.i("APP", "Auth state changed: User signed in, starting sync...")
                        clientsRepository.startSync(appScope)
                        tasksRepository.startSync(appScope)
                        requestsRepository.startSync(appScope)
                        pinsRepository.startSync(appScope)
                        routeRepository.startSync(appScope)
                        
                        // Run backfill on sign-in
                        appScope.launch { runBackfillIfNeeded() }
                    }
                    // Note: We don't stop sync on sign-out as the listener will naturally disconnect
                }
            } catch (e: Exception) {
                // Firebase initialization can fail in emulators without proper Google Play Services
                FTLog.e("APP", "Failed to initialize Firestore sync (will work in offline mode): ${e.message}", e)
            }
        }
    }
    
    private suspend fun runBackfillIfNeeded() {
        try {
            val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            val backfillDone = prefs.getBoolean("backfill_done_v1", false)
            
            if (!backfillDone) {
                Log.d("FieldTechApp", "Running reports backfill...")
                
                val reportRepository = ReportRepository(
                    reportDao = database.reportDao(),
                    clientDao = database.clientDao(),
                    photoDao = database.photoDao()
                )
                
                val backfill = BackfillLocalReportsToCloud(
                    reportRepository = reportRepository,
                    reportsRemote = ReportsRemote()
                )
                
                // Backfill last 180 days
                backfill.run(days = 180)
                
                // Mark as done
                prefs.edit().putBoolean("backfill_done_v1", true).apply()
                Log.d("FieldTechApp", "Backfill complete and marked as done")
            } else {
                Log.d("FieldTechApp", "Backfill already done, skipping")
            }
        } catch (e: Exception) {
            Log.e("FieldTechApp", "Backfill failed", e)
            // Don't mark as done so it can retry next time
        }
    }
    
    /**
     * Schedule periodic cleanup of old completed/cancelled/deleted requests and tasks
     * Runs once daily to delete items older than 14 days along with their files
     */
    private fun scheduleCleanupWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED) // Need network to delete from Firebase
            .build()
        
        val cleanupRequest = PeriodicWorkRequestBuilder<CleanupWorker>(
            repeatInterval = 1,
            repeatIntervalTimeUnit = TimeUnit.DAYS
        )
            .setConstraints(constraints)
            .setInitialDelay(1, TimeUnit.HOURS) // Wait 1 hour after app start for first run
            .build()
        
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            CleanupWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP, // Don't reschedule if already scheduled
            cleanupRequest
        )
        
        FTLog.i("APP", "Scheduled daily cleanup worker for old data (>14 days)")
    }
    
    /**
     * Clean up old error logs on app start
     * Keeps only errors from the last 7 days
     */
    private fun cleanupOldErrors() {
        appScope.launch {
            try {
                database.errorLogDao().deleteOlderThan(7)
                Log.d("FieldTechApp", "Cleaned up error logs older than 7 days")
            } catch (e: Exception) {
                Log.e("FieldTechApp", "Failed to clean up old errors", e)
            }
        }
    }
    
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }
}





