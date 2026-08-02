package com.example.fieldtechv20kc.ui.components

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@Composable
fun NewSignaturePad(
    onSignatureChanged: (Boolean) -> Unit,
    onSignatureDataChanged: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Use a mutable list that triggers recomposition when changed
    val signaturePoints = remember { mutableStateListOf<Offset>() }
    var isDrawing by remember { mutableStateOf(false) }
    var hasSignature by remember { mutableStateOf(false) }
    
    // Create path from points - this will recompose when signaturePoints changes
    val signaturePath = remember(signaturePoints.toList()) {
        Path().apply {
            if (signaturePoints.isNotEmpty()) {
                moveTo(signaturePoints[0].x, signaturePoints[0].y)
                for (i in 1 until signaturePoints.size) {
                    lineTo(signaturePoints[i].x, signaturePoints[i].y)
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
                .height(200.dp)
                .background(Color.White)
                .border(2.dp, Color.Gray, RoundedCornerShape(8.dp))
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                isDrawing = true
                                signaturePoints.clear()
                                signaturePoints.add(offset)
                                hasSignature = true
                                onSignatureChanged(true)
                            },
                            onDrag = { change, _ ->
                                if (isDrawing) {
                                    signaturePoints.add(change.position)
                                }
                            },
                            onDragEnd = {
                                isDrawing = false
                                // Generate Base64 signature when done
                                if (hasSignature && signaturePoints.isNotEmpty()) {
                                    val base64 = generateSignatureBase64(signaturePoints.toList())
                                    onSignatureDataChanged(base64)
                                }
                            }
                        )
                    }
            ) {
                // Draw the signature path
                drawPath(
                    path = signaturePath,
                    color = Color.Black,
                    style = Stroke(width = 6.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
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
                signaturePoints.clear()
                hasSignature = false
                onSignatureChanged(false)
                onSignatureDataChanged("")
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Clear Signature")
        }
    }
}

private fun generateSignatureBase64(points: List<Offset>): String {
    return try {
        if (points.isEmpty()) return ""
        
        // Create bitmap
        val width = 300
        val height = 100
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        
        // White background
        canvas.drawColor(android.graphics.Color.WHITE)
        
        // Draw signature
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
        
        // Convert to Base64
        val outputStream = java.io.ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, outputStream)
        val byteArray = outputStream.toByteArray()
        android.util.Base64.encodeToString(byteArray, android.util.Base64.DEFAULT)
    } catch (e: Exception) {
        ""
    }
}





