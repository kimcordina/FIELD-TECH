package com.example.fieldtechv20kc.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.example.fieldtechv20kc.navigation.Screen
import com.example.fieldtechv20kc.ui.components.VoiceRecorderSection
import com.example.fieldtechv20kc.ui.components.rememberVoiceRecorderController
import com.example.fieldtechv20kc.utils.SettingsManager
import com.example.fieldtechv20kc.viewmodel.ClientsViewModel
import com.example.fieldtechv20kc.viewmodel.ServiceRequestsViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestCreateScreen(
    navController: NavController,
    requestsViewModel: ServiceRequestsViewModel,
    clientsViewModel: ClientsViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val settings by settingsManager.settings.collectAsState()
    val auth = remember { FirebaseAuth.getInstance() }
    val currentUserEmail = auth.currentUser?.email ?: "Unknown User"
    
    // Use rememberSaveable to persist state across navigation
    var selectedClientId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedClientName by rememberSaveable { mutableStateOf<String?>(null) }
    
    // Observe the full client record so we can show locality + island marker
    val selectedClient by remember(selectedClientId) {
        selectedClientId?.let { clientsViewModel.observeClientById(it) }
            ?: kotlinx.coroutines.flow.flowOf(null)
    }.collectAsState(initial = null)
    var notes by rememberSaveable { mutableStateOf("") }
    var voiceUri by rememberSaveable { mutableStateOf<String?>(null) }
    var photoUris by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    val voiceRecorderController = rememberVoiceRecorderController()
    
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Photo picker launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        photoUris = photoUris + uris.map { it.toString() }
    }
    
    // Listen for camera capture results
    LaunchedEffect(navController.currentBackStackEntry) {
        navController.currentBackStackEntry?.savedStateHandle?.get<String>("captured_photo")?.let { photoPath ->
            photoUris = photoUris + photoPath
            navController.currentBackStackEntry?.savedStateHandle?.remove<String>("captured_photo")
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "New Service Request",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Scrollable content area - the Create Request button stays pinned below
            // so it remains visible even when photos/voice fill smaller screens
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
            // Client Selection (Compact)
            if (selectedClientId != null && selectedClientName != null) {
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedClientName ?: "",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (!selectedClient?.locality.isNullOrBlank()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = selectedClient?.locality ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    com.example.fieldtechv20kc.ui.components.IslandBadge(selectedClient?.locality)
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = {
                                navController.navigate("report/clientPicker")
                            }) {
                                Text("Change", style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = {
                                selectedClientId = null
                                selectedClientName = null
                            }) {
                                Text("Clear", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            } else {
                Button(
                    onClick = { navController.navigate("report/clientPicker") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Person, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select Client")
                }
            }
            
            // Media Buttons (Voice, Gallery, Camera) - No headings
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Gallery Button
                OutlinedButton(
                    onClick = { photoPickerLauncher.launch("image/*") },
                    modifier = Modifier.weight(1f)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Photo, contentDescription = null, modifier = Modifier.size(24.dp))
                        Text("Gallery", style = MaterialTheme.typography.labelSmall)
                    }
                }
                
                // Camera Button
                OutlinedButton(
                    onClick = {
                        navController.navigate(Screen.Camera.route)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(24.dp))
                        Text("Camera", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            
            // Voice Recorder Section (Compact)
            VoiceRecorderSection(
                voiceUri = voiceUri,
                onVoiceUriChanged = { voiceUri = it },
                controller = voiceRecorderController
            )
            
            // Show selected photos with thumbnails (Compact)
            if (photoUris.isNotEmpty()) {
                Text(
                    text = "${photoUris.size} photo(s)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(photoUris) { photoUri ->
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(RoundedCornerShape(8.dp))
                        ) {
                            Image(
                                painter = rememberAsyncImagePainter(Uri.parse(photoUri)),
                                contentDescription = "Photo preview",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            // Delete button
                            IconButton(
                                onClick = {
                                    photoUris = photoUris.filter { it != photoUri }
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(24.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.9f)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove photo",
                                        tint = MaterialTheme.colorScheme.onError,
                                        modifier = Modifier.padding(2.dp).size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            // Notes (Compact)
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                label = { Text("Notes (optional)") },
                placeholder = { Text("Brief description...") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                maxLines = 3
            )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Create Button
            Button(
                onClick = {
                    if (selectedClientId == null) {
                        scope.launch {
                            snackbarHostState.showSnackbar("Please select a client")
                        }
                    } else {
                        // If a voice recording is still in progress, finalize it now
                        // so the completed file is attached instead of a corrupt partial one
                        if (voiceRecorderController.isRecording) {
                            voiceUri = voiceRecorderController.stopAndFinalize()
                        }
                        val finalVoiceUri = voiceUri
                        scope.launch {
                            try {
                                requestsViewModel.createRequest(
                                    clientId = selectedClientId!!,
                                    notes = notes.ifBlank { null },
                                    voiceUri = finalVoiceUri,
                                    photoUris = if (photoUris.isNotEmpty()) photoUris.joinToString(",") else null,
                                    requestedBy = currentUserEmail
                                )
                                navController.navigateUp()
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar("Error creating request: ${e.message}")
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedClientId != null
            ) {
                Text("Create Request")
            }
        }
    }
    
    // Listen for client selection result
    val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle
    LaunchedEffect(Unit) {
        savedStateHandle?.getStateFlow<String?>("selectedClientId", null)?.collect { clientId ->
            if (clientId != null) {
                selectedClientId = clientId
                savedStateHandle.remove<String?>("selectedClientId")
            }
        }
    }
    LaunchedEffect(Unit) {
        savedStateHandle?.getStateFlow<String?>("selectedClientName", null)?.collect { clientName ->
            if (clientName != null) {
                selectedClientName = clientName
                savedStateHandle.remove<String?>("selectedClientName")
            }
        }
    }
}
