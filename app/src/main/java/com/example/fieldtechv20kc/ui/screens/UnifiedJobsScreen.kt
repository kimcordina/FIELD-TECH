package com.example.fieldtechv20kc.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.fieldtechv20kc.data.model.Client
import com.example.fieldtechv20kc.data.model.RequestStatus
import com.example.fieldtechv20kc.data.model.ServiceRequest
import com.example.fieldtechv20kc.data.model.ServiceTask
import com.example.fieldtechv20kc.data.model.ServiceTaskWithClient
import com.example.fieldtechv20kc.data.model.TaskStatus
import com.example.fieldtechv20kc.data.model.Technicians
import com.example.fieldtechv20kc.navigation.Screen
import com.example.fieldtechv20kc.utils.DateUtils
import com.example.fieldtechv20kc.viewmodel.ClientsViewModel
import com.example.fieldtechv20kc.viewmodel.ServiceRequestsViewModel
import com.example.fieldtechv20kc.viewmodel.ServiceTasksViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

/**
 * Unified Jobs inbox: unassigned requests + assigned jobs in one list.
 * Backend models stay separate; this is a UX merge only.
 */
private sealed class WorkItem {
    abstract val sortKey: Long
    abstract val id: String

    data class Unassigned(
        val request: ServiceRequest
    ) : WorkItem() {
        override val sortKey: Long get() = request.requestedAt
        override val id: String get() = "req_${request.id}"
    }

    data class AssignedJob(
        val taskWithClient: ServiceTaskWithClient
    ) : WorkItem() {
        override val sortKey: Long get() = taskWithClient.task.createdAt
        override val id: String get() = "job_${taskWithClient.task.id}"
        val locality: String
            get() = taskWithClient.client?.locality?.takeIf { it.isNotBlank() } ?: "Unknown"
    }
}

private enum class WorkStatusFilter(val label: String) {
    ACTIVE("Active"),
    NEW("New"),
    ASSIGNED("Assigned"),
    DONE("Done"),
    DELETED("Deleted"),
    ALL("All")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedJobsScreen(
    navController: NavController,
    tasksViewModel: ServiceTasksViewModel,
    requestsViewModel: ServiceRequestsViewModel,
    clientsViewModel: ClientsViewModel,
    defaultTechnicianName: String?
) {
    val tasksWithClients by tasksViewModel.tasksWithClients.collectAsState()
    val requests by requestsViewModel.requests.collectAsState()

    var statusFilter by remember { mutableStateOf(WorkStatusFilter.ACTIVE) }
    var assigneeFilter by remember { mutableStateOf("ALL") }
    var showAssigneeMenu by remember { mutableStateOf(false) }
    var showStatusMenu by remember { mutableStateOf(false) }
    var viewMode by remember { mutableStateOf("location") }

    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedJobIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    var showQuickAssignDialog by remember { mutableStateOf(false) }
    var selectedRequestForAssign by remember { mutableStateOf<ServiceRequest?>(null) }
    val scope = rememberCoroutineScope()

    // Keep requests VM watching everything we might need; filter in-memory for the inbox
    LaunchedEffect(Unit) {
        requestsViewModel.setFilters(null, null, null)
    }

    // Tasks VM: pull broad set, refine locally with status + assignee
    LaunchedEffect(statusFilter, assigneeFilter) {
        val taskStatus = when (statusFilter) {
            WorkStatusFilter.ACTIVE, WorkStatusFilter.ASSIGNED, WorkStatusFilter.NEW -> TaskStatus.PENDING
            WorkStatusFilter.DONE -> TaskStatus.DONE
            WorkStatusFilter.DELETED -> TaskStatus.DELETED
            WorkStatusFilter.ALL -> null
        }
        tasksViewModel.setFilters(
            assignee = if (assigneeFilter == "ALL") null else assigneeFilter,
            fromDate = null,
            toDate = null,
            status = taskStatus
        )
    }

    val openRequests = remember(requests) {
        requests.filter { it.status == RequestStatus.OPEN && !it.deleted }
    }

    val workItems: List<WorkItem> = remember(tasksWithClients, openRequests, statusFilter, assigneeFilter) {
        val items = mutableListOf<WorkItem>()

        val includeRequests = statusFilter == WorkStatusFilter.ACTIVE ||
            statusFilter == WorkStatusFilter.NEW ||
            statusFilter == WorkStatusFilter.ALL

        val includeJobs = statusFilter != WorkStatusFilter.NEW

        if (includeRequests && assigneeFilter == "ALL") {
            openRequests.forEach { req ->
                items += WorkItem.Unassigned(req)
            }
        }

        if (includeJobs) {
            tasksWithClients.forEach { twc ->
                items += WorkItem.AssignedJob(twc)
            }
        }

        items.sortedByDescending { it.sortKey }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isSelectionMode) "${selectedJobIds.size} selected" else "Jobs",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                navigationIcon = {
                    if (isSelectionMode) {
                        IconButton(onClick = {
                            isSelectionMode = false
                            selectedJobIds = emptySet()
                        }) {
                            Icon(Icons.Default.Close, "Cancel")
                        }
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        TextButton(
                            onClick = {
                                if (selectedJobIds.size >= 2) {
                                    val currentUser = FirebaseAuth.getInstance().currentUser?.displayName ?: "Unknown"
                                    navController.navigate(
                                        Screen.RoutePlanner.createRoute(
                                            jobIds = selectedJobIds.joinToString(","),
                                            createdBy = currentUser,
                                            intendedAssignee = if (assigneeFilter != "ALL") assigneeFilter else null
                                        )
                                    )
                                    isSelectionMode = false
                                    selectedJobIds = emptySet()
                                }
                            },
                            enabled = selectedJobIds.size >= 2,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text("Create Route")
                        }
                    } else {
                        Box {
                            TextButton(
                                onClick = { showStatusMenu = true },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Text(statusFilter.label)
                                Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(18.dp))
                            }
                            DropdownMenu(
                                expanded = showStatusMenu,
                                onDismissRequest = { showStatusMenu = false }
                            ) {
                                WorkStatusFilter.entries.forEach { status ->
                                    DropdownMenuItem(
                                        text = { Text(status.label) },
                                        onClick = {
                                            statusFilter = status
                                            showStatusMenu = false
                                        }
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { viewMode = if (viewMode == "list") "location" else "list" }) {
                            Icon(
                                if (viewMode == "list") Icons.Default.LocationOn else Icons.Default.List,
                                if (viewMode == "list") "Location View" else "List View"
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (!isSelectionMode) {
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
        },
        bottomBar = {
            if (isSelectionMode) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(onClick = {
                            isSelectionMode = false
                            selectedJobIds = emptySet()
                        }) { Text("Cancel") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val currentUser = FirebaseAuth.getInstance().currentUser?.displayName ?: "Unknown"
                                navController.navigate(
                                    Screen.RoutePlanner.createRoute(
                                        jobIds = selectedJobIds.joinToString(","),
                                        createdBy = currentUser,
                                        intendedAssignee = if (assigneeFilter != "ALL") assigneeFilter else null
                                    )
                                )
                                isSelectionMode = false
                                selectedJobIds = emptySet()
                            },
                            enabled = selectedJobIds.size >= 2
                        ) { Text("Create Route") }
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    ExposedDropdownMenuBox(
                        expanded = showAssigneeMenu,
                        onExpandedChange = { showAssigneeMenu = !showAssigneeMenu }
                    ) {
                        OutlinedTextField(
                            readOnly = true,
                            value = if (assigneeFilter == "ALL") "All Technicians" else assigneeFilter,
                            onValueChange = {},
                            label = { Text("Assignee") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showAssigneeMenu) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            singleLine = true
                        )
                        ExposedDropdownMenu(
                            expanded = showAssigneeMenu,
                            onDismissRequest = { showAssigneeMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("All Technicians") },
                                onClick = {
                                    assigneeFilter = "ALL"
                                    showAssigneeMenu = false
                                }
                            )
                            Technicians.ALL.forEach { tech ->
                                DropdownMenuItem(
                                    text = { Text(tech) },
                                    onClick = {
                                        assigneeFilter = tech
                                        showAssigneeMenu = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { isSelectionMode = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.AddCircle, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Create Route")
                        }
                        OutlinedButton(
                            onClick = { navController.navigate(Screen.SavedRoutes.route) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Route, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("View Routes")
                        }
                    }
                }
            }

            if (workItems.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Assignment,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "No jobs found",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Create a request with + or assign a job from Clients",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (viewMode == "list") {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items = workItems, key = { it.id }) { item ->
                        WorkItemCard(
                            item = item,
                            clientsViewModel = clientsViewModel,
                            isSelectionMode = isSelectionMode,
                            isSelected = (item as? WorkItem.AssignedJob)?.let {
                                selectedJobIds.contains(it.taskWithClient.task.id)
                            } == true,
                            onClick = {
                                when (item) {
                                    is WorkItem.Unassigned -> {
                                        if (!isSelectionMode) {
                                            navController.navigate(Screen.RequestDetail.createRoute(item.request.id))
                                        }
                                    }
                                    is WorkItem.AssignedJob -> {
                                        val taskId = item.taskWithClient.task.id
                                        if (isSelectionMode) {
                                            selectedJobIds = if (selectedJobIds.contains(taskId)) {
                                                selectedJobIds - taskId
                                            } else {
                                                selectedJobIds + taskId
                                            }
                                        } else {
                                            navController.navigate(Screen.TaskDetail.createRoute(taskId))
                                        }
                                    }
                                }
                            },
                            onQuickAssign = if (item is WorkItem.Unassigned) {
                                {
                                    selectedRequestForAssign = item.request
                                    showQuickAssignDialog = true
                                }
                            } else null
                        )
                    }
                }
            } else {
                WorkByLocationView(
                    workItems = workItems,
                    isSelectionMode = isSelectionMode,
                    selectedJobIds = selectedJobIds,
                    clientsViewModel = clientsViewModel,
                    onItemClick = { item ->
                        when (item) {
                            is WorkItem.Unassigned -> {
                                if (!isSelectionMode) {
                                    navController.navigate(Screen.RequestDetail.createRoute(item.request.id))
                                }
                            }
                            is WorkItem.AssignedJob -> {
                                val taskId = item.taskWithClient.task.id
                                if (isSelectionMode) {
                                    selectedJobIds = if (selectedJobIds.contains(taskId)) {
                                        selectedJobIds - taskId
                                    } else {
                                        selectedJobIds + taskId
                                    }
                                } else {
                                    navController.navigate(Screen.TaskDetail.createRoute(taskId))
                                }
                            }
                        }
                    },
                    onQuickAssign = { request ->
                        selectedRequestForAssign = request
                        showQuickAssignDialog = true
                    }
                )
            }
        }
    }

    if (showQuickAssignDialog && selectedRequestForAssign != null) {
        val client by clientsViewModel.observeClientById(selectedRequestForAssign!!.clientId)
            .collectAsState(initial = null)
        if (client != null) {
            UnifiedTaskAssignmentDialog(
                client = client!!,
                onDismiss = {
                    showQuickAssignDialog = false
                    selectedRequestForAssign = null
                },
                initialNotes = selectedRequestForAssign?.notes,
                onAssign = { technicianName, voiceUri, notes, photoUris ->
                    // Capture before dialog onDismiss clears selectedRequestForAssign
                    // (dialog calls onAssign then onDismiss in the same click).
                    val currentRequest = selectedRequestForAssign ?: return@UnifiedTaskAssignmentDialog
                    scope.launch {
                        try {
                            val finalVoiceUri = voiceUri ?: currentRequest.voiceUri
                            val finalPhotoUris = photoUris ?: currentRequest.photoUris
                            val finalNotes = notes.takeIf { it.isNotBlank() } ?: currentRequest.notes
                            val task = ServiceTask(
                                clientId = currentRequest.clientId,
                                title = "Service visit",
                                assignedToName = technicianName,
                                scheduledDate = DateUtils.getTodayMidnight(),
                                status = TaskStatus.PENDING,
                                notes = finalNotes?.takeIf { it.isNotBlank() },
                                voiceNoteUri = finalVoiceUri,
                                photoUris = finalPhotoUris,
                                createdByName = FirebaseAuth.getInstance().currentUser?.email ?: "Unknown"
                            )
                            tasksViewModel.upsert(task)
                            requestsViewModel.linkTask(currentRequest.id, task.id)
                            showQuickAssignDialog = false
                            selectedRequestForAssign = null
                        } catch (e: Exception) {
                            android.util.Log.e("UnifiedJobs", "Error assigning job", e)
                        }
                    }
                }
            )
        }
    }

    // Keep unused param referenced for future role defaults
    @Suppress("UNUSED_EXPRESSION")
    defaultTechnicianName
}

@Composable
private fun WorkByLocationView(
    workItems: List<WorkItem>,
    isSelectionMode: Boolean,
    selectedJobIds: Set<String>,
    clientsViewModel: ClientsViewModel,
    onItemClick: (WorkItem) -> Unit,
    onQuickAssign: (ServiceRequest) -> Unit
) {
    // Jobs already have locality; for unassigned requests resolve via clients VM in cards.
    // Group jobs by locality, put unassigned under "Needs assignment" first.
    val unassigned = workItems.filterIsInstance<WorkItem.Unassigned>()
    val jobs = workItems.filterIsInstance<WorkItem.AssignedJob>()
    val byLocality = jobs.groupBy { it.locality }
    val sortedLocalities = byLocality.keys.sorted()

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (unassigned.isNotEmpty()) {
            item(key = "header_new") {
                LocalityHeader(title = "Needs assignment", count = unassigned.size)
            }
            items(items = unassigned, key = { it.id }) { item ->
                WorkItemCard(
                    item = item,
                    clientsViewModel = clientsViewModel,
                    isSelectionMode = isSelectionMode,
                    isSelected = false,
                    onClick = { onItemClick(item) },
                    onQuickAssign = { onQuickAssign(item.request) }
                )
            }
        }

        sortedLocalities.forEach { locality ->
            val localityItems = byLocality[locality] ?: emptyList()
            item(key = "header_$locality") {
                LocalityHeader(title = locality, count = localityItems.size, showIsland = true)
            }
            items(items = localityItems, key = { it.id }) { item ->
                WorkItemCard(
                    item = item,
                    clientsViewModel = clientsViewModel,
                    isSelectionMode = isSelectionMode,
                    isSelected = selectedJobIds.contains(item.taskWithClient.task.id),
                    onClick = { onItemClick(item) },
                    onQuickAssign = null
                )
            }
        }
    }
}

@Composable
private fun LocalityHeader(title: String, count: Int, showIsland: Boolean = false) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Default.LocationOn,
                    null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                if (showIsland) {
                    com.example.fieldtechv20kc.ui.components.IslandBadge(title)
                }
            }
            Text(
                "$count",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WorkItemCard(
    item: WorkItem,
    clientsViewModel: ClientsViewModel,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onQuickAssign: (() -> Unit)?
) {
    when (item) {
        is WorkItem.Unassigned -> {
            val client by clientsViewModel.observeClientById(item.request.clientId)
                .collectAsState(initial = null)
            UnassignedRequestCard(
                request = item.request,
                client = client,
                onClick = onClick,
                onQuickAssign = onQuickAssign
            )
        }
        is WorkItem.AssignedJob -> AssignedJobCard(
            task = item.taskWithClient.task,
            clientName = item.taskWithClient.client?.name,
            clientLocality = item.taskWithClient.client?.locality,
            isSelectionMode = isSelectionMode,
            isSelected = isSelected,
            onClick = onClick
        )
    }
}

@Composable
private fun UnassignedRequestCard(
    request: ServiceRequest,
    client: Client?,
    onClick: () -> Unit,
    onQuickAssign: (() -> Unit)?
) {
    val amber = Color(0xFFFF8F00)
    val amberBg = Color(0xFFFFF3E0)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, amber, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = amberBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Clickable details only — keeps the Assign shortcut from fighting card navigation
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onClick)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        client?.name ?: "Unknown Client",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (!client?.locality.isNullOrBlank()) {
                        Text(
                            client!!.locality,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        com.example.fieldtechv20kc.ui.components.IslandBadge(client.locality)
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Needs assignment",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = amber
                )
                if (!request.notes.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        request.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    formatTaskDate(request.requestedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                StatusChip(label = "NEW", background = amber, foreground = Color.White)
                if (onQuickAssign != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    IconButton(onClick = onQuickAssign) {
                        Icon(Icons.Default.PersonAdd, "Assign", tint = amber)
                    }
                }
            }
        }
    }
}

@Composable
private fun AssignedJobCard(
    task: ServiceTask,
    clientName: String?,
    clientLocality: String?,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val (chipLabel, chipBg, chipFg, cardTint) = when (task.status) {
        TaskStatus.PENDING -> Quad("ASSIGNED", Color(0xFF1565C0), Color.White, Color(0xFFE3F2FD))
        TaskStatus.DONE -> Quad("DONE", Color(0xFF2E7D32), Color.White, Color(0xFFE8F5E9))
        TaskStatus.CANCELED -> Quad("CANCELED", Color(0xFF757575), Color.White, Color(0xFFF5F5F5))
        TaskStatus.DELETED -> Quad("DELETED", Color(0xFF757575), Color.White, Color(0xFFF5F5F5))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardTint),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = null,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .size(10.dp)
                    .background(Technicians.getColorForTechnician(task.assignedToName), CircleShape)
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        clientName ?: "Unknown Client",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (!clientLocality.isNullOrBlank()) {
                        Text(
                            clientLocality,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        com.example.fieldtechv20kc.ui.components.IslandBadge(clientLocality)
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = task.assignedToName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1565C0)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    formatTaskDate(task.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            StatusChip(label = chipLabel, background = chipBg, foreground = chipFg)
        }
    }
}

@Composable
private fun StatusChip(label: String, background: Color, foreground: Color) {
    Surface(shape = RoundedCornerShape(4.dp), color = background) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = foreground,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
