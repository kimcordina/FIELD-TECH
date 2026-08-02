package com.example.fieldtechv20kc.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtechv20kc.data.model.RequestStatus
import com.example.fieldtechv20kc.data.model.ServiceRequest
import com.example.fieldtechv20kc.data.repository.ServiceRequestsRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ServiceRequestsViewModel(
    private val repository: ServiceRequestsRepository
) : ViewModel() {
    
    private val _statusFilter = MutableStateFlow<RequestStatus?>(RequestStatus.OPEN)
    private val _localityFilter = MutableStateFlow<String?>(null)
    private val _searchQuery = MutableStateFlow<String?>(null)
    
    val requests: StateFlow<List<ServiceRequest>> = combine(
        _statusFilter,
        _localityFilter,
        _searchQuery
    ) { status, locality, query ->
        Triple(status, locality, query)
    }.flatMapLatest { (status, locality, query) ->
        repository.observe(status, locality, query)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
    
    fun setFilters(status: RequestStatus?, locality: String?, query: String?) {
        _statusFilter.value = status
        _localityFilter.value = locality
        _searchQuery.value = query
    }
    
    fun observeRequest(id: String): Flow<ServiceRequest?> {
        return repository.observeById(id)
    }
    
    suspend fun createRequest(
        clientId: String,
        notes: String?,
        voiceUri: String?,
        photoUris: String? = null,
        requestedBy: String?
    ): String {
        println("🆕 REQUEST CREATE: ViewModel called with clientId=$clientId, voiceUri=$voiceUri, photoUris=$photoUris")
        val id = repository.create(clientId, notes, voiceUri, photoUris, requestedBy)
        println("🆕 REQUEST CREATE: Repository returned id=$id")
        return id
    }
    
    fun setStatus(id: String, status: RequestStatus, cancelledBy: String? = null) {
        viewModelScope.launch {
            repository.setStatus(id, status, cancelledBy)
        }
    }
    
    fun linkTask(id: String, taskId: String) {
        viewModelScope.launch {
            repository.linkTask(id, taskId)
        }
    }
    
    fun deleteRequest(id: String) {
        viewModelScope.launch {
            repository.delete(id)
        }
    }
}
