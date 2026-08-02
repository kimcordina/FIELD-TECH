package com.example.fieldtechv20kc.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.fieldtechv20kc.viewmodel.ReportViewModel
import com.example.fieldtechv20kc.utils.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportDetailScreen(
    reportId: Long,
    navController: NavController,
    viewModel: ReportViewModel
) {
    val currentReport by viewModel.currentReport.collectAsState()
    
    LaunchedEffect(reportId) {
        viewModel.loadReport(reportId)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Report Details") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                // Note: Share functionality available in Saved Reports screen
            )
        }
    ) { paddingValues ->
        currentReport?.let { reportWithDetails ->
            val report = reportWithDetails.report
            val client = reportWithDetails.client
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "NCORDINA Field Service Report",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        if (report.reportRef.isNotBlank()) {
                            Text(
                                text = "Ref: ${report.reportRef}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = "Date: ${DateUtils.formatDateTime(report.createdAt.time)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // Client Information
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Client Information",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Name: ${client?.name ?: "N/A"}")
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text("Locality: ${client?.locality ?: "N/A"}")
                            Spacer(modifier = Modifier.width(6.dp))
                            com.example.fieldtechv20kc.ui.components.IslandBadge(client?.locality)
                        }
                    }
                }
                
                // Job Information
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Job Information",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Job Type: ${report.jobType.displayName}")
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        if (report.equipmentInstalledRepaired.isNotEmpty()) {
                            Text(
                                text = "Equipment Installed/Repaired:",
                                fontWeight = FontWeight.Medium
                            )
                            Text(report.equipmentInstalledRepaired)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        
                        if (report.serialNumbers.isNotEmpty()) {
                            Text(
                                text = "Serial Number/s:",
                                fontWeight = FontWeight.Medium
                            )
                            Text(report.serialNumbers)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        
                        Text(
                            text = "Work Carried Out:",
                            fontWeight = FontWeight.Medium
                        )
                        Text(report.workCarriedOut)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Findings:",
                            fontWeight = FontWeight.Medium
                        )
                        Text(report.findings)
                    }
                }
                
                // Photos
                if (reportWithDetails.photos.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "Photos",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            reportWithDetails.photos.forEach { photo ->
                                Text("• ${photo.caption.ifEmpty { "Photo" }}")
                            }
                        }
                    }
                }
                
                // Internal Notes (if present)
                if (!report.internalNotes.isNullOrBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Internal Notes",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "(Not in PDF)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f),
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = report.internalNotes!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
                
                // Status
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Report Status",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (report.isCompleted) "Completed" else "In Progress",
                            color = if (report.isCompleted) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                        if (report.signerName.isNotEmpty()) {
                            Text("Signed by: ${report.signerName}")
                        }
                        if (report.pdfPath.isNotEmpty()) {
                            Text("PDF: ${report.pdfPath}")
                        }
                    }
                }
            }
        } ?: run {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}
