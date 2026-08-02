package com.example.fieldtechv20kc.ui.screens

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.example.fieldtechv20kc.data.constants.LegalText
import com.example.fieldtechv20kc.data.database.AppDatabase
import com.example.fieldtechv20kc.FieldTechApplication
import com.example.fieldtechv20kc.data.model.ReportWithDetails
import com.example.fieldtechv20kc.data.remote.firestore.ReportsRemote
import com.example.fieldtechv20kc.data.remote.storage.ReportStorage
import com.example.fieldtechv20kc.navigation.Screen
import com.example.fieldtechv20kc.ui.components.SignaturePadView
import com.example.fieldtechv20kc.utils.PdfGenerator
import com.example.fieldtechv20kc.utils.SettingsManager
import com.example.fieldtechv20kc.viewmodel.ReportViewModel
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignatureScreen(
    navController: NavController,
    viewModel: ReportViewModel
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val settings by settingsManager.settings.collectAsState()
    val currentJobType by viewModel.currentJobType.collectAsState()
    val currentUnifiedJobType by viewModel.currentUnifiedJobType.collectAsState()
    val currentClient by viewModel.currentClient.collectAsState()
    val equipmentInstalledRepaired by viewModel.equipmentInstalledRepaired.collectAsState()
    val serialNumbers by viewModel.serialNumbers.collectAsState()
    val workCarriedOut by viewModel.workCarriedOut.collectAsState()
    val technicianName by viewModel.technicianName.collectAsState()
    val currentPhotos by viewModel.currentPhotos.collectAsState()
    val timeStarted by viewModel.timeStarted.collectAsState()
    val timeCompleted by viewModel.timeCompleted.collectAsState()
    
    var signerName by remember { mutableStateOf("") }
    var isGeneratingPdf by remember { mutableStateOf(false) }
    var signaturePadView by remember { mutableStateOf<SignaturePadView?>(null) }
    var signatureStateVersion by remember { mutableStateOf(0) } // Triggers recomposition
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Derive hasSignature from actual SignaturePadView state (not from LaunchedEffect)
    // This prevents state reset during recompositions from network changes
    // signatureStateVersion dependency ensures recomposition when signature is drawn
    val hasSignature = remember(signaturePadView, signatureStateVersion) {
        derivedStateOf {
            val isEmpty = signaturePadView?.isEmpty() ?: true
            !isEmpty
        }
    }.value
    
    // IMPORTANT: Do NOT use LaunchedEffect(Unit) to reset state!
    // It runs on every recomposition (network changes, etc.) and wipes the signature.
    // State is properly cleared via viewModel.clearCurrentReport() when starting a new report.
    
    // Track if we've verified client on screen entry (avoid showing error during initial load)
    var hasCheckedClient by remember { mutableStateOf(false) }
    
    // Check if client is null - but wait a moment for it to load
    LaunchedEffect(Unit) {
        // Give client time to load (it should be synchronous now, but add small buffer)
        kotlinx.coroutines.delay(100)
        hasCheckedClient = true
        
        val clientName = currentClient?.name ?: "NULL"
        val clientId = currentClient?.id ?: "NULL"
        println("🔍 SignatureScreen: Checking client state... currentClient = $clientName")
        if (currentClient == null) {
            println("❌ CRITICAL ERROR: SignatureScreen opened with NULL client!")
            println("❌ This means the client wasn't set in ReportViewModel")
            println("❌ Check the navigation flow - client should be set before reaching this screen")
        } else {
            println("✅ SignatureScreen: Client loaded successfully")
            println("✅ Client: $clientName (id=$clientId)")
        }
    }
    
    // Show warning dialog if client is null (but only after initial check completes)
    if (hasCheckedClient && currentClient == null) {
        AlertDialog(
            onDismissRequest = { navController.popBackStack() },
            title = { Text("Client Data Missing") },
            text = { 
                Text("The client information is missing. This report cannot be saved. Please go back and start a new report.")
            },
            confirmButton = {
                Button(onClick = { navController.popBackStack() }) {
                    Text("Go Back")
                }
            }
        )
    }
    
    // Save signature function
    fun saveSignatureData(): String? {
        val pad = signaturePadView ?: return null
        if (pad.isEmpty()) return null
        
        try {
            // Get high-quality bitmap with white background for PDF
            val bitmap = pad.getSignatureBitmap(backgroundColor = Color.WHITE)
            
            // Save to file
            val photoDir = File(context.getExternalFilesDir(null), "FieldTechPhotos")
            if (!photoDir.exists()) photoDir.mkdirs()
            
            val signatureFile = File(photoDir, "signature_${System.currentTimeMillis()}.png")
            signatureFile.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            
            // Save vector data for PDF rendering
            val bounds = pad.getSignatureBoundsInView()
            if (bounds != null) {
                val prefs = context.getSharedPreferences("signature_vector_data", android.content.Context.MODE_PRIVATE)
                val editor = prefs.edit()
                
                editor.putFloat("bounds_left", bounds.left.toFloat())
                editor.putFloat("bounds_top", bounds.top.toFloat())
                editor.putFloat("bounds_right", bounds.right.toFloat())
                editor.putFloat("bounds_bottom", bounds.bottom.toFloat())
                
                // Store stroke count and paths
                val strokes = pad.getAllStrokes()
                editor.putInt("stroke_count", strokes.size)
                
                strokes.forEachIndexed { index, stroke ->
                    // Extract points from the path
                    val pathMeasure = android.graphics.PathMeasure(stroke.path, false)
                    val length = pathMeasure.length
                    val numPoints = (length / 2f).toInt().coerceAtLeast(2)
                    
                    val points = mutableListOf<String>()
                    for (i in 0 until numPoints) {
                        val distance = (i.toFloat() / numPoints) * length
                        val coords = FloatArray(2)
                        pathMeasure.getPosTan(distance, coords, null)
                        points.add("${coords[0]},${coords[1]}")
                    }
                    
                    editor.putString("stroke_${index}_points", points.joinToString(","))
                    editor.putString("stroke_${index}_widths", List(numPoints) { stroke.paint.strokeWidth.toString() }.joinToString(","))
                    editor.putInt("stroke_${index}_color", stroke.paint.color)
                }
                
                editor.apply()
            }
            
            bitmap.recycle()
            
            println("DEBUG: Signature saved to ${signatureFile.absolutePath}")
            return signatureFile.absolutePath
            
        } catch (e: Exception) {
            println("DEBUG: Error saving signature: ${e.message}")
            e.printStackTrace()
            return null
        }
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Legal Terms & Signature",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    ) 
                },
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
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            currentUnifiedJobType?.let { unifiedJobType ->
                // Legal Terms
                val legalTitle = if (unifiedJobType.isCustom && unifiedJobType.customJobType?.legalTitle != null) {
                    unifiedJobType.customJobType!!.legalTitle
                } else {
                    currentJobType?.let { LegalText.getLegalTitle(it) } ?: "Legal Terms"
                }
                
                val legalText = if (unifiedJobType.isCustom && unifiedJobType.customJobType?.legalText != null) {
                    unifiedJobType.customJobType!!.legalText
                } else {
                    currentJobType?.let { LegalText.getLegalText(it) } ?: ""
                }
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = legalTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = legalText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                
                // Signer Name
                OutlinedTextField(
                    value = signerName,
                    onValueChange = { signerName = it },
                    label = { Text("Signer Name") },
                    placeholder = { Text("Enter your full name") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                // Signature Pad Section
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Signature",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(
                            onClick = { 
                                signaturePadView?.clear()
                                signatureStateVersion++ // Trigger recomposition
                                println("DEBUG: Signature cleared - hasSignature will auto-update from derivedState")
                            }
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear")
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // AndroidView to embed the custom signature pad
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(312.dp), // 25% larger: 250 * 1.25 = 312.5 ≈ 312
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = androidx.compose.ui.graphics.Color.White
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                SignaturePadView(ctx).apply {
                                    setPenColor(Color.BLACK)
                                    setPenWidth(4f * resources.displayMetrics.density)
                                    setBackgroundColor(Color.WHITE)
                                    signaturePadView = this
                                    
                                    // Trigger recomposition when signature is drawn
                                    setOnTouchListener { v, event ->
                                        val result = onTouchEvent(event)
                                        if (event.action == android.view.MotionEvent.ACTION_UP) {
                                            // Update version to trigger hasSignature recalculation
                                            signatureStateVersion++
                                        }
                                        result
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                
                // Save Button with clear visual feedback
                val isButtonEnabled = hasSignature && signerName.isNotBlank() && !isGeneratingPdf
                
                Button(
                    onClick = {
                        println("🖊️ SAVE: Button clicked!")
                        println("🖊️ SAVE: hasSignature = $hasSignature")
                        println("🖊️ SAVE: signerName = '$signerName'")
                        println("🖊️ SAVE: signaturePadView?.isEmpty() = ${signaturePadView?.isEmpty()}")
                        println("🖊️ SAVE: isButtonEnabled = $isButtonEnabled")
                        
                        // Extract client ID and task ID early to avoid null issues
                        val clientId = currentClient?.id
                        val clientName = currentClient?.name
                        val taskId = viewModel.linkedTaskId.value
                        
                        // Check if client exists before attempting save
                        if (clientId == null) {
                            println("❌ SAVE ERROR: currentClient is NULL!")
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    message = "Error: Client data is missing. Please go back and restart the report.",
                                    duration = SnackbarDuration.Long
                                )
                            }
                            return@Button
                        }
                        
                        // Use the signature pad itself as the source of truth (not the
                        // derived hasSignature snapshot, which can lag behind during
                        // recompositions triggered by connectivity changes)
                        val signatureIsPresent = signaturePadView?.isEmpty() == false
                        
                        if (signatureIsPresent && signerName.isNotBlank()) {
                            println("✅ SAVE: All conditions met, starting PDF generation...")
                            println("✅ SAVE: Client = $clientName (id=$clientId), Task = $taskId")
                            isGeneratingPdf = true
                            
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                try {
                                    println("DEBUG: Saving signature...")
                                    val signaturePath = saveSignatureData()
                                    
                                    if (signaturePath != null) {
                                        viewModel.setSignerName(signerName)
                                        viewModel.setSignatureFilePath(signaturePath)
                                        
                                        println("DEBUG: Generating PDF...")

                                        // Allocate unique NC-####-YY ref once; same value goes on PDF + DB row
                                        val reportRef = com.example.fieldtechv20kc.utils.ReportRefAllocator.allocate(context)
                                        println("DEBUG: Allocated reportRef=$reportRef")
                                        
                                        val tempReport = com.example.fieldtechv20kc.data.model.Report(
                                            clientId = clientId,
                                            jobType = currentJobType ?: com.example.fieldtechv20kc.data.model.JobType.SERVICE_REPAIR,
                                            equipmentInstalledRepaired = equipmentInstalledRepaired,
                                            serialNumbers = serialNumbers,
                                            workCarriedOut = workCarriedOut,
                                            technicianName = technicianName,
                                            findings = "",
                                            signerName = signerName,
                                            signatureData = signaturePath,
                                            pdfPath = "",
                                            timeStarted = timeStarted,
                                            timeCompleted = timeCompleted,
                                            reportRef = reportRef
                                        )
                                        
                                        val tempClient = currentClient ?: com.example.fieldtechv20kc.data.model.Client(
                                            id = "unknown",
                                            name = "Unknown",
                                            locality = "Unknown",
                                            legalName = "",
                                            companyNumber = "",
                                            address = ""
                                        )
                                        
                                        val reportWithDetails = ReportWithDetails(
                                            report = tempReport,
                                            client = tempClient,
                                            photos = currentPhotos
                                        )
                                        
                                        // Create PdfGenerator without cloud services (they're not needed for PDF generation)
                                        // Cloud uploads are handled by OutboxWorker after the PDF is saved locally
                                        println("DEBUG: Creating PDF generator...")
                                        val pdfGenerator = PdfGenerator(
                                            context = context,
                                            reportStorage = null,
                                            reportsRemote = null
                                        )
                                        println("DEBUG: Calling generateReportPdf...")
                                        val pdfPath = pdfGenerator.generateReportPdf(
                                            reportWithDetails = reportWithDetails,
                                            settings = settings
                                        )
                                        println("DEBUG: PDF generation complete. pdfPath='$pdfPath'")
                                        
                                        if (pdfPath.isNotEmpty()) {
                                            println("DEBUG: PDF generated successfully, calling saveReport...")
                                            
                                            // Call the new parameterized saveReport with explicit values
                                            val saveResult = viewModel.saveReport(
                                                clientId = clientId,
                                                taskId = taskId,
                                                pdfPath = pdfPath,
                                                signatureFilePath = signaturePath,
                                                reportRef = reportRef
                                            )
                                            
                                            println("DEBUG: saveReport returned result=$saveResult")
                                            
                                            // Handle the result
                                            when (saveResult) {
                                                is com.example.fieldtechv20kc.viewmodel.SaveResult.Success -> {
                                                    println("DEBUG: ✅ Report saved successfully!")
                                                    
                                                    // Get the report ID from the result
                                                    val reportId = saveResult.reportId
                                                    
                                                    // Enqueue uploads for PDF and metadata
                                                    try {
                                                        val database = AppDatabase.getDatabase(context)
                                                        val outbox = try {
                                                            com.example.fieldtechv20kc.data.repository.OutboxRepository.get()
                                                        } catch (e: Exception) {
                                                            com.example.fieldtechv20kc.data.repository.OutboxRepository.init(database)
                                                            com.example.fieldtechv20kc.data.repository.OutboxRepository.get()
                                                        }
                                                        
                                                        outbox.enqueueUpsertReport(reportId)
                                                        outbox.enqueueUploadPdf(reportId, pdfPath)
                                                        
                                                        // Kick the worker
                                                        com.example.fieldtechv20kc.utils.OutboxWorkHelpers.kickNow(context)
                                                        android.util.Log.d("FT/OUTBOX", "Enqueued uploads for report=$reportId")
                                                    } catch (e: Exception) {
                                                        android.util.Log.e("FT/OUTBOX", "Failed to enqueue uploads", e)
                                                    }
                                                    
                                                    // Auto-email report if enabled
                                                    val settingsManager = SettingsManager.getInstance(context)
                                                    val settings = settingsManager.settings.value
                                                    if (com.example.fieldtechv20kc.utils.EmailHelper.isEmailConfigured(settings)) {
                                                        val success = com.example.fieldtechv20kc.utils.EmailHelper.emailReport(
                                                            context = context,
                                                            pdfPath = pdfPath,
                                                            recipientEmail = settings.reportEmailRecipient,
                                                            clientName = clientName ?: "Client"
                                                        )
                                                        if (success) {
                                                            android.util.Log.d("SignatureScreen", "Auto-email report launched successfully")
                                                        }
                                                    }
                                                    
                                                    viewModel.clearCurrentReport()
                                                    
                                                    // Check connectivity so the technician gets clear feedback offline
                                                    val isOnline = try {
                                                        com.example.fieldtechv20kc.utils.ConnectivityObserver(context).isCurrentlyConnected()
                                                    } catch (e: Exception) {
                                                        true
                                                    }
                                                    
                                                    // Navigate back to the Reports tab (root) - must be on Main thread.
                                                    // IMPORTANT: navigate FIRST, then confirm via Toast. The previous
                                                    // approach suspended on showSnackbar() before navigating, so if the
                                                    // technician tapped back/a tab during the snackbar the navigation was
                                                    // cancelled and the old (half-cleared) report flow stayed alive -
                                                    // causing stale client data in the next report and a save that could
                                                    // never succeed. A Toast survives navigation, a snackbar does not.
                                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                        android.widget.Toast.makeText(
                                                            context,
                                                            if (isOnline) {
                                                                "Report saved successfully!"
                                                            } else {
                                                                "Report saved on device — it will upload automatically when you're back online"
                                                            },
                                                            android.widget.Toast.LENGTH_LONG
                                                        ).show()
                                                        navController.navigate(Screen.SavedReports.route) {
                                                            popUpTo(navController.graph.startDestinationId) {
                                                                inclusive = false
                                                            }
                                                            launchSingleTop = true
                                                        }
                                                    }
                                                }
                                                is com.example.fieldtechv20kc.viewmodel.SaveResult.Failure -> {
                                                    println("DEBUG: ❌ Report save failed: ${saveResult.reason}")
                                                    android.util.Log.e("SignatureScreen", "Report save failed: ${saveResult.reason}")
                                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                        snackbarHostState.showSnackbar(
                                                            message = "Failed to save report: ${saveResult.reason}",
                                                            duration = SnackbarDuration.Long
                                                        )
                                                    }
                                                }
                                            }
                                        } else {
                                            throw Exception("PDF generation failed: empty path")
                                        }
                                    } else {
                                        throw Exception("Failed to save signature")
                                    }
                                } catch (e: Exception) {
                                    println("DEBUG: Error during report save: ${e.message}")
                                    e.printStackTrace()
                                    android.util.Log.e("SignatureScreen", "Error saving report", e)
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        snackbarHostState.showSnackbar(
                                            message = "Error saving report: ${e.message}",
                                            duration = SnackbarDuration.Long
                                        )
                                    }
                                } finally {
                                    println("DEBUG: Finally block - setting isGeneratingPdf to false")
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        isGeneratingPdf = false
                                    }
                                }
                            }
                        } else {
                            println("DEBUG: Conditions NOT met!")
                            println("DEBUG: Missing: ${if (!hasSignature) "signature" else ""} ${if (signerName.isBlank()) "signer name" else ""}")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = isButtonEnabled,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isButtonEnabled) MaterialTheme.colorScheme.primary 
                                        else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (isButtonEnabled) MaterialTheme.colorScheme.onPrimary
                                       else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    if (isGeneratingPdf) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Generating Report...")
                    } else {
                        Text(
                            "Save and Complete Report",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                
                // Helper text when button is disabled (clear user feedback)
                if (!isButtonEnabled && !isGeneratingPdf) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = when {
                            !hasSignature && signerName.isBlank() -> "⚠️ Please add signature and enter signer name above"
                            !hasSignature -> "⚠️ Please add signature above"
                            signerName.isBlank() -> "⚠️ Please enter signer name above"
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
