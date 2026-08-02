package com.example.fieldtechv20kc.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.fieldtechv20kc.data.database.AppDatabase
import com.example.fieldtechv20kc.data.remote.firestore.ReportsRemote
import com.example.fieldtechv20kc.data.remote.storage.ReportStorage
import com.example.fieldtechv20kc.data.repository.OutboxRepository
import com.example.fieldtechv20kc.data.repository.ReportRepository
import com.example.fieldtechv20kc.viewmodel.UnifiedReportsViewModel
import com.example.fieldtechv20kc.utils.DateUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashBinScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Initialize UnifiedReportsViewModel
    val database = AppDatabase.getDatabase(context)
    
    val outbox = remember {
        try {
            OutboxRepository.get()
        } catch (e: Exception) {
            OutboxRepository.init(database)
            OutboxRepository.get()
        }
    }
    
    val unifiedViewModel: UnifiedReportsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
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
    
    // Get trash items from flow
    val trashedReports by unifiedViewModel.observeTrashItems().collectAsState(initial = emptyList())
    
    // Selection mode
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedReportIds by remember { mutableStateOf(setOf<String>()) }
    var showEmptyTrashDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isSelectionMode) {
                            "${selectedReportIds.size} selected"
                        } else {
                            "Trash Bin (${trashedReports.size})"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isSelectionMode) {
                            isSelectionMode = false
                            selectedReportIds = setOf()
                        } else {
                            navController.popBackStack()
                        }
                    }) {
                        Icon(
                            Icons.Default.ArrowBack,
                            "Back",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                actions = {
                    if (!isSelectionMode && trashedReports.isNotEmpty()) {
                        TextButton(
                            onClick = { isSelectionMode = true },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text("Select")
                        }
                        
                        TextButton(
                            onClick = { showEmptyTrashDialog = true },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text("Empty Trash")
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
            if (isSelectionMode && selectedReportIds.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 3.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Restore button
                        Button(
                            onClick = {
                                unifiedViewModel.restoreMultipleFromTrash(selectedReportIds.toList())
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        "Restored ${selectedReportIds.size} report${if (selectedReportIds.size > 1) "s" else ""}",
                                        duration = SnackbarDuration.Short
                                    )
                                }
                                isSelectionMode = false
                                selectedReportIds = setOf()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Restore")
                        }
                        
                        // Delete permanently button
                        Button(
                            onClick = {
                                scope.launch {
                                    val count = selectedReportIds.size
                                    unifiedViewModel.permanentlyDeleteMultiple(selectedReportIds.toList())
                                    snackbarHostState.showSnackbar(
                                        "Permanently deleted $count report${if (count > 1) "s" else ""}",
                                        duration = SnackbarDuration.Short
                                    )
                                    isSelectionMode = false
                                    selectedReportIds = setOf()
                                }
                            },
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
    ) { paddingValues ->
        if (trashedReports.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Trash is empty",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "Deleted reports will appear here",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            // List of trashed reports
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(trashedReports, key = { it.id }) { row ->
                    TrashReportCard(
                        row = row,
                        unifiedViewModel = unifiedViewModel,
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
    
    // Empty trash confirmation dialog
    if (showEmptyTrashDialog) {
        AlertDialog(
            onDismissRequest = { showEmptyTrashDialog = false },
            title = { Text("Empty Trash?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "This will permanently delete all ${trashedReports.size} report${if (trashedReports.size > 1) "s" else ""} in the trash.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        "⚠️ This action cannot be undone!",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val count = trashedReports.size
                            unifiedViewModel.emptyTrash()
                            showEmptyTrashDialog = false
                            snackbarHostState.showSnackbar(
                                "Permanently deleted $count report${if (count > 1) "s" else ""}",
                                duration = SnackbarDuration.Short
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Empty Trash")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showEmptyTrashDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun TrashReportCard(
    row: com.example.fieldtechv20kc.viewmodel.UnifiedReportRow,
    unifiedViewModel: UnifiedReportsViewModel,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onSelectionToggle: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val scope = rememberCoroutineScope()
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isSelectionMode) {
                    Modifier.clickable { onSelectionToggle() }
                } else {
                    Modifier
                }
            ),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSelectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onSelectionToggle() }
                    )
                }
                
                Column(modifier = Modifier.weight(1f)) {
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
            }
            
            // Job type and technician
            Text(
                text = "${row.jobType} • ${row.technicianName}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // Timestamp
            Text(
                text = "Deleted: ${DateUtils.formatDateTime(row.timestamp)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // Action buttons (hidden in selection mode)
            if (!isSelectionMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Restore button
                    TextButton(
                        onClick = { showRestoreConfirm = true }
                    ) {
                        Icon(
                            Icons.Default.Restore,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Restore", style = MaterialTheme.typography.labelMedium)
                    }
                    
                    // Delete permanently button
                    TextButton(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
    
    // Restore confirmation dialog
    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text("Restore report?") },
            text = { Text("This will restore the report to your reports list.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRestoreConfirm = false
                        unifiedViewModel.restoreFromTrash(row.id)
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                "Report restored",
                                duration = SnackbarDuration.Short
                            )
                        }
                    }
                ) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Delete confirmation dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete permanently?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("This will permanently delete the report.")
                    Text(
                        text = "⚠️ This action cannot be undone!",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        scope.launch {
                            unifiedViewModel.permanentlyDelete(row.id)
                            snackbarHostState.showSnackbar(
                                "Report permanently deleted",
                                duration = SnackbarDuration.Short
                            )
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

