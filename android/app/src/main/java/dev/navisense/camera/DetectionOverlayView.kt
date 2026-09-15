package dev.navisense.camera

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import dev.navisense.contracts.DetectedObject
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * High-contrast, hardware-accelerated overlay that draws traditional YOLO bounding boxes,
 * class labels, and confidence tags over the live CameraX viewfinder.
 */
class DetectionOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val lock = Any()
    private var currentDetections: List<DetectedObject> = emptyList()

    private val density = context.resources.displayMetrics.density
    private val scaledDensity = context.resources.displayMetrics.scaledDensity

    private val boxStrokeWidth = 3f * density
    private val tagPaddingH = 8f * density
    private val tagPaddingV = 4f * density
    private val tagCornerRadius = 4f * density

    // Pre-allocated Paint objects
    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = boxStrokeWidth
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val tagBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 12f * scaledDensity
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val tempRect = RectF()
    private val tagRect = RectF()

    // Distinct vibrant color palette for classes
    private val classColors = intArrayOf(
        0xFF00E5FF.toInt(), // 0: Cyan (e.g. Person)
        0xFF00E676.toInt(), // 1: Neon Green (e.g. Chair)
        0xFFFFD600.toInt(), // 2: Yellow (e.g. Table)
        0xFFFF6D00.toInt(), // 3: Orange (e.g. Backpack)
        0xFFE040FB.toInt(), // 4: Magenta (e.g. Bottle)
        0xFF76FF03.toInt(), // 5: Lime (e.g. Keys)
        0xFF2979FF.toInt(), // 6: Electric Blue (e.g. Wallet)
        0xFFFF1744.toInt(), // 7: Red / Coral
        0xFF00B0FF.toInt(), // 8: Light Blue
        0xFFFFAB00.toInt()  // 9: Amber
    )

    /**
     * Updates the active list of detections to render. Safe to call from any thread.
     */
    fun setDetections(detections: List<DetectedObject>) {
        synchronized(lock) {
            currentDetections = detections
        }
        postInvalidate()
    }

    /**
     * Clears all bounding boxes from the overlay. Safe to call from any thread.
     */
    fun clearDetections() {
        synchronized(lock) {
            currentDetections = emptyList()
        }
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val detections = synchronized(lock) { currentDetections }
        if (detections.isEmpty()) return

        val viewW = width.toFloat()
        val viewH = height.toFloat()
        if (viewW <= 0f || viewH <= 0f) return

        val fontMetrics = textPaint.fontMetrics
        val textHeight = fontMetrics.descent - fontMetrics.ascent

        for (detection in detections) {
            val norm = detection.boundingBox

            val x1 = (norm.left * viewW).coerceIn(0f, viewW)
            val y1 = (norm.top * viewH).coerceIn(0f, viewH)
            val x2 = (norm.right * viewW).coerceIn(0f, viewW)
            val y2 = (norm.bottom * viewH).coerceIn(0f, viewH)

            val left = min(x1, x2)
            val top = min(y1, y2)
            val right = max(x1, x2)
            val bottom = max(y1, y2)

            if (right - left < 4f || bottom - top < 4f) continue

            val colorIndex = (detection.classId and 0x7FFFFFFF) % classColors.size
            val baseColor = classColors[colorIndex]

            // 1. Subtle semi-transparent box fill tint (12% opacity)
            fillPaint.color = (baseColor and 0x00FFFFFF) or 0x20000000
            tempRect.set(left, top, right, bottom)
            canvas.drawRect(tempRect, fillPaint)

            // 2. Solid bounding box stroke
            boxPaint.color = baseColor
            canvas.drawRect(tempRect, boxPaint)

            // 3. Label tag text: e.g. "CHAIR 85%" or "PERSON 92%"
            val confidencePct = (detection.confidence * 100f).toInt().coerceIn(0, 100)
            val labelText = "${detection.label.uppercase(Locale.ROOT)} $confidencePct%"
            val textWidth = textPaint.measureText(labelText)

            val tagW = textWidth + (tagPaddingH * 2f)
            val tagH = textHeight + (tagPaddingV * 2f)

            // Position tag above box if room exists, otherwise position inside top of box
            val tagTop = if (top - tagH >= 0f) top - tagH else top
            val tagBottom = tagTop + tagH
            val tagLeft = left
            val tagRight = min(tagLeft + tagW, viewW)

            tagRect.set(tagLeft, tagTop, tagRight, tagBottom)
            tagBgPaint.color = baseColor
            canvas.drawRoundRect(tagRect, tagCornerRadius, tagCornerRadius, tagBgPaint)

            // 4. Dark text on vibrant badge for maximum readability
            val textX = tagLeft + tagPaddingH
            val textY = tagBottom - tagPaddingV - fontMetrics.descent
            textPaint.color = 0xFF000000.toInt()
            canvas.drawText(labelText, textX, textY, textPaint)
        }
    }
}
