package com.example.fieldtechv20kc.ui.components

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * A lightweight, dependency-free signature pad.
 * - Smooth real-time strokes via quadratic Bézier interpolation
 * - Records taps as dots
 * - Undo / Redo / Clear
 * - Export to Bitmap (transparent by default)
 */
// Public stroke data class for access outside the view
data class SignatureStroke(
    val path: Path,
    val paint: Paint
)

class SignaturePadView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val strokes = mutableListOf<SignatureStroke>()
    private val redoStack = mutableListOf<SignatureStroke>()

    // Current drawing state
    private var currentPath: Path? = null
    private var currentPaint: Paint = defaultPaint()
    private var lastX = 0f
    private var lastY = 0f
    private var downX = 0f
    private var downY = 0f
    private var moved = false

    // Config
    private var penColor: Int = Color.BLACK
    private var penWidth: Float = 6f * resources.displayMetrics.density // default ~6dp

    // Smoothing threshold
    private val touchTolerance = 3f * resources.displayMetrics.density

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        // Make sure we render with anti-aliasing
        setLayerType(LAYER_TYPE_HARDWARE, null)
        // Better touch feel
        setWillNotDraw(false)
    }

    private fun defaultPaint(): Paint {
        return Paint().apply {
            isAntiAlias = true
            isDither = true
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            color = penColor
            strokeWidth = penWidth
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Draw all finalized strokes
        for (s in strokes) {
            canvas.drawPath(s.path, s.paint)
        }
        // Draw current (in-progress) path
        currentPath?.let { canvas.drawPath(it, currentPaint) }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Prevent parents (e.g., ScrollView) from intercepting while drawing
        parent?.requestDisallowInterceptTouchEvent(true)

        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startStroke(x, y)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                // Some devices batch historical points — handle them for smoother lines
                for (i in 0 until event.historySize) {
                    val hx = event.getHistoricalX(i)
                    val hy = event.getHistoricalY(i)
                    continueStroke(hx, hy)
                }
                continueStroke(x, y)
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                endStroke(x, y)
                invalidate()
                // Allow parent to intercept again
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }

    private fun startStroke(x: Float, y: Float) {
        moved = false
        downX = x
        downY = y
        lastX = x
        lastY = y
        currentPath = Path().apply { moveTo(x, y) }
        currentPaint = defaultPaint()
        // Any new stroke invalidates redo history
        redoStack.clear()
    }

    private fun continueStroke(x: Float, y: Float) {
        val dx = abs(x - lastX)
        val dy = abs(y - lastY)
        if (dx >= touchTolerance || dy >= touchTolerance) {
            moved = true
            // Quadratic Bézier smoothing: control point at the previous position,
            // end point at the midpoint between last and current.
            val mx = (x + lastX) / 2f
            val my = (y + lastY) / 2f
            currentPath?.quadTo(lastX, lastY, mx, my)
            lastX = x
            lastY = y
        }
    }

    private fun endStroke(x: Float, y: Float) {
        if (currentPath == null) return

        // If user didn't move (a tap), render a visible dot
        if (!moved) {
            val dotRadius = max(1f, penWidth / 2f)
            currentPath = Path().apply { addCircle(x, y, dotRadius, Path.Direction.CW) }
        } else {
            // Finish the path to the last point for continuity
            currentPath?.lineTo(lastX, lastY)
        }

        strokes.add(SignatureStroke(currentPath!!, Paint(currentPaint)))
        currentPath = null
    }

    /** Public API **/

    /** Clears the canvas and history. */
    fun clear() {
        strokes.clear()
        redoStack.clear()
        currentPath = null
        invalidate()
    }

    /** Whether there are no strokes drawn. */
    fun isEmpty(): Boolean = strokes.isEmpty() && currentPath == null

    /** Undo last stroke if present. */
    fun undo() {
        if (currentPath != null) return // don't undo mid-stroke
        if (strokes.isNotEmpty()) {
            val last = strokes.removeAt(strokes.size - 1)
            redoStack.add(last)
            invalidate()
        }
    }

    /** Redo a previously undone stroke. */
    fun redo() {
        if (currentPath != null) return
        if (redoStack.isNotEmpty()) {
            val stroke = redoStack.removeAt(redoStack.size - 1)
            strokes.add(stroke)
            invalidate()
        }
    }

    /** Set the pen color (e.g., Color.BLACK). */
    fun setPenColor(color: Int) {
        penColor = color
        // Also update current paint if user changes while drawing
        currentPaint.color = color
        invalidate()
    }

    /** Set the pen width in pixels. */
    fun setPenWidth(widthPx: Float) {
        penWidth = max(1f, widthPx)
        currentPaint.strokeWidth = penWidth
        invalidate()
    }

    /**
     * Render current signature into a Bitmap.
     * @param backgroundColor pass Color.TRANSPARENT (default) for a transparent PNG,
     *                        or a real color (e.g., Color.WHITE) for an opaque background.
     * @param extraPaddingPx  optional padding around content to avoid clipping when overlaying on PDFs.
     */
    fun getSignatureBitmap(
        backgroundColor: Int = Color.TRANSPARENT,
        extraPaddingPx: Int = (8f * resources.displayMetrics.density).toInt()
    ): Bitmap {
        // Determine the bounds of all strokes to tightly crop the output
        val bounds = computeContentBounds() ?: Rect(0, 0, width, height)
        // Expand with padding
        bounds.inset(-extraPaddingPx, -extraPaddingPx)

        // Clamp to view bounds
        bounds.left = max(0, bounds.left)
        bounds.top = max(0, bounds.top)
        bounds.right = min(width, bounds.right)
        bounds.bottom = min(height, bounds.bottom)

        val outWidth = max(1, bounds.width())
        val outHeight = max(1, bounds.height())

        val bitmap = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        if (backgroundColor != Color.TRANSPARENT) {
            canvas.drawColor(backgroundColor)
        } else {
            // Ensure full transparency
            canvas.drawARGB(0, 0, 0, 0)
        }

        canvas.translate(-bounds.left.toFloat(), -bounds.top.toFloat())

        // Draw all strokes
        for (s in strokes) canvas.drawPath(s.path, s.paint)
        // If user is mid-stroke, include it too
        currentPath?.let { canvas.drawPath(it, currentPaint) }

        return bitmap
    }

    /**
     * Optionally provide the total path bounds (useful when laying out on a PDF).
     * Returns null if no content.
     */
    fun getSignatureBoundsInView(): Rect? = computeContentBounds()

    private fun computeContentBounds(): Rect? {
        if (strokes.isEmpty() && currentPath == null) return null
        val rectF = RectF()
        var hasAny = false

        fun addPathBounds(path: Path, paint: Paint) {
            val r = RectF()
            path.computeBounds(r, true)
            // Inflate by half stroke width to avoid clipping thick edges
            val inflate = paint.strokeWidth / 2f + 1f
            r.inset(-inflate, -inflate)
            if (!hasAny) {
                rectF.set(r)
                hasAny = true
            } else {
                rectF.union(r)
            }
        }

        for (s in strokes) addPathBounds(s.path, s.paint)
        currentPath?.let { addPathBounds(it, currentPaint) }

        if (!hasAny) return null

        val out = Rect()
        rectF.roundOut(out)
        // Clamp within view
        out.left = max(0, out.left)
        out.top = max(0, out.top)
        out.right = min(width, out.right)
        out.bottom = min(height, out.bottom)
        return out
    }
    
    /** Get all strokes for vector storage */
    fun getAllStrokes(): List<SignatureStroke> = strokes.toList()
}

