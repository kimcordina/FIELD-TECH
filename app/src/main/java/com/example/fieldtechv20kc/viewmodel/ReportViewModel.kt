package com.example.fieldtechv20kc.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.fieldtechv20kc.FieldTechApplication
import com.example.fieldtechv20kc.data.database.AppDatabase
import com.example.fieldtechv20kc.data.model.*
import com.example.fieldtechv20kc.data.remote.firestore.ReportsRemote
import com.example.fieldtechv20kc.data.remote.storage.ReportStorage
import com.example.fieldtechv20kc.ui.screens.UnifiedJobType
import com.example.fieldtechv20kc.data.repository.ReportRepository
import com.example.fieldtechv20kc.data.repository.StatisticsRepository
import com.example.fieldtechv20kc.utils.DebugHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Sealed interface for save operation results
sealed interface SaveResult {
    data class Success(val reportId: Long) : SaveResult
    data class Failure(val reason: String) : SaveResult
}

class ReportViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {
    
    private val repository: ReportRepository
    private val statisticsRepository: StatisticsRepository
    private lateinit var serviceTasksRepository: com.example.fieldtechv20kc.data.repository.ServiceTasksRepository
    
    // Keys for SavedStateHandle persistence
    private companion object {
        const val KEY_CLIENT_ID = "clientId"
        const val KEY_TASK_ID = "taskId"
    }
    
    private val _reports = MutableStateFlow<List<ReportWithDetails>>(emptyList())
    val reports: StateFlow<List<ReportWithDetails>> = _reports.asStateFlow()
    
    private val _currentReport = MutableStateFlow<ReportWithDetails?>(null)
    val currentReport: StateFlow<ReportWithDetails?> = _currentReport.asStateFlow()
    
    private val _currentClient = MutableStateFlow<Client?>(null)
    val currentClient: StateFlow<Client?> = _currentClient.asStateFlow()
    
    // Client selection state (for picker flow)
    private val _selectedClientId = MutableStateFlow<String?>(null)
    val selectedClientId: StateFlow<String?> = _selectedClientId.asStateFlow()
    
    private val _selectedClientName = MutableStateFlow<String?>(null)
    val selectedClientName: StateFlow<String?> = _selectedClientName.asStateFlow()
    
    // Job integration
    private val _linkedTaskId = MutableStateFlow<String?>(null)
    val linkedTaskId: StateFlow<String?> = _linkedTaskId.asStateFlow()
    
    // Client information form state
    private val _clientName = MutableStateFlow("")
    val clientName: StateFlow<String> = _clientName.asStateFlow()
    
    private val _clientLocality = MutableStateFlow("")
    val clientLocality: StateFlow<String> = _clientLocality.asStateFlow()
    
    private val _clientLegalName = MutableStateFlow("")
    val clientLegalName: StateFlow<String> = _clientLegalName.asStateFlow()
    
    private val _clientCompanyNumber = MutableStateFlow("")
    val clientCompanyNumber: StateFlow<String> = _clientCompanyNumber.asStateFlow()
    
    private val _clientAddress = MutableStateFlow("")
    val clientAddress: StateFlow<String> = _clientAddress.asStateFlow()
    
    private val _currentJobType = MutableStateFlow<JobType?>(null)
    val currentJobType: StateFlow<JobType?> = _currentJobType.asStateFlow()
    
    // Unified job type support (for both default and custom job types)
    private val _currentUnifiedJobType = MutableStateFlow<UnifiedJobType?>(null)
    val currentUnifiedJobType: StateFlow<UnifiedJobType?> = _currentUnifiedJobType.asStateFlow()
    
    private val _currentPhotos = MutableStateFlow<List<Photo>>(emptyList())
    val currentPhotos: StateFlow<List<Photo>> = _currentPhotos.asStateFlow()
    
    private val _equipmentInstalledRepaired = MutableStateFlow("")
    val equipmentInstalledRepaired: StateFlow<String> = _equipmentInstalledRepaired.asStateFlow()
    
    private val _serialNumbers = MutableStateFlow("")
    val serialNumbers: StateFlow<String> = _serialNumbers.asStateFlow()
    
    private val _workCarriedOut = MutableStateFlow("")
    val workCarriedOut: StateFlow<String> = _workCarriedOut.asStateFlow()
    
    private val _technicianName = MutableStateFlow("")
    val technicianName: StateFlow<String> = _technicianName.asStateFlow()
    
    
    private val _signerName = MutableStateFlow("")
    val signerName: StateFlow<String> = _signerName.asStateFlow()
    
    private val _signatureData = MutableStateFlow("")
    val signatureData: StateFlow<String> = _signatureData.asStateFlow()
    
    private val _signatureFilePath = MutableStateFlow<String?>(null)
    val signatureFilePath: StateFlow<String?> = _signatureFilePath.asStateFlow()
    
    // Time tracking fields
    private val _timeStarted = MutableStateFlow("")
    val timeStarted: StateFlow<String> = _timeStarted.asStateFlow()
    
    private val _timeCompleted = MutableStateFlow("")
    val timeCompleted: StateFlow<String> = _timeCompleted.asStateFlow()
    
    // Internal notes (not included in PDF)
    private val _internalNotes = MutableStateFlow("")
    val internalNotes: StateFlow<String> = _internalNotes.asStateFlow()
    
    init {
        // Log VM instance for debugging
        Log.d("ReportViewModel", "init VM=${hashCode()}")
        
        try {
            DebugHelper.log("Initializing ReportViewModel (instance=${hashCode()})")
            val database = AppDatabase.getDatabase(application)
            
            // Create cloud services for reports
            val reportStorage = ReportStorage()
            val reportsRemote = ReportsRemote()
            
            repository = ReportRepository(
                database.reportDao(),
                database.clientDao(),
                database.photoDao(),
                reportStorage,
                reportsRemote
            )
            statisticsRepository = StatisticsRepository(database.statisticsDao())
            
            // Use centralized repository from FieldTechApplication
            val app = application as FieldTechApplication
            serviceTasksRepository = app.tasksRepository
            
            DebugHelper.log("Repository initialized successfully")
            loadReports()
            
            // Rehydrate state from SavedStateHandle if available
            viewModelScope.launch {
                val clientId = savedStateHandle.get<String>(KEY_CLIENT_ID)
                val taskId = savedStateHandle.get<String>(KEY_TASK_ID)
                
                Log.d("ReportViewModel", "Rehydrating from SavedState: clientId=$clientId, taskId=$taskId")
                
                if (clientId != null && _currentClient.value == null) {
                    Log.d("ReportViewModel", "Attempting to rehydrate client with ID: $clientId")
                    val client = repository.getClientById(clientId)
                    if (client != null) {
                        _currentClient.value = client
                        Log.d("ReportViewModel", "✅ Client rehydrated: ${client.name}")
                    } else {
                        Log.e("ReportViewModel", "❌ Failed to rehydrate client: not found in DB")
                    }
                }
                
                if (taskId != null && _linkedTaskId.value == null) {
                    _linkedTaskId.value = taskId
                    Log.d("ReportViewModel", "✅ Task ID rehydrated: $taskId")
                }
            }
        } catch (e: Exception) {
            DebugHelper.logError("Failed to initialize database", e)
            // Handle database initialization error
            throw RuntimeException("Failed to initialize database", e)
        }
    }
    
    private fun loadReports() {
        viewModelScope.launch {
            try {
                repository.getAllReportsWithDetails().collect { reportsList ->
                    _reports.value = reportsList
                }
            } catch (e: Exception) {
                // Handle error gracefully
                _reports.value = emptyList()
            }
        }
    }
    
    fun setCurrentClient(client: Client) {
        Log.d("ReportViewModel", "setCurrentClient called: ${client.name} (id=${client.id}) in VM=${hashCode()}")
        _currentClient.value = client
        savedStateHandle[KEY_CLIENT_ID] = client.id
        
        // Also update individual client field states
        _clientName.value = client.name
        _clientLocality.value = client.locality ?: ""
        _clientLegalName.value = client.legalName
        _clientCompanyNumber.value = client.companyNumber
        _clientAddress.value = client.address
        
        Log.d("ReportViewModel", "✅ Client saved to SavedStateHandle: ${client.id}")
    }
    
    // Alias for ClientPickerScreen compatibility
    fun setSelectedClient(client: Client) {
        setCurrentClient(client)
    }
    
    // Client selection methods for picker flow
    // IMPORTANT: This is now a suspend function to ensure client is fully loaded before proceeding
    // This prevents "Client Data Missing" errors when saving reports offline
    suspend fun setSelectedClient(id: String, name: String?) {
        Log.d("ReportViewModel", "setSelectedClient called: id=$id, name=$name")
        _selectedClientId.value = id
        _selectedClientName.value = name
        
        // Load the full client synchronously to prevent race conditions
        // This ensures currentClient is set BEFORE navigation is allowed
        withContext(Dispatchers.IO) {
            try {
                Log.d("ReportViewModel", "Loading client from database... (id=$id)")
                val client = repository.getClientById(id)
                if (client != null) {
                    withContext(Dispatchers.Main) {
                        setCurrentClient(client)
                    }
                    Log.d("ReportViewModel", "✅ Client fully loaded: ${client.name} (id=${client.id})")
                } else {
                    Log.e("ReportViewModel", "❌ Client not found in database: $id")
                }
            } catch (e: Exception) {
                Log.e("ReportViewModel", "❌ Error loading client: ${e.message}", e)
            }
        }
    }
    
    fun clearSelectedClient() {
        _selectedClientId.value = null
        _selectedClientName.value = null
        // Also drop the loaded client object and its form fields so the UI
        // never shows one client's details while another (or none) is selected
        _currentClient.value = null
        _clientName.value = ""
        _clientLocality.value = ""
        _clientLegalName.value = ""
        _clientCompanyNumber.value = ""
        _clientAddress.value = ""
        savedStateHandle.remove<String>(KEY_CLIENT_ID)
    }

    fun canProceedWithClient(): Boolean {
        // Check BOTH ID and actual client object to ensure client is fully loaded
        val hasId = !_selectedClientId.value.isNullOrBlank()
        val hasClient = _currentClient.value != null
        
        if (hasId && !hasClient) {
            Log.w("ReportViewModel", "⚠️ Client ID set but currentClient null - still loading?")
        }
        
        return hasId && hasClient  // Both must be true
    }
    
    // Job integration methods
    fun setLinkedTaskId(taskId: String?) {
        Log.d("ReportViewModel", "setLinkedTaskId called: $taskId in VM=${hashCode()}")
        _linkedTaskId.value = taskId
        if (taskId != null) {
            savedStateHandle[KEY_TASK_ID] = taskId
            Log.d("ReportViewModel", "✅ Task ID saved to SavedStateHandle: $taskId")
        } else {
            savedStateHandle.remove<String>(KEY_TASK_ID)
        }
    }
    
    // Client information form setters
    fun setClientName(name: String) {
        _clientName.value = name
    }
    
    fun setClientLocality(locality: String) {
        _clientLocality.value = locality
    }
    
    fun setClientLegalName(legalName: String) {
        _clientLegalName.value = legalName
    }
    
    fun setClientCompanyNumber(companyNumber: String) {
        _clientCompanyNumber.value = companyNumber
    }
    
    fun setClientAddress(address: String) {
        _clientAddress.value = address
    }
    
    fun setCurrentJobType(jobType: JobType) {
        _currentJobType.value = jobType
    }
    
    fun setCurrentUnifiedJobType(unifiedJobType: UnifiedJobType) {
        _currentUnifiedJobType.value = unifiedJobType
        // Also set the regular job type for backward compatibility
        if (!unifiedJobType.isCustom) {
            unifiedJobType.originalJobType?.let { _currentJobType.value = it }
        }
    }
    
    fun setEquipmentInstalledRepaired(equipment: String) {
        _equipmentInstalledRepaired.value = equipment
    }
    
    fun setSerialNumbers(serialNumbers: String) {
        _serialNumbers.value = serialNumbers
    }
    
    fun setWorkCarriedOut(work: String) {
        _workCarriedOut.value = work
    }
    
    fun setTechnicianName(name: String) {
        _technicianName.value = name
    }
    
    
    fun setSignerName(name: String) {
        _signerName.value = name
    }
    
    fun setSignatureData(signature: String) {
        _signatureData.value = signature
    }
    
    fun setSignatureFilePath(filePath: String?) {
        _signatureFilePath.value = filePath
    }
    
    fun setTimeStarted(time: String) {
        _timeStarted.value = time
    }
    
    fun setTimeCompleted(time: String) {
        _timeCompleted.value = time
    }
    
    fun setInternalNotes(notes: String) {
        _internalNotes.value = notes
    }
    
    fun addPhoto(photo: Photo) {
        _currentPhotos.value = _currentPhotos.value + photo
    }
    
    fun removePhoto(photo: Photo) {
        _currentPhotos.value = _currentPhotos.value.filter { it != photo }
    }
    
    /**
     * Saves a report with explicit parameters instead of reading from mutable UI state.
     * This prevents transient null values from killing the save operation.
     * Returns SaveResult to provide user-visible feedback on success/failure.
     */
    suspend fun saveReport(
        clientId: String,
        taskId: String?,
        pdfPath: String,
        signatureFilePath: String?,
        reportRef: String
    ): SaveResult = withContext(Dispatchers.IO) {
        try {
            Log.d("ReportViewModel", "saveReport called: clientId=$clientId, taskId=$taskId, pdfPath=$pdfPath, reportRef=$reportRef")
            
            // Fetch the client from repository to ensure we have the latest data
            val client = repository.getClientById(clientId)
            if (client == null) {
                Log.e("ReportViewModel", "❌ Client not found in DB: $clientId")
                return@withContext SaveResult.Failure("Client not found")
            }
            
            val unifiedJobType = _currentUnifiedJobType.value
            val jobType = _currentJobType.value ?: JobType.SERVICE_REPAIR
            
            Log.d("ReportViewModel", "Client: ${client.name}, JobType: $jobType")
            
            repository.insertClient(client)
            Log.d("ReportViewModel", "Client upserted with ID: ${client.id}")
            
            val report = Report(
                clientId = client.id,
                jobType = jobType,
                equipmentInstalledRepaired = _equipmentInstalledRepaired.value,
                serialNumbers = _serialNumbers.value,
                workCarriedOut = _workCarriedOut.value,
                technicianName = _technicianName.value,
                findings = "",
                signerName = _signerName.value,
                signatureData = signatureFilePath ?: _signatureData.value,
                pdfPath = pdfPath,
                isCompleted = true,
                // Custom job type fields
                isCustomJobType = unifiedJobType?.isCustom ?: false,
                customJobTypeId = unifiedJobType?.customJobType?.id,
                customJobTypeDisplayName = unifiedJobType?.customJobType?.displayName,
                customJobTypeLegalTitle = unifiedJobType?.customJobType?.legalTitle,
                customJobTypeLegalText = unifiedJobType?.customJobType?.legalText,
                // Time tracking fields
                timeStarted = if (_timeStarted.value.isNotBlank()) _timeStarted.value else null,
                timeCompleted = if (_timeCompleted.value.isNotBlank()) _timeCompleted.value else null,
                // Internal notes (not included in PDF)
                internalNotes = if (_internalNotes.value.isNotBlank()) _internalNotes.value else null,
                reportRef = reportRef
            )
            
            val reportId = repository.insertReport(report)
            Log.d("ReportViewModel", "Report inserted with ID: $reportId")
            
            // Save photos
            _currentPhotos.value.forEach { photo ->
                val photoId = repository.insertPhoto(photo.copy(reportId = reportId))
                Log.d("ReportViewModel", "Photo inserted with ID: $photoId")
            }
            
            // Update client's last service date (report creation date)
            try {
                val app = getApplication<Application>() as FieldTechApplication
                val clientsRepository = app.clientsRepository
                clientsRepository.updateLastServiceDate(client.id, report.createdAt.time)
                Log.d("ReportViewModel", "Updated client lastServiceDate to ${report.createdAt.time}")
            } catch (e: Exception) {
                Log.e("ReportViewModel", "Failed to update client lastServiceDate", e)
            }
            
            // Update statistics - only count service reports
            if (jobType == JobType.SERVICE_REPAIR) {
                statisticsRepository.onReportCreated(client.locality ?: "Unknown")
            }
            
            // Link job if this report was started from a job
            if (taskId != null && ::serviceTasksRepository.isInitialized) {
                try {
                    Log.d("ReportViewModel", "Linking task $taskId to report $reportId")
                    serviceTasksRepository.linkReportAndComplete(taskId, reportId.toString())
                    Log.d("ReportViewModel", "✅ Task $taskId linked and marked as DONE")
                } catch (e: Exception) {
                    Log.e("ReportViewModel", "Failed to link task", e)
                    // Don't fail the whole save if task linking fails
                }
            }
            
            Log.d("ReportViewModel", "✅ saveReport completed successfully, reportId: $reportId")
            SaveResult.Success(reportId)
            
        } catch (t: Throwable) {
            Log.e("ReportViewModel", "❌ saveReport failed", t)
            SaveResult.Failure(t.message ?: "Unknown error")
        }
    }
    
    fun loadReport(reportId: Long) {
        viewModelScope.launch {
            val reportWithDetails = repository.getReportWithDetailsById(reportId)
            _currentReport.value = reportWithDetails
            reportWithDetails?.let { report ->
                _currentClient.value = report.client
                _currentJobType.value = report.report.jobType
                _equipmentInstalledRepaired.value = report.report.equipmentInstalledRepaired
                _serialNumbers.value = report.report.serialNumbers
                _workCarriedOut.value = report.report.workCarriedOut
                _technicianName.value = report.report.technicianName
                _signerName.value = report.report.signerName
                _signatureData.value = report.report.signatureData
                _timeStarted.value = report.report.timeStarted ?: ""
                _timeCompleted.value = report.report.timeCompleted ?: ""
                _currentPhotos.value = report.photos
                
                // Load client information fields
                report.client?.let { client ->
                    _clientName.value = client.name
                    _clientLocality.value = client.locality ?: ""
                    _clientLegalName.value = client.legalName
                    _clientCompanyNumber.value = client.companyNumber
                    _clientAddress.value = client.address
                }
            }
        }
    }
    
    fun deleteReport(report: Report) {
        viewModelScope.launch {
            repository.deleteReport(report)
        }
    }
    
    fun clearCurrentReport() {
        Log.d("ReportViewModel", "clearCurrentReport called in VM=${hashCode()}")
        
        _currentClient.value = null
        _currentJobType.value = null
        _currentUnifiedJobType.value = null
        _equipmentInstalledRepaired.value = ""
        _serialNumbers.value = ""
        _workCarriedOut.value = ""
        _signerName.value = ""
        _signatureData.value = ""
        _signatureFilePath.value = null
        _currentPhotos.value = emptyList()
        _currentReport.value = null
        _linkedTaskId.value = null
        _internalNotes.value = ""
        
        // Clear client selection + form fields. Leaving these behind made a
        // "new" report still display the previous client's details while
        // _currentClient was null - a state where the save could never succeed.
        _selectedClientId.value = null
        _selectedClientName.value = null
        _clientName.value = ""
        _clientLocality.value = ""
        _clientLegalName.value = ""
        _clientCompanyNumber.value = ""
        _clientAddress.value = ""
        
        // Technician name re-fills from settings default on the next report;
        // times must not carry over between reports.
        _technicianName.value = ""
        _timeStarted.value = ""
        _timeCompleted.value = ""
        
        // Clear SavedStateHandle
        savedStateHandle.remove<String>(KEY_CLIENT_ID)
        savedStateHandle.remove<String>(KEY_TASK_ID)
        
        Log.d("ReportViewModel", "✅ Current report and SavedStateHandle cleared")
    }
}
