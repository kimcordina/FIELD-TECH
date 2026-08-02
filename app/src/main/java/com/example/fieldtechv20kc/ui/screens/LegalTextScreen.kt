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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.fieldtechv20kc.data.constants.LegalText
import com.example.fieldtechv20kc.data.model.JobType
import com.example.fieldtechv20kc.navigation.Screen
import com.example.fieldtechv20kc.utils.DebugHelper
import com.example.fieldtechv20kc.viewmodel.ReportViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalTextScreen(
    navController: NavController,
    viewModel: ReportViewModel
) {
    val currentJobType by viewModel.currentJobType.collectAsState()
    val currentUnifiedJobType by viewModel.currentUnifiedJobType.collectAsState()
    
    LaunchedEffect(currentJobType, currentUnifiedJobType) {
        DebugHelper.log("LegalTextScreen: currentJobType = $currentJobType")
        DebugHelper.log("LegalTextScreen: currentUnifiedJobType = $currentUnifiedJobType")
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Legal Terms") },
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
            if (currentUnifiedJobType != null) {
                val legalTitle = if (currentUnifiedJobType!!.isCustom && currentUnifiedJobType!!.customJobType?.legalTitle != null) {
                    currentUnifiedJobType!!.customJobType!!.legalTitle
                } else {
                    currentJobType?.let { LegalText.getLegalTitle(it) } ?: "Legal Terms"
                }
                
                val legalText = if (currentUnifiedJobType!!.isCustom && currentUnifiedJobType!!.customJobType?.legalText != null) {
                    currentUnifiedJobType!!.customJobType!!.legalText
                } else {
                    currentJobType?.let { LegalText.getLegalText(it) } ?: "No legal text available."
                }
                
                Text(
                    text = legalTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = legalText,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                
                Text(
                    text = "Please review the above terms carefully. You will be asked to sign to acknowledge these terms on the next screen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = {
                        navController.navigate(Screen.Signature.route)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Continue to Signature")
                }
            } else {
                // Fallback content if job type is not available
                Text(
                    text = "No job type selected. Please go back and select a job type.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = {
                        navController.popBackStack()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Go Back to Job Type")
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = {
                        navController.navigate(Screen.Signature.route)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Continue to Signature Anyway")
                }
            }
        }
    }
}
