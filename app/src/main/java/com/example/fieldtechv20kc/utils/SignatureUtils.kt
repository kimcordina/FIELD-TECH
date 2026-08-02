package com.example.fieldtechv20kc.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.geometry.Offset
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object SignatureUtils {
    
    fun saveSignatureAsImage(
        context: Context,
        signatureStrokes: List<List<Offset>>,
        signerName: String
    ): String? {
        return try {
            println("DEBUG: SignatureUtils - saveSignatureAsImage called with ${signatureStrokes.size} strokes")
            if (signatureStrokes.isEmpty()) {
                println("DEBUG: SignatureUtils - No signature strokes, returning null")
                return null
            }
            
            // Create signature directory
            val signatureDir = File(context.getExternalFilesDir(null), "Signatures")
            if (!signatureDir.exists()) {
                val created = signatureDir.mkdirs()
                println("DEBUG: SignatureUtils - Created signature directory: $created")
            }
            
            // Create signature file
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val signatureFile = File(signatureDir, "signature_${timeStamp}.png")
            println("DEBUG: SignatureUtils - Creating signature file: ${signatureFile.absolutePath}")
            
            // Create bitmap
            val bitmap = createSignatureBitmap(signatureStrokes)
            println("DEBUG: SignatureUtils - Created bitmap: ${bitmap.width}x${bitmap.height}")
            
            // Save bitmap to file
            val outputStream = FileOutputStream(signatureFile)
            val compressed = bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            outputStream.flush()
            outputStream.close()
            
            println("DEBUG: SignatureUtils - Bitmap compressed: $compressed")
            println("DEBUG: SignatureUtils - File size: ${signatureFile.length()} bytes")
            println("DEBUG: SignatureUtils - File exists: ${signatureFile.exists()}")
            println("DEBUG: SignatureUtils - Signature saved to: ${signatureFile.absolutePath}")
            
            signatureFile.absolutePath
        } catch (e: Exception) {
            println("DEBUG: SignatureUtils - Error saving signature: ${e.message}")
            e.printStackTrace()
            null
        }
    }
    
    private fun createSignatureBitmap(strokes: List<List<Offset>>): Bitmap {
        println("DEBUG: SignatureUtils - createSignatureBitmap called with ${strokes.size} strokes")
        
        // Flatten all points to determine bounds
        val allPoints = strokes.flatten()
        if (allPoints.isEmpty()) {
            // Return empty bitmap if no points
            return Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        }
        
        // Determine bounds
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE
        
        allPoints.forEach { offset ->
            minX = minOf(minX, offset.x)
            minY = minOf(minY, offset.y)
            maxX = maxOf(maxX, offset.x)
            maxY = maxOf(maxY, offset.y)
        }
        
        println("DEBUG: SignatureUtils - Bounds: minX=$minX, minY=$minY, maxX=$maxX, maxY=$maxY")
        
        // Add padding
        val padding = 20f
        minX = (minX - padding).coerceAtLeast(0f)
        minY = (minY - padding).coerceAtLeast(0f)
        maxX += padding
        maxY += padding
        
        // Create higher resolution bitmap for better quality
        val scaleFactor = 2f // Double the resolution
        val width = ((maxX - minX) * scaleFactor).toInt().coerceAtLeast(400)
        val height = ((maxY - minY) * scaleFactor).toInt().coerceAtLeast(200)
        
        println("DEBUG: SignatureUtils - Creating bitmap: ${width}x${height}")
        
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // White background
        canvas.drawColor(android.graphics.Color.WHITE)
        
        // Draw signature with higher quality settings
        val paint = Paint().apply {
            color = android.graphics.Color.BLACK
            strokeWidth = 12f * scaleFactor // Scale stroke width with resolution
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true // Enable anti-aliasing for smoother lines
        }
        
        // Draw each stroke separately to avoid connecting lines between separate strokes
        strokes.forEach { stroke ->
            if (stroke.isNotEmpty()) {
                val path = Path()
                path.moveTo((stroke[0].x - minX) * scaleFactor, (stroke[0].y - minY) * scaleFactor)
                for (i in 1 until stroke.size) {
                    path.lineTo((stroke[i].x - minX) * scaleFactor, (stroke[i].y - minY) * scaleFactor)
                }
                canvas.drawPath(path, paint)
            }
        }
        
        println("DEBUG: SignatureUtils - Signature path drawn on bitmap")
        
        return bitmap
    }
}
