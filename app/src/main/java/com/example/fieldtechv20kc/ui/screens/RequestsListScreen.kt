package com.example.fieldtechv20kc.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.fieldtechv20kc.data.model.RequestStatus
import com.example.fieldtechv20kc.data.model.ServiceRequest
import com.example.fieldtechv20kc.navigation.Screen
import com.example.fieldtechv20kc.viewmodel.ServiceRequestsViewModel
import com.example.fieldtechv20kc.viewmodel.ClientsViewModel
import com.example.fieldtechv20kc.utils.DateUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestsListScreen(
    navController: NavController,
    requestsViewModel: ServiceRequestsViewModel,
    clientsViewModel: ClientsViewModel,
    tasksViewModel: com.example.fieldtechv20kc.viewmodel.ServiceTasksViewModel
) {
    val requests by requestsViewModel.requests.collectAsState()
    
    var selectedStatus by remember { mutableStateOf<RequestStatus?>(RequestStatus.OPEN) }
    var searchQuery by remember { mutableStateOf("") }
    var showQuickAssignDialog by remember { mutableStateOf(false) }
    var selectedRequestForAssign by remember { mutableStateOf<ServiceRequest?>(null) }
    
    // View mode: "list" or "location" - DEFAULT TO LOCATION
    var viewMode by remember { mutableStateOf("location") }
    
    // Dropdown states
    var showStatusMenu by remember { mutableStateOf(false) }
    
    LaunchedEffect(selectedStatus, searchQuery) {
        requestsViewModel.setFilters(
            selectedStatus,
            null, // No locality filter - Location View handles grouping
            searchQuery.ifBlank { null }
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Service Requests",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    ) 
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    // Status dropdown
                    Box {
                        TextButton(
                            onClick = { showStatusMenu = true },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text("Status: ${selectedStatus?.name ?: "All"}")
                            Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(18.dp))
                        }
                        DropdownMenu(
                            expanded = showStatusMenu,
                            onDismissRequest = { showStatusMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("All Statuses") },
                                onClick = {
                                    selectedStatus = null
                                    showStatusMenu = false
                                }
                            )
                            RequestStatus.values().forEach { status ->
                                DropdownMenuItem(
                                    text = { Text(status.name) },
                                    onClick = {
                                        selectedStatus = status
                                        showStatusMenu = false
                                    }
                                )
                            }
                        }
                    }
                    
                    // View mode toggle
                    IconButton(onClick = { viewMode = if (viewMode == "list") "location" else "list" }) {
                        Icon(
                            if (viewMode == "list") Icons.Default.LocationOn else Icons.Default.List,
                            if (viewMode == "list") "Location View" else "List View"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { navController.navigate(Screen.RequestCreate.route) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("New Request")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (viewMode == "location") {
                // Location View (grouped by locality)
                LocationGroupedRequestsView(
                    requests = requests,
                    clientsViewModel = clientsViewModel,
                    navController = navController,
                    onQuickAssign = { request ->
                        selectedRequestForAssign = request
                        showQuickAssignDialog = true
                    }
                )
            } else {
                // List View
                Column(modifier = Modifier.padding(16.dp)) {
                    // Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Search by client or notes") },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Requests List
                    if (requests.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.RequestPage,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "No service requests found",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Tap the + button below to create your first request",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(requests) { request ->
                                RequestListItem(
                                    request = request,
                                    clientsViewModel = clientsViewModel,
                                    onClick = { navController.navigate(Screen.RequestDetail.createRoute(request.id)) },
                                    onQuickAssign = if (request.status == RequestStatus.OPEN) {
                                        {
                                            selectedRequestForAssign = request
                                            showQuickAssignDialog = true
                                        }
                                    } else null
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Quick Assign Dialog for OPEN requests
    if (showQuickAssignDialog && selectedRequestForAssign != null) {
        val client by clientsViewModel.observeClientById(selectedRequestForAssign!!.clientId).collectAsState(initial = null)
        val scope = rememberCoroutineScope()
        
        if (client != null) {
            com.example.fieldtechv20kc.ui.screens.UnifiedTaskAssignmentDialog(
                client = client!!,
                onDismiss = { 
                    showQuickAssignDialog = false
                    selectedRequestForAssign = null
                },
                initialNotes = selectedRequestForAssign?.notes,
                onAssign = { technicianName, voiceUri, notes, photoUris ->
                    scope.launch {
                        try {
                            val currentRequest = selectedRequestForAssign
                            if (currentRequest != null) {
                                // Use new voice note if provided, otherwise use request's voice note
                                val finalVoiceUri = voiceUri ?: currentRequest.voiceUri
                                // Use new photos if provided, otherwise use request's photos
                                val finalPhotoUris = photoUris ?: currentRequest.photoUris
                                // Use dialog notes if provided, otherwise carry forward request's notes
                                val finalNotes = notes.takeIf { it.isNotBlank() } ?: currentRequest.notes
                                
                                val task = com.example.fieldtechv20kc.data.model.ServiceTask(
                                    clientId = currentRequest.clientId,
                                    title = "Service visit",
                                    assignedToName = technicianName,
                                    scheduledDate = com.example.fieldtechv20kc.utils.DateUtils.getTodayMidnight(),
                                    status = com.example.fieldtechv20kc.data.model.TaskStatus.PENDING,
                                    notes = finalNotes?.takeIf { it.isNotBlank() },
                                    voiceNoteUri = finalVoiceUri,
                                    photoUris = finalPhotoUris,
                                    createdByName = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email ?: "Unknown"
                                )
                                tasksViewModel.upsert(task)
                                requestsViewModel.linkTask(currentRequest.id, task.id)
                                showQuickAssignDialog = false
                                selectedRequestForAssign = null
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("RequestsList", "Error assigning job", e)
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun LocationGroupedRequestsView(
    requests: List<ServiceRequest>,
    clientsViewModel: ClientsViewModel,
    navController: NavController,
    onQuickAssign: (ServiceRequest) -> Unit
) {
    // Group requests by locality
    val requestsWithClients = requests.map { request ->
        val client by clientsViewModel.observeClientById(request.clientId).collectAsState(initial = null)
        request to client
    }
    
    val groupedByLocality = requestsWithClients.groupBy { (_, client) ->
        client?.locality ?: "Unknown"
    }
    
    if (groupedByLocality.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(
                    Icons.Default.RequestPage,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "No requests in this area",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Try selecting a different locality or create a new request",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            groupedByLocality.forEach { (locality, requestClientPairs) ->
                item {
                    // Locality header with island marker
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = locality,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        com.example.fieldtechv20kc.ui.components.IslandBadge(locality)
                    }
                }
                
                items(requestClientPairs) { (request, _) ->
                    RequestListItem(
                        request = request,
                        clientsViewModel = clientsViewModel,
                        onClick = { navController.navigate(Screen.RequestDetail.createRoute(request.id)) },
                        onQuickAssign = if (request.status == RequestStatus.OPEN) {
                            { onQuickAssign(request) }
                        } else null
                    )
                }
                
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun RequestListItem(
    request: ServiceRequest,
    clientsViewModel: ClientsViewModel,
    onClick: () -> Unit,
    onQuickAssign: (() -> Unit)? = null
) {
    val client by clientsViewModel.observeClientById(request.clientId).collectAsState(initial = null)
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    // Client name
                    Text(
                        text = client?.name ?: "Unknown Client",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    // Locality below client name, with island marker
                    if (!client?.locality.isNullOrBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = client?.locality!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            com.example.fieldtechv20kc.ui.components.IslandBadge(client?.locality)
                        }
                    }
                    // Creation date
                    Text(
                        text = formatRequestDate(request.requestedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Right side: Status chip above assign icon
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Status Chip (smaller)
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = when (request.status) {
                            RequestStatus.OPEN -> MaterialTheme.colorScheme.errorContainer
                            RequestStatus.ASSIGNED -> MaterialTheme.colorScheme.primaryContainer
                            RequestStatus.DONE -> MaterialTheme.colorScheme.tertiaryContainer
                            RequestStatus.CANCELED -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ) {
                        Text(
                            text = request.status.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = when (request.status) {
                                RequestStatus.OPEN -> MaterialTheme.colorScheme.onErrorContainer
                                RequestStatus.ASSIGNED -> MaterialTheme.colorScheme.onPrimaryContainer
                                RequestStatus.DONE -> MaterialTheme.colorScheme.onTertiaryContainer
                                RequestStatus.CANCELED -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    
                    // Quick assign icon (only for OPEN requests)
                    if (request.status == RequestStatus.OPEN && onQuickAssign != null) {
                        IconButton(
                            onClick = { onQuickAssign() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.Assignment,
                                contentDescription = "Assign Job",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
            
            // Requested by / Cancelled by info
            Spacer(modifier = Modifier.height(4.dp))
            if (request.status == RequestStatus.CANCELED && !request.cancelledByName.isNullOrBlank()) {
                Text(
                    text = "Cancelled by: ${request.cancelledByName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium
                )
            } else if (!request.requestedByName.isNullOrBlank()) {
                Text(
                    text = "Requested by: ${request.requestedByName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (!request.notes.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = request.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            if (request.voiceUri != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.VoiceChat,
                        contentDescription = "Voice Note",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Voice note attached",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

fun formatRequestDate(timestamp: Long): String {
    return DateUtils.formatDateTime(timestamp)
}
