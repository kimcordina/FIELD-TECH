package com.example.fieldtechv20kc.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.consumeAllChanges
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.example.fieldtechv20kc.utils.SignatureUtils

@Composable
fun UltraSimpleSignaturePad(
    onSignatureChanged: (Boolean) -> Unit,
    onSignatureDataChanged: (String) -> Unit = {},
    onSignatureFileChanged: (String?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // Use separate strokes to handle disconnected parts
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var currentStroke = remember { mutableStateListOf<Offset>() }
    var isDrawing by remember { mutableStateOf(false) }
    var hasSignature by remember { mutableStateOf(false) }
    var canvasSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }
    var dragStartTime by remember { mutableStateOf(0L) }
    var dragStartPosition by remember { mutableStateOf(Offset.Zero) }
    
    // Recreate paths for all strokes
    val paths = remember(strokes.size) {
        strokes.map { strokePoints ->
            Path().apply {
                if (strokePoints.isNotEmpty()) {
                    moveTo(strokePoints[0].x, strokePoints[0].y)
                    for (i in 1 until strokePoints.size) {
                        lineTo(strokePoints[i].x, strokePoints[i].y)
                    }
                }
            }
        }
    }
    
    // Current stroke path
    val currentPath = remember(currentStroke.size) {
        Path().apply {
            if (currentStroke.isNotEmpty()) {
                moveTo(currentStroke[0].x, currentStroke[0].y)
                for (i in 1 until currentStroke.size) {
                    lineTo(currentStroke[i].x, currentStroke[i].y)
                }
            }
        }
    }
    
    Column(
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
                .background(Color.White)
                .border(2.dp, Color.Gray, RoundedCornerShape(8.dp))
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { size ->
                        canvasSize = androidx.compose.ui.geometry.Size(size.width.toFloat(), size.height.toFloat())
                    }
                    .pointerInput(Unit) {
                        // Use a single gesture detector that handles both taps and drags
                        detectDragGestures(
                            onDragStart = { offset ->
                                // Check if the touch is within bounds
                                if (offset.x >= 0 && offset.x <= canvasSize.width && 
                                    offset.y >= 0 && offset.y <= canvasSize.height) {
                                    isDrawing = true
                                    dragStartTime = System.currentTimeMillis()
                                    dragStartPosition = offset
                                    // Start a new stroke
                                    currentStroke.clear()
                                    currentStroke.add(offset)
                                    hasSignature = true
                                    onSignatureChanged(true)
                                }
                            },
                            onDrag = { change, _ ->
                                if (isDrawing) {
                                    // Check if the drag position is within bounds
                                    val position = change.position
                                    if (position.x >= 0 && position.x <= canvasSize.width && 
                                        position.y >= 0 && position.y <= canvasSize.height) {
                                        currentStroke.add(position)
                                    } else {
                                        // If we go outside bounds, stop drawing
                                        isDrawing = false
                                    }
                                }
                            },
                            onDragEnd = {
                                isDrawing = false
                                val dragDuration = System.currentTimeMillis() - dragStartTime
                                val dragDistance = (dragStartPosition - (currentStroke.lastOrNull() ?: dragStartPosition)).getDistance()
                                
                                // If it was a very short drag with minimal movement, treat it as a tap (dot)
                                if (dragDuration < 200 && dragDistance < 10f && currentStroke.size <= 2) {
                                    // Create a small dot by adding a second point close to the first
                                    if (currentStroke.size == 1) {
                                        val dotPoint = Offset(
                                            currentStroke[0].x + 2f,
                                            currentStroke[0].y + 2f
                                        )
                                        currentStroke.add(dotPoint)
                                    }
                                }
                                
                                // Save the current stroke if it has points
                                if (currentStroke.isNotEmpty()) {
                                    strokes.add(currentStroke.toList())
                                    currentStroke.clear()
                                }
                                
                                // Save signature as image file when done
                                if (hasSignature) {
                                    val signatureFilePath = SignatureUtils.saveSignatureAsImage(
                                        context = context,
                                        signatureStrokes = strokes,
                                        signerName = "Client"
                                    )
                                    println("DEBUG: Signature saved to file: $signatureFilePath")
                                    onSignatureFileChanged(signatureFilePath)
                                    
                                    // Also generate Base64 for backward compatibility
                                    val allPoints = strokes.flatten()
                                    val base64 = createSignatureBase64(allPoints)
                                    onSignatureDataChanged(base64)
                                }
                            }
                        )
                    }
            ) {
                // Draw all completed strokes
                paths.forEach { path ->
                    drawPath(
                        path = path,
                        color = Color.Black,
                        style = Stroke(width = 8.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    )
                }
                
                // Draw the current stroke being drawn
                    drawPath(
                        path = currentPath,
                        color = Color.Black,
                        style = Stroke(width = 8.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    )
            }
            
            if (!hasSignature) {
                Text(
                    text = "Sign here",
                    color = Color.Gray,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
                Button(
                    onClick = {
                        strokes.clear()
                        currentStroke.clear()
                        hasSignature = false
                        onSignatureChanged(false)
                        onSignatureDataChanged("")
                        onSignatureFileChanged(null)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Clear Signature")
                }
    }
}

private fun createSignatureBase64(points: List<Offset>): String {
    return try {
        if (points.isEmpty()) return ""
        
        val width = 300
        val height = 100
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        
        canvas.drawColor(android.graphics.Color.WHITE)
        
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            strokeWidth = 6f
            style = android.graphics.Paint.Style.STROKE
            strokeCap = android.graphics.Paint.Cap.ROUND
        }
        
        if (points.size > 1) {
            val path = android.graphics.Path()
            path.moveTo(points[0].x, points[0].y)
            for (i in 1 until points.size) {
                path.lineTo(points[i].x, points[i].y)
            }
            canvas.drawPath(path, paint)
        }
        
        val outputStream = java.io.ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, outputStream)
        val byteArray = outputStream.toByteArray()
        android.util.Base64.encodeToString(byteArray, android.util.Base64.DEFAULT)
    } catch (e: Exception) {
        ""
    }
}
