package com.example.fieldtechv20kc.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.fieldtechv20kc.data.model.CustomJobType
import com.example.fieldtechv20kc.data.model.JobType
import com.example.fieldtechv20kc.utils.SettingsManager
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobTypeSettingsScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val settings by settingsManager.settings.collectAsState()
    
    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editingJobType by remember { mutableStateOf<CustomJobType?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var jobTypeToDelete by remember { mutableStateOf<CustomJobType?>(null) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Job Type Settings",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add New Job Type")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = "Customize Job Types",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Text(
                text = "Edit existing job types or create new ones. Customize titles and legal text for each job type.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            
            // Default Job Types Section
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Default Job Types",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    JobType.values().forEach { jobType ->
                        DefaultJobTypeItem(
                            jobType = jobType,
                            settings = settings,
                            settingsManager = settingsManager,
                            onEditClick = {
                                editingJobType = CustomJobType(
                                    id = jobType.name,
                                    displayName = settings.defaultJobTypeTitles[jobType.name] ?: jobType.displayName,
                                    legalTitle = settings.defaultJobTypeLegalTitles[jobType.name] ?: getDefaultLegalTitle(jobType),
                                    legalText = settings.defaultJobTypeLegalTexts[jobType.name] ?: getDefaultLegalText(jobType),
                                    isDefault = true
                                )
                                showEditDialog = true
                            }
                        )
                        
                        if (jobType != JobType.values().last()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }
                }
            }
            
            // Custom Job Types Section
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Custom Job Types",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        
                        TextButton(
                            onClick = { showAddDialog = true }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add New")
                        }
                    }
                    
                    if (settings.customJobTypes.isEmpty()) {
                        Text(
                            text = "No custom job types yet. Tap 'Add New' to create one.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    } else {
                        Spacer(modifier = Modifier.height(16.dp))
                        settings.customJobTypes.forEach { customJobType ->
                            CustomJobTypeItem(
                                customJobType = customJobType,
                                onEditClick = {
                                    editingJobType = customJobType
                                    showEditDialog = true
                                },
                                onDeleteClick = {
                                    jobTypeToDelete = customJobType
                                    showDeleteDialog = true
                                }
                            )
                            
                            if (customJobType != settings.customJobTypes.last()) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
    
    // Add New Job Type Dialog
    if (showAddDialog) {
        AddEditJobTypeDialog(
            onDismiss = { showAddDialog = false },
            onSave = { customJobType ->
                settingsManager.addCustomJobType(customJobType)
                showAddDialog = false
            }
        )
    }
    
    // Edit Job Type Dialog
    if (showEditDialog && editingJobType != null) {
        AddEditJobTypeDialog(
            initialJobType = editingJobType,
            onDismiss = { 
                showEditDialog = false
                editingJobType = null
            },
            onSave = { customJobType ->
                if (customJobType.isDefault) {
                    // Update default job type
                    val jobType = JobType.valueOf(customJobType.id)
                    settingsManager.updateDefaultJobTypeTitle(jobType, customJobType.displayName)
                    settingsManager.updateDefaultJobTypeLegalTitle(jobType, customJobType.legalTitle)
                    settingsManager.updateDefaultJobTypeLegalText(jobType, customJobType.legalText)
                } else {
                    // Update custom job type
                    settingsManager.updateCustomJobType(customJobType)
                }
                showEditDialog = false
                editingJobType = null
            }
        )
    }
    
    // Delete Confirmation Dialog
    if (showDeleteDialog && jobTypeToDelete != null) {
        AlertDialog(
            onDismissRequest = { 
                showDeleteDialog = false
                jobTypeToDelete = null
            },
            title = { Text("Delete Job Type") },
            text = { Text("Are you sure you want to delete '${jobTypeToDelete?.displayName}'? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        jobTypeToDelete?.let { settingsManager.deleteCustomJobType(it.id) }
                        showDeleteDialog = false
                        jobTypeToDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showDeleteDialog = false
                        jobTypeToDelete = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun DefaultJobTypeItem(
    jobType: JobType,
    settings: com.example.fieldtechv20kc.data.model.ReportSettings,
    settingsManager: SettingsManager,
    onEditClick: () -> Unit
) {
    val customTitle = settings.defaultJobTypeTitles[jobType.name] ?: jobType.displayName
    val customLegalTitle = settings.defaultJobTypeLegalTitles[jobType.name]
    val customLegalText = settings.defaultJobTypeLegalTexts[jobType.name]
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = customTitle,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            if (customLegalTitle != null || customLegalText != null) {
                Text(
                    text = "Customized",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        IconButton(onClick = onEditClick) {
            Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun CustomJobTypeItem(
    customJobType: CustomJobType,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = customJobType.displayName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Custom Job Type",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        IconButton(onClick = onEditClick) {
            Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(20.dp))
        }
        
        IconButton(onClick = onDeleteClick) {
            Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
fun AddEditJobTypeDialog(
    initialJobType: CustomJobType? = null,
    onDismiss: () -> Unit,
    onSave: (CustomJobType) -> Unit
) {
    var displayName by remember { mutableStateOf(initialJobType?.displayName ?: "") }
    var legalTitle by remember { mutableStateOf(initialJobType?.legalTitle ?: "") }
    var legalText by remember { mutableStateOf(initialJobType?.legalText ?: "") }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (initialJobType == null) "Add New Job Type" else "Edit Job Type",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Close")
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Form Fields
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text("Job Type Name *") },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    OutlinedTextField(
                        value = legalTitle,
                        onValueChange = { legalTitle = it },
                        label = { Text("Legal Title *") },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    OutlinedTextField(
                        value = legalText,
                        onValueChange = { legalText = it },
                        label = { Text("Legal Text *") },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(400.dp), // Much larger height for legal text
                        maxLines = 20,
                        minLines = 15
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Button(
                        onClick = {
                            if (displayName.isNotBlank() && legalTitle.isNotBlank() && legalText.isNotBlank()) {
                                val customJobType = CustomJobType(
                                    id = initialJobType?.id ?: UUID.randomUUID().toString(),
                                    displayName = displayName.trim(),
                                    legalTitle = legalTitle.trim(),
                                    legalText = legalText.trim(),
                                    isDefault = initialJobType?.isDefault ?: false
                                )
                                onSave(customJobType)
                            }
                        },
                        enabled = displayName.isNotBlank() && legalTitle.isNotBlank() && legalText.isNotBlank()
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

// Helper functions to get default legal text
private fun getDefaultLegalTitle(jobType: JobType): String {
    return when (jobType) {
        JobType.SERVICE_REPAIR -> "Service/Repair Authorisation & Acknowledgement (N. Cordina Marketing Ltd)"
        JobType.INSTALLATION_ON_LOAN -> "Loan Installation Terms & Acknowledgement (N. Cordina Marketing Ltd)"
        JobType.INSTALLATION_PURCHASED -> "Purchase Installation Terms & Acknowledgement (N. Cordina Marketing Ltd)"
    }
}

private fun getDefaultLegalText(jobType: JobType): String {
    return when (jobType) {
        JobType.SERVICE_REPAIR -> "I, the undersigned, hereby request and authorise N. Cordina Marketing Ltd..."
        JobType.INSTALLATION_ON_LOAN -> "I, the undersigned, hereby acknowledge the temporary loan..."
        JobType.INSTALLATION_PURCHASED -> "I, the undersigned, hereby acknowledge the purchase..."
    }
}
