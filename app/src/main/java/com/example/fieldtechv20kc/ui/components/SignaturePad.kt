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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@Composable
fun SignaturePad(
    onSignatureChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var path by remember { mutableStateOf(Path()) }
    var hasSignature by remember { mutableStateOf(false) }
    var isDrawing by remember { mutableStateOf(false) }
    
    Column(
        modifier = modifier
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    isDrawing = true
                                    path.moveTo(offset.x, offset.y)
                                    hasSignature = true
                                    onSignatureChanged(true)
                                },
                                onDrag = { change, _ ->
                                    if (isDrawing) {
                                        path.lineTo(change.position.x, change.position.y)
                                    }
                                },
                                onDragEnd = {
                                    isDrawing = false
                                }
                            )
                        }
                ) {
                    drawPath(
                        path = path,
                        color = Color.Black,
                        style = Stroke(width = 4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
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
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(
                onClick = {
                    path = Path()
                    hasSignature = false
                    isDrawing = false
                    onSignatureChanged(false)
                }
            ) {
                Text("Clear")
            }
        }
    }
}
