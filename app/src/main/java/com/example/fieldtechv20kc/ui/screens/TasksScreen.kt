package com.example.fieldtechv20kc.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.fieldtechv20kc.data.model.ServiceTask
import com.example.fieldtechv20kc.data.model.TaskStatus
import com.example.fieldtechv20kc.navigation.Screen
import com.example.fieldtechv20kc.utils.DateUtils
import com.example.fieldtechv20kc.viewmodel.ServiceTasksViewModel
import com.google.firebase.auth.FirebaseAuth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    navController: NavController,
    viewModel: ServiceTasksViewModel,
    defaultTechnicianName: String? // From Settings
) {
    val tasksWithClients by viewModel.tasksWithClients.collectAsState()
    
    // Get unique assignee names for filter dropdown
    val uniqueAssignees = remember(tasksWithClients) {
        tasksWithClients.map { it.task.assignedToName }.distinct().sorted()
    }
    
    var assigneeFilter by remember { mutableStateOf("ALL") } // Default to ALL
    var statusFilter by remember { mutableStateOf("Pending") }
    var showAssigneeMenu by remember { mutableStateOf(false) }
    var showStatusMenu by remember { mutableStateOf(false) }
    
    // View mode: "list" or "location" - DEFAULT TO LOCATION
    var viewMode by remember { mutableStateOf("location") }
    
    // Multi-select state for route creation
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedJobIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showRouteConfigDialog by remember { mutableStateOf(false) }
    
    // Apply filters
    LaunchedEffect(assigneeFilter, statusFilter) {
        val status = when (statusFilter) {
            "Pending" -> TaskStatus.PENDING
            "Done" -> TaskStatus.DONE
            "Deleted" -> TaskStatus.DELETED
            "All" -> null
            else -> null
        }
        
        viewModel.setFilters(
            assignee = if (assigneeFilter == "ALL") null else assigneeFilter,
            fromDate = null,
            toDate = null,
            status = status
        )
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
                        // "Create Route" button in selection mode
                        TextButton(
                            onClick = { 
                                if (selectedJobIds.isNotEmpty()) {
                                    showRouteConfigDialog = true
                                }
                            },
                            enabled = selectedJobIds.isNotEmpty(),
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text("Create Route")
                        }
                    } else {
                        // Status dropdown
                        Box {
                            TextButton(
                                onClick = { showStatusMenu = true },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Text("Status: $statusFilter")
                                Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(18.dp))
                            }
                            DropdownMenu(
                                expanded = showStatusMenu,
                                onDismissRequest = { showStatusMenu = false }
                            ) {
                                listOf("Pending", "Done", "Deleted", "All").forEach { status ->
                                    DropdownMenuItem(
                                        text = { Text(status) },
                                        onClick = {
                                            statusFilter = status
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
                }
            )
        },
        floatingActionButton = {
            if (!isSelectionMode) {
                ExtendedFloatingActionButton(
                    onClick = { navController.navigate(Screen.Clients.route) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("New Job")
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
                        // Cancel button
                        OutlinedButton(
                            onClick = {
                                isSelectionMode = false
                                selectedJobIds = emptySet()
                            }
                        ) {
                            Text("Cancel")
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                            // Create Route button
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
                            ) {
                                Text("Create Route")
                            }
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
            // Assignee Filter (compact, at top)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
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
                            com.example.fieldtechv20kc.data.model.Technicians.ALL.forEach { tech ->
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
                    
                    // Route action buttons (prominent, always visible)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Create Route button
                        Button(
                            onClick = { isSelectionMode = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                Icons.Default.AddCircle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Create Route")
                        }
                        
                        // View Routes button
                        OutlinedButton(
                            onClick = { navController.navigate(Screen.SavedRoutes.route) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.Route,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("View Routes")
                        }
                    }
                }
            }

            // Jobs List or Location View
            if (tasksWithClients.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            Icons.Default.Assignment,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "No jobs found",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Assign jobs from the Clients tab or adjust your filters above",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                if (viewMode == "list") {
                    // List View
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(items = tasksWithClients, key = { it.task.id }) { taskWithClient ->
                            TaskListItem(
                                task = taskWithClient.task,
                                clientName = taskWithClient.client?.name,
                                clientLocality = taskWithClient.client?.locality,
                                isSelectionMode = isSelectionMode,
                                isSelected = selectedJobIds.contains(taskWithClient.task.id),
                                onClick = {
                                    if (isSelectionMode) {
                                        // Toggle selection
                                        selectedJobIds = if (selectedJobIds.contains(taskWithClient.task.id)) {
                                            selectedJobIds - taskWithClient.task.id
                                        } else {
                                            selectedJobIds + taskWithClient.task.id
                                        }
                                    } else {
                                        navController.navigate(Screen.TaskDetail.createRoute(taskWithClient.task.id))
                                    }
                                }
                            )
                        }
                    }
                } else {
                    // Location View - Grouped by locality
                    JobsByLocationView(
                        tasksWithClients = tasksWithClients,
                        isSelectionMode = isSelectionMode,
                        selectedJobIds = selectedJobIds,
                        onJobClick = { taskId ->
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
                    )
                }
            }
        }
    }
}

@Composable
fun TaskListItem(
    task: ServiceTask,
    clientName: String?,
    clientLocality: String?,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit
) {
    // Get route info for this job
    val context = LocalContext.current
    val routeDao = remember { 
        com.example.fieldtechv20kc.data.database.AppDatabase.getDatabase(context).routeDao()
    }
    var routeInfo by remember { mutableStateOf<Pair<String, String>?>(null) } // (routeName, assignee)
    
    LaunchedEffect(task.id) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // Find if this job is in any route
                val stop = routeDao.getStopByJobId(task.id)
                if (stop != null) {
                    val route = routeDao.getRouteById(stop.routeId)
                    if (route != null && !route.deleted) {
                        routeInfo = Pair(route.name, route.intendedAssignee ?: "Unassigned")
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp), // Reduced from 16dp
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top // Changed from CenterVertically
        ) {
            // Selection Checkbox (only in selection mode)
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = null,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            
            // Color Dot
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .size(10.dp) // Slightly smaller
                    .clip(CircleShape)
                    .background(com.example.fieldtechv20kc.data.model.Technicians.getColorForTechnician(task.assignedToName))
            )

            // Job Info
            Column(modifier = Modifier.weight(1f)) {
                // Client name with locality on the same line (no comma, smaller locality)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = clientName ?: "Unknown Client",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (!clientLocality.isNullOrBlank()) {
                        Text(
                            text = clientLocality,
                            style = MaterialTheme.typography.bodySmall, // Smaller
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        com.example.fieldtechv20kc.ui.components.IslandBadge(clientLocality)
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Assigned to
                Text(
                    text = "Assigned to: ${task.assignedToName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // Route info (if in a route)
                routeInfo?.let { (routeName, assignee) ->
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.Route,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "In route: $routeName",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(2.dp))
                
                // Date (creation date)
                Text(
                    text = formatTaskDate(task.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Route indicator badge (if in route)
            if (routeInfo != null) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Route,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "In Route",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

// Location-based view - Groups jobs by locality
@Composable
fun JobsByLocationView(
    tasksWithClients: List<com.example.fieldtechv20kc.data.model.ServiceTaskWithClient>,
    isSelectionMode: Boolean,
    selectedJobIds: Set<String>,
    onJobClick: (String) -> Unit
) {
    // Group tasks by locality
    val tasksByLocality = tasksWithClients.groupBy { it.client?.locality ?: "Unknown Location" }
    val sortedLocalities = tasksByLocality.keys.sortedBy { it }
    
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        sortedLocalities.forEach { locality ->
            val localityTasks = tasksByLocality[locality] ?: emptyList()
            
            // Locality header (compact)
            item(key = "header_$locality") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp), // Much smaller padding
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp), // Smaller icon
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = locality,
                                style = MaterialTheme.typography.labelLarge, // Smaller text
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            com.example.fieldtechv20kc.ui.components.IslandBadge(locality)
                        }
                        Text(
                            text = "${localityTasks.size} jobs",
                            style = MaterialTheme.typography.labelSmall, // Smaller badge
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // Jobs in this locality
            items(items = localityTasks, key = { it.task.id }) { taskWithClient ->
                TaskListItem(
                    task = taskWithClient.task,
                    clientName = taskWithClient.client?.name,
                    clientLocality = taskWithClient.client?.locality,
                    isSelectionMode = isSelectionMode,
                    isSelected = selectedJobIds.contains(taskWithClient.task.id),
                    onClick = { onJobClick(taskWithClient.task.id) }
                )
            }
        }
    }
}

// Removed getTaskColor - now using Technicians.getColorForTechnician()

fun formatTaskDate(timestamp: Long): String {
    return DateUtils.formatDateWithDay(timestamp)
}

