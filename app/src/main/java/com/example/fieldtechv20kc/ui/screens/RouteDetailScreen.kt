package com.example.fieldtechv20kc.ui.screens

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.fieldtechv20kc.FieldTechApplication
import com.example.fieldtechv20kc.data.model.RouteStop
import com.example.fieldtechv20kc.data.model.RouteWithStops
import com.example.fieldtechv20kc.data.repository.RouteRepository
import com.example.fieldtechv20kc.utils.FTLog
import com.example.fieldtechv20kc.utils.GoogleMapsHelper
import com.example.fieldtechv20kc.utils.LocationHelper
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Detail screen for a specific route.
 * Shows stops, progress, and navigation controls.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteDetailScreen(
    navController: NavController,
    routeId: String,
    viewModel: RouteDetailViewModel = run {
        val app = LocalContext.current.applicationContext as FieldTechApplication
        viewModel(
            factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return RouteDetailViewModel(app.routeRepository, routeId) as T
                }
            }
        )
    }
) {
    val context = LocalContext.current
    val routeWithStops by viewModel.routeWithStops.collectAsState()
    val isGettingLocation by viewModel.isGettingLocation.collectAsState()
    
    // Location permission launcher
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        if (granted) {
            viewModel.startNavigation(context)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(routeWithStops?.route?.name ?: "Route", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            if (routeWithStops != null && !routeWithStops!!.route.isCompleted) {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (LocationHelper.hasLocationPermission(context)) {
                            viewModel.startNavigation(context)
                        } else {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    if (isGettingLocation) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Getting Location...")
                    } else {
                        Icon(Icons.Default.Navigation, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (routeWithStops!!.route.completedStopsCount > 0) "Resume Navigation" else "Start Navigation"
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        if (routeWithStops == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            val route = routeWithStops!!.route
            val stops = routeWithStops!!.stops

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                // Route info card
                item {
                    RouteInfoCard(
                        route = route,
                        totalStops = stops.size,
                        onCompleteRoute = {
                            viewModel.completeRoute()
                            navController.popBackStack()
                        }
                    )
                }

                // Stops list
                itemsIndexed(stops, key = { _, stop -> stop.id }) { index, stop ->
                    StopItem(
                        stop = stop,
                        index = index,
                        onToggleComplete = {
                            viewModel.toggleStopCompletion(stop)
                        },
                        onOpenInMaps = {
                            if (stop.latitude != null && stop.longitude != null) {
                                GoogleMapsHelper.openLocation(
                                    context,
                                    stop.latitude,
                                    stop.longitude,
                                    stop.clientName
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun RouteInfoCard(
    route: com.example.fieldtechv20kc.data.model.Route,
    totalStops: Int,
    onCompleteRoute: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (route.isCompleted) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (route.isCompleted) "Route Completed ✓" else "In Progress",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (route.isCompleted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )

                if (!route.isCompleted && route.completedStopsCount == totalStops) {
                    TextButton(onClick = onCompleteRoute) {
                        Text("Mark Complete")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Progress
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Progress: ${route.completedStopsCount}/$totalStops stops",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "${((route.completedStopsCount.toFloat() / totalStops.toFloat()) * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { route.completedStopsCount.toFloat() / totalStops.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Details
            if (!route.intendedAssignee.isNullOrBlank()) {
                Text(
                    text = "Assigned to: ${route.intendedAssignee}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (route.totalEstimatedDistance != null) {
                Text(
                    text = "Distance: ${String.format("%.1f", route.totalEstimatedDistance)} km",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (route.totalEstimatedTime != null) {
                Text(
                    text = "Est. Time: ${route.totalEstimatedTime} min",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = "Created by: ${route.createdBy}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun StopItem(
    stop: RouteStop,
    index: Int,
    onToggleComplete: () -> Unit,
    onOpenInMaps: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (stop.isCompleted) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleComplete)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox
            Checkbox(
                checked = stop.isCompleted,
                onCheckedChange = { onToggleComplete() }
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Stop number
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
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
                    textDecoration = if (stop.isCompleted) TextDecoration.LineThrough else null,
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

            // Open in maps button
            IconButton(onClick = onOpenInMaps) {
                Icon(
                    Icons.Default.Place,
                    contentDescription = "Open in Maps",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// ViewModel
class RouteDetailViewModel(
    private val repository: RouteRepository,
    private val routeId: String
) : ViewModel() {
    private val TAG = "RouteDetailVM"

    private val _routeWithStops = MutableStateFlow<RouteWithStops?>(null)
    val routeWithStops: StateFlow<RouteWithStops?> = _routeWithStops.asStateFlow()

    private val _isGettingLocation = MutableStateFlow(false)
    val isGettingLocation: StateFlow<Boolean> = _isGettingLocation.asStateFlow()

    init {
        loadRoute()
    }

    private fun loadRoute() {
        viewModelScope.launch {
            repository.observeRouteWithStops(routeId).collect { route ->
                _routeWithStops.value = route
            }
        }
    }

    fun toggleStopCompletion(stop: RouteStop) {
        viewModelScope.launch {
            try {
                if (!stop.isCompleted) {
                    // Mark as completed
                    val currentUser = FirebaseAuth.getInstance().currentUser?.displayName ?: "Unknown"
                    repository.markStopCompleted(stop.id, currentUser)
                    FTLog.i(TAG, "✅ Marked stop ${stop.id} as completed")
                } else {
                    // Unmark (set back to incomplete)
                    repository.markStopUncompleted(stop.id)
                    FTLog.i(TAG, "↩️ Unmarked stop ${stop.id} (set to incomplete)")
                }
            } catch (e: Exception) {
                FTLog.e(TAG, "❌ Failed to toggle stop completion: ${e.message}", e)
            }
        }
    }

    fun completeRoute() {
        viewModelScope.launch {
            try {
                val currentUser = FirebaseAuth.getInstance().currentUser?.displayName ?: "Unknown"
                repository.markRouteCompleted(routeId, currentUser)
                FTLog.i(TAG, "Route marked as completed: $routeId")
                
                // Note: Push notifications are handled by Cloud Functions when route status changes
            } catch (e: Exception) {
                FTLog.e(TAG, "Failed to complete route: ${e.message}", e)
            }
        }
    }

    fun startNavigation(context: android.content.Context) {
        viewModelScope.launch {
            _isGettingLocation.value = true
            try {
                // Get current location
                val locationResult = LocationHelper.getCurrentLocation(context)
                FTLog.i(TAG, "📍 Current location: ${locationResult.location?.latitude}, ${locationResult.location?.longitude}")

                // Get remaining (uncompleted) stops
                val route = _routeWithStops.value
                if (route != null) {
                    FTLog.i(TAG, "🗺️ Route: ${route.route.name}, Total stops: ${route.stops.size}")
                    
                    // Log all stops with their coordinates
                    route.stops.forEachIndexed { index, stop ->
                        FTLog.i(TAG, "  Stop ${index + 1}: ${stop.clientName} (${stop.locality})")
                        FTLog.i(TAG, "    - Coords: ${stop.latitude}, ${stop.longitude}")
                        FTLog.i(TAG, "    - Address: ${stop.address}")
                        FTLog.i(TAG, "    - Completed: ${stop.isCompleted}")
                    }
                    
                    val remainingStops = route.stops.filter { !it.isCompleted }
                    FTLog.i(TAG, "🎯 Remaining (uncompleted) stops: ${remainingStops.size}")
                    
                    remainingStops.forEachIndexed { index, stop ->
                        FTLog.i(TAG, "  Remaining ${index + 1}: ${stop.clientName} @ ${stop.latitude},${stop.longitude}")
                    }

                    if (remainingStops.isEmpty()) {
                        FTLog.w(TAG, "⚠️ No remaining stops to navigate")
                        Toast.makeText(context, "All stops completed!", Toast.LENGTH_SHORT).show()
                    } else {
                        // Launch Google Maps
                        FTLog.i(TAG, "🚀 Launching navigation with ${remainingStops.size} stops")
                        GoogleMapsHelper.launchNavigation(
                            context,
                            locationResult.location,
                            remainingStops,
                            showSplitWarning = true
                        )
                    }
                }
            } catch (e: Exception) {
                FTLog.e(TAG, "❌ Failed to start navigation: ${e.message}", e)
                Toast.makeText(context, "Navigation failed: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                _isGettingLocation.value = false
            }
        }
    }
}

