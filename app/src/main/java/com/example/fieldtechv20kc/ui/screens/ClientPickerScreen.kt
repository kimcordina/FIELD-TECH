package com.example.fieldtechv20kc.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.fieldtechv20kc.data.model.Client
import com.example.fieldtechv20kc.navigation.Screen
import com.example.fieldtechv20kc.ui.components.ClientsGroupedList
import com.example.fieldtechv20kc.viewmodel.ClientsViewModel
import com.example.fieldtechv20kc.viewmodel.ReportViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientPickerScreen(
    navController: NavController,
    clientsViewModel: ClientsViewModel,
    reportViewModel: ReportViewModel
) {
    val clients by clientsViewModel.clients.collectAsState()
    val localities by clientsViewModel.localities.collectAsState()
    
    var locMenuExpanded by remember { mutableStateOf(false) }
    
    // Handle new client creation result
    LaunchedEffect(navController) {
        val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle
        savedStateHandle?.getStateFlow("newClientId", "")?.collect { id ->
            if (id.isNotEmpty()) {
                val name = savedStateHandle.get<String>("newClientName")
                // Return the new client to the previous screen (ClientInfo)
                val prev = navController.previousBackStackEntry?.savedStateHandle
                prev?.set("selectedClientId", id)
                prev?.set("selectedClientName", name)
                savedStateHandle.remove<String>("newClientId")
                savedStateHandle.remove<String>("newClientName")
                navController.popBackStack()
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select Client") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(Screen.ClientNew.route) }
            ) {
                Icon(Icons.Default.Add, "New Client")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Header controls (same as ClientsListScreen)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Search bar - full width
                OutlinedTextField(
                    value = clientsViewModel.query,
                    onValueChange = { clientsViewModel.query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Search") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    placeholder = { Text("name / locality / address") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (clientsViewModel.query.isNotEmpty()) {
                            IconButton(onClick = { clientsViewModel.query = "" }) {
                                Icon(Icons.Default.Clear, "Clear")
                            }
                        }
                    }
                )

                // Filters row - locality dropdown and group toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    @OptIn(ExperimentalMaterial3Api::class)
                    ExposedDropdownMenuBox(
                        expanded = locMenuExpanded,
                        onExpandedChange = { locMenuExpanded = !locMenuExpanded },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            readOnly = true,
                            value = clientsViewModel.selectedLocality ?: "All localities",
                            onValueChange = {},
                            label = { Text("Locality") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = locMenuExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            singleLine = true
                        )
                        ExposedDropdownMenu(
                            expanded = locMenuExpanded,
                            onDismissRequest = { locMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("All localities") },
                                onClick = {
                                    clientsViewModel.selectedLocality = null
                                    locMenuExpanded = false
                                }
                            )
                            localities.forEach { loc ->
                                DropdownMenuItem(
                                    text = { Text(loc) },
                                    onClick = {
                                        clientsViewModel.selectedLocality = loc
                                        locMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Group by locality toggle
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(
                            "Group",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Switch(
                            checked = clientsViewModel.groupByLocality,
                            onCheckedChange = { clientsViewModel.groupByLocality = it }
                        )
                    }
                }
            }
            
            // Grouped list for selection
            if (clients.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No clients found")
                }
            } else {
                ClientsGroupedList(
                    clients = clients,
                    groupByLocality = clientsViewModel.groupByLocality,
                    onClientClick = { client ->
                        // Return result to previous screen
                        val prev = navController.previousBackStackEntry?.savedStateHandle
                        prev?.set("selectedClientId", client.id)
                        prev?.set("selectedClientName", client.name)
                        navController.popBackStack()
                    }
                ) { client, onClick ->
                    // Simple, tappable row variant
                    ClientPickerItem(client = client, onClick = onClick)
                }
            }
        }
    }
}

@Composable
fun ClientPickerItem(
    client: Client,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = client.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (!client.locality.isNullOrBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = client.locality!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        com.example.fieldtechv20kc.ui.components.IslandBadge(client.locality)
                    }
                }
                if (client.address.isNotEmpty()) {
                    Text(
                        text = client.address,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(Icons.Default.ChevronRight, null)
        }
    }
}

