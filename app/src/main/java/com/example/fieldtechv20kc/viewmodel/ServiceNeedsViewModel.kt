package com.example.fieldtechv20kc.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtechv20kc.data.model.Client
import com.example.fieldtechv20kc.data.model.ServiceDueRules
import com.example.fieldtechv20kc.data.model.ServiceDueStatus
import com.example.fieldtechv20kc.data.model.ServiceDueThresholds
import com.example.fieldtechv20kc.data.repository.ClientsRepository
import com.example.fieldtechv20kc.utils.SettingsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ServiceNeedsFilter {
    ALL_DUE,
    STARRED,
    OVERDUE,
    LATE,
    SOON,
    SILENCED
}

data class ServiceNeedsRow(
    val client: Client,
    val status: ServiceDueStatus
)

class ServiceNeedsViewModel(
    private val clientsRepository: ClientsRepository,
    private val settingsManager: SettingsManager
) : ViewModel() {

    private val _filter = MutableStateFlow(ServiceNeedsFilter.ALL_DUE)
    val filter: StateFlow<ServiceNeedsFilter> = _filter

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val thresholdsFlow = settingsManager.settings

    init {
        viewModelScope.launch {
            try {
                val remote = com.example.fieldtechv20kc.data.remote.firestore.CompanyServiceSettingsRemote()
                val thresholds = remote.getThresholds()
                settingsManager.applyServiceDueThresholdsFromRemote(
                    thresholds.soonMonths,
                    thresholds.lateMonths,
                    thresholds.overdueMonths,
                    thresholds.starredOverdueMonths
                )
            } catch (_: Exception) {
                // Offline: keep local cached thresholds
            }
        }
    }

    val rows: StateFlow<List<ServiceNeedsRow>> = combine(
        clientsRepository.observeClients(),
        thresholdsFlow,
        _filter,
        _query
    ) { clients, settings, filter, query ->
        val thresholds = ServiceDueThresholds(
            soonMonths = settings.serviceSoonMonths,
            lateMonths = settings.serviceLateMonths,
            overdueMonths = settings.serviceOverdueMonths,
            starredOverdueMonths = settings.serviceStarredOverdueMonths
        )
        val q = query.trim()
        clients
            .asSequence()
            .filter { !it.deleted }
            .map { client ->
                ServiceNeedsRow(
                    client = client,
                    status = ServiceDueRules.classify(client, thresholds)
                )
            }
            .filter { row ->
                when (filter) {
                    ServiceNeedsFilter.ALL_DUE ->
                        !row.client.serviceAlertsSilenced && row.status != ServiceDueStatus.OK
                    ServiceNeedsFilter.STARRED ->
                        row.client.priorityStarred && !row.client.serviceAlertsSilenced
                    ServiceNeedsFilter.OVERDUE ->
                        !row.client.serviceAlertsSilenced && row.status == ServiceDueStatus.OVERDUE
                    ServiceNeedsFilter.LATE ->
                        !row.client.serviceAlertsSilenced && row.status == ServiceDueStatus.LATE
                    ServiceNeedsFilter.SOON ->
                        !row.client.serviceAlertsSilenced && row.status == ServiceDueStatus.SOON
                    ServiceNeedsFilter.SILENCED ->
                        row.client.serviceAlertsSilenced
                }
            }
            .filter { row ->
                if (q.isBlank()) true
                else {
                    val c = row.client
                    c.name.contains(q, ignoreCase = true) ||
                        (c.locality?.contains(q, ignoreCase = true) == true) ||
                        c.address.contains(q, ignoreCase = true)
                }
            }
            .sortedWith(
                compareBy<ServiceNeedsRow> { ServiceDueRules.sortKey(it.client, it.status) }
                    .thenBy { it.client.name.lowercase() }
            )
            .toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setFilter(filter: ServiceNeedsFilter) {
        _filter.value = filter
    }

    fun setQuery(query: String) {
        _query.value = query
    }

    fun toggleStar(client: Client) {
        viewModelScope.launch {
            clientsRepository.setPriorityStarred(client.id, !client.priorityStarred)
        }
    }

    fun toggleSilence(client: Client) {
        viewModelScope.launch {
            clientsRepository.setServiceAlertsSilenced(client.id, !client.serviceAlertsSilenced)
        }
    }
}
