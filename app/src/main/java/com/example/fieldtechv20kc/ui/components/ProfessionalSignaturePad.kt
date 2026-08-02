package com.example.fieldtechv20kc.ui.components

import android.graphics.*
import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.*

// Constants for high-quality output
private const val OUTPUT_SIZE = 2400  // Square output for 6cm x 6cm at 300+ DPI
private const val BITMAP_DPI = 300    // Standard print quality DPI
private const val CONSTANT_STROKE_WIDTH = 4f  // Constant thickness regardless of speed
private const val VELOCITY_FILTER_WEIGHT = 0.7f
private const val SMOOTHING_RATIO = 0.25f
private const val MIN_SEGMENT_LENGTH = 3f

/**
 * Professional-grade signature pad with high-resolution output, smooth curves,
 * and excellent touch responsiveness for capturing all signature movements.
 * Outputs 2400x2400px bitmaps at 300 DPI for print-quality signatures.
 */
@Composable
fun ProfessionalSignaturePad(
    onSignatureChanged: (Boolean) -> Unit,
    onSignatureDataChanged: (String) -> Unit = {},
    onSignatureFileChanged: (String?) -> Unit = {},
    modifier: Modifier = Modifier,
    clearSignature: Boolean = false // New parameter to trigger clearing
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    
    // State management
    var strokes by remember { mutableStateOf(listOf<EnhancedStroke>()) }
    var currentStroke by remember { mutableStateOf<EnhancedStroke?>(null) }
    var lastVelocity by remember { mutableStateOf(0f) }
    var signatureBounds by remember { mutableStateOf<RectF?>(null) }
    var hasSignature by remember { mutableStateOf(false) }
    var currentStrokeUpdate by remember { mutableStateOf(0) } // Force recomposition
    
    // Store vector data for optional vector PDF generation
    var vectorStrokes by remember { mutableStateOf(listOf<VectorStrokeData>()) }
    
    // Clear signature when clearSignature parameter is true
    LaunchedEffect(clearSignature) {
        if (clearSignature) {
            strokes = emptyList()
            currentStroke = null
            signatureBounds = null
            hasSignature = false
            vectorStrokes = emptyList()
            currentStrokeUpdate = 0
            
            // Clear SharedPreferences data
            try {
                val prefs = context.getSharedPreferences("signature_vector_data", android.content.Context.MODE_PRIVATE)
                prefs.edit().clear().apply()
                println("DEBUG: Cleared signature data from SharedPreferences")
            } catch (e: Exception) {
                println("DEBUG: Error clearing signature data: ${e.message}")
            }
            
            onSignatureChanged(false)
            onSignatureDataChanged("")
            onSignatureFileChanged(null)
        }
    }
    
    Column(
        modifier = modifier
    ) {
        // Signature area with nice background
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp), // Good size for signing
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .border(
                        width = 1.dp,
                        color = Color(0xFFE0E0E0),
                        shape = RoundedCornerShape(8.dp)
                    )
            ) {
                // Signature canvas with enhanced rendering
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(currentStrokeUpdate) { // Force recomposition when currentStroke changes
                            detectDragGestures(
                                onDragStart = { offset ->
                                    // Drawing starts
                                    currentStroke = EnhancedStroke(
                                        points = mutableListOf(
                                            StrokePoint(
                                                position = offset,
                                                timestamp = System.currentTimeMillis(),
                                                pressure = 1f,
                                                width = CONSTANT_STROKE_WIDTH
                                            )
                                        )
                                    )
                                    lastVelocity = 0f
                                    hasSignature = true
                                    onSignatureChanged(true)
                                    // Force recomposition by updating counter
                                    currentStrokeUpdate++
                                    println("DEBUG: onDragStart - currentStroke created with ${currentStroke?.points?.size} points, update counter: $currentStrokeUpdate")
                                },
                                onDrag = { _, dragAmount ->
                                    currentStroke?.let { stroke ->
                                        val lastPoint = stroke.points.lastOrNull() ?: return@let
                                        val currentTime = System.currentTimeMillis()
                                        val newPosition = lastPoint.position + dragAmount
                                        
                                        // Calculate distance for noise reduction
                                        val distance = sqrt(
                                            dragAmount.x * dragAmount.x + dragAmount.y * dragAmount.y
                                        )
                                        
                                        // Skip if movement is too small (reduces noise)
                                        if (distance < MIN_SEGMENT_LENGTH) return@let
                                        
                                        // Add point with constant width
                                        stroke.points.add(
                                            StrokePoint(
                                                position = newPosition,
                                                timestamp = currentTime,
                                                pressure = 1f,
                                                width = CONSTANT_STROKE_WIDTH
                                            )
                                        )
                                        // Force recomposition by updating counter
                                        currentStrokeUpdate++
                                        println("DEBUG: onDrag - currentStroke now has ${stroke.points.size} points, update counter: $currentStrokeUpdate")
                                    }
                                },
                                onDragEnd = {
                                    currentStroke?.let { stroke ->
                                        stroke.isComplete = true
                                        strokes = strokes + stroke
                                        
                                        // Store vector data for PDF
                                        val vectorData = VectorStrokeData(
                                            points = stroke.points.map { 
                                                PointF(it.position.x, it.position.y) 
                                            },
                                            widths = stroke.points.map { it.width }
                                        )
                                        vectorStrokes = vectorStrokes + vectorData
                                        
                                        currentStroke = null
                                        
                                        // Update bounds and generate signature
                                        val newBounds = updateSignatureBounds(strokes)
                                        signatureBounds = newBounds
                                        
                                        println("DEBUG: Signature completed - strokes: ${strokes.size}, bounds: $newBounds")
                                        
                                        // Only generate signature if we have valid bounds
                                        if (newBounds != null && strokes.isNotEmpty()) {
                                            println("DEBUG: Generating enhanced signature with bounds: $newBounds")
                                            generateEnhancedSignature(
                                                strokes = strokes,
                                                vectorStrokes = vectorStrokes,
                                                bounds = newBounds,
                                                context = context,
                                                onSignatureChanged = onSignatureChanged,
                                                onSignatureDataChanged = onSignatureDataChanged,
                                                onSignatureFileChanged = onSignatureFileChanged
                                            )
                                        } else {
                                            // Still mark as having signature even if generation fails
                                            println("DEBUG: Signature bounds are null or no strokes, but marking as having signature")
                                            onSignatureChanged(true)
                                        }
                                    }
                                }
                            )
                        }
                ) {
                    // Draw all completed strokes
                    strokes.forEach { stroke ->
                        drawEnhancedStroke(stroke, density)
                    }
                    
                    // Draw current stroke being drawn (real-time)
                    currentStroke?.let { stroke ->
                        println("DEBUG: Drawing currentStroke with ${stroke.points.size} points")
                        drawEnhancedStroke(stroke, density)
                    }
                }
                
                // Professional placeholder text
                if (!hasSignature) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Sign Here",
                            color = Color(0xFF9E9E9E),
                            fontSize = 18.sp,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Use your finger or stylus",
                            color = Color(0xFFBDBDBD),
                            fontSize = 14.sp,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "High-resolution 2400x2400px output",
                            color = Color(0xFFBDBDBD),
                            fontSize = 12.sp,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Professional action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = {
                    strokes = emptyList()
                    vectorStrokes = emptyList()
                    currentStroke = null
                    signatureBounds = null
                    hasSignature = false
                    onSignatureChanged(false)
                    onSignatureDataChanged("")
                    onSignatureFileChanged(null)
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFF666666)
                )
            ) {
                Text("Clear")
            }
            
            if (hasSignature) {
                Button(
                    onClick = {
                        // Regenerate signature data
                        generateEnhancedSignature(
                            strokes = strokes,
                            vectorStrokes = vectorStrokes,
                            bounds = signatureBounds,
                            context = context,
                            onSignatureChanged = onSignatureChanged,
                            onSignatureDataChanged = onSignatureDataChanged,
                            onSignatureFileChanged = onSignatureFileChanged
                        )
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2196F3)
                    )
                ) {
                    Text("Save")
                }
            }
        }
    }
}

// Data classes
private data class EnhancedStroke(
    val points: MutableList<StrokePoint>,
    val color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Black,
    var isComplete: Boolean = false
)

private data class StrokePoint(
    val position: Offset,
    val timestamp: Long,
    val pressure: Float = 1f,
    val width: Float
)

data class VectorStrokeData(
    val points: List<android.graphics.PointF>,
    val widths: List<Float>,
    val color: Int = android.graphics.Color.BLACK
)

// Enhanced drawing with cubic Bezier curves
private fun DrawScope.drawEnhancedStroke(stroke: EnhancedStroke, density: androidx.compose.ui.unit.Density) {
    val points = stroke.points
    if (points.size < 2) return
    
    val path = Path()
    path.moveTo(points[0].position.x, points[0].position.y)
    
    // Use cubic Bezier curves for ultra-smooth lines
    for (i in 1 until points.size) {
        val p0 = if (i > 0) points[i - 1] else points[0]
        val p1 = points[i]
        val p2 = if (i < points.size - 1) points[i + 1] else p1
        
        // Calculate control points
        val cp1x = p0.position.x + (p1.position.x - p0.position.x) * SMOOTHING_RATIO
        val cp1y = p0.position.y + (p1.position.y - p0.position.y) * SMOOTHING_RATIO
        val cp2x = p1.position.x - (p2.position.x - p0.position.x) * SMOOTHING_RATIO
        val cp2y = p1.position.y - (p2.position.y - p0.position.y) * SMOOTHING_RATIO
        
        path.cubicTo(
            cp1x, cp1y,
            cp2x, cp2y,
            p1.position.x, p1.position.y
        )
    }
    
    drawPath(
        path = path,
        color = stroke.color,
        style = Stroke(
            width = CONSTANT_STROKE_WIDTH * density.density,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}

// Update signature bounds
private fun updateSignatureBounds(strokes: List<EnhancedStroke>): RectF? {
    if (strokes.isEmpty()) return null
    
    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxX = Float.MIN_VALUE
    var maxY = Float.MIN_VALUE
    
    strokes.forEach { stroke ->
        stroke.points.forEach { point ->
            minX = min(minX, point.position.x)
            minY = min(minY, point.position.y)
            maxX = max(maxX, point.position.x)
            maxY = max(maxY, point.position.y)
        }
    }
    
    return RectF(minX, minY, maxX, maxY)
}

// Generate high-quality signature bitmap
private fun generateEnhancedSignature(
    strokes: List<EnhancedStroke>,
    vectorStrokes: List<VectorStrokeData>,
    bounds: RectF?,
    context: android.content.Context,
    onSignatureChanged: (Boolean) -> Unit,
    onSignatureDataChanged: (String) -> Unit,
    onSignatureFileChanged: (String?) -> Unit
) {
    if (strokes.isEmpty() || bounds == null) {
        // Don't change signature state here - let the caller handle it
        return
    }
    
    try {
        // Create high-resolution square bitmap
        val bitmap = Bitmap.createBitmap(
            OUTPUT_SIZE, 
            OUTPUT_SIZE, 
            Bitmap.Config.ARGB_8888
        ).apply {
            setDensity(BITMAP_DPI)
        }
        
        val canvas = android.graphics.Canvas(bitmap)
        
        // Configure paint for maximum quality
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            isDither = true
            style = android.graphics.Paint.Style.STROKE
            strokeCap = android.graphics.Paint.Cap.ROUND
            strokeJoin = android.graphics.Paint.Join.ROUND
            color = android.graphics.Color.BLACK
            isSubpixelText = true
            flags = android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.DITHER_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG
        }
        
        // White background
        canvas.drawColor(android.graphics.Color.WHITE)
        
        // Calculate scaling to fit signature
        val padding = OUTPUT_SIZE * 0.1f
        val signatureWidth = bounds.width()
        val signatureHeight = bounds.height()
        val availableSize = OUTPUT_SIZE - (2 * padding)
        val scale = min(
            availableSize / signatureWidth,
            availableSize / signatureHeight
        )
        
        // Center the signature
        val scaledWidth = signatureWidth * scale
        val scaledHeight = signatureHeight * scale
        val offsetX = (OUTPUT_SIZE - scaledWidth) / 2
        val offsetY = (OUTPUT_SIZE - scaledHeight) / 2
        
        // Draw strokes with enhanced quality
        strokes.forEach { stroke ->
            if (stroke.points.size >= 2) {
                val path = android.graphics.Path()
                
                val firstPoint = stroke.points[0]
                val startX = (firstPoint.position.x - bounds.left) * scale + offsetX
                val startY = (firstPoint.position.y - bounds.top) * scale + offsetY
                path.moveTo(startX, startY)
                
                // Draw smooth cubic Bezier curves
                for (i in 1 until stroke.points.size) {
                    val p0 = stroke.points[i - 1]
                    val p1 = stroke.points[i]
                    
                    val x0 = (p0.position.x - bounds.left) * scale + offsetX
                    val y0 = (p0.position.y - bounds.top) * scale + offsetY
                    val x1 = (p1.position.x - bounds.left) * scale + offsetX
                    val y1 = (p1.position.y - bounds.top) * scale + offsetY
                    
                    val dx = x1 - x0
                    val dy = y1 - y0
                    path.cubicTo(
                        x0 + dx * 0.33f, y0 + dy * 0.33f,
                        x0 + dx * 0.67f, y0 + dy * 0.67f,
                        x1, y1
                    )
                    
                    paint.strokeWidth = CONSTANT_STROKE_WIDTH * scale
                }
                
                canvas.drawPath(path, paint)
            }
        }
        
        // Save with maximum quality
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        val base64String = Base64.encodeToString(byteArray, Base64.NO_WRAP)
        
        // Save to file
        val signatureDir = File(context.getExternalFilesDir(null), "Signatures")
        if (!signatureDir.exists()) {
            signatureDir.mkdirs()
        }
        
        val timeStamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
            .format(java.util.Date())
        val signatureFile = File(signatureDir, "signature_${timeStamp}.png")
        
        try {
            FileOutputStream(signatureFile).use { fos ->
                fos.write(byteArray)
            }
            
            // Also save vector data for PDF generation
            saveVectorData(context, vectorStrokes, bounds)
            
            onSignatureFileChanged(signatureFile.absolutePath)
        } catch (e: Exception) {
            e.printStackTrace()
            onSignatureFileChanged(null)
        }
        
        onSignatureDataChanged(base64String)
        onSignatureChanged(true)
        
        bitmap.recycle()
        
    } catch (e: Exception) {
        println("Error generating enhanced signature: ${e.message}")
        e.printStackTrace()
        onSignatureChanged(false)
    }
}

// Save vector data for PDF generation
private fun saveVectorData(
    context: android.content.Context,
    vectorStrokes: List<VectorStrokeData>,
    bounds: RectF
) {
    try {
        // Store vector data in SharedPreferences for PDF generator to access
        val prefs = context.getSharedPreferences("signature_vector_data", android.content.Context.MODE_PRIVATE)
        val editor = prefs.edit()
        
        // Store bounds
        editor.putFloat("bounds_left", bounds.left)
        editor.putFloat("bounds_top", bounds.top)
        editor.putFloat("bounds_right", bounds.right)
        editor.putFloat("bounds_bottom", bounds.bottom)
        
        // Store stroke count
        editor.putInt("stroke_count", vectorStrokes.size)
        
        // Store each stroke's data
        vectorStrokes.forEachIndexed { index, stroke ->
            val pointsJson = stroke.points.joinToString(",") { "${it.x},${it.y}" }
            val widthsJson = stroke.widths.joinToString(",")
            
            editor.putString("stroke_${index}_points", pointsJson)
            editor.putString("stroke_${index}_widths", widthsJson)
            editor.putInt("stroke_${index}_color", stroke.color)
        }
        
        editor.apply()
        println("DEBUG: Vector signature data saved to SharedPreferences")
        
    } catch (e: Exception) {
        println("Error saving vector data: ${e.message}")
        e.printStackTrace()
    }
}