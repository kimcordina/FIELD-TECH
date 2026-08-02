package com.example.fieldtechv20kc.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtechv20kc.data.model.ServiceTask
import com.example.fieldtechv20kc.data.model.ServiceTaskWithClient
import com.example.fieldtechv20kc.data.model.TaskStatus
import com.example.fieldtechv20kc.data.repository.ServiceTasksRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ServiceTasksViewModel(
    private val repository: ServiceTasksRepository
) : ViewModel() {

    private val _assigneeFilter = MutableStateFlow<String?>("ALL") // Default to show all
    private val _fromDateFilter = MutableStateFlow<Long?>(null)
    private val _toDateFilter = MutableStateFlow<Long?>(null)
    private val _statusFilter = MutableStateFlow<TaskStatus?>(null)

    // Combined filters flow
    private val filtersFlow = combine(
        _assigneeFilter,
        _fromDateFilter,
        _toDateFilter,
        _statusFilter
    ) { assignee, fromDate, toDate, status ->
        Filters(assignee, fromDate, toDate, status)
    }

    // Jobs list reactive to filters - now with client data
    val tasksWithClients: StateFlow<List<ServiceTaskWithClient>> = filtersFlow
        .flatMapLatest { filters ->
            repository.observeWithClients(
                assignee = filters.assignee,
                fromDate = filters.fromDate,
                toDate = filters.toDate
            ).map { taskList ->
                // Apply status filter in-memory if specified
                if (filters.status != null) {
                    taskList.filter { it.task.status == filters.status }
                } else {
                    taskList
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )
    
    // Keep the old tasks property for backward compatibility (jobs)
    val tasks: StateFlow<List<ServiceTask>> = tasksWithClients
        .map { list -> list.map { it.task } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    fun setFilters(
        assignee: String?,
        fromDate: Long?,
        toDate: Long?,
        status: TaskStatus?
    ) {
        _assigneeFilter.value = assignee
        _fromDateFilter.value = fromDate
        _toDateFilter.value = toDate
        _statusFilter.value = status
    }

    fun observeTask(taskId: String): Flow<ServiceTask?> {
        return repository.observeById(taskId)
    }

    fun observeTasksForClient(clientId: String): Flow<List<ServiceTask>> {
        return repository.observeForClient(clientId)
    }

    suspend fun upsert(task: ServiceTask) {
        println("🆕 TASK UPSERT: ViewModel called with taskId=${task.id}, voiceNoteUri=${task.voiceNoteUri}, photoUris=${task.photoUris}")
        repository.upsert(task)
        println("🆕 TASK UPSERT: Repository upsert completed")
    }

    suspend fun setStatus(taskId: String, status: TaskStatus) {
        repository.setStatus(taskId, status)
    }

    suspend fun linkReportAndComplete(taskId: String, reportId: String) {
        repository.linkReportAndComplete(taskId, reportId)
    }
    
    suspend fun deleteJob(taskId: String, deletedBy: String) {
        repository.deleteJob(taskId, deletedBy)
    }

    suspend fun deleteTask(taskId: String) {
        repository.delete(taskId)
    }

    private data class Filters(
        val assignee: String?,
        val fromDate: Long?,
        val toDate: Long?,
        val status: TaskStatus?
    )
}



