package com.example.fieldtechv20kc.ui.screens

import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.example.fieldtechv20kc.data.model.RequestStatus
import com.example.fieldtechv20kc.data.model.ServiceTask
import com.example.fieldtechv20kc.data.model.TaskStatus
import com.example.fieldtechv20kc.data.model.Technicians
import com.example.fieldtechv20kc.navigation.Screen
import com.example.fieldtechv20kc.utils.DateUtils
import com.example.fieldtechv20kc.viewmodel.ClientsViewModel
import com.example.fieldtechv20kc.viewmodel.ServiceRequestsViewModel
import com.example.fieldtechv20kc.viewmodel.ServiceTasksViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestDetailScreen(
    navController: NavController,
    requestId: String,
    requestsViewModel: ServiceRequestsViewModel,
    clientsViewModel: ClientsViewModel,
    tasksViewModel: ServiceTasksViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth = remember { FirebaseAuth.getInstance() }
    val currentUserEmail = auth.currentUser?.email ?: "Unknown User"
    
    val request by requestsViewModel.observeRequest(requestId).collectAsState(initial = null)
    val client = request?.let { req ->
        clientsViewModel.observeClientById(req.clientId).collectAsState(initial = null).value
    }
    
    var showAssignDialog by remember { mutableStateOf(false) }
    var showStatusMenu by remember { mutableStateOf(false) }
    
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var selectedPhotoUri by remember { mutableStateOf<String?>(null) }
    
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Cleanup media player on dispose
    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
        }
    }
    
    if (request == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Request Details",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showStatusMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(
                        expanded = showStatusMenu,
                        onDismissRequest = { showStatusMenu = false }
                    ) {
                        if (request?.status != RequestStatus.DONE) {
                            DropdownMenuItem(
                                text = { Text("Mark Done") },
                                onClick = {
                                    requestsViewModel.setStatus(requestId, RequestStatus.DONE)
                                    showStatusMenu = false
                                }
                            )
                        }
                        if (request?.status != RequestStatus.CANCELED) {
                            DropdownMenuItem(
                                text = { Text("Cancel") },
                                onClick = {
                                    requestsViewModel.setStatus(requestId, RequestStatus.CANCELED, currentUserEmail)
                                    showStatusMenu = false
                                    navController.popBackStack()
                                }
                            )
                        }
                        if (request?.status != RequestStatus.OPEN) {
                            DropdownMenuItem(
                                text = { Text("Reopen") },
                                onClick = {
                                    requestsViewModel.setStatus(requestId, RequestStatus.OPEN)
                                    showStatusMenu = false
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
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
            // Client Info Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { 
                        client?.let { navController.navigate(Screen.ClientDetail.createRoute(it.id)) }
                    },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = client?.name ?: "Unknown Client",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (client?.locality != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = client?.locality ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            com.example.fieldtechv20kc.ui.components.IslandBadge(client?.locality)
                        }
                    }
                    if (!client?.address.isNullOrBlank()) {
                        Text(
                            text = client?.address ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            
            // Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Status:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                AssistChip(
                    onClick = {},
                    label = { Text(request?.status?.name ?: "") },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = when (request?.status) {
                            RequestStatus.OPEN -> MaterialTheme.colorScheme.errorContainer
                            RequestStatus.ASSIGNED -> MaterialTheme.colorScheme.primaryContainer
                            RequestStatus.DONE -> MaterialTheme.colorScheme.tertiaryContainer
                            RequestStatus.CANCELED -> MaterialTheme.colorScheme.surfaceVariant
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        labelColor = when (request?.status) {
                            RequestStatus.OPEN -> MaterialTheme.colorScheme.onErrorContainer
                            RequestStatus.ASSIGNED -> MaterialTheme.colorScheme.onPrimaryContainer
                            RequestStatus.DONE -> MaterialTheme.colorScheme.onTertiaryContainer
                            RequestStatus.CANCELED -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                )
            }
            
            // Requested By
            if (!request?.requestedByName.isNullOrBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Requested by:",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = request?.requestedByName ?: "",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            
            // Cancelled By (only show if status is CANCELED)
            if (request?.status == RequestStatus.CANCELED && !request?.cancelledByName.isNullOrBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Cancelled by:",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = request?.cancelledByName ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            
            // Requested At
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Requested:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = DateUtils.formatDateTime(request?.requestedAt ?: 0),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            
            Divider()
            
            // Notes
            if (!request?.notes.isNullOrBlank()) {
                Column {
                    Text(
                        text = "Notes:",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            text = request?.notes ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
            
            // Voice Note
            if (request?.voiceUri != null) {
                Column {
                    Text(
                        text = "Voice Note:",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.VoiceChat,
                                    contentDescription = "Voice Note"
                                )
                                Text(
                                    text = if (isPlaying) "Playing..." else "Voice recording",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            IconButton(
                                onClick = {
                                    if (isPlaying) {
                                        mediaPlayer?.pause()
                                        isPlaying = false
                                    } else {
                                        scope.launch {
                                            try {
                                                val voiceUri = request?.voiceUri ?: return@launch
                                                
                                                // Check if it's a Firebase Storage path (not a local content:// URI)
                                                val playbackUri = if (voiceUri.startsWith("companies/") || voiceUri.startsWith("/companies/")) {
                                                    // Convert storage path to download URL
                                                    val storagePath = voiceUri.removePrefix("/")
                                                    val storageRef = FirebaseStorage.getInstance().getReference(storagePath)
                                                    storageRef.downloadUrl.await()
                                                } else {
                                                    // Use local content URI or file path
                                                    if (voiceUri.startsWith("content://") || voiceUri.startsWith("file://")) {
                                                        Uri.parse(voiceUri)
                                                    } else {
                                                        Uri.fromFile(java.io.File(voiceUri))
                                                    }
                                                }
                                                
                                                if (mediaPlayer == null) {
                                                    mediaPlayer = MediaPlayer().apply {
                                                        setDataSource(context, playbackUri)
                                                        prepare()
                                                        setOnCompletionListener {
                                                            isPlaying = false
                                                        }
                                                    }
                                                }
                                                mediaPlayer?.start()
                                                isPlaying = true
                                            } catch (e: Exception) {
                                                snackbarHostState.showSnackbar("Error playing voice note: ${e.message}")
                                                e.printStackTrace()
                                            }
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play"
                                )
                            }
                        }
                    }
                }
            }
            
            // Photos
            if (!request?.photoUris.isNullOrBlank()) {
                val photoList = request?.photoUris?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
                if (photoList.isNotEmpty()) {
                    Column {
                        Text(
                            text = "Photos:",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${photoList.size} photo(s) attached",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(photoList) { photoUri ->
                                Card(
                                    modifier = Modifier
                                        .size(100.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { selectedPhotoUri = photoUri },
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                ) {
                                    Image(
                                        painter = rememberAsyncImagePainter(Uri.parse(photoUri)),
                                        contentDescription = "Attached photo",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Assign as Job Button (only if not already assigned or done)
            if (request?.status == RequestStatus.OPEN) {
                Button(
                    onClick = { showAssignDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Assignment, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Assign as Job")
                }
            }
        }
    }
    
    // Job Assignment Dialog - Use UnifiedTaskAssignmentDialog for consistency
    if (showAssignDialog && request != null && client != null) {
        com.example.fieldtechv20kc.ui.screens.UnifiedTaskAssignmentDialog(
            client = client!!,
            onDismiss = { showAssignDialog = false },
            initialNotes = request?.notes,
            onAssign = { technicianName, voiceUri, notes, photoUris ->
                scope.launch {
                    try {
                        val currentRequest = request
                        if (currentRequest != null) {
                            // Use new voice note if provided, otherwise use request's voice note
                            val finalVoiceUri = voiceUri ?: currentRequest.voiceUri
                            // Use new photos if provided, otherwise use request's photos
                            val finalPhotoUris = photoUris ?: currentRequest.photoUris
                            // Use dialog notes if provided, otherwise carry forward request's notes
                            val finalNotes = notes.takeIf { it.isNotBlank() } ?: currentRequest.notes
                            
                            val task = ServiceTask(
                                clientId = currentRequest.clientId,
                                title = "Service visit",
                                assignedToName = technicianName,
                                scheduledDate = DateUtils.getTodayMidnight(),
                                status = TaskStatus.PENDING,
                                notes = finalNotes?.takeIf { it.isNotBlank() },
                                voiceNoteUri = finalVoiceUri,
                                photoUris = finalPhotoUris,
                                createdByName = currentUserEmail
                            )
                            tasksViewModel.upsert(task)
                            requestsViewModel.linkTask(requestId, task.id)
                            showAssignDialog = false
                            // Navigate back immediately
                            navController.navigateUp()
                        }
                    } catch (e: Exception) {
                        // Log error but don't block navigation
                        android.util.Log.e("RequestDetail", "Error assigning job", e)
                    }
                }
            }
        )
    }
    
    // Photo enlargement dialog
    selectedPhotoUri?.let { photoUri ->
        AlertDialog(
            onDismissRequest = { selectedPhotoUri = null },
            confirmButton = {
                TextButton(onClick = { selectedPhotoUri = null }) {
                    Text("Close")
                }
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 500.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(Uri.parse(photoUri)),
                        contentDescription = "Enlarged photo",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        )
    }
}
