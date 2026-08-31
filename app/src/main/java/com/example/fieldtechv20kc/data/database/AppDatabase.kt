package com.example.fieldtechv20kc.data.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context
import com.example.fieldtechv20kc.data.database.dao.ClientDao
import com.example.fieldtechv20kc.data.database.dao.ClientPinsDao
import com.example.fieldtechv20kc.data.database.dao.ErrorLogDao
import com.example.fieldtechv20kc.data.database.dao.OutboxDao
import com.example.fieldtechv20kc.data.database.dao.PhotoDao
import com.example.fieldtechv20kc.data.database.dao.ReportDao
import com.example.fieldtechv20kc.data.database.dao.RouteDao
import com.example.fieldtechv20kc.data.database.dao.ServiceTasksDao
import com.example.fieldtechv20kc.data.database.dao.ServiceRequestsDao
import com.example.fieldtechv20kc.data.model.Client
import com.example.fieldtechv20kc.data.model.ClientPinEntity
import com.example.fieldtechv20kc.data.model.ErrorLog
import com.example.fieldtechv20kc.data.model.OutboxJob
import com.example.fieldtechv20kc.data.model.Photo
import com.example.fieldtechv20kc.data.model.Report
import com.example.fieldtechv20kc.data.model.Route
import com.example.fieldtechv20kc.data.model.RouteStop
import com.example.fieldtechv20kc.data.model.ServiceTask
import com.example.fieldtechv20kc.data.model.ServiceRequest
import com.example.fieldtechv20kc.data.model.Statistics
import com.example.fieldtechv20kc.data.model.LocalityStatistics

@Database(
    entities = [Client::class, Report::class, Photo::class, Statistics::class, LocalityStatistics::class, ServiceTask::class, ServiceRequest::class, ClientPinEntity::class, OutboxJob::class, ErrorLog::class, Route::class, RouteStop::class],
    version = 34,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun clientDao(): ClientDao
    abstract fun reportDao(): ReportDao
    abstract fun photoDao(): PhotoDao
    abstract fun statisticsDao(): StatisticsDao
    abstract fun serviceTasksDao(): ServiceTasksDao
    abstract fun serviceRequestsDao(): ServiceRequestsDao
    abstract fun clientPinsDao(): ClientPinsDao
    abstract fun outboxDao(): OutboxDao
    abstract fun errorLogDao(): ErrorLogDao
    abstract fun routeDao(): RouteDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Drop the old clients table and recreate with new schema
                database.execSQL("DROP TABLE IF EXISTS clients")
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS clients (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        locality TEXT NOT NULL
                    )
                """.trimIndent())
            }
        }
        
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Drop the old reports table and recreate with new schema
                database.execSQL("DROP TABLE IF EXISTS reports")
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS reports (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        clientId INTEGER NOT NULL,
                        jobType TEXT NOT NULL,
                        equipmentInstalledRepaired TEXT NOT NULL,
                        serialNumbers TEXT NOT NULL,
                        workCarriedOut TEXT NOT NULL,
                        findings TEXT NOT NULL,
                        signatureData TEXT NOT NULL,
                        pdfPath TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        isCompleted INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }
        
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Drop the old reports table and recreate with new schema
                database.execSQL("DROP TABLE IF EXISTS reports")
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS reports (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        clientId INTEGER NOT NULL,
                        jobType TEXT NOT NULL,
                        equipmentInstalledRepaired TEXT NOT NULL,
                        serialNumbers TEXT NOT NULL,
                        workCarriedOut TEXT NOT NULL,
                        findings TEXT NOT NULL,
                        signerName TEXT NOT NULL,
                        signatureData TEXT NOT NULL,
                        pdfPath TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        isCompleted INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }
        
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add new fields to clients table
                database.execSQL("ALTER TABLE clients ADD COLUMN legalName TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE clients ADD COLUMN companyNumber TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE clients ADD COLUMN address TEXT NOT NULL DEFAULT ''")
            }
        }
        
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add custom job type fields to reports table
                database.execSQL("ALTER TABLE reports ADD COLUMN isCustomJobType INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE reports ADD COLUMN customJobTypeId TEXT")
                database.execSQL("ALTER TABLE reports ADD COLUMN customJobTypeDisplayName TEXT")
                database.execSQL("ALTER TABLE reports ADD COLUMN customJobTypeLegalTitle TEXT")
                database.execSQL("ALTER TABLE reports ADD COLUMN customJobTypeLegalText TEXT")
            }
        }
        
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add time tracking fields to reports table
                database.execSQL("ALTER TABLE reports ADD COLUMN timeStarted TEXT")
                database.execSQL("ALTER TABLE reports ADD COLUMN timeCompleted TEXT")
            }
        }
        
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add technician name field to reports table
                database.execSQL("ALTER TABLE reports ADD COLUMN technicianName TEXT NOT NULL DEFAULT ''")
            }
        }
        
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create statistics table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS statistics (
                        id INTEGER PRIMARY KEY NOT NULL,
                        totalReports INTEGER NOT NULL DEFAULT 0,
                        lastUpdated INTEGER NOT NULL
                    )
                """.trimIndent())
                
                // Create locality_statistics table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS locality_statistics (
                        locality TEXT PRIMARY KEY NOT NULL,
                        reportCount INTEGER NOT NULL DEFAULT 0,
                        lastUpdated INTEGER NOT NULL
                    )
                """.trimIndent())
                
                // Initialize statistics with default values
                database.execSQL("""
                    INSERT OR IGNORE INTO statistics (id, totalReports, lastUpdated) 
                    VALUES (1, 0, ${System.currentTimeMillis()})
                """.trimIndent())
            }
        }
        
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Step 1: Migrate clients table from INTEGER id to TEXT UUID
                // Create new clients table with UUID and new fields
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS clients_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        companyId TEXT NOT NULL DEFAULT 'local',
                        name TEXT NOT NULL,
                        locality TEXT NOT NULL DEFAULT '',
                        legalName TEXT NOT NULL DEFAULT '',
                        companyNumber TEXT NOT NULL DEFAULT '',
                        address TEXT NOT NULL DEFAULT '',
                        phone TEXT,
                        email TEXT,
                        hasPump INTEGER NOT NULL DEFAULT 1,
                        pumpModel TEXT,
                        installDate INTEGER,
                        lastServiceDate INTEGER,
                        latitude REAL,
                        longitude REAL,
                        notes TEXT,
                        updatedAt INTEGER NOT NULL,
                        deleted INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                
                // Copy existing client data, generating UUIDs
                database.execSQL("""
                    INSERT INTO clients_new (id, name, locality, legalName, companyNumber, address, updatedAt)
                    SELECT 
                        'legacy-' || CAST(id AS TEXT),
                        name,
                        COALESCE(locality, ''),
                        COALESCE(legalName, ''),
                        COALESCE(companyNumber, ''),
                        COALESCE(address, ''),
                        ${System.currentTimeMillis()}
                    FROM clients
                """.trimIndent())
                
                // Drop old clients table
                database.execSQL("DROP TABLE clients")
                
                // Rename new table
                database.execSQL("ALTER TABLE clients_new RENAME TO clients")
                
                // Create indices for clients
                database.execSQL("CREATE INDEX IF NOT EXISTS index_clients_name ON clients(name)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_clients_deleted_name ON clients(deleted, name)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_clients_lastServiceDate ON clients(lastServiceDate)")
                
                // Step 2: Update reports table - change clientId from INTEGER to TEXT
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS reports_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        clientId TEXT,
                        jobType TEXT NOT NULL,
                        equipmentInstalledRepaired TEXT NOT NULL,
                        serialNumbers TEXT NOT NULL,
                        workCarriedOut TEXT NOT NULL,
                        technicianName TEXT NOT NULL,
                        findings TEXT NOT NULL,
                        signerName TEXT NOT NULL,
                        signatureData TEXT NOT NULL,
                        pdfPath TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        isCompleted INTEGER NOT NULL,
                        isCustomJobType INTEGER NOT NULL DEFAULT 0,
                        customJobTypeId TEXT,
                        customJobTypeDisplayName TEXT,
                        customJobTypeLegalTitle TEXT,
                        customJobTypeLegalText TEXT,
                        timeStarted TEXT,
                        timeCompleted TEXT
                    )
                """.trimIndent())
                
                // Copy existing reports, converting clientId to TEXT UUID format
                database.execSQL("""
                    INSERT INTO reports_new 
                    SELECT 
                        id,
                        'legacy-' || CAST(clientId AS TEXT),
                        jobType,
                        equipmentInstalledRepaired,
                        serialNumbers,
                        workCarriedOut,
                        technicianName,
                        findings,
                        signerName,
                        signatureData,
                        pdfPath,
                        createdAt,
                        isCompleted,
                        isCustomJobType,
                        customJobTypeId,
                        customJobTypeDisplayName,
                        customJobTypeLegalTitle,
                        customJobTypeLegalText,
                        timeStarted,
                        timeCompleted
                    FROM reports
                """.trimIndent())
                
                // Drop old reports table
                database.execSQL("DROP TABLE reports")
                
                // Rename new table
                database.execSQL("ALTER TABLE reports_new RENAME TO reports")
                
                // Create index for reports.clientId
                database.execSQL("CREATE INDEX IF NOT EXISTS index_reports_clientId ON reports(clientId)")
            }
        }
        
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // The locality field already exists in the clients table from v10,
                // but we need to ensure the index exists
                // Check if locality column exists, if not add it (safety check)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_clients_locality_name ON clients(locality, name)")
            }
        }
        
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create service_tasks table for local task assignments
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS service_tasks (
                        id TEXT NOT NULL PRIMARY KEY,
                        clientId TEXT NOT NULL,
                        title TEXT NOT NULL,
                        assignedToName TEXT NOT NULL,
                        colorTag TEXT NOT NULL,
                        scheduledDate INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        notes TEXT,
                        linkedReportId TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        deleted INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                
                // Create indices for efficient queries
                database.execSQL("CREATE INDEX IF NOT EXISTS idx_tasks_assignee ON service_tasks(assignedToName)")
                database.execSQL("CREATE INDEX IF NOT EXISTS idx_tasks_schedule ON service_tasks(scheduledDate)")
                database.execSQL("CREATE INDEX IF NOT EXISTS idx_tasks_status ON service_tasks(status)")
                database.execSQL("CREATE INDEX IF NOT EXISTS idx_tasks_client_status ON service_tasks(clientId, status)")
            }
        }
        
        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Remove colorTag column from service_tasks table
                // SQLite doesn't support DROP COLUMN, so we need to recreate the table
                
                // Create new table without colorTag
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS service_tasks_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        clientId TEXT NOT NULL,
                        title TEXT NOT NULL,
                        assignedToName TEXT NOT NULL,
                        scheduledDate INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        notes TEXT,
                        linkedReportId TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        deleted INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                
                // Copy data from old table (excluding colorTag)
                database.execSQL("""
                    INSERT INTO service_tasks_new 
                    SELECT id, clientId, title, assignedToName, scheduledDate, status, 
                           notes, linkedReportId, createdAt, updatedAt, deleted
                    FROM service_tasks
                """.trimIndent())
                
                // Drop old table
                database.execSQL("DROP TABLE service_tasks")
                
                // Rename new table
                database.execSQL("ALTER TABLE service_tasks_new RENAME TO service_tasks")
                
                // Recreate indices with the correct names that Room expects
                database.execSQL("CREATE INDEX IF NOT EXISTS index_service_tasks_assignedToName ON service_tasks(assignedToName)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_service_tasks_scheduledDate ON service_tasks(scheduledDate)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_service_tasks_status ON service_tasks(status)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_service_tasks_clientId_status ON service_tasks(clientId, status)")
            }
        }
        
        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Remove phone and email columns from clients table
                // SQLite doesn't support DROP COLUMN, so we need to recreate the table
                
                // Create new table without phone and email
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS clients_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        companyId TEXT NOT NULL DEFAULT 'local',
                        name TEXT NOT NULL,
                        locality TEXT,
                        legalName TEXT NOT NULL DEFAULT '',
                        companyNumber TEXT NOT NULL DEFAULT '',
                        address TEXT NOT NULL DEFAULT '',
                        hasPump INTEGER NOT NULL DEFAULT 1,
                        pumpModel TEXT,
                        installDate INTEGER,
                        lastServiceDate INTEGER,
                        latitude REAL,
                        longitude REAL,
                        notes TEXT,
                        updatedAt INTEGER NOT NULL,
                        deleted INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                
                // Copy data from old table (excluding phone and email)
                database.execSQL("""
                    INSERT INTO clients_new 
                    SELECT id, companyId, name, locality, legalName, companyNumber, address,
                           hasPump, pumpModel, installDate, lastServiceDate, 
                           latitude, longitude, notes, updatedAt, deleted
                    FROM clients
                """.trimIndent())
                
                // Drop old table
                database.execSQL("DROP TABLE clients")
                
                // Rename new table
                database.execSQL("ALTER TABLE clients_new RENAME TO clients")
                
                // Recreate indices
                database.execSQL("CREATE INDEX IF NOT EXISTS index_clients_name ON clients(name)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_clients_deleted_name ON clients(deleted, name)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_clients_locality_name ON clients(locality, name)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_clients_lastServiceDate ON clients(lastServiceDate)")
            }
        }
        
        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create service_requests table for service request management
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS service_requests (
                        id TEXT NOT NULL PRIMARY KEY,
                        clientId TEXT NOT NULL,
                        notes TEXT,
                        voiceUri TEXT,
                        status TEXT NOT NULL,
                        linkedTaskId TEXT,
                        requestedByName TEXT,
                        requestedAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        deleted INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                
                // Create indices for efficient queries
                database.execSQL("CREATE INDEX IF NOT EXISTS index_service_requests_status ON service_requests(status)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_service_requests_requestedAt ON service_requests(requestedAt)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_service_requests_clientId_status ON service_requests(clientId, status)")
            }
        }
        
        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add voiceNoteUri column to service_tasks table
                database.execSQL("ALTER TABLE service_tasks ADD COLUMN voiceNoteUri TEXT DEFAULT NULL")
            }
        }
        
        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create client_pins table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS client_pins (
                        id TEXT NOT NULL PRIMARY KEY,
                        clientId TEXT NOT NULL,
                        label TEXT NOT NULL,
                        latitude REAL,
                        longitude REAL,
                        isPrimary INTEGER NOT NULL DEFAULT 0,
                        status TEXT NOT NULL,
                        sourceUrl TEXT,
                        createdBy TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        deleted INTEGER NOT NULL DEFAULT 0
                    )
                """)
                
                // Create indices
                database.execSQL("CREATE INDEX IF NOT EXISTS index_client_pins_clientId ON client_pins(clientId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_client_pins_clientId_isPrimary ON client_pins(clientId, isPrimary)")
                
                // Optional: Backfill legacy locations
                // For each client with latitude/longitude and no pins, create a "Legacy location" pin
                database.execSQL("""
                    INSERT INTO client_pins (id, clientId, label, latitude, longitude, isPrimary, status, createdAt, updatedAt, deleted)
                    SELECT 
                        lower(hex(randomblob(16))),
                        id,
                        'Legacy location',
                        latitude,
                        longitude,
                        1,
                        'SEEDED',
                        ${System.currentTimeMillis()},
                        ${System.currentTimeMillis()},
                        0
                    FROM clients
                    WHERE latitude IS NOT NULL 
                        AND longitude IS NOT NULL
                        AND id NOT IN (SELECT DISTINCT clientId FROM client_pins WHERE deleted = 0)
                """)
            }
        }
        
        private val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add outbox_jobs table for WorkManager-based upload queue
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS outbox_jobs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        type TEXT NOT NULL,
                        reportId INTEGER NOT NULL,
                        payload TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        attempts INTEGER NOT NULL,
                        lastError TEXT
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_outbox_jobs_reportId ON outbox_jobs(reportId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_outbox_jobs_type ON outbox_jobs(type)")
            }
        }
        
        private val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add internalNotes column to reports table
                database.execSQL("ALTER TABLE reports ADD COLUMN internalNotes TEXT")
            }
        }
        
        private val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add cancelledByName column to service_requests table
                database.execSQL("ALTER TABLE service_requests ADD COLUMN cancelledByName TEXT")
            }
        }
        
        private val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add createdByName and cancelledByName columns to service_tasks table
                database.execSQL("ALTER TABLE service_tasks ADD COLUMN createdByName TEXT")
                database.execSQL("ALTER TABLE service_tasks ADD COLUMN cancelledByName TEXT")
            }
        }
        
        private val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add clientCode column to clients table
                database.execSQL("ALTER TABLE clients ADD COLUMN clientCode TEXT")
                // Create index on clientCode for faster lookups
                database.execSQL("CREATE INDEX IF NOT EXISTS index_clients_clientCode ON clients(clientCode)")
            }
        }
        
        private val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add mapsUrl column to clients table
                database.execSQL("ALTER TABLE clients ADD COLUMN mapsUrl TEXT")
            }
        }
        
        private val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create error_logs table for diagnostics
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS error_logs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        level TEXT NOT NULL,
                        tag TEXT NOT NULL,
                        message TEXT NOT NULL,
                        stackTrace TEXT,
                        timestamp INTEGER NOT NULL
                    )
                """.trimIndent())
                // Create index on timestamp for faster queries
                database.execSQL("CREATE INDEX IF NOT EXISTS index_error_logs_timestamp ON error_logs(timestamp DESC)")
            }
        }
        
        private val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add quarantine and retry tracking fields to outbox_jobs
                database.execSQL("ALTER TABLE outbox_jobs ADD COLUMN quarantined INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE outbox_jobs ADD COLUMN lastAttemptAt INTEGER")
                // Create index on quarantined for faster queries
                database.execSQL("CREATE INDEX IF NOT EXISTS index_outbox_jobs_quarantined ON outbox_jobs(quarantined)")
            }
        }
        
        private val MIGRATION_25_26 = object : Migration(25, 26) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create routes table (old schema)
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS routes (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        technicianName TEXT NOT NULL,
                        startStrategy TEXT NOT NULL,
                        totalEstimatedDistance REAL,
                        isCompleted INTEGER NOT NULL,
                        completedAt INTEGER
                    )
                """)
                
                // Create route_stops table (old schema)
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS route_stops (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        routeId INTEGER NOT NULL,
                        jobId TEXT NOT NULL,
                        clientId TEXT NOT NULL,
                        clientName TEXT NOT NULL,
                        locality TEXT NOT NULL,
                        orderIndex INTEGER NOT NULL,
                        latitude REAL NOT NULL,
                        longitude REAL NOT NULL,
                        distanceFromPrevious REAL,
                        isCompleted INTEGER NOT NULL,
                        completedAt INTEGER
                    )
                """)
            }
        }
        
        private val MIGRATION_26_27 = object : Migration(26, 27) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Drop old routes tables (they were never used in production yet)
                database.execSQL("DROP TABLE IF EXISTS route_stops")
                database.execSQL("DROP TABLE IF EXISTS routes")
                
                // Create new routes table with updated schema
                database.execSQL("""
                    CREATE TABLE routes (
                        id TEXT PRIMARY KEY NOT NULL,
                        name TEXT NOT NULL,
                        createdBy TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        intendedAssignee TEXT,
                        totalEstimatedDistance REAL,
                        totalEstimatedTime INTEGER,
                        completedStopsCount INTEGER NOT NULL DEFAULT 0,
                        totalStopsCount INTEGER NOT NULL DEFAULT 0,
                        isCompleted INTEGER NOT NULL DEFAULT 0,
                        completedAt INTEGER,
                        completedBy TEXT,
                        deleted INTEGER NOT NULL DEFAULT 0
                    )
                """)
                
                // Create new route_stops table with updated schema
                database.execSQL("""
                    CREATE TABLE route_stops (
                        id TEXT PRIMARY KEY NOT NULL,
                        routeId TEXT NOT NULL,
                        jobId TEXT NOT NULL,
                        clientId TEXT NOT NULL,
                        clientName TEXT NOT NULL,
                        locality TEXT NOT NULL,
                        address TEXT,
                        orderIndex INTEGER NOT NULL,
                        latitude REAL,
                        longitude REAL,
                        distanceFromPrevious REAL,
                        timeFromPrevious INTEGER,
                        isCompleted INTEGER NOT NULL DEFAULT 0,
                        completedAt INTEGER,
                        completedBy TEXT
                    )
                """)
            }
        }
        
        private val MIGRATION_27_28 = object : Migration(27, 28) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add deletedByName and deletedAt fields to service_tasks
                database.execSQL("ALTER TABLE service_tasks ADD COLUMN deletedByName TEXT")
                database.execSQL("ALTER TABLE service_tasks ADD COLUMN deletedAt INTEGER")
            }
        }
        
        private val MIGRATION_28_29 = object : Migration(28, 29) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add photoUris field to service_tasks and service_requests
                database.execSQL("ALTER TABLE service_tasks ADD COLUMN photoUris TEXT")
                database.execSQL("ALTER TABLE service_requests ADD COLUMN photoUris TEXT")
            }
        }
        
        private val MIGRATION_29_30 = object : Migration(29, 30) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add productsEquipment field to clients table
                database.execSQL("ALTER TABLE clients ADD COLUMN productsEquipment TEXT")
            }
        }
        
        private val MIGRATION_30_31 = object : Migration(30, 31) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add salesman field to clients table
                database.execSQL("ALTER TABLE clients ADD COLUMN salesman TEXT")
            }
        }
        
        private val MIGRATION_31_32 = object : Migration(31, 32) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add deleted field to reports table for trash bin functionality
                // We need to recreate the table to avoid DEFAULT value in schema metadata
                // which causes Room validation to fail
                
                // 1. Create new table with deleted column
                database.execSQL("""
                    CREATE TABLE reports_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        clientId TEXT,
                        jobType TEXT NOT NULL,
                        equipmentInstalledRepaired TEXT NOT NULL,
                        serialNumbers TEXT NOT NULL,
                        workCarriedOut TEXT NOT NULL,
                        technicianName TEXT NOT NULL,
                        findings TEXT NOT NULL,
                        signerName TEXT NOT NULL,
                        signatureData TEXT NOT NULL,
                        pdfPath TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        isCompleted INTEGER NOT NULL,
                        isCustomJobType INTEGER NOT NULL,
                        customJobTypeId TEXT,
                        customJobTypeDisplayName TEXT,
                        customJobTypeLegalTitle TEXT,
                        customJobTypeLegalText TEXT,
                        timeStarted TEXT,
                        timeCompleted TEXT,
                        internalNotes TEXT,
                        deleted INTEGER NOT NULL
                    )
                """.trimIndent())
                
                // 2. Copy all existing data with deleted = 0
                database.execSQL("""
                    INSERT INTO reports_new 
                    SELECT 
                        id, clientId, jobType, equipmentInstalledRepaired, serialNumbers,
                        workCarriedOut, technicianName, findings, signerName, signatureData,
                        pdfPath, createdAt, isCompleted, isCustomJobType, customJobTypeId,
                        customJobTypeDisplayName, customJobTypeLegalTitle, customJobTypeLegalText,
                        timeStarted, timeCompleted, internalNotes,
                        0 as deleted
                    FROM reports
                """.trimIndent())
                
                // 3. Drop old table
                database.execSQL("DROP TABLE reports")
                
                // 4. Rename new table
                database.execSQL("ALTER TABLE reports_new RENAME TO reports")
                
                // 5. Recreate indexes
                database.execSQL("CREATE INDEX IF NOT EXISTS index_reports_clientId ON reports(clientId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_reports_deleted ON reports(deleted)")
            }
        }

        private val MIGRATION_32_33 = object : Migration(32, 33) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Unique human-facing report reference, e.g. NC-0132-26
                database.execSQL(
                    "ALTER TABLE reports ADD COLUMN reportRef TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        private val MIGRATION_33_34 = object : Migration(33, 34) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE clients ADD COLUMN priorityStarred INTEGER NOT NULL DEFAULT 0"
                )
                database.execSQL(
                    "ALTER TABLE clients ADD COLUMN serviceAlertsSilenced INTEGER NOT NULL DEFAULT 0"
                )
            }
        }
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "field_tech_database"
                )
                .addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                    MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
                    MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13,
                    MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17,
                    MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21,
                    MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25,
                    MIGRATION_25_26, MIGRATION_26_27, MIGRATION_27_28, MIGRATION_28_29,
                    MIGRATION_29_30, MIGRATION_30_31, MIGRATION_31_32, MIGRATION_32_33,
                    MIGRATION_33_34
                )
                .fallbackToDestructiveMigration() // Re-enabled to handle migration issues
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
