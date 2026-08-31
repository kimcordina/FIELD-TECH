package com.example.fieldtechv20kc.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.example.fieldtechv20kc.data.model.Client
import com.example.fieldtechv20kc.data.model.ClientSort
import com.example.fieldtechv20kc.navigation.Screen
import com.example.fieldtechv20kc.ui.components.ClientsGroupedList
import com.example.fieldtechv20kc.ui.components.VoiceRecorderSection
import com.example.fieldtechv20kc.utils.DateUtils
import com.example.fieldtechv20kc.viewmodel.ClientsViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientsListScreen(
    navController: NavController,
    viewModel: ClientsViewModel,
    tasksViewModel: com.example.fieldtechv20kc.viewmodel.ServiceTasksViewModel
) {
    val clients by viewModel.clients.collectAsState()
    val localities by viewModel.localities.collectAsState()
    val pendingTasks by viewModel.clientPendingTasks.collectAsState()
    val scope = rememberCoroutineScope()
    val auth = remember { FirebaseAuth.getInstance() }
    val currentUserEmail = auth.currentUser?.email ?: "Unknown User"
    
    // Get user role from Firestore
    val usersRemote = remember { com.example.fieldtechv20kc.data.remote.firestore.UsersRemote() }
    var userRole by remember { mutableStateOf("NONE") }
    
    LaunchedEffect(Unit) {
        try {
            val profile = usersRemote.getProfile()
            userRole = profile?.role ?: "NONE"
        } catch (e: Exception) {
            android.util.Log.e("ClientsListScreen", "Error loading user role", e)
        }
    }
    
    var locMenuExpanded by remember { mutableStateOf(false) }
    var showAssignDialog by remember { mutableStateOf(false) }
    var selectedClientForTask by remember { mutableStateOf<Client?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Selection mode state
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedClientIds by remember { mutableStateOf(setOf<String>()) }
    var showMultiAssignSheet by remember { mutableStateOf(false) }
    
    // Delete all clients state
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var showDeleteAllMenu by remember { mutableStateOf(false) }
    
    // Show Select button only for TECH and MANAGER
    val canUseMultiSelect = userRole == "TECH" || userRole == "MANAGER"
    
    // Sort menu state
    var showSortMenu by remember { mutableStateOf(false) }
    
    // Advanced filter dialog state
    var showAdvancedFilterDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        if (isSelectionMode) "${selectedClientIds.size} selected" else "Clients",
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
                            selectedClientIds = setOf()
                        }) {
                            Icon(Icons.Default.Close, "Exit selection mode")
                        }
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        // No actions in selection mode
                    } else {
                        // Sort menu
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                BadgedBox(
                                    badge = {
                                        if (viewModel.sortBy != ClientSort.NAME_ASC) {
                                            Badge(
                                                containerColor = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Sort, "Sort")
                                }
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                ClientSort.values().forEach { sort ->
                                    DropdownMenuItem(
                                        text = { 
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(sort.displayName)
                                                if (viewModel.sortBy == sort) {
                                                    Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(20.dp),
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            viewModel.sortBy = sort
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }
                        
                        // Advanced filter
                        IconButton(onClick = { showAdvancedFilterDialog = true }) {
                            BadgedBox(
                                badge = {
                                    if (viewModel.showOnlyNoRecentService) {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            ) {
                                Icon(Icons.Default.FilterList, "Advanced Filters")
                            }
                        }
                        
                        // Select button (only for TECH and MANAGER)
                        if (canUseMultiSelect) {
                            TextButton(
                                onClick = { isSelectionMode = true },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Text("Select")
                            }
                        }
                        // Import button
                        IconButton(onClick = { navController.navigate(Screen.ClientImport.route) }) {
                            Icon(Icons.Default.Upload, "Import")
                        }
                        // More menu (Delete All)
                        Box {
                            IconButton(onClick = { showDeleteAllMenu = true }) {
                                Icon(Icons.Default.MoreVert, "More options")
                            }
                            DropdownMenu(
                                expanded = showDeleteAllMenu,
                                onDismissRequest = { showDeleteAllMenu = false }
                            ) {
                                // New Client option
                                DropdownMenuItem(
                                    text = { 
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.Add,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("New Client")
                                        }
                                    },
                                    onClick = {
                                        showDeleteAllMenu = false
                                        navController.navigate(Screen.ClientNew.route)
                                    }
                                )
                                
                                // Delete All Clients option
                                DropdownMenuItem(
                                    text = { 
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                "Delete All Clients",
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    },
                                    onClick = {
                                        showDeleteAllMenu = false
                                        showDeleteAllDialog = true
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (isSelectionMode) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 3.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Select all button
                        TextButton(
                            onClick = {
                                selectedClientIds = clients.map { it.id }.toSet()
                            }
                        ) {
                            Text("Select all in view")
                        }
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Cancel button
                            OutlinedButton(
                                onClick = {
                                    isSelectionMode = false
                                    selectedClientIds = setOf()
                                }
                            ) {
                                Text("Cancel")
                            }
                            
                            // Assign button
                            Button(
                                onClick = { showMultiAssignSheet = true },
                                enabled = selectedClientIds.isNotEmpty()
                            ) {
                                Text("Assign")
                            }
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
            // Header controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Search bar - full width
                OutlinedTextField(
                    value = viewModel.query,
                    onValueChange = { viewModel.query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Search") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    placeholder = { Text("name / locality / address") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (viewModel.query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.query = "" }) {
                                Icon(Icons.Default.Clear, "Clear")
                            }
                        }
                    }
                )

                // Filters row - locality dropdown and group toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    @OptIn(ExperimentalMaterial3Api::class)
                    ExposedDropdownMenuBox(
                        expanded = locMenuExpanded,
                        onExpandedChange = { locMenuExpanded = !locMenuExpanded },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            readOnly = true,
                            value = viewModel.selectedLocality ?: "All localities",
                            onValueChange = {},
                            label = { Text("Locality") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = locMenuExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            singleLine = true
                        )
                        ExposedDropdownMenu(
                            expanded = locMenuExpanded,
                            onDismissRequest = { locMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("All localities") },
                                onClick = {
                                    viewModel.selectedLocality = null
                                    locMenuExpanded = false
                                }
                            )
                            localities.forEach { loc ->
                                DropdownMenuItem(
                                    text = { Text(loc) },
                                    onClick = {
                                        viewModel.selectedLocality = loc
                                        locMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Group by locality toggle
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(
                            "Group",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Switch(
                            checked = viewModel.groupByLocality,
                            onCheckedChange = { viewModel.groupByLocality = it }
                        )
                    }
                }
            }
            
            // Advanced filter dialog (triggered from TopAppBar)
            if (showAdvancedFilterDialog) {
                AlertDialog(
                    onDismissRequest = { showAdvancedFilterDialog = false },
                    icon = { Icon(Icons.Default.FilterList, null) },
                    title = { Text("Advanced Filters") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            // No recent service filter
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "No Recent Service",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "Show clients not serviced in ${viewModel.monthsThreshold} months",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = viewModel.showOnlyNoRecentService,
                                    onCheckedChange = { viewModel.showOnlyNoRecentService = it }
                                )
                            }
                            
                            // Months threshold slider (only shown when filter is enabled)
                            if (viewModel.showOnlyNoRecentService) {
                                Column {
                                    Text(
                                        "Months Threshold: ${viewModel.monthsThreshold}",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                    Slider(
                                        value = viewModel.monthsThreshold.toFloat(),
                                        onValueChange = { viewModel.monthsThreshold = it.toInt() },
                                        valueRange = 1f..24f,
                                        steps = 22
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showAdvancedFilterDialog = false }) {
                            Text("Done")
                        }
                    },
                    dismissButton = {
                        if (viewModel.showOnlyNoRecentService) {
                            TextButton(
                                onClick = {
                                    viewModel.showOnlyNoRecentService = false
                                    showAdvancedFilterDialog = false
                                }
                            ) {
                                Text("Clear")
                            }
                        }
                    }
                )
            }
            
            // Grouped clients list
            if (clients.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            Icons.Default.Business,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (viewModel.query.isNotEmpty() || viewModel.selectedLocality != null) {
                                "No clients match your search"
                            } else {
                                "No clients yet"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (viewModel.query.isNotEmpty() || viewModel.selectedLocality != null) {
                                "Try adjusting your filters or search terms"
                            } else {
                                "Tap the + button below to add your first client"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        if (viewModel.query.isEmpty() && viewModel.selectedLocality == null) {
                            Button(onClick = { navController.navigate(Screen.ClientNew.route) }) {
                                Icon(Icons.Default.Add, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Add Client")
                            }
                        }
                    }
                }
            } else {
                ClientsGroupedList(
                    clients = clients,
                    groupByLocality = viewModel.groupByLocality,
                    onClientClick = { client ->
                        if (isSelectionMode) {
                            // Toggle selection
                            selectedClientIds = if (selectedClientIds.contains(client.id)) {
                                selectedClientIds - client.id
                            } else {
                                selectedClientIds + client.id
                            }
                        } else {
                            navController.navigate(Screen.ClientDetail.createRoute(client.id))
                        }
                    }
                ) { client, onClick ->
                    ClientListItem(
                        client = client,
                        onClick = onClick,
                        onAssignTask = { selectedClient ->
                            // Show assign job dialog directly
                            selectedClientForTask = selectedClient
                            showAssignDialog = true
                        },
                        pendingTaskTechnician = pendingTasks[client.id],
                        isSelectionMode = isSelectionMode,
                        isSelected = selectedClientIds.contains(client.id)
                    )
                }
            }
        }
    }
    
    // Assign Job Dialog
    if (showAssignDialog && selectedClientForTask != null) {
        UnifiedTaskAssignmentDialog(
            client = selectedClientForTask!!,
            onDismiss = { 
                showAssignDialog = false
                selectedClientForTask = null
            },
            onAssign = { technician, voiceUri, notes, photoUris ->
                // Capture the client before launching coroutine to avoid null pointer
                val client = selectedClientForTask
                if (client != null) {
                    scope.launch {
                        tasksViewModel.upsert(
                            com.example.fieldtechv20kc.data.model.ServiceTask(
                                clientId = client.id,
                                assignedToName = technician,
                                scheduledDate = com.example.fieldtechv20kc.utils.DateUtils.getTodayMidnight(),
                                title = "Service visit",
                                notes = notes.takeIf { it.isNotBlank() },
                                voiceNoteUri = voiceUri,
                                photoUris = photoUris,
                                createdByName = currentUserEmail
                            )
                        )
                        
                        snackbarHostState.showSnackbar("Job assigned successfully!")
                        showAssignDialog = false
                        selectedClientForTask = null
                    }
                }
            }
        )
    }
    
    // Delete All Clients Confirmation Dialog
    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp)
                )
            },
            title = {
                Text(
                    "Delete All Clients?",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "This will permanently delete all ${clients.size} clients from your database.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        "⚠️ This action cannot be undone!",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        "All associated reports, jobs, and pins will remain but will lose their client references.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                // Delete all clients
                                clients.forEach { client ->
                                    viewModel.deleteClient(client.id)
                                }
                                showDeleteAllDialog = false
                                snackbarHostState.showSnackbar(
                                    message = "Deleted ${clients.size} clients",
                                    duration = SnackbarDuration.Long
                                )
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar(
                                    message = "Error deleting clients: ${e.message}",
                                    duration = SnackbarDuration.Long
                                )
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete All")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDeleteAllDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Multi-Assign Bottom Sheet
    if (showMultiAssignSheet) {
        MultiAssignBottomSheet(
            selectedClients = clients.filter { selectedClientIds.contains(it.id) },
            pendingTasks = pendingTasks,
            onDismiss = { showMultiAssignSheet = false },
            onAssign = { technician, notes, skipExisting ->
                scope.launch {
                    var created = 0
                    var skipped = 0
                    
                    selectedClientIds.forEach { clientId ->
                        // Check if client already has pending job
                        val hasPendingTask = pendingTasks.containsKey(clientId)
                        
                        if (skipExisting && hasPendingTask) {
                            skipped++
                        } else {
                            // Create job
                            tasksViewModel.upsert(
                                com.example.fieldtechv20kc.data.model.ServiceTask(
                                    clientId = clientId,
                                    assignedToName = technician,
                                    scheduledDate = com.example.fieldtechv20kc.utils.DateUtils.getTodayMidnight(),
                                    title = "Service visit",
                                    notes = notes.takeIf { it.isNotBlank() },
                                    voiceNoteUri = null,
                                    createdByName = currentUserEmail
                                )
                            )
                            created++
                        }
                    }
                    
                    // Show summary
                    val message = if (skipped > 0) {
                        "Created $created jobs. Skipped $skipped (already pending)."
                    } else {
                        "Created $created jobs."
                    }
                    snackbarHostState.showSnackbar(message)
                    
                    // Exit selection mode
                    isSelectionMode = false
                    selectedClientIds = setOf()
                    showMultiAssignSheet = false
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiAssignBottomSheet(
    selectedClients: List<Client>,
    pendingTasks: Map<String, String>,
    onDismiss: () -> Unit,
    onAssign: (technician: String, notes: String, skipExisting: Boolean) -> Unit
) {
    var selectedTechnician by remember { mutableStateOf<String?>(null) }
    var notes by remember { mutableStateOf("") }
    var skipExisting by remember { mutableStateOf(true) }
    
    val clientsWithPending = selectedClients.count { pendingTasks.containsKey(it.id) }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header
            Text(
                text = "Assign Jobs to ${selectedClients.size} Client${if (selectedClients.size > 1) "s" else ""}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            Divider()
            
            // Technician selection
            Text(
                text = "Select Technician",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Jenson", "Abubakar").forEach { tech ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedTechnician = tech }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedTechnician == tech,
                            onClick = { selectedTechnician = tech }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = tech,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (selectedTechnician == tech) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
            
            Divider()
            
            // Notes field
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (optional)") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                placeholder = { Text("Add notes for all ${selectedClients.size} jobs...") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4
            )
            
            Divider()
            
            // Skip existing toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Skip clients with pending jobs",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    if (clientsWithPending > 0) {
                        Text(
                            text = "$clientsWithPending client${if (clientsWithPending > 1) "s" else ""} already ${if (clientsWithPending > 1) "have" else "has"} pending job${if (clientsWithPending > 1) "s" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = skipExisting,
                    onCheckedChange = { skipExisting = it }
                )
            }
            
            // Warning if skip is OFF
            if (!skipExisting && clientsWithPending > 0) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "This will create duplicate pending jobs for some clients.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
            
            // Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                
                Button(
                    onClick = {
                        if (selectedTechnician != null) {
                            onAssign(selectedTechnician!!, notes, skipExisting)
                        }
                    },
                    enabled = selectedTechnician != null,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.Assignment,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Assign")
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun ClientListItem(
    client: Client,
    onClick: () -> Unit,
    onAssignTask: ((Client) -> Unit)? = null,
    pendingTaskTechnician: String? = null,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false
) {
    val cardColor = if (pendingTaskTechnician != null) {
        com.example.fieldtechv20kc.data.model.Technicians.getColorForTechnician(pendingTaskTechnician).copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    
    val cardBorder = if (pendingTaskTechnician != null) {
        BorderStroke(1.dp, androidx.compose.ui.graphics.Color.Black)
    } else {
        null
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = cardBorder
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Client name with assign button or checkbox
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Checkbox in selection mode
                if (isSelectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = null // Handled by card click
                    )
                }
                
                Column(modifier = Modifier.weight(1f)) {
                    // Client name
                    Text(
                        text = client.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    // Locality below client name, with island marker
                    if (!client.locality.isNullOrBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = client.locality!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            com.example.fieldtechv20kc.ui.components.IslandBadge(client.locality)
                        }
                    }
                    
                    // Show technician name if there's a pending job
                    if (pendingTaskTechnician != null) {
                        val technicianColor = when (pendingTaskTechnician) {
                            "Jenson" -> androidx.compose.ui.graphics.Color(0xFF228B22) // Dark Green
                            "Abubakar" -> androidx.compose.ui.graphics.Color(0xFFDC143C) // Crimson Red
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Text(
                            text = pendingTaskTechnician,
                            style = MaterialTheme.typography.bodySmall,
                            color = technicianColor,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                
                // Assign Job button (hide in selection mode)
                if (onAssignTask != null && !isSelectionMode) {
                    IconButton(
                        onClick = { onAssignTask(client) },
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
            
            // Last service date + badges
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Last service date
                if (client.lastServiceDate != null) {
                    val dateStr = DateUtils.formatDate(client.lastServiceDate)
                    
                    // Check if overdue (1 year = 365 days)
                    val daysSince = (System.currentTimeMillis() - client.lastServiceDate) / (1000 * 60 * 60 * 24)
                    val isOverdue = daysSince > 365
                    
                    if (isOverdue) {
                        AssistChip(
                            onClick = {},
                            label = { Text("Overdue") },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                labelColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        )
                    }
                    
                    Text(
                        text = "Last: $dateStr",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedTaskAssignmentDialog(
    client: Client,
    onDismiss: () -> Unit,
    onAssign: (technician: String, voiceUri: String?, notes: String, photoUris: String?) -> Unit,
    initialNotes: String? = null
) {
    val context = LocalContext.current
    var selectedTechnician by remember { mutableStateOf<String?>(null) }
    var notes by remember { mutableStateOf(initialNotes.orEmpty()) }
    var voiceUri by remember { mutableStateOf<String?>(null) }
    var photoUris by remember { mutableStateOf<List<String>>(emptyList()) }
    val voiceRecorderController = com.example.fieldtechv20kc.ui.components.rememberVoiceRecorderController()
    
    // Photo picker launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        photoUris = photoUris + uris.map { it.toString() }
    }
    
    // Camera launcher using PhotoCaptureContract
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = com.example.fieldtechv20kc.utils.PhotoCaptureContract()
    ) { photoPath ->
        photoPath?.let {
            photoUris = photoUris + it
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Assign Job to ${client.name}")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Select a technician to assign this service job:",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                // Technician selection
                com.example.fieldtechv20kc.data.model.Technicians.ALL.forEach { tech ->
                    val color = com.example.fieldtechv20kc.data.model.Technicians.getColorForTechnician(tech)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedTechnician = tech },
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedTechnician == tech) 
                                color.copy(alpha = 0.3f) 
                            else 
                                MaterialTheme.colorScheme.surface
                        ),
                        border = if (selectedTechnician == tech) 
                            BorderStroke(2.dp, color) 
                        else 
                            null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(color, shape = RoundedCornerShape(4.dp))
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = tech,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (selectedTechnician == tech) FontWeight.Bold else FontWeight.Normal
                            )
                            Spacer(Modifier.weight(1f))
                            if (selectedTechnician == tech) {
                                Icon(Icons.Default.Check, "Selected", tint = color)
                            }
                        }
                    }
                }
                
                Spacer(Modifier.height(8.dp))
                
                // Voice Note Section
                VoiceRecorderSection(
                    voiceUri = voiceUri,
                    onVoiceUriChanged = { voiceUri = it },
                    controller = voiceRecorderController
                )
                
                Spacer(Modifier.height(8.dp))
                
                // Photos Section
                Text(
                    text = "Photos (Optional)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { photoPickerLauncher.launch("image/*") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Photo, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pick Photos")
                    }
                    
                    OutlinedButton(
                        onClick = {
                            cameraLauncher.launch(Unit)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Take Photo")
                    }
                }
                
                if (photoUris.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "${photoUris.size} photo(s) selected",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(photoUris) { photoUri ->
                            Box(
                                modifier = Modifier
                                    .size(100.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            ) {
                                Image(
                                    painter = rememberAsyncImagePainter(Uri.parse(photoUri)),
                                    contentDescription = "Photo preview",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                // Delete button
                                IconButton(
                                    onClick = {
                                        photoUris = photoUris.filter { it != photoUri }
                                    },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(24.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove photo",
                                            tint = MaterialTheme.colorScheme.onError,
                                            modifier = Modifier.padding(2.dp).size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                Spacer(Modifier.height(8.dp))
                
                // Notes
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    placeholder = { Text("Add any special instructions...") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    selectedTechnician?.let { tech ->
                        // Finalize any in-progress voice recording before submitting,
                        // so a completed file is attached instead of a corrupt partial one
                        val finalVoiceUri = if (voiceRecorderController.isRecording) {
                            voiceRecorderController.stopAndFinalize()
                        } else {
                            voiceUri
                        }
                        onAssign(tech, finalVoiceUri, notes, if (photoUris.isNotEmpty()) photoUris.joinToString(",") else null)
                        // Close after onAssign so callers can capture state in onAssign first.
                        // (Calling onDismiss before a coroutine reads selection state caused silent no-ops.)
                        onDismiss()
                    }
                },
                enabled = selectedTechnician != null
            ) {
                Text("Assign Job")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

