package com.example.fieldtechv20kc.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.navigation.NavController
import com.example.fieldtechv20kc.BuildConfig
import com.example.fieldtechv20kc.navigation.Screen
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.fieldtechv20kc.viewmodel.ReportViewModel
import com.example.fieldtechv20kc.utils.SettingsManager
import com.example.fieldtechv20kc.data.remote.firestore.UsersRemote
import com.example.fieldtechv20kc.usecases.CleanupOldDataUseCase
import com.example.fieldtechv20kc.usecases.TestCleanupLogic
import androidx.work.WorkManager
import androidx.work.OneTimeWorkRequestBuilder
import com.example.fieldtechv20kc.workers.CleanupWorker
import com.example.fieldtechv20kc.data.database.AppDatabase
import com.example.fieldtechv20kc.data.remote.firestore.FirestoreRequestsDataSource
import com.example.fieldtechv20kc.data.remote.firestore.FirestoreTasksDataSource
import com.example.fieldtechv20kc.data.remote.storage.FirebaseStorageService
import com.example.fieldtechv20kc.data.repository.ServiceRequestsRepository
import com.example.fieldtechv20kc.data.repository.ServiceTasksRepository
import com.example.fieldtechv20kc.data.remote.firestore.ReportsRemote
import com.example.fieldtechv20kc.data.remote.storage.ReportStorage
import com.example.fieldtechv20kc.data.repository.OutboxRepository
import com.example.fieldtechv20kc.data.repository.ReportRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import android.content.Context
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: ReportViewModel
) {
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val settingsManager = SettingsManager.getInstance(navController.context)
    val settings by settingsManager.settings.collectAsState()
    var showAboutDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showColorPickerDialog by remember { mutableStateOf(false) }
    
    // User profile state
    val auth = FirebaseAuth.getInstance()
    val usersRemote = remember { UsersRemote() }
    val scope = rememberCoroutineScope()
    
    var role by remember { mutableStateOf("NONE") }
    var assignedToName by remember { mutableStateOf<String?>(null) }
    var notificationsEnabled by remember { mutableStateOf(false) }
    var isLoadingProfile by remember { mutableStateOf(true) }
    var currentToken by remember { mutableStateOf<String?>(null) }
    var activeTokenCount by remember { mutableStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Load profile on first load
    LaunchedEffect(Unit) {
        try {
            val profile = usersRemote.getProfile()
            if (profile != null) {
                role = profile.role
                assignedToName = profile.assignedToName
                notificationsEnabled = profile.notificationsEnabled
            } else {
                // Create baseline profile if doesn't exist
                usersRemote.upsertProfile(
                    displayName = auth.currentUser?.email,
                    assignedToName = null,
                    role = "NONE"
                )
            }
            
            // Get token info
            currentToken = com.example.fieldtechv20kc.notifications.NotificationHelper.getCurrentToken()
            activeTokenCount = usersRemote.getActiveTokenCount()
            
            isLoadingProfile = false
        } catch (e: Exception) {
            android.util.Log.e("Settings", "Error loading profile", e)
            isLoadingProfile = false
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    ) 
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
                .verticalScroll(rememberScrollState())
        ) {
            // App Information Section
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "NC Field Tech",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Version ${BuildConfig.VERSION_NAME}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Professional field service reporting app for technicians and service providers.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // User Profile & Notifications Section
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "User Profile & Notifications",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Divider()
                    
                    if (isLoadingProfile) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        // Role Selector
                        Column {
                            Text(
                                text = "Role",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            var roleMenuExpanded by remember { mutableStateOf(false) }
                            
                            ExposedDropdownMenuBox(
                                expanded = roleMenuExpanded,
                                onExpandedChange = { roleMenuExpanded = it }
                            ) {
                                OutlinedTextField(
                                    value = role,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Select your role") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = roleMenuExpanded) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor()
                                )
                                ExposedDropdownMenu(
                                    expanded = roleMenuExpanded,
                                    onDismissRequest = { roleMenuExpanded = false }
                                ) {
                                    listOf("NONE", "TECH", "MANAGER", "REQUESTER").forEach { roleOption ->
                                        DropdownMenuItem(
                                            text = { Text(roleOption) },
                                            onClick = {
                                                role = roleOption
                                                roleMenuExpanded = false
                                                
                                                // Update profile - preserve current notificationsEnabled state
                                                scope.launch {
                                                    try {
                                                        android.util.Log.d("Settings", "Updating role to: $roleOption, notifications: $notificationsEnabled")
                                                        
                                                        usersRemote.upsertProfile(
                                                            displayName = auth.currentUser?.email,
                                                            assignedToName = if (roleOption == "TECH") assignedToName else null,
                                                            role = roleOption,
                                                            notificationsEnabled = notificationsEnabled  // Preserve current setting
                                                        )
                                                        
                                                        // Wait a moment for Firestore to sync
                                                        kotlinx.coroutines.delay(500)
                                                        
                                                        // Verify the update
                                                        val profile = usersRemote.getProfile()
                                                        android.util.Log.d("Settings", "Profile after update: $profile")
                                                        
                                                        if (profile?.role == roleOption) {
                                                            snackbarHostState.showSnackbar("Role updated to $roleOption")
                                                        } else {
                                                            android.util.Log.e("Settings", "Role mismatch! Expected: $roleOption, Got: ${profile?.role}")
                                                            snackbarHostState.showSnackbar("ERROR: Role not saved! Check Firestore permissions.")
                                                        }
                                                    } catch (e: Exception) {
                                                        android.util.Log.e("Settings", "Error updating role", e)
                                                        snackbarHostState.showSnackbar("Error updating role: ${e.message}")
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        
                        // Technician Identity (only if role is TECH)
                        if (role == "TECH") {
                            Column {
                                Text(
                                    text = "Technician Identity",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                var techMenuExpanded by remember { mutableStateOf(false) }
                                
                                ExposedDropdownMenuBox(
                                    expanded = techMenuExpanded,
                                    onExpandedChange = { techMenuExpanded = it }
                                ) {
                                    OutlinedTextField(
                                        value = assignedToName ?: "Not set",
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Select technician") },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = techMenuExpanded) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .menuAnchor()
                                    )
                                    ExposedDropdownMenu(
                                        expanded = techMenuExpanded,
                                        onDismissRequest = { techMenuExpanded = false }
                                    ) {
                                        listOf("Jenson", "Abubakar").forEach { techName ->
                                            DropdownMenuItem(
                                                text = { Text(techName) },
                                                onClick = {
                                                    assignedToName = techName
                                                    techMenuExpanded = false
                                                    
                                                    // Update profile - preserve current notificationsEnabled state
                                                    scope.launch {
                                                        try {
                                                            usersRemote.upsertProfile(
                                                                displayName = auth.currentUser?.email,
                                                                assignedToName = techName,
                                                                role = role,
                                                                notificationsEnabled = notificationsEnabled  // Preserve current setting
                                                            )
                                                        } catch (e: Exception) {
                                                            android.util.Log.e("Settings", "Error updating technician", e)
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        
                        // Notifications Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Allow Notifications",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = when (role) {
                                        "TECH" -> "Receive job assignments"
                                        "MANAGER" -> "Receive job completions"
                                        "REQUESTER" -> "Not available for requesters"
                                        else -> "Select a role to enable"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = notificationsEnabled,
                                onCheckedChange = { enabled ->
                                    // REQUESTER cannot enable notifications
                                    if (role == "REQUESTER") return@Switch
                                    
                                    notificationsEnabled = enabled
                                    scope.launch {
                                        try {
                                            usersRemote.setNotificationsEnabled(enabled)
                                        } catch (e: Exception) {
                                            android.util.Log.e("Settings", "Error updating notifications", e)
                                        }
                                    }
                                },
                                enabled = role != "REQUESTER" && role != "NONE"
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            
            // Diagnostics & Advanced Section
            var showSyncHealthDialog by remember { mutableStateOf(false) }
            var showPushDiagnosticsDialog by remember { mutableStateOf(false) }
            var showNotificationResetDialog by remember { mutableStateOf(false) }
            
            val errorCount by database.errorLogDao().observeErrorCount().collectAsState(initial = 0)
            val activeJobsCount by database.outboxDao().observeActiveCount().collectAsState(initial = 0)
            val quarantinedJobsCount by database.outboxDao().observeQuarantinedCount().collectAsState(initial = 0)
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column {
                    Text(
                        text = "Diagnostics & Advanced",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 8.dp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    // Error Tray
                    SettingsItem(
                        icon = Icons.Default.BugReport,
                        title = "Error Tray",
                        subtitle = if (errorCount > 0) "$errorCount error${if (errorCount > 1) "s" else ""} logged" else "No errors",
                        onClick = { navController.navigate(Screen.ErrorTray.route) },
                        badge = if (errorCount > 0) errorCount else null,
                        badgeColor = MaterialTheme.colorScheme.error
                    )
                    
                    Divider(modifier = Modifier.padding(horizontal = 16.dp))
                    
                    // Sync Health
                    SettingsItem(
                        icon = Icons.Default.CloudSync,
                        title = "Sync Health",
                        subtitle = if (quarantinedJobsCount > 0) "$quarantinedJobsCount failed uploads" 
                                   else if (activeJobsCount > 0) "$activeJobsCount pending"
                                   else "All synced",
                        onClick = { showSyncHealthDialog = true },
                        badge = if (quarantinedJobsCount > 0) quarantinedJobsCount else null,
                        badgeColor = MaterialTheme.colorScheme.error
                    )
                    
                    Divider(modifier = Modifier.padding(horizontal = 16.dp))
                    
                    // Notification Reset
                    SettingsItem(
                        icon = Icons.Default.Warning,
                        title = "Reset Notifications",
                        subtitle = "Force disable all notifications",
                        onClick = { showNotificationResetDialog = true },
                        tintColor = MaterialTheme.colorScheme.error
                    )
                }
            }
            
            // Sync Health Dialog
            if (showSyncHealthDialog) {
                SyncHealthDialog(
                    database = database,
                    context = context,
                    scope = scope,
                    onDismiss = { showSyncHealthDialog = false }
                )
            }
            
            // Push Diagnostics Dialog
            if (showPushDiagnosticsDialog) {
                PushDiagnosticsDialog(
                    usersRemote = usersRemote,
                    currentToken = currentToken,
                    activeTokenCount = activeTokenCount,
                    notificationsEnabled = notificationsEnabled,
                    snackbarHostState = snackbarHostState,
                    scope = scope,
                    onTokenRefresh = {
                        scope.launch {
                            try {
                                val result = com.example.fieldtechv20kc.notifications.NotificationHelper.registerToken()
                                if (result.isSuccess) {
                                    currentToken = result.getOrNull()
                                    activeTokenCount = usersRemote.getActiveTokenCount()
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("Settings", "Error registering token", e)
                            }
                        }
                    },
                    onDismiss = { showPushDiagnosticsDialog = false }
                )
            }
            
            // Notification Reset Dialog
            if (showNotificationResetDialog) {
                NotificationResetDialog(
                    usersRemote = usersRemote,
                    currentToken = currentToken,
                    role = role,
                    assignedToName = assignedToName,
                    auth = auth,
                    snackbarHostState = snackbarHostState,
                    scope = scope,
                    onSuccess = {
                        notificationsEnabled = false
                        scope.launch {
                            activeTokenCount = usersRemote.getActiveTokenCount()
                        }
                    },
                    onDismiss = { showNotificationResetDialog = false }
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Section Header: Appearance
            Text(
                text = "APPEARANCE",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            
            // General Settings Section
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column {
                    Text(
                        text = "General",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 8.dp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    // Theme Settings
                    SettingsItem(
                        icon = Icons.Default.Palette,
                        title = "Theme",
                        subtitle = if (settings.isDarkMode) "Dark Mode" else "Light Mode",
                        onClick = { showThemeDialog = true }
                    )
                    
                    Divider(modifier = Modifier.padding(horizontal = 16.dp))
                    
                    // Accent Color Settings
                    SettingsItem(
                        icon = Icons.Default.ColorLens,
                        title = "Accent Color",
                        subtitle = "Customize app colors",
                        onClick = { showColorPickerDialog = true }
                    )
                    
                    Divider(modifier = Modifier.padding(horizontal = 16.dp))
                    
                    // Statistics
                    SettingsItem(
                        icon = Icons.Outlined.Insights,
                        title = "Statistics",
                        subtitle = "View reports and performance data",
                        onClick = { navController.navigate(Screen.Statistics.route) }
                    )

                    Divider(modifier = Modifier.padding(horizontal = 16.dp))

                    SettingsItem(
                        icon = Icons.Default.Build,
                        title = "Service due rules",
                        subtitle = "Soon ${settings.serviceSoonMonths}mo · Late ${settings.serviceLateMonths}mo · Overdue ${settings.serviceOverdueMonths}mo",
                        onClick = { navController.navigate(Screen.ServiceDueSettings.route) }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Section Header: Communication
            Text(
                text = "COMMUNICATION",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            
            // Email Settings Section
            var showEmailSettings by remember { mutableStateOf(false) }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column {
                    Text(
                        text = "Email Settings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 8.dp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    // Auto-email reports toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showEmailSettings = !showEmailSettings }
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.Email,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = "Auto-Email Reports",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = if (settings.autoEmailReportsEnabled) 
                                        "Sending to ${settings.reportEmailRecipient}" 
                                    else 
                                        "Disabled",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = settings.autoEmailReportsEnabled,
                            onCheckedChange = { 
                                if (it && settings.reportEmailRecipient.isBlank()) {
                                    // If enabling but no email set, show settings
                                    showEmailSettings = true
                                }
                                settingsManager.updateAutoEmailReportsEnabled(it) 
                            }
                        )
                    }
                    
                    // Email recipient setting
                    if (showEmailSettings) {
                        Divider(modifier = Modifier.padding(horizontal = 16.dp))
                        var emailInput by remember { mutableStateOf(settings.reportEmailRecipient) }
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Email Address",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            OutlinedTextField(
                                value = emailInput,
                                onValueChange = { emailInput = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Gmail Address") },
                                placeholder = { Text("example@gmail.com") },
                                leadingIcon = { Icon(Icons.Default.Email, null) },
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Email
                                )
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                            ) {
                                TextButton(onClick = { showEmailSettings = false }) {
                                    Text("Cancel")
                                }
                                Button(
                                    onClick = {
                                        settingsManager.updateReportEmailRecipient(emailInput)
                                        showEmailSettings = false
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Email saved")
                                        }
                                    },
                                    enabled = android.util.Patterns.EMAIL_ADDRESS.matcher(emailInput).matches()
                                ) {
                                    Text("Save")
                                }
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Section Header: Reports
            Text(
                text = "REPORTS",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            
            // Report Settings Section
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column {
                    Text(
                        text = "Report Settings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 8.dp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    // Client Info Settings
                    SettingsItem(
                        icon = Icons.Default.Person,
                        title = "Client Info",
                        subtitle = "Configure client information fields",
                        onClick = { navController.navigate(com.example.fieldtechv20kc.navigation.Screen.ClientInfoSettings.route) }
                    )
                    
                    Divider(modifier = Modifier.padding(horizontal = 16.dp))
                    
                    // Job Type Settings
                    SettingsItem(
                        icon = Icons.Default.Build,
                        title = "Job Type",
                        subtitle = "Manage job types and categories",
                        onClick = { navController.navigate(com.example.fieldtechv20kc.navigation.Screen.JobTypeSettings.route) }
                    )
                    
                    Divider(modifier = Modifier.padding(horizontal = 16.dp))
                    
                    // Documentation Settings
                    SettingsItem(
                        icon = Icons.Default.Description,
                        title = "Documentation",
                        subtitle = "Configure documentation options",
                        onClick = { navController.navigate(com.example.fieldtechv20kc.navigation.Screen.JobDocumentationSettings.route) }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Section Header: System
            Text(
                text = "SYSTEM",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            
            // Support Section
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column {
                    Text(
                        text = "Support",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 8.dp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    // About & Support
                    SettingsItem(
                        icon = Icons.Default.Info,
                        title = "About & Support",
                        subtitle = "Created by Kim Cordina - 23.09.25 (kim@ncordina.com)",
                        onClick = { showAboutDialog = true }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Section Header: Data Management
            Text(
                text = "DATA MANAGEMENT",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Automatic Cleanup",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Text(
                        text = "Completed/cancelled/deleted requests and jobs are automatically deleted after 14 days, including voice notes and photos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    var cleanupSimulation by remember { mutableStateOf<String?>(null) }
                    var isSimulating by remember { mutableStateOf(false) }
                    var isRunningCleanup by remember { mutableStateOf(false) }
                    
                    // Simulate button
                    OutlinedButton(
                        onClick = {
                            isSimulating = true
                            scope.launch {
                                try {
                                    val database = AppDatabase.getDatabase(context)
                                    val requestsRepo = ServiceRequestsRepository(
                                        dao = database.serviceRequestsDao(),
                                        remote = FirestoreRequestsDataSource(),
                                        storage = FirebaseStorageService()
                                    )
                                    val tasksRepo = ServiceTasksRepository(
                                        dao = database.serviceTasksDao(),
                                        remote = FirestoreTasksDataSource(),
                                        storage = FirebaseStorageService()
                                    )
                                    
                                    val testLogic = TestCleanupLogic(requestsRepo, tasksRepo)
                                    cleanupSimulation = testLogic.simulateCleanup(14)
                                    isSimulating = false
                                } catch (e: Exception) {
                                    cleanupSimulation = "Error: ${e.message}"
                                    isSimulating = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSimulating
                    ) {
                        if (isSimulating) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Simulate Cleanup (Test Mode)")
                    }
                    
                    // Manual trigger button
                    Button(
                        onClick = {
                            isRunningCleanup = true
                            WorkManager.getInstance(context)
                                .enqueue(OneTimeWorkRequestBuilder<CleanupWorker>().build())
                            scope.launch {
                                snackbarHostState.showSnackbar("Cleanup worker started. Check logs with tag: FT/CLEANUP")
                                kotlinx.coroutines.delay(2000)
                                isRunningCleanup = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isRunningCleanup,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary
                        )
                    ) {
                        if (isRunningCleanup) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onTertiary)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Run Cleanup Now")
                    }
                    
                    // Show simulation results
                    if (cleanupSimulation != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        "Simulation Results:",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    IconButton(
                                        onClick = { cleanupSimulation = null },
                                        modifier = Modifier.size(20.dp).offset(x = 8.dp, y = (-8).dp)
                                    ) {
                                        Icon(Icons.Default.Close, "Close", modifier = Modifier.size(16.dp))
                                    }
                                }
                                Text(
                                    cleanupSimulation!!,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Sign Out Button
            Button(
                onClick = {
                    scope.launch {
                        // Deactivate this device's push token BEFORE signing out,
                        // otherwise the token stays registered under this account and
                        // the next account on this device gets duplicate notifications
                        try {
                            com.example.fieldtechv20kc.notifications.NotificationHelper.deactivateCurrentToken()
                        } catch (e: Exception) {
                            android.util.Log.e("Settings", "Failed to deactivate token on sign-out", e)
                        }
                        com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Sign Out")
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
    
    // About Dialog
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = {
                Text("About NC Field Tech")
            },
            text = {
                Column {
                    Text("Version: ${BuildConfig.VERSION_NAME}")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("NC Field Tech is a professional field service reporting application designed for technicians and service providers.")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Created by: Kim Cordina")
                    Text("Date: 23.09.25")
                    Text("Contact: kim@ncordina.com")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Features:")
                    Text("• Create detailed field reports")
                    Text("• Capture photos and signatures")
                    Text("• Generate PDF reports")
                    Text("• Manage client information")
                    Text("• Export and share reports")
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "This App was created and developed by Dr Kim Cordina, and if you dont use it properly hazin ikun ghalik!",
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showAboutDialog = false }
                ) {
                    Text("OK")
                }
            }
        )
    }
    
    // Theme Selection Dialog
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = {
                Text("Choose Theme")
            },
            text = {
                Column {
                    Text("Select your preferred theme:")
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Light Theme Option
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (!settings.isDarkMode) 
                                MaterialTheme.colorScheme.primaryContainer 
                            else MaterialTheme.colorScheme.surface
                        ),
                        onClick = {
                            settingsManager.updateDarkMode(false)
                            showThemeDialog = false
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Palette,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "Light Mode",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (!settings.isDarkMode) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Dark Theme Option
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (settings.isDarkMode) 
                                MaterialTheme.colorScheme.primaryContainer 
                            else MaterialTheme.colorScheme.surface
                        ),
                        onClick = {
                            settingsManager.updateDarkMode(true)
                            showThemeDialog = false
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Palette,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "Dark Mode",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (settings.isDarkMode) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showThemeDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Color Picker Dialog
    if (showColorPickerDialog) {
        AlertDialog(
            onDismissRequest = { showColorPickerDialog = false },
            title = {
                Text("Choose Accent Color")
            },
            text = {
                Column {
                    Text("Select your preferred accent color:")
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    val colors = listOf(
                        "#1976D2", // Blue
                        "#388E3C", // Green
                        "#F57C00", // Orange
                        "#D32F2F", // Red
                        "#7B1FA2", // Purple
                        "#00796B", // Teal
                        "#5D4037", // Brown
                        "#455A64", // Blue Grey
                        "#E91E63", // Pink
                        "#FF5722", // Deep Orange
                        "#795548", // Brown
                        "#607D8B"  // Blue Grey
                    )
                    
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(colors) { color ->
                            val isSelected = settings.accentColor == color
                            Card(
                                modifier = Modifier.size(48.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(android.graphics.Color.parseColor(color))
                                ),
                                onClick = {
                                    settingsManager.updateAccentColor(color)
                                    showColorPickerDialog = false
                                }
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.ColorLens,
                                            contentDescription = "Selected",
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showColorPickerDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
    
}

@Composable
fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    textColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = androidx.compose.ui.graphics.Color.Transparent
        ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = textColor
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = textColor
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SyncStatusCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val outbox = remember {
        try {
            OutboxRepository.get()
        } catch (e: Exception) {
            val database = AppDatabase.getDatabase(context)
            OutboxRepository.init(database)
            OutboxRepository.get()
        }
    }
    val pending by outbox.observePendingCount().collectAsState(initial = 0)
    
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<com.example.fieldtechv20kc.utils.CloudSelfTestResult?>(null) }
    
    var scanning by remember { mutableStateOf(false) }
    var scanResult by remember { mutableStateOf<String?>(null) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.CloudSync,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Sync Status",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            
            Text(
                text = if (pending == 0) "All synced" else "$pending pending upload${if (pending > 1) "s" else ""}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            
            // Action buttons - Row 1
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (pending > 0) {
                    TextButton(
                        onClick = {
                            com.example.fieldtechv20kc.utils.OutboxWorkHelpers.kickNow(context)
                        }
                    ) {
                        Icon(
                            Icons.Filled.CloudSync,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Retry all")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                
                TextButton(
                    enabled = !scanning,
                    onClick = {
                        scanning = true
                        scope.launch {
                            try {
                                val database = AppDatabase.getDatabase(context)
                                val reportRepo = ReportRepository(
                                    reportDao = database.reportDao(),
                                    clientDao = database.clientDao(),
                                    photoDao = database.photoDao()
                                )
                                val res = com.example.fieldtechv20kc.usecases.EnqueueUnsyncedReports(
                                    reportRepo = reportRepo,
                                    outbox = outbox
                                ).run(days = 365)
                                
                                com.example.fieldtechv20kc.utils.OutboxWorkHelpers.kickNow(context)
                                
                                scanResult = "Scanned ${res.scannedReports} reports\n" +
                                        "Enqueued: PDFs=${res.pdfJobs}, meta=${res.metaJobs}"
                            } catch (e: Exception) {
                                scanResult = "Error: ${e.message}"
                                android.util.Log.e("FT/SETTINGS", "Scan failed", e)
                            } finally {
                                scanning = false
                            }
                        }
                    }
                ) {
                    Text(if (scanning) "Scanning…" else "Scan & enqueue unsynced")
                }
            }
            
            // Action buttons - Row 2
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Force retry all failed jobs button
                var retrying by remember { mutableStateOf(false) }
                var retryResult by remember { mutableStateOf<String?>(null) }
                
                TextButton(
                    enabled = !retrying,
                    onClick = {
                        retrying = true
                        scope.launch {
                            try {
                                val database = AppDatabase.getDatabase(context)
                                
                                // Un-quarantine all quarantined jobs first
                                val quarantinedJobs = database.outboxDao().getQuarantinedJobs()
                                quarantinedJobs.forEach { job ->
                                    database.outboxDao().unquarantine(job.id)
                                }
                                
                                // Reset attempt count on all active jobs
                                val resetCount = database.outboxDao().resetAllAttempts()
                                
                                // Kick the worker
                                com.example.fieldtechv20kc.utils.OutboxWorkHelpers.kickNow(context)
                                
                                val totalCount = quarantinedJobs.size + resetCount
                                retryResult = "Un-quarantined ${quarantinedJobs.size}, reset $resetCount failed jobs, and kicked worker"
                                android.util.Log.d("FT/SETTINGS", retryResult!!)
                            } catch (e: Exception) {
                                retryResult = "Error: ${e.message}"
                                android.util.Log.e("FT/SETTINGS", "Force retry failed", e)
                            } finally {
                                retrying = false
                            }
                        }
                    }
                ) {
                    Text(if (retrying) "Retrying…" else "Force Retry All Failed Jobs")
                }
                
                // Show result as a snackbar
                LaunchedEffect(retryResult) {
                    retryResult?.let {
                        scope.launch {
                            androidx.compose.material3.SnackbarHostState().showSnackbar(it)
                        }
                        retryResult = null
                    }
                }
                
                TextButton(
                    enabled = !testing,
                    onClick = {
                        testing = true
                        scope.launch {
                            val r = com.example.fieldtechv20kc.utils.CloudSelfTest.run(context)
                            testResult = r
                            testing = false
                        }
                    }
                ) {
                    Text(if (testing) "Testing…" else "Run cloud self-test")
                }
            }
        }
    }
    
    // Self-test result dialog
    if (testResult != null) {
        AlertDialog(
            onDismissRequest = { testResult = null },
            title = { 
                Text(
                    if (testResult!!.ok) "Cloud self-test: PASS ✅" else "Cloud self-test: FAIL ❌",
                    style = MaterialTheme.typography.titleLarge
                ) 
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    testResult!!.steps.forEach { step ->
                        Text(
                            text = step,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { testResult = null }) {
                    Text("OK")
                }
            }
        )
    }
    
    // Scan result dialog
    if (scanResult != null) {
        AlertDialog(
            onDismissRequest = { scanResult = null },
            title = { Text("Backfill queued") },
            text = { Text(scanResult!!) },
            confirmButton = {
                TextButton(onClick = { scanResult = null }) {
                    Text("OK")
                }
            }
        )
    }
}

// Helper Composable for Diagnostic Row
@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
    }
}

// Settings Item Component
@Composable
fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    badge: Int? = null,
    badgeColor: Color? = null,
    tintColor: Color? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = tintColor ?: MaterialTheme.colorScheme.primary
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (badge != null && badgeColor != null) {
                Badge(
                    containerColor = badgeColor
                ) {
                    Text(badge.toString())
                }
            }
            Icon(
                Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// Sync Health Dialog
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncHealthDialog(
    database: com.example.fieldtechv20kc.data.database.AppDatabase,
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    onDismiss: () -> Unit
) {
    val activeJobsCount by database.outboxDao().observeActiveCount().collectAsState(initial = 0)
    val quarantinedJobsCount by database.outboxDao().observeQuarantinedCount().collectAsState(initial = 0)
    val errorCount by database.errorLogDao().observeErrorCount().collectAsState(initial = 0)
    var isForceSync by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sync Health") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Metrics
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = activeJobsCount.toString(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text("Pending", style = MaterialTheme.typography.bodySmall)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = quarantinedJobsCount.toString(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (quarantinedJobsCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                        Text("Failed", style = MaterialTheme.typography.bodySmall)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = errorCount.toString(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (errorCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                        Text("Errors", style = MaterialTheme.typography.bodySmall)
                    }
                }
                
                HorizontalDivider()
                
                Text(
                    text = "Note: Error logs older than 7 days are automatically cleaned up.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // Actions
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            isForceSync = true
                            scope.launch {
                                try {
                                    com.example.fieldtechv20kc.utils.OutboxWorkHelpers.kickNow(context)
                                    kotlinx.coroutines.delay(1000)
                                    isForceSync = false
                                } catch (e: Exception) {
                                    isForceSync = false
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isForceSync
                    ) {
                        if (isForceSync) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Force Sync", style = MaterialTheme.typography.labelSmall)
                    }
                    
                    if (quarantinedJobsCount > 0) {
                        Button(
                            onClick = {
                                scope.launch {
                                    try {
                                        val quarantinedJobs = database.outboxDao().getQuarantinedJobs()
                                        quarantinedJobs.forEach { job ->
                                            database.outboxDao().unquarantine(job.id)
                                        }
                                        com.example.fieldtechv20kc.utils.OutboxWorkHelpers.kickNow(context)
                                    } catch (e: Exception) {
                                        com.example.fieldtechv20kc.utils.FTLog.e("SETTINGS", "Failed to retry", e)
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Retry Failed", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                
                // Clear old errors button
                if (errorCount > 0) {
                    HorizontalDivider()
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                try {
                                    // Clear errors older than 1 day
                                    database.errorLogDao().deleteOlderThan(1)
                                    com.example.fieldtechv20kc.utils.FTLog.i("SETTINGS", "Cleared old error logs")
                                } catch (e: Exception) {
                                    com.example.fieldtechv20kc.utils.FTLog.e("SETTINGS", "Failed to clear errors", e)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Clear Old Errors (>1 day)", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

// Push Diagnostics Dialog
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PushDiagnosticsDialog(
    usersRemote: com.example.fieldtechv20kc.data.remote.firestore.UsersRemote,
    currentToken: String?,
    activeTokenCount: Int,
    notificationsEnabled: Boolean,
    snackbarHostState: SnackbarHostState,
    scope: kotlinx.coroutines.CoroutineScope,
    onTokenRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Push Diagnostics") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Token Info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("FCM Token", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Text(
                            text = currentToken?.take(20) ?: "No token",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1
                        )
                    }
                    Text(
                        text = "$activeTokenCount active",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                
                HorizontalDivider()
                
                // Actions
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onTokenRefresh,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Refresh", style = MaterialTheme.typography.labelSmall)
                    }
                    
                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    val functions = com.google.firebase.functions.FirebaseFunctions.getInstance()
                                    val result = functions.getHttpsCallable("sendTestToUid").call().await()
                                    android.util.Log.d("Settings", "Test notification: $result")
                                    snackbarHostState.showSnackbar("Test notification sent!")
                                } catch (e: Exception) {
                                    android.util.Log.e("Settings", "Error sending test", e)
                                    snackbarHostState.showSnackbar("Error: ${e.message}")
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Test", style = MaterialTheme.typography.labelSmall)
                    }
                }
                
                HorizontalDivider()
                
                // Debug Actions
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                try {
                                    usersRemote.setNotificationsEnabled(false)
                                    val profile = usersRemote.getProfile()
                                    android.util.Log.d("Settings", "Profile after force disable: $profile")
                                    snackbarHostState.showSnackbar("Notifications FORCE DISABLED")
                                } catch (e: Exception) {
                                    android.util.Log.e("Settings", "Error force disabling", e)
                                    snackbarHostState.showSnackbar("Error: ${e.message}")
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Force Disable", style = MaterialTheme.typography.labelSmall)
                    }
                    
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                try {
                                    val profile = usersRemote.getProfile()
                                    android.util.Log.d("Settings", "Current profile: $profile")
                                    val msg = "Role: ${profile?.role}\nNotifications: ${profile?.notificationsEnabled}"
                                    snackbarHostState.showSnackbar(msg)
                                } catch (e: Exception) {
                                    android.util.Log.e("Settings", "Error checking", e)
                                    snackbarHostState.showSnackbar("Error: ${e.message}")
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Check Firestore", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

// Notification Reset Dialog
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationResetDialog(
    usersRemote: com.example.fieldtechv20kc.data.remote.firestore.UsersRemote,
    currentToken: String?,
    role: String,
    assignedToName: String?,
    auth: com.google.firebase.auth.FirebaseAuth,
    snackbarHostState: SnackbarHostState,
    scope: kotlinx.coroutines.CoroutineScope,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("Reset All Notifications?") },
        text = { 
            Text("This will:\n\n• Disable all notifications\n• Deactivate all FCM tokens\n• Reset your notification settings\n• Force a clean slate\n\nThis action cannot be undone.")
        },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        try {
                            // 1. Disable notifications
                            usersRemote.setNotificationsEnabled(false)
                            
                            // 2. Deactivate all tokens
                            if (currentToken != null) {
                                usersRemote.deactivateToken(currentToken)
                            }
                            
                            // 3. Force update role to ensure fresh state
                            usersRemote.upsertProfile(
                                displayName = auth.currentUser?.email,
                                assignedToName = assignedToName,
                                role = role,
                                notificationsEnabled = false
                            )
                            
                            // 4. Reload profile to verify
                            val profile = usersRemote.getProfile()
                            android.util.Log.d("Settings", "Profile after RESET: $profile")
                            
                            snackbarHostState.showSnackbar("✅ Notifications FULLY RESET!")
                            com.example.fieldtechv20kc.utils.FTLog.i("SETTINGS", "User performed notification reset")
                            
                            onSuccess()
                            onDismiss()
                        } catch (e: Exception) {
                            android.util.Log.e("Settings", "Error resetting", e)
                            snackbarHostState.showSnackbar("Error: ${e.message}")
                            com.example.fieldtechv20kc.utils.FTLog.e("SETTINGS", "Failed to reset notifications", e)
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("RESET NOW")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
