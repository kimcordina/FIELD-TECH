package com.example.fieldtechv20kc.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.fieldtechv20kc.data.database.AppDatabase
import com.example.fieldtechv20kc.data.remote.firestore.ReportsRemote
import com.example.fieldtechv20kc.data.remote.storage.ReportStorage
import com.example.fieldtechv20kc.data.repository.OutboxRepository
import com.example.fieldtechv20kc.data.repository.ReportRepository
import com.example.fieldtechv20kc.navigation.Screen
import com.example.fieldtechv20kc.viewmodel.ReportViewModel
import com.example.fieldtechv20kc.viewmodel.UnifiedReportsViewModel
import com.example.fieldtechv20kc.viewmodel.UnifiedReportRow
import com.example.fieldtechv20kc.utils.DateUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedReportsScreen(
    navController: NavController,
    viewModel: ReportViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Initialize UnifiedReportsViewModel
    val database = AppDatabase.getDatabase(context)
    
    // Get OutboxRepository instance (initialize if needed)
    val outbox = remember {
        try {
            OutboxRepository.get()
        } catch (e: Exception) {
            OutboxRepository.init(database)
            OutboxRepository.get()
        }
    }
    
    val unifiedViewModel: UnifiedReportsViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return UnifiedReportsViewModel(
                    reportsRemote = ReportsRemote(),
                    reportStorage = ReportStorage(),
                    reportRepository = ReportRepository(
                        reportDao = database.reportDao(),
                        clientDao = database.clientDao(),
                        photoDao = database.photoDao()
                    ),
                    outbox = outbox
                ) as T
            }
        }
    )
    
    val rows by unifiedViewModel.unified.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Search state
    var searchQuery by remember { mutableStateOf("") }
    var isSearchVisible by remember { mutableStateOf(false) }
    
    // Locality filter state
    var selectedLocality by remember { mutableStateOf<String?>(null) }
    
    // Selection mode state
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedReportIds by remember { mutableStateOf(setOf<String>()) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    
    // Trash count state (collected from flow)
    val trashItems by unifiedViewModel.observeTrashItems().collectAsState(initial = emptyList())
    val trashCount = trashItems.size
    
    // Get unique localities from reports
    val localities = remember(rows) {
        rows.map { it.clientLocality }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }
    
    // Filtered reports based on search query and locality
    val filteredRows = remember(rows, searchQuery, selectedLocality) {
        var filtered = rows
        
        // Apply locality filter
        if (selectedLocality != null) {
            filtered = filtered.filter { it.clientLocality == selectedLocality }
        }
        
        // Apply search filter
        if (searchQuery.isNotBlank()) {
            val query = searchQuery.lowercase()
            filtered = filtered.filter { row ->
                row.clientName.lowercase().contains(query) ||
                row.jobType.lowercase().contains(query) ||
                row.technicianName.lowercase().contains(query) ||
                row.clientLocality.lowercase().contains(query) ||
                row.reportRef.lowercase().contains(query)
            }
        }
        
        filtered
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (!isSelectionMode) {
                ExtendedFloatingActionButton(
                    onClick = {
                        viewModel.clearCurrentReport()
                        navController.navigate(Screen.ClientInfo.route)
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("New Report")
                }
            }
        },
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        if (isSelectionMode) {
                            "${selectedReportIds.size} selected"
                        } else if (searchQuery.isNotBlank()) {
                            "Search Results: ${filteredRows.size}"
                        } else {
                            "Reports"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    if (isSelectionMode) {
                        IconButton(onClick = {
                            isSelectionMode = false
                            selectedReportIds = setOf()
                        }) {
                            Icon(
                                Icons.Default.Close,
                                "Exit selection mode",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        // No actions in selection mode (actions in bottom bar)
                    } else {
                        // Trash bin button (trashCount is already defined as state above)
                        IconButton(
                            onClick = { 
                                navController.navigate("trash_bin")
                            }
                        ) {
                            BadgedBox(
                                badge = {
                                    if (trashCount > 0) {
                                        Badge {
                                            Text(trashCount.toString())
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Trash Bin",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                        // Select button
                        TextButton(
                            onClick = { isSelectionMode = true },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text("Select")
                        }
                        // Search button
                        IconButton(
                            onClick = {
                                isSearchVisible = !isSearchVisible
                                if (!isSearchVisible) {
                                    searchQuery = ""
                                }
                            }
                        ) {
                            Icon(
                                if (isSearchVisible) Icons.Default.Clear else Icons.Default.Search,
                                contentDescription = if (isSearchVisible) "Close Search" else "Search Reports",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
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
                                selectedReportIds = filteredRows.map { it.id }.toSet()
                            }
                        ) {
                            Text("Select all")
                        }
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Share button
                            Button(
                                onClick = {
                                    scope.launch {
                                        // Share selected reports
                                        val selectedRows = filteredRows.filter { selectedReportIds.contains(it.id) }
                                        val uris = selectedRows.mapNotNull { row ->
                                            row.localPdfPath?.let { Uri.parse("file://$it") }
                                        }
                                        
                                        if (uris.isNotEmpty()) {
                                            val shareIntent = Intent().apply {
                                                action = Intent.ACTION_SEND_MULTIPLE
                                                type = "application/pdf"
                                                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "Share ${uris.size} Reports"))
                                            
                                            // Exit selection mode
                                            isSelectionMode = false
                                            selectedReportIds = setOf()
                                        } else {
                                            snackbarHostState.showSnackbar("No PDFs available to share")
                                        }
                                    }
                                },
                                enabled = selectedReportIds.isNotEmpty()
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Share")
                            }
                            
                            // Delete button
                            Button(
                                onClick = { showDeleteConfirmDialog = true },
                                enabled = selectedReportIds.isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Delete")
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
            // Search Bar
            if (isSearchVisible) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        placeholder = {
                            Text("Search reports by client, job type, technician...")
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = "" }
                                ) {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = "Clear search",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                }
            }
            
            // Locality Filter
            var localityExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = localityExpanded,
                onExpandedChange = { localityExpanded = it },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                OutlinedTextField(
                    value = selectedLocality ?: "All Localities",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Locality") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = localityExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = localityExpanded,
                    onDismissRequest = { localityExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("All Localities") },
                        onClick = {
                            selectedLocality = null
                            localityExpanded = false
                        }
                    )
                    localities.forEach { locality ->
                        DropdownMenuItem(
                            text = { Text(locality) },
                            onClick = {
                                selectedLocality = locality
                                localityExpanded = false
                            }
                        )
                    }
                }
            }
            
            // Unified Reports List
            if (filteredRows.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Card(
                            modifier = Modifier.size(120.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            shape = RoundedCornerShape(60.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "No matching reports" else "No reports yet",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) {
                                "Try adjusting your search terms"
                            } else {
                                "Create your first report to see it here"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(filteredRows, key = { it.id }) { row ->
                        UnifiedReportCard(
                            row = row,
                            unifiedViewModel = unifiedViewModel,
                            navController = navController,
                            onRetryClick = {
                                unifiedViewModel.retryForReportId(row.id, context)
                            },
                            isSelectionMode = isSelectionMode,
                            isSelected = selectedReportIds.contains(row.id),
                            onSelectionToggle = {
                                selectedReportIds = if (selectedReportIds.contains(row.id)) {
                                    selectedReportIds - row.id
                                } else {
                                    selectedReportIds + row.id
                                }
                            },
                            snackbarHostState = snackbarHostState
                        )
                    }
                }
            }
        }
    }
    
    // Bulk delete confirmation dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            icon = {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp)
                )
            },
            title = {
                Text(
                    "Delete ${selectedReportIds.size} Reports?",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "This will delete ${selectedReportIds.size} selected report${if (selectedReportIds.size > 1) "s" else ""}.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        "You'll have a few seconds to undo this action.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                val selectedRows = filteredRows.filter { selectedReportIds.contains(it.id) }
                                val count = selectedRows.size
                                
                                // Move reports to trash bin
                                unifiedViewModel.moveMultipleToTrash(selectedRows)
                                
                                showDeleteConfirmDialog = false
                                isSelectionMode = false
                                selectedReportIds = setOf()
                                
                                // Show confirmation snackbar
                                snackbarHostState.showSnackbar(
                                    message = "Moved $count report${if (count > 1) "s" else ""} to trash",
                                    duration = SnackbarDuration.Short
                                )
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar(
                                    message = "Error deleting reports: ${e.message}",
                                    duration = SnackbarDuration.Long
                                )
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDeleteConfirmDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun UnifiedReportCard(
    row: UnifiedReportRow,
    unifiedViewModel: UnifiedReportsViewModel,
    navController: NavController,
    onRetryClick: () -> Unit,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onSelectionToggle: () -> Unit = {},
    snackbarHostState: SnackbarHostState? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showConfirmDelete by remember { mutableStateOf(false) }
    
    val cardAlpha = if (row.isPending) 0.85f else 1f
    val cardColor = if (row.isPending) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.surface
    }
    
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = cardAlpha }
            .then(
                if (isSelectionMode) {
                    Modifier.clickable { onSelectionToggle() }
                } else {
                    Modifier
                }
            ),
        colors = CardDefaults.elevatedCardColors(
            containerColor = cardColor
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header row with client name and pending badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Checkbox for selection mode
                if (isSelectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onSelectionToggle() }
                    )
                }
                
                Column(modifier = Modifier.weight(1f)) {
                    // Client name on its own line
                    Text(
                        text = row.clientName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (row.reportRef.isNotBlank()) {
                        Text(
                            text = row.reportRef,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    // Locality underneath in smaller, non-bold font, with island marker
                    if (row.clientLocality.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = row.clientLocality,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            com.example.fieldtechv20kc.ui.components.IslandBadge(row.clientLocality)
                        }
                    }
                }
                
                if (row.isPending) {
                    PendingBadge()
                }
            }
            
            // Job type and technician
            Text(
                text = "${row.jobType} • ${row.technicianName}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // Timestamp
            Text(
                text = DateUtils.formatDateTime(row.timestamp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // Photo count
            if (row.photoCount > 0) {
                Text(
                    text = "${row.photoCount} photo(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Action buttons - use FlowRow to wrap when needed (hidden in selection mode)
            if (!isSelectionMode) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    maxItemsInEachRow = 4
                ) {
                    // Retry button for pending reports (leftmost)
                    if (row.isPending) {
                        TextButton(onClick = onRetryClick) {
                            Icon(
                                Icons.Outlined.CloudUpload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Retry", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    
                    // View button - shows text details without opening PDF
                    TextButton(
                        onClick = {
                            // Navigate to report detail screen
                            val reportId = row.id.toLongOrNull()
                            if (reportId != null) {
                                navController.navigate(Screen.ReportDetail.createRoute(reportId))
                            }
                        }
                    ) {
                        Text("View", style = MaterialTheme.typography.labelMedium)
                    }
                    
                    // Open PDF button
                    val hasPdf = row.isFullySynced || !row.localPdfPath.isNullOrBlank()
                    TextButton(
                        enabled = hasPdf,
                        onClick = {
                            scope.launch {
                                try {
                                    val uri = unifiedViewModel.resolveOpenUri(context, row)
                                    if (uri != null) {
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(uri, "application/pdf")
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(intent)
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    ) {
                        Text(
                            text = if (row.isFullySynced) "Open PDF" else "Open local",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    
                    // Share button
                    TextButton(
                        enabled = hasPdf,
                        onClick = {
                            scope.launch {
                                try {
                                    val intent = unifiedViewModel.shareIntent(context, row)
                                    if (intent != null) {
                                        context.startActivity(Intent.createChooser(intent, "Share report"))
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    ) {
                        Text("Share", style = MaterialTheme.typography.labelMedium)
                    }
                    
                    // Delete button (rightmost)
                    TextButton(
                        onClick = { showConfirmDelete = true },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Delete", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
    
    // Delete confirmation dialog
    if (showConfirmDelete) {
        AlertDialog(
            onDismissRequest = { showConfirmDelete = false },
            title = { Text("Delete report?") },
            text = { 
                Text("This report will be moved to the trash bin. You can restore it later or delete it permanently from there.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmDelete = false
                        scope.launch {
                            // Move to trash bin
                            unifiedViewModel.moveToTrash(row)
                            
                            // Show confirmation snackbar
                            snackbarHostState?.showSnackbar(
                                message = "Report moved to trash",
                                duration = SnackbarDuration.Short
                            )
                        }
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDelete = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun PendingBadge() {
    AssistChip(
        onClick = {},
        label = { 
            Text(
                "Pending upload",
                style = MaterialTheme.typography.labelSmall
            ) 
        },
        leadingIcon = {
            Icon(
                Icons.Outlined.CloudUpload,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        },
        enabled = false,
        colors = AssistChipDefaults.assistChipColors(
            disabledContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
            disabledLabelColor = MaterialTheme.colorScheme.onTertiaryContainer,
            disabledLeadingIconContentColor = MaterialTheme.colorScheme.onTertiaryContainer
        )
    )
}
