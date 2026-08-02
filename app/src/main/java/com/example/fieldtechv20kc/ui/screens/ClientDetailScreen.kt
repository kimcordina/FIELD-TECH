package com.example.fieldtechv20kc.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.fieldtechv20kc.data.model.PinStatus
import com.example.fieldtechv20kc.data.model.ServiceTask
import com.example.fieldtechv20kc.navigation.Screen
import com.example.fieldtechv20kc.utils.DateUtils
import com.example.fieldtechv20kc.utils.SettingsManager
import com.example.fieldtechv20kc.viewmodel.ClientPinsViewModel
import com.example.fieldtechv20kc.viewmodel.ClientsViewModel
import com.example.fieldtechv20kc.viewmodel.ServiceTasksViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientDetailScreen(
    clientId: String,
    navController: NavController,
    viewModel: ClientsViewModel,
    tasksViewModel: ServiceTasksViewModel? = null
) {
    val client by viewModel.observeClient(clientId).collectAsState(initial = null)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val auth = remember { FirebaseAuth.getInstance() }
    val currentUserEmail = auth.currentUser?.email ?: "Unknown User"
    
    // Pins ViewModel
    val pinsViewModel = remember { ClientPinsViewModel(context) }
    val pins by pinsViewModel.observePins(clientId).collectAsState(initial = emptyList())
    
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showAssignDialog by remember { mutableStateOf(false) }
    var showAddPinDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Client Details") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, "Menu")
                        }
                        
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Edit") },
                                onClick = {
                                    showMenu = false
                                    navController.navigate(Screen.ClientEdit.createRoute(clientId))
                                },
                                leadingIcon = { Icon(Icons.Default.Edit, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                onClick = {
                                    showMenu = false
                                    showDeleteDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.Delete, null) }
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (client == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Client name header with locality + island marker
                Column {
                    Text(
                        text = client!!.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (!client!!.locality.isNullOrBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = client!!.locality!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            com.example.fieldtechv20kc.ui.components.IslandBadge(client!!.locality)
                        }
                    }
                }
                
                // Contact Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Contact Information",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        // Client Code
                        if (!client!!.clientCode.isNullOrBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Tag, 
                                    null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(
                                        "Client Code",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        client!!.clientCode!!,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                        
                        if (client!!.address.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(
                                    Icons.Default.LocationOn, 
                                    null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    client!!.address,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                        
                        // Last service date
                        if (client!!.lastServiceDate != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.CalendarToday, 
                                    null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(
                                        "Last Service",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        DateUtils.formatDate(client!!.lastServiceDate!!),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Products/Equipment Card
                if (!client!!.productsEquipment.isNullOrBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "Products/Equipment",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(
                                    Icons.Default.Build,
                                    null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    client!!.productsEquipment!!,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
                
                // Salesman Card
                if (!client!!.salesman.isNullOrBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "Salesman",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(
                                    Icons.Default.Person,
                                    null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    client!!.salesman!!,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
                
                // Pins Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Location Pins",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = { showAddPinDialog = true }) {
                                Icon(Icons.Default.Add, "Add Pin")
                            }
                        }
                        
                        if (pins.isEmpty()) {
                            Text(
                                "No location pins yet. Add one to enable navigation.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            pins.forEach { pin ->
                                Spacer(Modifier.height(8.dp))
                                PinListItem(
                                    pin = pin,
                                    onNavigate = {
                                        val intent = if (pin.latitude != null && pin.longitude != null) {
                                            Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${pin.latitude},${pin.longitude}"))
                                        } else if (pin.sourceUrl != null) {
                                            Intent(Intent.ACTION_VIEW, Uri.parse(pin.sourceUrl))
                                        } else {
                                            null
                                        }
                                        intent?.let { context.startActivity(it) }
                                    },
                                    onSetPrimary = {
                                        pinsViewModel.setPrimary(clientId, pin.id) { result ->
                                            result.onSuccess {
                                                scope.launch {
                                                    snackbarHostState.showSnackbar("Primary pin updated")
                                                }
                                            }
                                        }
                                    },
                                    onDelete = {
                                        pinsViewModel.deletePin(pin.id, clientId) { result ->
                                            result.onSuccess {
                                                scope.launch {
                                                    snackbarHostState.showSnackbar("Pin deleted")
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                
                // Notes Card
                if (!client!!.notes.isNullOrBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Notes",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(client!!.notes!!)
                        }
                    }
                }
                
                // Assign Job Button
                if (tasksViewModel != null) {
                    Button(
                        onClick = { showAssignDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Assignment, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Assign Job", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
    
    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Client") },
            text = { Text("Are you sure you want to delete this client?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteClient(clientId)
                        showDeleteDialog = false
                        navController.popBackStack()
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Assign Job Dialog - Use the unified colored version
    if (showAssignDialog && tasksViewModel != null) {
        val currentClient = client
        if (currentClient != null) {
            UnifiedTaskAssignmentDialog(
                client = currentClient,
                onDismiss = { showAssignDialog = false },
                onAssign = { technician, voiceUri, notes, photoUris ->
                    scope.launch {
                        tasksViewModel.upsert(
                            ServiceTask(
                                clientId = clientId,
                                assignedToName = technician,
                                scheduledDate = DateUtils.getTodayMidnight(),
                                title = "Service visit",
                                notes = notes.takeIf { it.isNotBlank() },
                                voiceNoteUri = voiceUri,
                                photoUris = photoUris,
                                createdByName = currentUserEmail
                            )
                        )
                        
                        snackbarHostState.showSnackbar("Job assigned successfully!")
                        showAssignDialog = false
                    }
                }
            )
        }
    }
    
    // Add Pin Dialog
    if (showAddPinDialog) {
        AddPinDialog(
            clientId = clientId,
            pinsViewModel = pinsViewModel,
            onDismiss = { showAddPinDialog = false },
            onSuccess = {
                showAddPinDialog = false
                scope.launch {
                    snackbarHostState.showSnackbar("Pin added successfully")
                }
            }
        )
    }
}

@Composable
fun PinListItem(
    pin: com.example.fieldtechv20kc.data.model.ClientPinEntity,
    onNavigate: () -> Unit,
    onSetPrimary: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (pin.isPrimary) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = pin.label,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (pin.isPrimary) FontWeight.Bold else FontWeight.Normal
                        )
                        if (pin.isPrimary) {
                            Spacer(Modifier.width(8.dp))
                            AssistChip(
                                onClick = {},
                                label = { Text("Primary", style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.height(24.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row {
                        AssistChip(
                            onClick = {},
                            label = { 
                                Text(
                                    if (pin.status == PinStatus.VERIFIED) "Verified" else "Seeded",
                                    style = MaterialTheme.typography.labelSmall
                                ) 
                            },
                            modifier = Modifier.height(24.dp),
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (pin.status == PinStatus.VERIFIED)
                                    MaterialTheme.colorScheme.tertiaryContainer
                                else
                                    MaterialTheme.colorScheme.secondaryContainer
                            )
                        )
                        if (pin.latitude != null && pin.longitude != null) {
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "${pin.latitude}, ${pin.longitude}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onNavigate,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Map, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Navigate", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
                
                if (!pin.isPrimary) {
                    OutlinedButton(
                        onClick = onSetPrimary,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Star, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Set Primary", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
                
                IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPinDialog(
    clientId: String,
    pinsViewModel: ClientPinsViewModel,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var label by remember { mutableStateOf("") }
    var pasteText by remember { mutableStateOf("") }
    var showPasteOption by remember { mutableStateOf(true) }
    val isLoadingLocation by pinsViewModel.isLoadingLocation.collectAsState()
    
    // Location permission launcher
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            pinsViewModel.addFromCurrentLocation(context, clientId, label) { result ->
                result.onSuccess { onSuccess() }
                    .onFailure { 
                        scope.launch {
                            // Show error
                        }
                    }
            }
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Location Pin") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    placeholder = { Text("e.g., Front door, Main entrance") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                if (showPasteOption) {
                    OutlinedTextField(
                        value = pasteText,
                        onValueChange = { pasteText = it },
                        label = { Text("Paste link or coordinates") },
                        placeholder = { Text("35.8989, 14.5146 or Google Maps link") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                    
                    Button(
                        onClick = {
                            pinsViewModel.addFromPaste(clientId, label, pasteText) { result ->
                                result.onSuccess { onSuccess() }
                                    .onFailure {
                                        scope.launch {
                                            // Show error
                                        }
                                    }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = pasteText.isNotBlank()
                    ) {
                        Icon(Icons.Default.ContentPaste, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add from Paste")
                    }
                    
                    Text(
                        "— OR —",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
                
                Button(
                    onClick = {
                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoadingLocation
                ) {
                    if (isLoadingLocation) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.MyLocation, null)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(if (isLoadingLocation) "Getting location..." else "Use my current location")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}


