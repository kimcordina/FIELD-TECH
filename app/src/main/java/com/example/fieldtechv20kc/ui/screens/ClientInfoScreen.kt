package com.example.fieldtechv20kc.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.fieldtechv20kc.data.model.Client
import com.example.fieldtechv20kc.navigation.Screen
import com.example.fieldtechv20kc.utils.SettingsManager
import com.example.fieldtechv20kc.viewmodel.ReportViewModel
import kotlinx.coroutines.launch

// Maltese Localities List (Malta first, then Gozo)
val malteseLocalities = listOf(
    "Attard",
    "Balzan",
    "Birgu (Vittoriosa)",
    "Birkirkara",
    "Birzebbuga",
    "Bormla (Cospicua)",
    "Bugibba",
    "Dingli",
    "Fgura",
    "Floriana",
    "Gharghur",
    "Ghaxaq",
    "Gudja",
    "Gzira",
    "Hamrun",
    "Iklin",
    "Kalkara",
    "Kirkop",
    "Lija",
    "Luqa",
    "Marsa",
    "Marsaskala",
    "Marsaxlokk",
    "Mdina",
    "Mellieha",
    "Mgarr",
    "Mosta",
    "Mqabba",
    "Msida",
    "Mtarfa",
    "Naxxar",
    "Paceville",
    "Paola (Rahal Gdid)",
    "Pembroke",
    "Pieta",
    "Qawra",
    "Qormi",
    "Qrendi",
    "Rabat",
    "Safi",
    "San Gwann",
    "Santa Lucija",
    "Santa Venera",
    "Senglea (Isla)",
    "Siggiewi",
    "Sliema",
    "St Julian's (San Giljan)",
    "St Paul's Bay (San Pawl il-Bahar)",
    "Swieqi",
    "Tarxien",
    "Ta' Xbiex",
    "Valletta",
    "Xghajra",
    "Zabbar",
    "Zebbug (Malta)",
    "Zejtun",
    "Zurrieq",
    // Gozo
    "Fontana (Gozo)",
    "Ghajnsielem (Gozo)",
    "Gharb (Gozo)",
    "Ghasri (Gozo)",
    "Kercem (Gozo)",
    "Marsalforn (Gozo)",
    "Mgarr (Gozo)",
    "Munxar (Gozo)",
    "Nadur (Gozo)",
    "Qala (Gozo)",
    "San Lawrenz (Gozo)",
    "Sannat (Gozo)",
    "Victoria/Rabat (Gozo)",
    "Xaghra (Gozo)",
    "Xewkija (Gozo)",
    "Xlendi (Gozo)",
    "Zebbug (Gozo)"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientInfoScreen(
    navController: NavController,
    viewModel: ReportViewModel
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val settings by settingsManager.settings.collectAsState()
    
    // Use ViewModel state instead of local state
    val selectedClientId by viewModel.selectedClientId.collectAsState()
    val selectedClientName by viewModel.selectedClientName.collectAsState()
    val currentClient by viewModel.currentClient.collectAsState()
    val name by viewModel.clientName.collectAsState()
    val locality by viewModel.clientLocality.collectAsState()
    val legalName by viewModel.clientLegalName.collectAsState()
    val companyNumber by viewModel.clientCompanyNumber.collectAsState()
    val address by viewModel.clientAddress.collectAsState()
    
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    // Local state for UI interactions
    var customLocality by remember { mutableStateOf("") }
    var showLocalityDropdown by remember { mutableStateOf(false) }
    var isCustomLocality by remember { mutableStateOf(false) }
    
    // Job completion sheet state
    var showTaskSheet by remember { mutableStateOf(false) }
    var pendingTasks by remember { mutableStateOf<List<com.example.fieldtechv20kc.data.model.ServiceTask>>(emptyList()) }
    
    // Get ServiceTasksRepository
    val app = context.applicationContext as com.example.fieldtechv20kc.FieldTechApplication
    val serviceTasksRepository = remember { app.tasksRepository }
    
    // Check if started from a job (linkedTaskId already set means we came from a job)
    val linkedTaskId by viewModel.linkedTaskId.collectAsState()
    val startedFromTask = linkedTaskId != null
    
    // Handle client selection result from picker
    LaunchedEffect(navController) {
        val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle
        savedStateHandle?.getStateFlow("selectedClientId", "")?.collect { id ->
            if (id.isNotEmpty()) {
                val name = savedStateHandle.get<String>("selectedClientName")
                viewModel.setSelectedClient(id, name)
                // Clear one-time results
                savedStateHandle.remove<String>("selectedClientId")
                savedStateHandle.remove<String>("selectedClientName")
            }
        }
    }
    
    // Initialize fields with settings defaults if empty
    LaunchedEffect(Unit) {
        if (name.isEmpty()) {
            // Initialize with empty values - will be populated by user input
        }
        if (legalName.isEmpty() && settings.clientLegalName.isNotEmpty()) {
            viewModel.setClientLegalName(settings.clientLegalName)
        }
        if (companyNumber.isEmpty() && settings.clientCompanyNumber.isNotEmpty()) {
            viewModel.setClientCompanyNumber(settings.clientCompanyNumber)
        }
        if (address.isEmpty() && settings.clientAddress.isNotEmpty()) {
            viewModel.setClientAddress(settings.clientAddress)
        }
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Client Information",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Client Information",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Please enter the client details below",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            
            // Client Selection Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (selectedClientId == null) {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Client *",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (selectedClientId == null) {
                        // No client selected
                        Button(
                            onClick = { navController.navigate("report/clientPicker") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Default.Person, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Select Client")
                        }
                    } else {
                        // Client selected
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    selectedClientName ?: "Selected client",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        currentClient?.locality?.takeIf { it.isNotBlank() } ?: "Client selected",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    com.example.fieldtechv20kc.ui.components.IslandBadge(currentClient?.locality)
                                }
                            }
                            Row {
                                TextButton(onClick = { navController.navigate("report/clientPicker") }) {
                                    Text("Change")
                                }
                                TextButton(onClick = { viewModel.clearSelectedClient() }) {
                                    Text("Clear")
                                }
                            }
                        }
                    }
                }
            }
            
            // Locality Dropdown with Custom Option
            Column {
                Text(
                    text = "Locality *",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                if (isCustomLocality) {
                    // Custom locality input
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = customLocality,
                            onValueChange = { 
                                customLocality = it
                                viewModel.setClientLocality(it)
                            },
                            label = { Text("Enter custom locality") },
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        TextButton(
                            onClick = {
                                isCustomLocality = false
                                viewModel.setClientLocality("")
                                customLocality = ""
                            }
                        ) {
                            Text("Cancel")
                        }
                    }
                } else {
                    // Dropdown for predefined localities
                    ExposedDropdownMenuBox(
                        expanded = showLocalityDropdown,
                        onExpandedChange = { showLocalityDropdown = !showLocalityDropdown }
                    ) {
                        OutlinedTextField(
                            value = locality,
                            onValueChange = { },
                            readOnly = true,
                            label = { Text("Select locality") },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = "Dropdown"
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )
                        
                        ExposedDropdownMenu(
                            expanded = showLocalityDropdown,
                            onDismissRequest = { showLocalityDropdown = false }
                        ) {
                            // Add custom option at the top
                            DropdownMenuItem(
                                text = { Text("+ Add Custom Locality") },
                                onClick = {
                                    isCustomLocality = true
                                    showLocalityDropdown = false
                                }
                            )
                            
                            Divider()
                            
                            // List all Maltese localities
                            malteseLocalities.forEach { localityName ->
                                DropdownMenuItem(
                                    text = { Text(localityName) },
                                    onClick = {
                                        viewModel.setClientLocality(localityName)
                                        showLocalityDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
            
            // Conditional fields based on settings
            if (settings.clientLegalNameEnabled) {
                OutlinedTextField(
                    value = legalName,
                    onValueChange = { viewModel.setClientLegalName(it) },
                    label = { Text("Client Company Legal Name") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
            }
            
            if (settings.clientCompanyNumberEnabled) {
                OutlinedTextField(
                    value = companyNumber,
                    onValueChange = { viewModel.setClientCompanyNumber(it) },
                    label = { Text("Client Company Number") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
            }
            
            if (settings.clientAddressEnabled) {
                OutlinedTextField(
                    value = address,
                    onValueChange = { viewModel.setClientAddress(it) },
                    label = { Text("Client Address") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
            }
            
            Button(
                onClick = {
                    if (!viewModel.canProceedWithClient()) {
                        scope.launch {
                            snackbarHostState.showSnackbar("Please select a client to continue.")
                        }
                        return@Button
                    }
                    
                    if (locality.isBlank()) {
                        scope.launch {
                            snackbarHostState.showSnackbar("Please select a locality to continue.")
                        }
                        return@Button
                    }
                    
                    // If started from a job, proceed directly (no popup)
                    if (startedFromTask) {
                        navController.navigate(Screen.JobType.route)
                        return@Button
                    }
                    
                    // Not started from a job - check for pending jobs
                    scope.launch {
                        try {
                            val tasks = serviceTasksRepository.getPendingByClientOnce(selectedClientId!!)
                            if (tasks.isEmpty()) {
                                // No pending jobs, proceed normally
                                navController.navigate(Screen.JobType.route)
                            } else {
                                // Show job completion sheet
                                pendingTasks = tasks
                                showTaskSheet = true
                            }
                        } catch (e: Exception) {
                            // On error, just proceed (don't block user)
                            navController.navigate(Screen.JobType.route)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                // Require the client OBJECT to be loaded, not just a leftover ID -
                // otherwise the technician can walk the whole flow and hit an
                // unfixable "Client data is missing" error at the signature step
                enabled = selectedClientId != null && currentClient != null && locality.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    "Continue",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
    
    // Job completion sheet
    if (showTaskSheet && pendingTasks.isNotEmpty()) {
        com.example.fieldtechv20kc.ui.components.CompleteTaskSheet(
            clientName = selectedClientName ?: "Client",
            tasks = pendingTasks,
            onSelectTaskAndConfirm = { taskIdOrNull ->
                viewModel.setLinkedTaskId(taskIdOrNull)
                showTaskSheet = false
                navController.navigate(Screen.JobType.route)
            },
            onViewTask = { taskId ->
                showTaskSheet = false
                // Pop back to the start of the report flow, then navigate to jobs
                // This ensures we're in the main nav graph where jobs route exists
                navController.popBackStack(Screen.ClientInfo.route, inclusive = true)
                // Navigate to the jobs tab with the specific job
                navController.navigate(Screen.Tasks.route) {
                    launchSingleTop = true
                }
                // Note: The job detail navigation would need to be handled by the Jobs screen
                // For now, user will land on Jobs tab and can find their job there
            },
            onDismiss = { showTaskSheet = false }
        )
    }
}
