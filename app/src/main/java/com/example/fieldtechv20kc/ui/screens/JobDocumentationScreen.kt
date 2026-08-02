package com.example.fieldtechv20kc.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalDensity
import com.example.fieldtechv20kc.data.model.Photo
import com.example.fieldtechv20kc.navigation.Screen
import com.example.fieldtechv20kc.utils.SettingsManager
import com.example.fieldtechv20kc.viewmodel.ReportViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Default Work Carried Out Options
val defaultWorkCarriedOutOptions = listOf(
    "Periodical service completed. Equipment operating correctly.",
    "Fault diagnosed and repaired. Dosing pump back in service.",
    "Dosing pump installed, calibrated and working correctly."
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobDocumentationScreen(
    navController: NavController,
    viewModel: ReportViewModel
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val settings by settingsManager.settings.collectAsState()
    
    // Use ViewModel state instead of local remember state
    val equipmentInstalledRepaired by viewModel.equipmentInstalledRepaired.collectAsState()
    val serialNumbers by viewModel.serialNumbers.collectAsState()
    val workCarriedOut by viewModel.workCarriedOut.collectAsState()
    val technicianName by viewModel.technicianName.collectAsState()
    val currentPhotos by viewModel.currentPhotos.collectAsState()
    val timeStarted by viewModel.timeStarted.collectAsState()
    val timeCompleted by viewModel.timeCompleted.collectAsState()
    val internalNotes by viewModel.internalNotes.collectAsState()
    
    // Equipment list state - simplified approach
    var equipmentList by remember { mutableStateOf<List<String>>(emptyList()) }
    var newEquipmentItem by remember { mutableStateOf("") }
    
    // Work Carried Out dropdown state
    var selectedWorkOptions by remember { mutableStateOf<List<String>>(emptyList()) }
    var customWorkText by remember { mutableStateOf("") }
    var showWorkCarriedOutDropdown by remember { mutableStateOf(false) }
    var showCustomWorkInput by remember { mutableStateOf(false) }
    
    // Function to update ViewModel with combined work description
    fun updateWorkCarriedOut() {
        val combinedText = buildString {
            selectedWorkOptions.forEachIndexed { index, option ->
                if (index > 0) append("\n\n")
                append("• $option")
            }
            if (customWorkText.isNotBlank()) {
                if (selectedWorkOptions.isNotEmpty()) append("\n\n")
                append(customWorkText)
            }
        }
        viewModel.setWorkCarriedOut(combinedText)
    }
    
    // Initialize equipment list from ViewModel data and default equipment
    LaunchedEffect(equipmentInstalledRepaired, settings.defaultEquipment) {
        try {
            println("DEBUG: Initializing equipment list from: '$equipmentInstalledRepaired'")
            val items = if (equipmentInstalledRepaired.isBlank()) {
                println("DEBUG: Equipment string is blank, using default equipment")
                settings.defaultEquipment
            } else {
                val splitItems = equipmentInstalledRepaired.split("\n").filter { it.isNotBlank() }
                println("DEBUG: Split items: $splitItems")
                splitItems
            }
            equipmentList = items
            println("DEBUG: Equipment list initialized with ${items.size} items")
            
            // Update ViewModel with the initialized equipment list
            if (equipmentInstalledRepaired.isBlank() && settings.defaultEquipment.isNotEmpty()) {
                viewModel.setEquipmentInstalledRepaired(settings.defaultEquipment.joinToString("\n"))
            }
            
            // Initialize technician name with default if empty
            if (technicianName.isBlank() && settings.defaultTechnicianName.isNotEmpty()) {
                viewModel.setTechnicianName(settings.defaultTechnicianName)
            }
        } catch (e: Exception) {
            println("ERROR: Exception initializing equipment list: ${e.message}")
            e.printStackTrace()
            equipmentList = emptyList()
        }
    }
    
    // Function to add new equipment item
    fun addEquipmentItem() {
        println("DEBUG: addEquipmentItem called with: '$newEquipmentItem'")
        val trimmedItem = newEquipmentItem.trim()
        println("DEBUG: trimmed item: '$trimmedItem'")
        
        if (trimmedItem.isNotEmpty()) {
            try {
                println("DEBUG: Current equipment list size: ${equipmentList.size}")
                val updatedList = equipmentList + trimmedItem
                println("DEBUG: Updated list size: ${updatedList.size}")
                
                equipmentList = updatedList
                println("DEBUG: Equipment list updated")
                
                val equipmentString = updatedList.joinToString("\n")
                println("DEBUG: Equipment string: '$equipmentString'")
                
                viewModel.setEquipmentInstalledRepaired(equipmentString)
                println("DEBUG: ViewModel updated")
                
                newEquipmentItem = ""
                println("DEBUG: Input field cleared")
            } catch (e: Exception) {
                println("ERROR: Exception in addEquipmentItem: ${e.message}")
                e.printStackTrace()
            }
        } else {
            println("DEBUG: Trimmed item is empty, not adding")
        }
    }
    
    // Function to remove equipment item
    fun removeEquipmentItem(index: Int) {
        try {
            if (index >= 0 && index < equipmentList.size) {
                val updatedList = equipmentList.toMutableList()
                updatedList.removeAt(index)
                equipmentList = updatedList
                viewModel.setEquipmentInstalledRepaired(updatedList.joinToString("\n"))
            }
        } catch (e: Exception) {
            println("Error removing equipment item: ${e.message}")
        }
    }
    
    // Handle photo capture result from CameraScreen
    LaunchedEffect(navController.currentBackStackEntry) {
        navController.currentBackStackEntry?.savedStateHandle?.get<String>("captured_photo")?.let { photoPath ->
            println("DEBUG: Photo captured at: $photoPath")
            val photo = Photo(
                reportId = 0, // Will be set when saving
                filePath = photoPath,
                caption = "Photo ${currentPhotos.size + 1}"
            )
            viewModel.addPhoto(photo)
            // Clear the saved state
            navController.currentBackStackEntry?.savedStateHandle?.remove<String>("captured_photo")
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Job Documentation") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Please document the job details:",
                style = MaterialTheme.typography.titleMedium
            )
            
            // Equipment List Section
            Text(
                text = "Equipment Installed/Repaired *",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Add new equipment item
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newEquipmentItem,
                    onValueChange = { newEquipmentItem = it },
                    label = { Text("Add Equipment Item") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("Enter equipment name") }
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Button(
                    onClick = { addEquipmentItem() },
                    enabled = newEquipmentItem.isNotBlank()
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Equipment")
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Display equipment list
            if (equipmentList.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    equipmentList.forEachIndexed { index, equipmentItem ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${index + 1}. $equipmentItem",
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                
                                IconButton(
                                    onClick = { removeEquipmentItem(index) }
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove Equipment",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = "No equipment items added yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
            
            if (settings.serialNumbersEnabled) {
                OutlinedTextField(
                    value = serialNumbers,
                    onValueChange = { viewModel.setSerialNumbers(it) },
                    label = { Text("Serial Number/s") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            
            // Enhanced Work Carried Out with Multiple Selections and Custom Text
            Column {
                Text(
                    text = "Work Carried Out *",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                // Display selected work options
                if (selectedWorkOptions.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = "Selected Work Items:",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            selectedWorkOptions.forEachIndexed { index, option ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "• $option",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = {
                                            selectedWorkOptions = selectedWorkOptions.toMutableList().apply { removeAt(index) }
                                            updateWorkCarriedOut()
                                        },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove",
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                // Dropdown for selecting predefined work options
                ExposedDropdownMenuBox(
                    expanded = showWorkCarriedOutDropdown,
                    onExpandedChange = { showWorkCarriedOutDropdown = !showWorkCarriedOutDropdown }
                ) {
                    OutlinedTextField(
                        value = if (selectedWorkOptions.isEmpty()) "Select work descriptions" else "${selectedWorkOptions.size} item(s) selected",
                        onValueChange = { },
                        readOnly = true,
                        label = { Text("Add predefined work descriptions") },
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
                        expanded = showWorkCarriedOutDropdown,
                        onDismissRequest = { showWorkCarriedOutDropdown = false }
                    ) {
                        // List all default work carried out options
                        defaultWorkCarriedOutOptions.forEach { workOption ->
                            val isSelected = selectedWorkOptions.contains(workOption)
                            DropdownMenuItem(
                                text = { 
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = workOption,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (isSelected) {
                                            Text(
                                                text = "✓",
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    selectedWorkOptions = if (isSelected) {
                                        selectedWorkOptions.toMutableList().apply { remove(workOption) }
                                    } else {
                                        selectedWorkOptions.toMutableList().apply { add(workOption) }
                                    }
                                    updateWorkCarriedOut()
                                    // Close dropdown after selection
                                    showWorkCarriedOutDropdown = false
                                }
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Custom work text input
                if (showCustomWorkInput) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = customWorkText,
                            onValueChange = { 
                                customWorkText = it
                                updateWorkCarriedOut()
                            },
                            label = { Text("Add custom work description") },
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            modifier = Modifier.weight(1f),
                            minLines = 2,
                            maxLines = 4,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        TextButton(
                            onClick = {
                                showCustomWorkInput = false
                                customWorkText = ""
                                updateWorkCarriedOut()
                            }
                        ) {
                            Text("Remove")
                        }
                    }
                } else {
                    Button(
                        onClick = { showCustomWorkInput = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Text("+ Add Custom Work Description")
                    }
                }
            }
            
            OutlinedTextField(
                value = technicianName,
                onValueChange = { viewModel.setTechnicianName(it) },
                label = { Text("Name of Technician *") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Enter technician name") }
            )
            
            // Time tracking fields (conditional based on settings)
            if (settings.timeStartedEnabled) {
                OutlinedTextField(
                    value = timeStarted,
                    onValueChange = { viewModel.setTimeStarted(it) },
                    label = { Text("Time Started") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("e.g., 09:00") }
                )
            }
            
            if (settings.timeCompletedEnabled) {
                OutlinedTextField(
                    value = timeCompleted,
                    onValueChange = { viewModel.setTimeCompleted(it) },
                    label = { Text("Time Completed") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("e.g., 17:00") }
                )
            }
            
            
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Photos (Optional)",
                        style = MaterialTheme.typography.titleSmall
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Button(
                        onClick = {
                            try {
                                println("DEBUG: Launching camera...")
                                navController.navigate(Screen.Camera.route)
                            } catch (e: Exception) {
                                println("DEBUG: Error launching camera: ${e.message}")
                                e.printStackTrace()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Take Photo")
                    }
                    
                    if (currentPhotos.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(currentPhotos) { photo ->
                                PhotoItem(
                                    photo = photo,
                                    onDelete = {
                                        viewModel.removePhoto(photo)
                                    }
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "You can add photos to document the work performed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Internal Notes Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Internal Notes",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "(Not included in PDF)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = internalNotes,
                        onValueChange = { viewModel.setInternalNotes(it) },
                        label = { Text("Add notes for internal use only") },
                        placeholder = { Text("E.g. Follow-up needed, customer concerns, special requirements...") },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 5
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = {
                    viewModel.setEquipmentInstalledRepaired(equipmentInstalledRepaired)
                    viewModel.setSerialNumbers(serialNumbers)
                    viewModel.setWorkCarriedOut(workCarriedOut)
                    viewModel.setTechnicianName(technicianName)
                    viewModel.setTimeStarted(timeStarted)
                    viewModel.setTimeCompleted(timeCompleted)
                    viewModel.setInternalNotes(internalNotes)
                    navController.navigate(Screen.Signature.route)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = equipmentInstalledRepaired.isNotBlank() && workCarriedOut.isNotBlank() && technicianName.isNotBlank()
            ) {
                Text("Continue to Legal Terms & Signature")
            }
        }
    }
}

@Composable
fun PhotoItem(
    photo: Photo,
    onDelete: () -> Unit
) {
    val sizePx = with(LocalDensity.current) { 100.dp.roundToPx() }
    Card(modifier = Modifier.size(100.dp)) {
        Box {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(photo.filePath)
                    .size(sizePx) // decode near view size
                    .build(),
                contentDescription = photo.caption,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            IconButton(
                onClick = onDelete,
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete photo",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
