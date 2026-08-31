package com.example.fieldtechv20kc.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.fieldtechv20kc.data.model.ServiceDueThresholds
import com.example.fieldtechv20kc.data.remote.firestore.CompanyServiceSettingsRemote
import com.example.fieldtechv20kc.utils.SettingsManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceDueSettingsScreen(navController: NavController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val settings by settingsManager.settings.collectAsState()
    val remote = remember { CompanyServiceSettingsRemote() }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var soonMonths by remember { mutableStateOf(settings.serviceSoonMonths) }
    var lateMonths by remember { mutableStateOf(settings.serviceLateMonths) }
    var overdueMonths by remember { mutableStateOf(settings.serviceOverdueMonths) }
    var starredOverdueMonths by remember { mutableStateOf(settings.serviceStarredOverdueMonths) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            val remoteThresholds = remote.getThresholds()
            soonMonths = remoteThresholds.soonMonths
            lateMonths = remoteThresholds.lateMonths
            overdueMonths = remoteThresholds.overdueMonths
            starredOverdueMonths = remoteThresholds.starredOverdueMonths
            settingsManager.applyServiceDueThresholdsFromRemote(
                remoteThresholds.soonMonths,
                remoteThresholds.lateMonths,
                remoteThresholds.overdueMonths,
                remoteThresholds.starredOverdueMonths
            )
        } catch (_: Exception) {
            // Keep local defaults if offline / missing doc
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Service due rules") },
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
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Months since last service. Saved for the whole company so every phone matches.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            ThresholdStepper(
                title = "Soon",
                subtitle = "Default 1 month+",
                value = soonMonths,
                onChange = { soonMonths = it }
            )
            ThresholdStepper(
                title = "Late",
                subtitle = "Default 2 months+",
                value = lateMonths,
                onChange = { lateMonths = it }
            )
            ThresholdStepper(
                title = "Overdue",
                subtitle = "Default 3 months+",
                value = overdueMonths,
                onChange = { overdueMonths = it }
            )
            ThresholdStepper(
                title = "Starred → Overdue",
                subtitle = "Starred clients jump to Overdue after this many months (default 1)",
                value = starredOverdueMonths,
                onChange = { starredOverdueMonths = it }
            )

            Text(
                "Weekly overdue digest: Mondays 09:00 Europe/Malta → TECH + MANAGER. Silenced clients are excluded.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = {
                    // Keep ordering sane: soon <= late <= overdue
                    val s = soonMonths.coerceIn(1, 24)
                    val l = lateMonths.coerceAtLeast(s).coerceIn(1, 24)
                    val o = overdueMonths.coerceAtLeast(l).coerceIn(1, 36)
                    val starred = starredOverdueMonths.coerceIn(1, 12)
                    soonMonths = s
                    lateMonths = l
                    overdueMonths = o
                    starredOverdueMonths = starred
                    saving = true
                    scope.launch {
                        try {
                            val thresholds = ServiceDueThresholds(s, l, o, starred)
                            remote.saveThresholds(thresholds)
                            settingsManager.updateServiceDueThresholds(s, l, o, starred)
                            snackbarHostState.showSnackbar("Service due rules saved")
                        } catch (e: Exception) {
                            settingsManager.updateServiceDueThresholds(s, l, o, starred)
                            snackbarHostState.showSnackbar("Saved on this phone only (cloud sync failed)")
                        } finally {
                            saving = false
                        }
                    }
                },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (saving) "Saving…" else "Save")
            }
        }
    }
}

@Composable
private fun ThresholdStepper(
    title: String,
    subtitle: String,
    value: Int,
    onChange: (Int) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(onClick = { if (value > 1) onChange(value - 1) }) { Text("−") }
                Text(
                    "$value month${if (value == 1) "" else "s"}+",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                OutlinedButton(onClick = { if (value < 36) onChange(value + 1) }) { Text("+") }
            }
        }
    }
}
