package com.example.fieldtechv20kc.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.io.File
import java.io.IOException

/**
 * Holds the active MediaRecorder state so parent screens can finalize an
 * in-progress recording before submitting (e.g. "Create Request" pressed
 * while still recording).
 *
 * IMPORTANT: The recorded file is only valid after stop() has been called
 * (MPEG-4 requires the header to be written on stop). Never upload a file
 * while isRecording is true.
 */
@Stable
class VoiceRecorderController {
    var isRecording by mutableStateOf(false)
        internal set

    internal var recorder: MediaRecorder? = null
    internal var outputPath: String? = null

    /**
     * Stops and finalizes any active recording.
     * Returns the finalized file path, or null if nothing was recording
     * or the recording was too short to produce a valid file.
     */
    fun stopAndFinalize(): String? {
        val r = recorder ?: return null
        val path = outputPath
        return try {
            r.stop()
            r.release()
            path
        } catch (e: Exception) {
            // stop() throws if recording is too short / no valid data captured
            try { r.release() } catch (_: Exception) {}
            try { path?.let { File(it).delete() } } catch (_: Exception) {}
            null
        } finally {
            recorder = null
            outputPath = null
            isRecording = false
        }
    }
}

@Composable
fun rememberVoiceRecorderController(): VoiceRecorderController {
    return remember { VoiceRecorderController() }
}

@Composable
fun VoiceRecorderSection(
    voiceUri: String?,
    onVoiceUriChanged: (String?) -> Unit,
    modifier: Modifier = Modifier,
    controller: VoiceRecorderController = rememberVoiceRecorderController()
) {
    val context = LocalContext.current
    var hasPermission by remember { 
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
    }
    
    // Cleanup on dispose - finalize any in-progress recording
    DisposableEffect(controller) {
        onDispose {
            controller.stopAndFinalize()
        }
    }
    
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Voice Note (Optional)",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
        
        if (voiceUri != null && !controller.isRecording) {
            // Show recorded voice note
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
                            contentDescription = "Voice Note",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "Voice note recorded",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    IconButton(onClick = { 
                        onVoiceUriChanged(null)
                        // Delete the file
                        try {
                            File(voiceUri).delete()
                        } catch (e: Exception) {
                            // Ignore delete errors
                        }
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Remove")
                    }
                }
            }
        } else if (controller.isRecording) {
            // Show recording in progress
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
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
                            Icons.Default.FiberManualRecord,
                            contentDescription = "Recording",
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "Recording... tap Stop to save",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = {
                        // Finalize recording, then attach the completed file
                        val finalizedPath = controller.stopAndFinalize()
                        onVoiceUriChanged(finalizedPath)
                    }) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop Recording")
                    }
                }
            }
        } else {
            // Show record button
            if (hasPermission) {
                OutlinedButton(
                    onClick = {
                        try {
                            val outputFile = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
                            
                            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                MediaRecorder(context)
                            } else {
                                @Suppress("DEPRECATION")
                                MediaRecorder()
                            }.apply {
                                // Use VOICE_RECOGNITION for better voice quality
                                setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                                // Set high quality audio parameters
                                setAudioEncodingBitRate(128000) // 128 kbps - good quality
                                setAudioSamplingRate(44100)     // 44.1 kHz - CD quality
                                setOutputFile(outputFile.absolutePath)
                                
                                prepare()
                                start()
                            }
                            
                            controller.recorder = recorder
                            controller.outputPath = outputFile.absolutePath
                            controller.isRecording = true
                            // NOTE: onVoiceUriChanged is intentionally NOT called here.
                            // The file is incomplete until stop() finalizes it - attaching
                            // it now would let the parent upload a corrupt recording.
                        } catch (e: IOException) {
                            // Handle error
                            controller.isRecording = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Record")
                }
            } else {
                OutlinedButton(
                    onClick = {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Grant Microphone Permission")
                }
            }
        }
    }
}
