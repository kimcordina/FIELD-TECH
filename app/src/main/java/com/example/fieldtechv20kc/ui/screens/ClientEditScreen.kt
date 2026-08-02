package com.example.fieldtechv20kc.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.fieldtechv20kc.data.model.ClientInput
import com.example.fieldtechv20kc.data.model.PinStatus
import com.example.fieldtechv20kc.data.repository.ClientPinsRepository
import com.example.fieldtechv20kc.viewmodel.ClientPinsViewModel
import com.example.fieldtechv20kc.viewmodel.ClientsViewModel
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientEditScreen(
    clientId: String?,
    navController: NavController,
    viewModel: ClientsViewModel
) {
    var name by remember { mutableStateOf("") }
    var clientCode by remember { mutableStateOf("") }
    var locality by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var mapsUrl by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var salesman by remember { mutableStateOf("") }
    var localityMenuExpanded by remember { mutableStateOf(false) }
    
    // Custom locality state
    var isCustomLocality by remember { mutableStateOf(false) }
    var customLocality by remember { mutableStateOf("") }
    
    // Products/Equipment checkboxes
    val productOptions = remember { listOf("2IN1", "RMW", "RINSE", "ECOMIX", "LAUNDRY", "HA25", "POOL", "OTHER") }
    var selectedProducts by remember { mutableStateOf(setOf<String>()) }
    
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    
    // Location pin state (new clients only - pin is saved right after the client is created)
    val pinsViewModel = remember { ClientPinsViewModel(context) }
    val isLoadingLocation by pinsViewModel.isLoadingLocation.collectAsState()
    var pendingPinLat by remember { mutableStateOf<Double?>(null) }
    var pendingPinLng by remember { mutableStateOf<Double?>(null) }
    var pendingPinStatus by remember { mutableStateOf<PinStatus?>(null) }
    var pendingPinSourceUrl by remember { mutableStateOf<String?>(null) }
    var pinPasteText by remember { mutableStateOf("") }
    
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            pinsViewModel.captureCurrentLocation(context) { result ->
                result.onSuccess { (lat, lng) ->
                    pendingPinLat = lat
                    pendingPinLng = lng
                    pendingPinStatus = PinStatus.VERIFIED
                    pendingPinSourceUrl = null
                }.onFailure { e ->
                    scope.launch {
                        snackbarHostState.showSnackbar(e.message ?: "Could not get location")
                    }
                }
            }
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("Location permission is required to set a pin")
            }
        }
    }
    
    // Maltese Localities List
    val localities = remember {
        listOf(
            // Malta
            "Attard", "Balzan", "Birgu (Vittoriosa)", "Birkirkara", "Birzebbuga",
            "Bormla (Cospicua)", "Bugibba", "Dingli", "Fgura", "Floriana",
            "Gharghur", "Ghaxaq", "Gudja", "Gzira", "Hamrun", "Iklin", "Kalkara",
            "Kirkop", "Lija", "Luqa", "Marsa", "Marsaskala", "Marsaxlokk", "Mdina",
            "Mellieha", "Mgarr", "Mosta", "Mqabba", "Msida", "Mtarfa", "Naxxar",
            "Paceville", "Paola (Rahal Gdid)", "Pembroke", "Pieta", "Qawra", "Qormi", "Qrendi", "Rabat",
            "Safi", "San Gwann", "Santa Lucija", "Santa Venera", "Senglea (Isla)",
            "Siggiewi", "Sliema", "St Julian's (San Giljan)", "St Paul's Bay (San Pawl il-Bahar)",
            "Swieqi", "Tarxien", "Ta' Xbiex", "Valletta", "Xghajra", "Zabbar",
            "Zebbug (Malta)", "Zejtun", "Zurrieq",
            // Gozo
            "Fontana (Gozo)", "Ghajnsielem (Gozo)", "Gharb (Gozo)", "Ghasri (Gozo)",
            "Kercem (Gozo)", "Marsalforn (Gozo)", "Mgarr (Gozo)", "Munxar (Gozo)",
            "Nadur (Gozo)", "Qala (Gozo)", "San Lawrenz (Gozo)", "Sannat (Gozo)",
            "Victoria/Rabat (Gozo)", "Xaghra (Gozo)", "Xewkija (Gozo)", "Xlendi (Gozo)",
            "Zebbug (Gozo)"
        )
    }
    
    // Load existing client if editing
    LaunchedEffect(clientId) {
        if (clientId != null) {
            viewModel.observeClient(clientId).collect { client ->
                client?.let {
                    name = it.name
                    clientCode = it.clientCode ?: ""
                    val clientLocality = it.locality ?: ""
                    
                    // Check if locality is custom (not in predefined list)
                    if (clientLocality.isNotEmpty() && !localities.contains(clientLocality)) {
                        isCustomLocality = true
                        customLocality = clientLocality
                    }
                    locality = clientLocality
                    
                    address = it.address
                    mapsUrl = it.mapsUrl ?: ""
                    notes = it.notes ?: ""
                    salesman = it.salesman ?: ""
                    
                    // Load products/equipment
                    selectedProducts = it.productsEquipment
                        ?.split(",")
                        ?.map { product -> product.trim() }
                        ?.filter { product -> product.isNotBlank() }
                        ?.toSet()
                        ?: emptySet()
                }
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (clientId == null) "New Client" else "Edit Client") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Client Name *") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            
            OutlinedTextField(
                value = clientCode,
                onValueChange = { clientCode = it },
                label = { Text("Client Code") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                placeholder = { Text("e.g., CL001, ABC123") }
            )
            
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
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = customLocality,
                            onValueChange = { 
                                customLocality = it
                                locality = it
                            },
                            label = { Text("Enter custom locality") },
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        TextButton(
                            onClick = {
                                isCustomLocality = false
                                locality = ""
                                customLocality = ""
                            }
                        ) {
                            Text("Cancel")
                        }
                    }
                } else {
                    // Dropdown for predefined localities
                    ExposedDropdownMenuBox(
                        expanded = localityMenuExpanded,
                        onExpandedChange = { localityMenuExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = locality,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Select locality") },
                            trailingIcon = {
                                Icon(Icons.Default.ArrowDropDown, "Dropdown")
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        
                        ExposedDropdownMenu(
                            expanded = localityMenuExpanded,
                            onDismissRequest = { localityMenuExpanded = false }
                        ) {
                            // Add custom option at the top
                            DropdownMenuItem(
                                text = { Text("+ Add Custom Locality") },
                                onClick = {
                                    isCustomLocality = true
                                    localityMenuExpanded = false
                                }
                            )
                            
                            HorizontalDivider()
                            
                            // List all Maltese localities
                            localities.forEach { loc ->
                                DropdownMenuItem(
                                    text = { Text(loc) },
                                    onClick = {
                                        locality = loc
                                        localityMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
            
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text("Address") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            
            OutlinedTextField(
                value = mapsUrl,
                onValueChange = { mapsUrl = it },
                label = { Text("Google Maps URL (Optional)") },
                placeholder = { Text("https://www.google.com/maps/...") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                supportingText = { Text("Paste any Google Maps link for navigation") }
            )
            
            // Location Pin Section (new clients only - existing clients manage pins
            // from the client detail screen)
            if (clientId == null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Location Pin (Optional)",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        if (pendingPinLat != null || pendingPinSourceUrl != null) {
                            // Pin captured - show summary with remove option
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            Icons.Default.LocationOn,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = "Pin captured",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            Text(
                                                text = if (pendingPinLat != null) {
                                                    "$pendingPinLat, $pendingPinLng"
                                                } else {
                                                    "Google Maps link"
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                    IconButton(onClick = {
                                        pendingPinLat = null
                                        pendingPinLng = null
                                        pendingPinStatus = null
                                        pendingPinSourceUrl = null
                                    }) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove pin",
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "The pin will be saved with the client.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
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
                            
                            Text(
                                text = "— OR —",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            OutlinedTextField(
                                value = pinPasteText,
                                onValueChange = { pinPasteText = it },
                                label = { Text("Paste link or coordinates") },
                                placeholder = { Text("35.8989, 14.5146 or Google Maps link") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                                shape = RoundedCornerShape(12.dp)
                            )
                            
                            Button(
                                onClick = {
                                    val parsed = ClientPinsRepository.parseLocationInput(pinPasteText)
                                    if (parsed == null) {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Invalid input. Paste coordinates or a Google Maps link.")
                                        }
                                    } else {
                                        pendingPinLat = parsed.first
                                        pendingPinLng = parsed.second
                                        pendingPinStatus = PinStatus.SEEDED
                                        pendingPinSourceUrl = if (pinPasteText.trim().startsWith("http")) pinPasteText.trim() else null
                                        pinPasteText = ""
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = pinPasteText.isNotBlank()
                            ) {
                                Icon(Icons.Default.ContentPaste, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Add from Paste")
                            }
                        }
                    }
                }
            }
            
            // Products/Equipment Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Products/Equipment",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Select all products/equipment used by this client",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    productOptions.forEach { product ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selectedProducts.contains(product),
                                onCheckedChange = { checked ->
                                    selectedProducts = if (checked) {
                                        selectedProducts + product
                                    } else {
                                        selectedProducts - product
                                    }
                                }
                            )
                            Text(
                                text = product,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }
            
            OutlinedTextField(
                value = salesman,
                onValueChange = { salesman = it },
                label = { Text("Salesman") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                placeholder = { Text("Enter sales representative name") }
            )
            
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                maxLines = 5,
                shape = RoundedCornerShape(12.dp)
            )
            
            Button(
                onClick = {
                    if (name.isBlank() || locality.isBlank()) {
                        scope.launch {
                            snackbarHostState.showSnackbar("Name and Locality are required")
                        }
                        return@Button
                    }
                    
                    scope.launch {
                        val input = ClientInput(
                            id = clientId,
                            name = name,
                            clientCode = clientCode.ifBlank { null },
                            locality = locality,
                            address = address.ifBlank { null },
                            hasPump = true, // Default value, not used
                            pumpModel = null, // Not used
                            latitude = null,
                            longitude = null,
                            mapsUrl = mapsUrl.ifBlank { null },
                            notes = notes.ifBlank { null },
                            productsEquipment = if (selectedProducts.isNotEmpty()) {
                                selectedProducts.joinToString(",")
                            } else {
                                null
                            },
                            salesman = salesman.ifBlank { null }
                        )
                        
                        val result = viewModel.saveClient(input)
                        if (result.isSuccess) {
                            val savedClientId = result.getOrNull()
                            
                            // If this is a new client (no clientId), return it to the previous screen
                            if (clientId == null && savedClientId != null) {
                                val prev = navController.previousBackStackEntry?.savedStateHandle
                                prev?.set("newClientId", savedClientId)
                                prev?.set("newClientName", name)
                                
                                // Save the captured location pin (if any) for the new client
                                if (pendingPinLat != null || pendingPinSourceUrl != null) {
                                    try {
                                        pinsViewModel.addPin(
                                            clientId = savedClientId,
                                            label = "Main location",
                                            latitude = pendingPinLat,
                                            longitude = pendingPinLng,
                                            status = pendingPinStatus ?: PinStatus.SEEDED,
                                            sourceUrl = pendingPinSourceUrl,
                                            setAsPrimary = true
                                        )
                                    } catch (e: Exception) {
                                        android.util.Log.e("ClientEdit", "Failed to save location pin", e)
                                        snackbarHostState.showSnackbar("Client saved, but the location pin could not be saved")
                                    }
                                }
                            }
                            
                            navController.popBackStack()
                        } else {
                            snackbarHostState.showSnackbar(
                                result.exceptionOrNull()?.message ?: "Error saving client"
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = name.isNotBlank() && locality.isNotBlank()
            ) {
                Text("Save Client")
            }
        }
    }
}

