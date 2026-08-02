package com.example.fieldtechv20kc.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.fieldtechv20kc.data.model.ReportWithDetails
import com.example.fieldtechv20kc.navigation.Screen
import com.example.fieldtechv20kc.utils.FileSharing
import com.example.fieldtechv20kc.viewmodel.ReportViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

fun openPdfWithExternalApp(context: android.content.Context, pdfPath: String) {
    try {
        val file = File(pdfPath)
        if (file.exists()) {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            val chooserIntent = Intent.createChooser(intent, "Open PDF with")
            context.startActivity(chooserIntent)
        } else {
            println("DEBUG: PDF file does not exist: $pdfPath")
        }
    } catch (e: Exception) {
        println("DEBUG: Error opening PDF: ${e.message}")
        e.printStackTrace()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: ReportViewModel
) {
    val context = LocalContext.current
    val reports by viewModel.reports.collectAsState()
    
    var showDeleteDialog by remember { mutableStateOf(false) }
    var reportToDelete by remember { mutableStateOf<com.example.fieldtechv20kc.data.model.Report?>(null) }
    
    // Multi-select state
    var isMultiSelectMode by remember { mutableStateOf(false) }
    var selectedReports by remember { mutableStateOf<Set<Long>>(emptySet()) }
    
    // Handle potential ViewModel initialization errors
    LaunchedEffect(Unit) {
        try {
            // This will trigger the ViewModel initialization
        } catch (e: Exception) {
            // Handle error gracefully - the UI will show empty state
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        if (isMultiSelectMode) {
                            "Selected: ${selectedReports.size}"
                        } else {
                            "NC Field Tech"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    ) 
                },
                actions = {
                    if (isMultiSelectMode) {
                        // Select All / Deselect All
                        IconButton(
                            onClick = {
                                if (selectedReports.size == reports.size) {
                                    selectedReports = emptySet()
                                } else {
                                    selectedReports = reports.map { it.report.id }.toSet()
                                }
                            }
                        ) {
                            Icon(
                                if (selectedReports.size == reports.size) {
                                    Icons.Default.Close
                                } else {
                                    Icons.Default.Add
                                },
                                contentDescription = if (selectedReports.size == reports.size) "Deselect All" else "Select All",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        
                        // Bulk Share
                        if (selectedReports.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    shareMultipleReports(context, reports.filter { selectedReports.contains(it.report.id) })
                                }
                            ) {
                                Icon(
                                    Icons.Default.Share,
                                    contentDescription = "Share Selected Reports",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                        
                        // Exit Multi-select
                        IconButton(
                            onClick = {
                                isMultiSelectMode = false
                                selectedReports = emptySet()
                            }
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Exit Multi-select",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    } else {
                        // Enter Multi-select
                        IconButton(
                            onClick = {
                                isMultiSelectMode = true
                            }
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Multi-select Mode",
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
        floatingActionButton = {
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
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Statistics Button - Always visible at top
            Button(
                onClick = { navController.navigate(Screen.Statistics.route) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Icon(
                    Icons.Outlined.Insights,
                    contentDescription = "Statistics",
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Statistics",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Reports List
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = "Field Service Reports",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                if (reports.isEmpty()) {
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
                                    Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "No reports yet",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Tap the + button to create your first report",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(reports) { reportWithDetails ->
                        ReportCard(
                            reportWithDetails = reportWithDetails,
                            isMultiSelectMode = isMultiSelectMode,
                            isSelected = selectedReports.contains(reportWithDetails.report.id),
                            onSelectionChanged = { isSelected ->
                                if (isSelected) {
                                    selectedReports = selectedReports + reportWithDetails.report.id
                                } else {
                                    selectedReports = selectedReports - reportWithDetails.report.id
                                }
                            },
                            onViewClick = {
                                if (!isMultiSelectMode) {
                                    navController.navigate(Screen.ReportDetail.createRoute(reportWithDetails.report.id))
                                }
                            },
                            onViewPdfClick = {
                                if (!isMultiSelectMode) {
                                    // Open PDF with external app
                                    if (reportWithDetails.report.pdfPath.isNotEmpty()) {
                                        openPdfWithExternalApp(context, reportWithDetails.report.pdfPath)
                                    }
                                }
                            },
                            onShareClick = {
                                if (!isMultiSelectMode) {
                                    // Share the PDF file if it exists
                                    if (reportWithDetails.report.pdfPath.isNotEmpty()) {
                                        FileSharing.shareFile(context, reportWithDetails.report.pdfPath)
                                    }
                                }
                            },
                            onDeleteClick = {
                                if (!isMultiSelectMode) {
                                    reportToDelete = reportWithDetails.report
                                    showDeleteDialog = true
                                }
                            }
                        )
                    }
                }
            }
        }
        }
    }
    
    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
                reportToDelete = null
            },
            title = {
                Text("Delete Report")
            },
            text = {
                Text("Are you sure you want to delete this report? This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        reportToDelete?.let { report ->
                            viewModel.deleteReport(report)
                        }
                        showDeleteDialog = false
                        reportToDelete = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        reportToDelete = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ReportCard(
    reportWithDetails: ReportWithDetails,
    isMultiSelectMode: Boolean = false,
    isSelected: Boolean = false,
    onSelectionChanged: (Boolean) -> Unit = {},
    onViewClick: () -> Unit,
    onViewPdfClick: () -> Unit,
    onShareClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val report = reportWithDetails.report
    val client = reportWithDetails.client
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Selection checkbox (only in multi-select mode)
                if (isMultiSelectMode) {
                    IconButton(
                        onClick = { onSelectionChanged(!isSelected) }
                    ) {
                        Icon(
                            if (isSelected) Icons.Default.Check else Icons.Default.Add,
                            contentDescription = if (isSelected) "Deselect" else "Select",
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = client?.name ?: "Unknown Client",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = report.jobType.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${client?.locality ?: "Unknown"} • ${dateFormat.format(report.createdAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        com.example.fieldtechv20kc.ui.components.IslandBadge(client?.locality)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Action buttons (only show when not in multi-select mode)
            if (!isMultiSelectMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    IconButton(
                        onClick = onViewClick,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Info, 
                            contentDescription = "View Report", 
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    IconButton(
                        onClick = onViewPdfClick,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Add, 
                            contentDescription = "View PDF", 
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    IconButton(
                        onClick = onShareClick,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Share, 
                            contentDescription = "Share Report", 
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Delete, 
                            contentDescription = "Delete Report", 
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

fun shareMultipleReports(context: android.content.Context, reports: List<ReportWithDetails>) {
    try {
        val pdfFiles = reports.mapNotNull { reportWithDetails ->
            val pdfFile = File(reportWithDetails.report.pdfPath)
            if (pdfFile.exists()) {
                pdfFile
            } else {
                null
            }
        }
        
        if (pdfFiles.isEmpty()) {
            println("DEBUG: No valid PDF files found to share")
            return
        }
        
        if (pdfFiles.size == 1) {
            // Single file sharing
            FileSharing.shareFile(context, pdfFiles[0].absolutePath)
        } else {
            // Multiple files sharing
            val uris = pdfFiles.map { file ->
                androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            }
            
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND_MULTIPLE
                type = "application/pdf"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            val chooserIntent = Intent.createChooser(shareIntent, "Share Reports")
            context.startActivity(chooserIntent)
        }
        
        println("DEBUG: Successfully shared ${pdfFiles.size} reports")
    } catch (e: Exception) {
        println("DEBUG: Error sharing multiple reports: ${e.message}")
        e.printStackTrace()
    }
}
