package com.example.fieldtechv20kc.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.outlined.NotificationsOff
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
import com.example.fieldtechv20kc.data.model.ServiceDueRules
import com.example.fieldtechv20kc.data.model.ServiceDueStatus
import com.example.fieldtechv20kc.data.model.ServiceTask
import com.example.fieldtechv20kc.navigation.Screen
import com.example.fieldtechv20kc.utils.DateUtils
import com.example.fieldtechv20kc.viewmodel.ServiceNeedsFilter
import com.example.fieldtechv20kc.viewmodel.ServiceNeedsRow
import com.example.fieldtechv20kc.viewmodel.ServiceNeedsViewModel
import com.example.fieldtechv20kc.viewmodel.ServiceTasksViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceNeedsScreen(
    navController: NavController,
    viewModel: ServiceNeedsViewModel,
    tasksViewModel: ServiceTasksViewModel,
    userRole: String
) {
    val rows by viewModel.rows.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val query by viewModel.query.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email ?: "Unknown User"

    val canStar = userRole == "TECH" || userRole == "MANAGER"
    val canSilence = userRole == "MANAGER"

    var showAssignDialog by remember { mutableStateOf(false) }
    var selectedClientForTask by remember { mutableStateOf<Client?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Service",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${rows.size} shown",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search clients") },
                singleLine = true
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ServiceNeedsFilter.entries.forEach { option ->
                    FilterChip(
                        selected = filter == option,
                        onClick = { viewModel.setFilter(option) },
                        label = {
                            Text(
                                when (option) {
                                    ServiceNeedsFilter.ALL_DUE -> "All due"
                                    ServiceNeedsFilter.STARRED -> "Starred"
                                    ServiceNeedsFilter.OVERDUE -> "Overdue"
                                    ServiceNeedsFilter.LATE -> "Late"
                                    ServiceNeedsFilter.SOON -> "Soon"
                                    ServiceNeedsFilter.SILENCED -> "Silenced"
                                }
                            )
                        }
                    )
                }
            }

            if (rows.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No clients match this filter",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(rows, key = { it.client.id }) { row ->
                        ServiceNeedsCard(
                            row = row,
                            canStar = canStar,
                            canSilence = canSilence,
                            onOpen = {
                                navController.navigate(Screen.ClientDetail.createRoute(row.client.id))
                            },
                            onToggleStar = { viewModel.toggleStar(row.client) },
                            onToggleSilence = { viewModel.toggleSilence(row.client) },
                            onAssign = {
                                selectedClientForTask = row.client
                                showAssignDialog = true
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAssignDialog && selectedClientForTask != null) {
        UnifiedTaskAssignmentDialog(
            client = selectedClientForTask!!,
            onDismiss = {
                showAssignDialog = false
                selectedClientForTask = null
            },
            onAssign = { technician, voiceUri, notes, photoUris ->
                val client = selectedClientForTask
                if (client != null) {
                    scope.launch {
                        tasksViewModel.upsert(
                            ServiceTask(
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
                        showAssignDialog = false
                        selectedClientForTask = null
                    }
                }
            }
        )
    }
}

@Composable
private fun ServiceNeedsCard(
    row: ServiceNeedsRow,
    canStar: Boolean,
    canSilence: Boolean,
    onOpen: () -> Unit,
    onToggleStar: () -> Unit,
    onToggleSilence: () -> Unit,
    onAssign: () -> Unit
) {
    val client = row.client
    val (badgeLabel, badgeBg, badgeFg, cardBg) = statusStyle(row.status, client.serviceAlertsSilenced)
    val months = ServiceDueRules.monthsSinceLastService(client.lastServiceDate)
    val ageLabel = when {
        client.lastServiceDate == null -> "No service on record"
        months.isInfinite() -> "No service on record"
        else -> {
            val m = months.toInt().coerceAtLeast(0)
            "Last service: ${DateUtils.formatDate(client.lastServiceDate)} · ${m} mo ago"
        }
    }

    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        client.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!client.locality.isNullOrBlank()) {
                        Text(
                            client.locality!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(badgeLabel) },
                    colors = AssistChipDefaults.assistChipColors(
                        disabledContainerColor = badgeBg,
                        disabledLabelColor = badgeFg
                    )
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                ageLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onToggleStar,
                    enabled = canStar
                ) {
                    Icon(
                        imageVector = if (client.priorityStarred) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = if (client.priorityStarred) "Unstar" else "Star",
                        tint = if (client.priorityStarred) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onToggleSilence,
                    enabled = canSilence
                ) {
                    Icon(
                        imageVector = if (client.serviceAlertsSilenced) Icons.Default.NotificationsOff else Icons.Outlined.NotificationsOff,
                        contentDescription = if (client.serviceAlertsSilenced) "Unsilence alerts" else "Silence alerts",
                        tint = if (client.serviceAlertsSilenced) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                if (canStar) {
                    OutlinedButton(onClick = onAssign) {
                        Icon(Icons.Default.Assignment, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Assign")
                    }
                }
            }
        }
    }
}

private data class StatusStyle(
    val label: String,
    val bg: Color,
    val fg: Color,
    val cardBg: Color
)

@Composable
private fun statusStyle(status: ServiceDueStatus, silenced: Boolean): StatusStyle {
    if (silenced) {
        return StatusStyle(
            label = "SILENCED",
            bg = Color(0xFFEEEEEE),
            fg = Color(0xFF616161),
            cardBg = Color(0xFFF5F5F5)
        )
    }
    return when (status) {
        ServiceDueStatus.OVERDUE -> StatusStyle(
            label = "OVERDUE",
            bg = Color(0xFFFFCDD2),
            fg = Color(0xFFB71C1C),
            cardBg = Color(0xFFFFEBEE)
        )
        ServiceDueStatus.LATE -> StatusStyle(
            label = "LATE",
            bg = Color(0xFFFFE0B2),
            fg = Color(0xFFE65100),
            cardBg = Color(0xFFFFF3E0)
        )
        ServiceDueStatus.SOON -> StatusStyle(
            label = "SOON",
            bg = Color(0xFFFFF9C4),
            fg = Color(0xFFF57F17),
            cardBg = Color(0xFFFFFDE7)
        )
        ServiceDueStatus.OK -> StatusStyle(
            label = "OK",
            bg = Color(0xFFE8F5E9),
            fg = Color(0xFF2E7D32),
            cardBg = MaterialTheme.colorScheme.surface
        )
    }
}
