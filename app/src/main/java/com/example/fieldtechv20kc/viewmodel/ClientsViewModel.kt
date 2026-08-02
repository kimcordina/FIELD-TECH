package com.example.fieldtechv20kc.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtechv20kc.data.database.AppDatabase
import com.example.fieldtechv20kc.data.model.Client
import com.example.fieldtechv20kc.data.model.ClientInput
import com.example.fieldtechv20kc.data.model.ClientSort
import com.example.fieldtechv20kc.data.model.ImportResult
import com.example.fieldtechv20kc.data.model.ServiceTask
import com.example.fieldtechv20kc.data.model.TaskStatus
import com.example.fieldtechv20kc.data.repository.ClientsRepository
import com.example.fieldtechv20kc.data.repository.ServiceTasksRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ClientsViewModel(
    private val repository: ClientsRepository,
    private val context: Context
) : ViewModel() {
    
    private val tasksRepository: ServiceTasksRepository by lazy {
        val app = context.applicationContext as com.example.fieldtechv20kc.FieldTechApplication
        app.tasksRepository
    }
    
    // Trigger for refreshing pending jobs
    private val _refreshTrigger = MutableStateFlow(0L)
    
    // Function to get pending job for a client
    suspend fun getPendingTaskForClient(clientId: String): String? {
        return tasksRepository.getPendingTaskForClient(clientId)?.assignedToName
    }
    
    // Map of client ID to pending job technician name
    val clientPendingTasks: StateFlow<Map<String, String>> = _refreshTrigger
        .flatMapLatest {
            repository.observeByLocality(query, selectedLocality)
        }
        .map { clientList ->
            val taskMap = mutableMapOf<String, String>()
            clientList.forEach { client ->
                try {
                    val techName = getPendingTaskForClient(client.id)
                    if (techName != null) {
                        taskMap[client.id] = techName
                    }
                } catch (e: Exception) {
                    // Ignore errors
                }
            }
            taskMap
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = emptyMap()
        )
    
    // Search and filter state (public vars for UI binding)
    var query by mutableStateOf("")
    var selectedLocality: String? by mutableStateOf(null) // null = All
    var groupByLocality by mutableStateOf(true) // default ON
    
    // Legacy properties for backward compatibility
    val searchQuery: String get() = query
    private var _hasPumpFilter by mutableStateOf<Boolean?>(null)
    val hasPumpFilter: Boolean? get() = _hasPumpFilter
    var sortBy by mutableStateOf(ClientSort.NAME_ASC)
    
    // Advanced filters
    var showOnlyNoRecentService by mutableStateOf(false)
    var monthsThreshold by mutableStateOf(6) // No service in last X months
    
    // Distinct localities for dropdown
    val localities: StateFlow<List<String>> = repository.observeLocalities()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )
    
    // Clients list (reactive with locality filter, sort, and advanced filters)
    val clients: StateFlow<List<Client>> = combine(
        snapshotFlow { query },
        snapshotFlow { selectedLocality },
        snapshotFlow { sortBy },
        snapshotFlow { showOnlyNoRecentService },
        snapshotFlow { monthsThreshold }
    ) { q, loc, sort, noRecentService, months ->
        Tuple5(q, loc, sort, noRecentService, months)
    }.flatMapLatest { (q, loc, sort, noRecentService, months) ->
        repository.observeByLocality(
            q = if (q.isBlank()) null else q,
            locality = loc
        ).map { clientList ->
            var filtered = clientList
            
            // Apply advanced filter: no recent service
            if (noRecentService) {
                val thresholdMillis = System.currentTimeMillis() - (months * 30L * 24 * 60 * 60 * 1000)
                filtered = filtered.filter { client ->
                    client.lastServiceDate == null || client.lastServiceDate!! < thresholdMillis
                }
            }
            
            // Apply sorting
            when (sort) {
                ClientSort.NAME_ASC -> filtered.sortedBy { it.name.lowercase() }
                ClientSort.NAME_DESC -> filtered.sortedByDescending { it.name.lowercase() }
                ClientSort.LOCALITY_ASC -> filtered.sortedBy { it.locality?.lowercase() ?: "" }
                ClientSort.LOCALITY_DESC -> filtered.sortedByDescending { it.locality?.lowercase() ?: "" }
                ClientSort.LAST_SERVICE_DESC -> filtered.sortedByDescending { it.lastServiceDate ?: 0L }
                ClientSort.RECENTLY_ADDED -> filtered.sortedByDescending { it.updatedAt }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )
    
    // Helper data class for combine
    private data class Tuple5<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)
    
    // Update search query (legacy method)
    fun setSearchQuery(q: String) {
        query = q
    }
    
    // Toggle pump filter
    fun togglePumpFilter() {
        _hasPumpFilter = when (_hasPumpFilter) {
            null -> true
            true -> false
            false -> null
        }
    }
    
    // Set pump filter explicitly
    fun setPumpFilter(hasPump: Boolean?) {
        _hasPumpFilter = hasPump
    }
    
    // Clear all filters
    fun clearFilters() {
        query = ""
        selectedLocality = null
        _hasPumpFilter = null
        sortBy = ClientSort.NAME_ASC
        showOnlyNoRecentService = false
        monthsThreshold = 6
    }
    
    // Get single client
    fun observeClient(id: String): Flow<Client?> {
        return repository.observeClient(id)
    }
    
    // Get all localities for filter dropdown
    fun observeLocalities(): Flow<List<String>> {
        return repository.observeLocalities()
    }
    
    // Get clients grouped by locality
    fun observeClientsGroupedByLocality(): Flow<List<Client>> {
        return repository.observeClientsGroupedByLocality()
    }
    
    // Add or update client
    suspend fun saveClient(input: ClientInput): Result<String> {
        return repository.addOrUpdateClient(input)
    }
    
    // Soft delete client
    fun deleteClient(id: String) {
        viewModelScope.launch {
            repository.softDelete(id)
        }
    }
    
    // Restore client
    fun restoreClient(id: String) {
        viewModelScope.launch {
            repository.restore(id)
        }
    }
    
    // Import clients
    suspend fun importClients(rows: List<ClientInput>): ImportResult {
        return repository.importClients(rows)
    }
    
    // Create job assignment
    fun assignTask(clientId: String, technician: String, scheduledDate: Long, notes: String, voiceNoteUri: String? = null) {
        viewModelScope.launch {
            tasksRepository.upsert(
                ServiceTask(
                    clientId = clientId,
                    assignedToName = technician,
                    scheduledDate = scheduledDate,
                    notes = notes.ifBlank { null },
                    voiceNoteUri = voiceNoteUri,
                    status = TaskStatus.PENDING
                )
            )
        }
    }
    
    // Refresh pending jobs
    fun refreshPendingTasks() {
        _refreshTrigger.value = System.currentTimeMillis()
    }
    
    // Observe a single client by ID
    fun observeClientById(id: String): Flow<Client?> {
        return repository.observeClient(id)
    }
}

