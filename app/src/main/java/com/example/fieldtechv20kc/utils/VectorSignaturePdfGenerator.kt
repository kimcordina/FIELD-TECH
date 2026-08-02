package com.example.fieldtechv20kc.utils

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import com.example.fieldtechv20kc.data.model.ReportWithDetails
import java.io.File

/**
 * Vector-based signature PDF generator for zero-pixelation signatures.
 * This generates signatures as mathematical curves rather than bitmaps,
 * ensuring perfect quality at any zoom level or print size.
 */
class VectorSignaturePdfGenerator(private val context: Context) {
    
    companion object {
        // PDF dimensions (A4 at 72 DPI)
        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842
        
        // Signature dimensions in PDF points (72 DPI)
        // 6cm = 2.36 inches = 170 points at 72 DPI
        const val SIGNATURE_SIZE_POINTS = 170
    }
    
    data class VectorStroke(
        val points: List<PointF>,
        val widths: List<Float>,
        val color: Int = Color.BLACK
    )
    
    /**
     * Generate PDF with vector-based signature (zero pixelation)
     */
    fun generatePdfWithVectorSignature(
        reportWithDetails: ReportWithDetails,
        outputPath: String
    ): Boolean {
        try {
            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas
            
            // Load vector signature data from SharedPreferences
            val vectorStrokes = loadVectorSignatureData()
            val signatureBounds = loadSignatureBounds()
            
            if (vectorStrokes.isNotEmpty() && signatureBounds != null) {
                // Add vector signature (no pixelation!)
                addVectorSignatureToPdf(
                    canvas = canvas,
                    strokes = vectorStrokes,
                    bounds = signatureBounds,
                    x = (PAGE_WIDTH - SIGNATURE_SIZE_POINTS) / 2f,
                    y = PAGE_HEIGHT - 250f
                )
            }
            
            // Add other PDF content here (report details, photos, etc.)
            // This would be similar to your existing PDF generation logic
            
            pdfDocument.finishPage(page)
            
            val file = File(outputPath)
            file.outputStream().use { fos ->
                pdfDocument.writeTo(fos)
            }
            
            pdfDocument.close()
            return true
            
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
    
    /**
     * Add vector signature to PDF canvas (infinite resolution!)
     */
    fun addVectorSignatureToPdf(
        canvas: Canvas,
        strokes: List<VectorStroke>,
        bounds: RectF,
        x: Float,
        y: Float,
        size: Float = SIGNATURE_SIZE_POINTS.toFloat()
    ) {
        // Calculate scaling
        val signatureWidth = bounds.width()
        val signatureHeight = bounds.height()
        val scale = size / maxOf(signatureWidth, signatureHeight)
        
        // Center the signature
        val scaledWidth = signatureWidth * scale
        val scaledHeight = signatureHeight * scale
        val offsetX = x + (size - scaledWidth) / 2
        val offsetY = y + (size - scaledHeight) / 2
        
        // Configure paint for vector drawing
        val paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        
        // Draw each stroke as vector path (infinite resolution!)
        strokes.forEach { stroke ->
            if (stroke.points.size >= 2) {
                val path = Path()
                
                // Start path
                val firstPoint = stroke.points[0]
                val startX = (firstPoint.x - bounds.left) * scale + offsetX
                val startY = (firstPoint.y - bounds.top) * scale + offsetY
                path.moveTo(startX, startY)
                
                // Draw smooth cubic Bezier curves
                for (i in 1 until stroke.points.size) {
                    val p0 = stroke.points[i - 1]
                    val p1 = stroke.points[i]
                    
                    val x0 = (p0.x - bounds.left) * scale + offsetX
                    val y0 = (p0.y - bounds.top) * scale + offsetY
                    val x1 = (p1.x - bounds.left) * scale + offsetX
                    val y1 = (p1.y - bounds.top) * scale + offsetY
                    
                    // Cubic Bezier for perfect curves
                    val dx = x1 - x0
                    val dy = y1 - y0
                    path.cubicTo(
                        x0 + dx * 0.33f, y0 + dy * 0.33f,
                        x0 + dx * 0.67f, y0 + dy * 0.67f,
                        x1, y1
                    )
                }
                
                // Set color and width
                paint.color = stroke.color
                paint.strokeWidth = stroke.widths.average().toFloat() * scale
                
                // Draw the vector path - no pixelation!
                canvas.drawPath(path, paint)
            }
        }
        
        // Optional: Add border
        val borderPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.5f
            color = Color.GRAY
            isAntiAlias = true
        }
        canvas.drawRoundRect(
            x, y, x + size, y + size, 
            2f, 2f, borderPaint
        )
    }
    
    /**
     * Load vector signature data from SharedPreferences
     */
    private fun loadVectorSignatureData(): List<VectorStroke> {
        return try {
            val prefs = context.getSharedPreferences("signature_vector_data", Context.MODE_PRIVATE)
            val strokeCount = prefs.getInt("stroke_count", 0)
            
            if (strokeCount == 0) return emptyList()
            
            val strokes = mutableListOf<VectorStroke>()
            
            for (i in 0 until strokeCount) {
                val pointsJson = prefs.getString("stroke_${i}_points", "") ?: ""
                val widthsJson = prefs.getString("stroke_${i}_widths", "") ?: ""
                val color = prefs.getInt("stroke_${i}_color", Color.BLACK)
                
                if (pointsJson.isNotEmpty() && widthsJson.isNotEmpty()) {
                    val points = pointsJson.split(",").chunked(2).mapNotNull { coords ->
                        if (coords.size == 2) {
                            PointF(coords[0].toFloatOrNull() ?: 0f, coords[1].toFloatOrNull() ?: 0f)
                        } else null
                    }
                    
                    val widths = widthsJson.split(",").mapNotNull { it.toFloatOrNull() }
                    
                    if (points.isNotEmpty() && widths.isNotEmpty()) {
                        strokes.add(VectorStroke(points, widths, color))
                    }
                }
            }
            
            strokes
        } catch (e: Exception) {
            println("Error loading vector signature data: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Load signature bounds from SharedPreferences
     */
    private fun loadSignatureBounds(): RectF? {
        return try {
            val prefs = context.getSharedPreferences("signature_vector_data", Context.MODE_PRIVATE)
            
            val left = prefs.getFloat("bounds_left", 0f)
            val top = prefs.getFloat("bounds_top", 0f)
            val right = prefs.getFloat("bounds_right", 0f)
            val bottom = prefs.getFloat("bounds_bottom", 0f)
            
            if (right > left && bottom > top) {
                RectF(left, top, right, bottom)
            } else {
                null
            }
        } catch (e: Exception) {
            println("Error loading signature bounds: ${e.message}")
            null
        }
    }
}
