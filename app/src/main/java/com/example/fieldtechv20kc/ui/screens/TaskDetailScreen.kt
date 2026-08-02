package com.example.fieldtechv20kc.ui.screens

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.example.fieldtechv20kc.data.model.TaskStatus
import com.example.fieldtechv20kc.navigation.Screen
import com.example.fieldtechv20kc.viewmodel.ClientPinsViewModel
import com.example.fieldtechv20kc.viewmodel.ClientsViewModel
import com.example.fieldtechv20kc.viewmodel.ServiceTasksViewModel
import com.example.fieldtechv20kc.utils.DateUtils
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    taskId: String,
    navController: NavController,
    tasksViewModel: ServiceTasksViewModel,
    clientsViewModel: ClientsViewModel
) {
    val task by tasksViewModel.observeTask(taskId).collectAsStateWithLifecycle(initialValue = null)
    val client by remember(task?.clientId) {
        task?.clientId?.let { clientsViewModel.observeClient(it) } ?: flowOf(null)
    }.collectAsStateWithLifecycle(initialValue = null)
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Pins ViewModel for navigation
    val pinsViewModel = remember { ClientPinsViewModel(context) }
    
    var showCompleteConfirmation by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    
    // Voice playback state
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var selectedPhotoUri by remember { mutableStateOf<String?>(null) }
    
    // Cleanup media player on dispose
    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Job Details") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        task?.let { taskData ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Job Header Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = client?.name ?: "Service visit",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        if (!client?.locality.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = client?.locality ?: "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                com.example.fieldtechv20kc.ui.components.IslandBadge(client?.locality)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AssistChip(
                                onClick = { },
                                label = { Text(taskData.status.name.replace("_", " ")) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = when (taskData.status) {
                                        TaskStatus.PENDING -> MaterialTheme.colorScheme.secondaryContainer
                                        TaskStatus.DONE -> MaterialTheme.colorScheme.primaryContainer
                                        TaskStatus.CANCELED -> MaterialTheme.colorScheme.errorContainer
                                        TaskStatus.DELETED -> MaterialTheme.colorScheme.surfaceVariant
                                    }
                                )
                            )
                            val techColor = com.example.fieldtechv20kc.data.model.Technicians.getColorForTechnician(taskData.assignedToName)
                            AssistChip(
                                onClick = { },
                                label = { Text(taskData.assignedToName) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = techColor.copy(alpha = 0.2f)
                                ),
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .background(techColor, shape = androidx.compose.foundation.shape.CircleShape)
                                    )
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Assigned to: ${taskData.assignedToName}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Scheduled: ${formatTaskDate(taskData.scheduledDate)}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (!taskData.createdByName.isNullOrBlank()) {
                            Text(
                                text = "Created by: ${taskData.createdByName}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (taskData.status == TaskStatus.CANCELED && !taskData.cancelledByName.isNullOrBlank()) {
                            Text(
                                text = "Cancelled by: ${taskData.cancelledByName}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        if (taskData.status == TaskStatus.DELETED) {
                            Text(
                                text = "Deleted by: ${taskData.deletedByName ?: "Unknown"}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                            taskData.deletedAt?.let { timestamp ->
                                Text(
                                    text = "Deleted on: ${DateUtils.formatDateTime(timestamp)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (taskData.notes != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Notes: ${taskData.notes}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        // Voice Note Playback
                        if (taskData.voiceNoteUri != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
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
                                                        val voiceUri = taskData.voiceNoteUri ?: return@launch
                                                        
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
                        
                        // Photos
                        if (!taskData.photoUris.isNullOrBlank()) {
                            val photoList = taskData.photoUris?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
                            if (photoList.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Photos",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${photoList.size} photo(s) attached",
                                    style = MaterialTheme.typography.bodySmall,
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
                }

                // Client Info Card
                client?.let { clientData ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Client Information",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = clientData.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = clientData.locality ?: "Unknown locality",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                com.example.fieldtechv20kc.ui.components.IslandBadge(clientData.locality)
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // Quick Actions - Navigate using pins
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        val pin = pinsViewModel.getPinForNavigation(clientData.id)
                                        val intent = when {
                                            // Priority 1: Use mapsUrl if available
                                            !clientData.mapsUrl.isNullOrBlank() -> {
                                                Intent(Intent.ACTION_VIEW, Uri.parse(clientData.mapsUrl))
                                            }
                                            // Priority 2: Use client coordinates if available
                                            clientData.latitude != null && clientData.longitude != null -> {
                                                Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${clientData.latitude},${clientData.longitude}"))
                                            }
                                            // Priority 3: Use pin data
                                            pin != null -> {
                                                if (pin.latitude != null && pin.longitude != null) {
                                                    Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${pin.latitude},${pin.longitude}"))
                                                } else if (pin.sourceUrl != null) {
                                                    Intent(Intent.ACTION_VIEW, Uri.parse(pin.sourceUrl))
                                                } else {
                                                    null
                                                }
                                            }
                                            // Priority 4: Fallback to address
                                            clientData.address != null -> {
                                                Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${clientData.address}"))
                                            }
                                            else -> null
                                        }
                                        
                                        intent?.let { 
                                            try {
                                                context.startActivity(it)
                                            } catch (e: Exception) {
                                                snackbarHostState.showSnackbar("No navigation app available")
                                            }
                                        } ?: snackbarHostState.showSnackbar("No location available for this client")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Map, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Open in Maps")
                            }
                        }
                    }
                }

                // Action Buttons
                if (taskData.status != TaskStatus.DONE && taskData.status != TaskStatus.CANCELED) {
                    Button(
                        onClick = {
                            // Navigate to report flow with clientId and jobId
                            navController.navigate("report/start?clientId=${taskData.clientId}&taskId=${taskData.id}")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Start Report", style = MaterialTheme.typography.titleMedium)
                    }
                }

                // Status Control Buttons
                if (taskData.status == TaskStatus.PENDING) {
                    Button(
                        onClick = { showCompleteConfirmation = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.CheckCircle, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Mark as Complete")
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedButton(
                        onClick = { showDeleteConfirmation = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Delete, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Delete Job")
                    }
                }

                // Linked Report Info
                if (taskData.linkedReportId != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Report Completed",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Report ID: ${taskData.linkedReportId}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }
        } ?: run {
            // Loading state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
    
    // Confirmation Dialog
    // Delete Confirmation Dialog
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete Job") },
            text = { Text("Are you sure you want to delete this job? This action will mark the job as deleted. No notifications will be sent.") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.displayName ?: "Unknown"
                            tasksViewModel.deleteJob(taskId, currentUser)
                            showDeleteConfirmation = false
                            navController.navigateUp() // Navigate back to jobs screen
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Yes, Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    if (showCompleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showCompleteConfirmation = false },
            title = { Text("Complete Job") },
            text = { Text("Are you sure this job is complete?") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            tasksViewModel.setStatus(taskId, TaskStatus.DONE)
                            showCompleteConfirmation = false
                            navController.navigateUp() // Navigate back to jobs screen
                        }
                    }
                ) {
                    Text("Yes, Complete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCompleteConfirmation = false }) {
                    Text("Cancel")
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

private fun flowOf(value: Nothing?): kotlinx.coroutines.flow.Flow<Nothing?> {
    return kotlinx.coroutines.flow.flowOf(value)
}



