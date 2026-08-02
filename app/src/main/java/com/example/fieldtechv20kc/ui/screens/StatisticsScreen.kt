package com.example.fieldtechv20kc.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.example.fieldtechv20kc.data.model.Statistics
import com.example.fieldtechv20kc.data.model.LocalityStatistics
import com.example.fieldtechv20kc.data.repository.StatisticsRepository
import com.example.fieldtechv20kc.data.repository.ReportRepository
import com.example.fieldtechv20kc.data.database.AppDatabase
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    navController: NavController,
    statisticsRepository: StatisticsRepository
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val statistics by statisticsRepository.getStatistics().collectAsState(initial = null)
    val localityStatistics by statisticsRepository.getAllLocalityStatistics().collectAsState(initial = emptyList())
    
    var showResetTotalDialog by remember { mutableStateOf(false) }
    var showResetAllDialog by remember { mutableStateOf(false) }
    var localityToReset by remember { mutableStateOf<String?>(null) }
    var showLocalityReportsDialog by remember { mutableStateOf(false) }
    var selectedLocality by remember { mutableStateOf<String?>(null) }
    var localityReports by remember { mutableStateOf<List<com.example.fieldtechv20kc.data.model.ReportWithDetails>>(emptyList()) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Statistics",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    ) 
                },
                actions = {
                    IconButton(
                        onClick = { showResetAllDialog = true }
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Reset All Statistics",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Total Reports Card
            Card(
                modifier = Modifier
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Assignment,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Total Reports",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${statistics?.totalReports ?: 0}",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { showResetTotalDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reset Total")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Locality Statistics Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Reports by Locality",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                
                if (localityStatistics.isNotEmpty()) {
                    TextButton(
                        onClick = { showResetAllDialog = true }
                    ) {
                        Text("Reset All")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (localityStatistics.isEmpty()) {
                // Empty state
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No locality data",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Create reports to see locality statistics",
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
                    items(localityStatistics) { localityStat ->
                        LocalityStatisticsCard(
                            localityStatistics = localityStat,
                            onResetClick = { locality ->
                                localityToReset = locality
                            },
                            onCardClick = { locality ->
                                selectedLocality = locality
                                scope.launch {
                                    val database = AppDatabase.getDatabase(context)
                                    val reportRepository = ReportRepository(
                                        database.reportDao(),
                                        database.clientDao(),
                                        database.photoDao()
                                    )
                                    localityReports = reportRepository.getReportsWithDetailsByLocality(locality)
                                    showLocalityReportsDialog = true
                                }
                            }
                        )
                    }
                }
            }
        }
    }
    
    // Reset Total Dialog
    if (showResetTotalDialog) {
        AlertDialog(
            onDismissRequest = { showResetTotalDialog = false },
            title = { Text("Reset Total Reports") },
            text = { Text("Are you sure you want to reset the total report count to 0? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            statisticsRepository.resetTotalReports()
                        }
                        showResetTotalDialog = false
                    }
                ) {
                    Text("Reset", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showResetTotalDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Reset All Dialog
    if (showResetAllDialog) {
        AlertDialog(
            onDismissRequest = { showResetAllDialog = false },
            title = { Text("Reset All Statistics") },
            text = { Text("Are you sure you want to reset all statistics? This will reset both total reports and all locality counts to 0. This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            statisticsRepository.resetTotalReports()
                            statisticsRepository.resetAllLocalityStatistics()
                        }
                        showResetAllDialog = false
                    }
                ) {
                    Text("Reset All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showResetAllDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Reset Single Locality Dialog
    localityToReset?.let { locality ->
        AlertDialog(
            onDismissRequest = { localityToReset = null },
            title = { Text("Reset Locality Statistics") },
            text = { Text("Are you sure you want to reset the report count for $locality to 0? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            statisticsRepository.resetLocalityReports(locality)
                        }
                        localityToReset = null
                    }
                ) {
                    Text("Reset", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { localityToReset = null }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Locality Reports Dialog
    if (showLocalityReportsDialog) {
        AlertDialog(
            onDismissRequest = { 
                showLocalityReportsDialog = false
                selectedLocality = null
                localityReports = emptyList()
            },
            title = { 
                Text("Reports for $selectedLocality")
            },
            text = {
                if (localityReports.isEmpty()) {
                    Text("No reports found for this locality.")
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 400.dp)
                    ) {
                        items(localityReports) { reportWithDetails ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Text(
                                        text = reportWithDetails.client?.name ?: "Unknown Client",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(reportWithDetails.report.createdAt),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { 
                        showLocalityReportsDialog = false
                        selectedLocality = null
                        localityReports = emptyList()
                    }
                ) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun LocalityStatisticsCard(
    localityStatistics: LocalityStatistics,
    onResetClick: (String) -> Unit,
    onCardClick: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCardClick(localityStatistics.locality) },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = localityStatistics.locality,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${localityStatistics.reportCount} report${if (localityStatistics.reportCount != 1) "s" else ""}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            IconButton(
                onClick = { onResetClick(localityStatistics.locality) }
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Reset locality statistics",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
