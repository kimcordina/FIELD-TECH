package com.example.fieldtechv20kc.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.fieldtechv20kc.navigation.Screen
import com.example.fieldtechv20kc.FieldTechApplication
import com.example.fieldtechv20kc.data.model.*
import com.example.fieldtechv20kc.data.repository.RouteRepository
import com.example.fieldtechv20kc.utils.DateUtils
import com.example.fieldtechv20kc.utils.FTLog
import com.example.fieldtechv20kc.utils.GoogleMapsHelper
import com.example.fieldtechv20kc.utils.LocationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Screen for creating and planning a new route.
 * Users can select optimization strategy, manually reorder stops, and save the route.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutePlannerScreen(
    navController: NavController,
    jobIds: String, // Comma-separated job IDs
    createdBy: String,
    intendedAssignee: String? = null,
    viewModel: RoutePlannerViewModel = run {
        val app = LocalContext.current.applicationContext as FieldTechApplication
        viewModel(
            factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return RoutePlannerViewModel(app.routeRepository, jobIds.split(","), createdBy, intendedAssignee) as T
                }
            }
        )
    }
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    
    var viewMode by remember { mutableStateOf("list") } // "list" or "location"
    var showOptimizationDialog by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    
    // Location permission launcher
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        if (granted) {
            viewModel.getCurrentLocation(context)
        }
    }

    LaunchedEffect(Unit) {
        // Initialize with default optimization
        viewModel.optimizeRoute(RouteOptimization.CLOSEST_FIRST, context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Plan Route", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (uiState.stops.isNotEmpty() && !uiState.isLoading) {
                        // View mode toggle
                        IconButton(onClick = { viewMode = if (viewMode == "list") "location" else "list" }) {
                            Icon(
                                if (viewMode == "list") Icons.Default.LocationOn else Icons.Default.List,
                                if (viewMode == "list") "Location View" else "List View"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = if (uiState.stops.isNotEmpty()) 80.dp else 0.dp) // Space for button
            ) {
                if (uiState.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (uiState.error != null) {
                    ErrorView(
                        error = uiState.error!!,
                        onRetry = { navController.popBackStack() }
                    )
                } else if (uiState.stops.isEmpty()) {
                    EmptyView()
                } else {
                    // Warning message if some jobs couldn't be added
                    if (uiState.warning != null) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = uiState.warning!!,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                    
                    // Route summary card
                    RouteSummaryCard(
                        stops = uiState.stops,
                        currentLocation = uiState.currentLocation,
                        onOptimizeClick = { showOptimizationDialog = true },
                        onGetLocationClick = {
                            if (LocationHelper.hasLocationPermission(context)) {
                                viewModel.getCurrentLocation(context)
                            } else {
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
                        }
                    )

                // Stops list or location view
                if (viewMode == "list") {
                    // List view with drag-to-reorder
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(
                            items = uiState.stops,
                        key = { _, stop -> stop.id }
                    ) { index, stop ->
                        RouteStopCard(
                            stop = stop,
                            index = index,
                            onMoveUp = if (index > 0) {{ viewModel.moveStop(index, index - 1) }} else null,
                            onMoveDown = if (index < uiState.stops.size - 1) {{ viewModel.moveStop(index, index + 1) }} else null,
                            onRemove = { viewModel.removeStop(stop) }
                        )
                    }
                }
                } else {
                    // Location view - Grouped by locality
                    RouteStopsByLocationView(
                        stops = uiState.stops,
                        onRemoveStop = { viewModel.removeStop(it) }
                    )
                }
            }
            }

            // Prominent "Save Route" button at bottom
            if (!uiState.isLoading && uiState.error == null && uiState.stops.isNotEmpty()) {
                Button(
                    onClick = { showSaveDialog = true },
                    enabled = !uiState.isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 4.dp,
                        pressedElevation = 8.dp
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Save Route",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Optimization dialog
        if (showOptimizationDialog) {
            OptimizationDialog(
                currentLocation = uiState.currentLocation,
                onDismiss = { showOptimizationDialog = false },
                onOptimize = { optimization ->
                    viewModel.optimizeRoute(optimization, context)
                    showOptimizationDialog = false
                }
            )
        }

        // Save route dialog
        if (showSaveDialog) {
            SaveRouteDialog(
                intendedAssignee = intendedAssignee,
                onDismiss = { showSaveDialog = false },
                onSave = { routeName, assignee ->
                    viewModel.saveRoute(routeName, assignee) { routeId ->
                        navController.popBackStack()
                        navController.navigate(Screen.RouteDetail.createRoute(routeId))
                    }
                    showSaveDialog = false
                },
                isSaving = uiState.isSaving
            )
        }
    }
}

@Composable
fun RouteSummaryCard(
    stops: List<RouteStop>,
    currentLocation: android.location.Location?,
    onOptimizeClick: () -> Unit,
    onGetLocationClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Route Summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Stops: ${stops.size}", style = MaterialTheme.typography.bodyMedium)
                    val totalDistance = stops.sumOf { it.distanceFromPrevious ?: 0.0 }
                    if (totalDistance > 0) {
                        Text("Distance: ${String.format("%.1f", totalDistance)} km", style = MaterialTheme.typography.bodyMedium)
                    }
                    val totalTime = stops.sumOf { (it.timeFromPrevious ?: 0).toLong() }
                    if (totalTime > 0) {
                        Text("Est. Time: ${totalTime} min", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    if (currentLocation != null) {
                        Text(
                            "Start: Current Location",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            LocationHelper.formatLocation(currentLocation),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        TextButton(onClick = onGetLocationClick, modifier = Modifier.padding(0.dp)) {
                            Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Get Location", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onOptimizeClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Route, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Optimize Route")
            }
        }
    }
}

@Composable
fun RouteStopCard(
    stop: RouteStop,
    index: Int,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Order number
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Stop info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stop.clientName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stop.locality,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    com.example.fieldtechv20kc.ui.components.IslandBadge(stop.locality)
                }
                if (stop.distanceFromPrevious != null && stop.distanceFromPrevious!! > 0) {
                    Text(
                        text = "↑ ${String.format("%.1f", stop.distanceFromPrevious)} km",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Reorder buttons
            Column {
                IconButton(
                    onClick = onMoveUp ?: {},
                    enabled = onMoveUp != null,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowUp,
                        contentDescription = "Move up",
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = onMoveDown ?: {},
                    enabled = onMoveDown != null,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Move down",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Remove button
            IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun OptimizationDialog(
    currentLocation: android.location.Location?,
    onDismiss: () -> Unit,
    onOptimize: (RouteOptimization) -> Unit
) {
    var selected by remember { mutableStateOf(RouteOptimization.CLOSEST_FIRST) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Optimize Route", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                if (currentLocation == null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Current location not available. Get location for best optimization.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                RouteOptimization.values().forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        RadioButton(
                            selected = selected == option,
                            onClick = { selected = option }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(option.displayName, fontWeight = FontWeight.Medium)
                            Text(
                                when (option) {
                                    RouteOptimization.CLOSEST_FIRST -> "Start with nearest stop, then optimize remaining"
                                    RouteOptimization.FARTHEST_FIRST -> "Start with farthest stop, then return closer"
                                    RouteOptimization.MANUAL -> "Keep current order (no changes)"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onOptimize(selected) }) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SaveRouteDialog(
    intendedAssignee: String?,
    onDismiss: () -> Unit,
    onSave: (String, String?) -> Unit,
    isSaving: Boolean
) {
    // Generate default route name with technician and date
    val defaultName = remember(intendedAssignee) {
        val techName = intendedAssignee ?: "Unassigned"
        "$techName – ${DateUtils.formatCompact(System.currentTimeMillis())}"
    }
    var routeName by remember { mutableStateOf(defaultName) }
    var selectedTechnician by remember { mutableStateOf<String?>(intendedAssignee) }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text("Save Route", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Route name field
                OutlinedTextField(
                    value = routeName,
                    onValueChange = { routeName = it },
                    label = { Text("Route Name") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSaving
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Technician selection
                Text(
                    "Assign route to:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                
                com.example.fieldtechv20kc.data.model.Technicians.ALL.forEach { tech ->
                    val color = com.example.fieldtechv20kc.data.model.Technicians.getColorForTechnician(tech)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isSaving) { 
                                selectedTechnician = tech
                                // Update route name with selected technician
                                routeName = "$tech – ${DateUtils.formatCompact(System.currentTimeMillis())}"
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedTechnician == tech) 
                                color.copy(alpha = 0.3f) 
                            else 
                                MaterialTheme.colorScheme.surface
                        ),
                        border = if (selectedTechnician == tech) 
                            BorderStroke(2.dp, color) 
                        else 
                            null,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(color, shape = RoundedCornerShape(4.dp))
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = tech,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (selectedTechnician == tech) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(routeName, selectedTechnician) },
                enabled = routeName.isNotBlank() && selectedTechnician != null && !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Text("Save")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("Cancel")
            }
        }
    )
}

fun generateDefaultRouteName(assignee: String?): String {
    val date = DateUtils.formatCompact(System.currentTimeMillis())
    return if (!assignee.isNullOrBlank()) {
        "$assignee – $date"
    } else {
        "Route – $date"
    }
}

@Composable
fun ErrorView(error: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Error",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = error,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text("Go Back")
        }
    }
}

@Composable
fun EmptyView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Route,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No stops available",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ViewModel
class RoutePlannerViewModel(
    private val repository: RouteRepository,
    jobIds: List<String>,
    private val createdBy: String,
    private val intendedAssignee: String?
) : ViewModel() {
    private val TAG = "RoutePlannerVM"
    
    // Remove duplicates from jobIds to prevent duplicate stops
    private val jobIds = jobIds.distinct()

    private val _uiState = MutableStateFlow(RoutePlannerUiState())
    val uiState: StateFlow<RoutePlannerUiState> = _uiState.asStateFlow()

    fun optimizeRoute(optimization: RouteOptimization, context: android.content.Context) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                // Get current location for optimization
                val locationResult = LocationHelper.getCurrentLocation(context)
                
                // Use currently displayed stops' jobIds (in case some were removed)
                // Remove duplicates to avoid creating duplicate stops
                val currentJobIds = if (_uiState.value.stops.isNotEmpty()) {
                    _uiState.value.stops.map { it.jobId }.distinct()
                } else {
                    jobIds
                }
                
                // Get optimized stops WITHOUT creating a route (no Firestore save)
                val result = repository.getOptimizedStops(
                    jobIds = currentJobIds,
                    optimization = optimization,
                    startLatitude = locationResult.location?.latitude,
                    startLongitude = locationResult.location?.longitude
                )
                
                // Update UI with optimized stops and warnings
                _uiState.value = _uiState.value.copy(
                    stops = result.stops,
                    currentLocation = locationResult.location,
                    isLoading = false,
                    error = null,
                    warning = if (result.hasFailures) {
                        "${result.failureCount} job(s) could not be added to the route (missing GPS coordinates or client data)"
                    } else null
                )
                
                FTLog.i(TAG, "✅ Optimized ${result.stops.size} stops using $optimization")
                if (result.hasFailures) {
                    FTLog.w(TAG, "⚠️ ${result.failureCount} jobs excluded: ${result.failedJobs.joinToString(", ")}")
                }
            } catch (e: Exception) {
                FTLog.e(TAG, "❌ Failed to optimize route: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to optimize route"
                )
            }
        }
    }

    fun getCurrentLocation(context: android.content.Context) {
        viewModelScope.launch {
            val result = LocationHelper.getCurrentLocation(context)
            _uiState.value = _uiState.value.copy(currentLocation = result.location)
        }
    }

    fun moveStop(fromIndex: Int, toIndex: Int) {
        val stops = _uiState.value.stops.toMutableList()
        val item = stops.removeAt(fromIndex)
        stops.add(toIndex, item)
        
        // Update order indices
        val reordered = stops.mapIndexed { index, stop ->
            stop.copy(orderIndex = index)
        }
        
        _uiState.value = _uiState.value.copy(stops = reordered)
    }

    fun removeStop(stop: RouteStop) {
        val stops = _uiState.value.stops.filter { it.id != stop.id }
        val reordered = stops.mapIndexed { index, s -> s.copy(orderIndex = index) }
        _uiState.value = _uiState.value.copy(stops = reordered)
    }

    fun saveRoute(routeName: String, assignee: String?, onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            try {
                // Save route directly from current UI stops (no recreation!)
                val routeId = repository.saveRouteFromStops(
                    stops = _uiState.value.stops,
                    routeName = routeName,
                    createdBy = createdBy,
                    intendedAssignee = assignee
                )
                
                FTLog.i(TAG, "✅ Route saved: $routeId with ${_uiState.value.stops.size} stops")
                onSuccess(routeId)
            } catch (e: Exception) {
                FTLog.e(TAG, "❌ Failed to save route: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = e.message ?: "Failed to save route"
                )
            }
        }
    }
}

data class RoutePlannerUiState(
    val stops: List<RouteStop> = emptyList(),
    val currentLocation: android.location.Location? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val warning: String? = null
)

@Composable
fun RouteStopsByLocationView(
    stops: List<RouteStop>,
    onRemoveStop: (RouteStop) -> Unit
) {
    // Group stops by locality
    val stopsByLocality = stops.groupBy { it.locality }
    val sortedLocalities = stopsByLocality.keys.sortedBy { it }
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        sortedLocalities.forEach { locality ->
            val localityStops = stopsByLocality[locality] ?: emptyList()
            
            // Locality header
            item(key = "header_$locality") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = locality,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            com.example.fieldtechv20kc.ui.components.IslandBadge(locality)
                        }
                        AssistChip(
                            onClick = { },
                            label = { Text("${localityStops.size} stops") },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    }
                }
            }
            
            // Stops in this locality
            items(localityStops.size, key = { localityStops[it].id }) { index ->
                val stop = localityStops[index]
                val originalIndex = stops.indexOf(stop)
                RouteStopCard(
                    stop = stop,
                    index = originalIndex,
                    onMoveUp = null, // Disable reordering in location view
                    onMoveDown = null,
                    onRemove = { onRemoveStop(stop) }
                )
            }
        }
    }
}

