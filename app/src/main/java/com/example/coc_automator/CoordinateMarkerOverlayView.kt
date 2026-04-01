package com.example.coc_automator

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.max
import kotlin.math.min
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import androidx.core.content.ContextCompat

/**
 * Transparent overlay on top of a reference [ImageView]; taps add base-space points (up to [maxCount]).
 */
class CoordinateMarkerOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    lateinit var targetImageView: ImageView

    val pointsBase: MutableList<Pair<Int, Int>> = mutableListOf()
    var maxCount: Int = 1
    var yOnly: Boolean = false
    var cardXOnly: Boolean = false
    /** When true and two points exist, draw the OCR crop rectangle between them. */
    var showOcrRectanglePreview: Boolean = false

    var onPointsChanged: (() -> Unit)? = null

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, android.R.color.holo_red_dark)
        style = Paint.Style.STROKE
        strokeWidth = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            3f,
            resources.displayMetrics,
        )
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(100, 255, 50, 50)
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            14f,
            resources.displayMetrics,
        )
        textAlign = Paint.Align.CENTER
    }
    private val ocrRectFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(60, 0, 200, 120)
        style = Paint.Style.FILL
    }
    private val ocrRectStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 0, 180, 100)
        style = Paint.Style.STROKE
        strokeWidth = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            2.5f,
            resources.displayMetrics,
        )
    }

    fun clearPoints() {
        pointsBase.clear()
        invalidate()
        onPointsChanged?.invoke()
    }

    fun undoLast() {
        if (pointsBase.isNotEmpty()) {
            pointsBase.removeAt(pointsBase.lastIndex)
            invalidate()
            onPointsChanged?.invoke()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP) return true
        if (!::targetImageView.isInitialized) return true
        if (pointsBase.size >= maxCount) return true
        val p = CoordinateImageMapping.touchToBase(targetImageView, event.x, event.y) ?: return true
        val stored = when {
            yOnly -> 0 to p.second
            cardXOnly -> p.first to 0
            else -> p
        }
        pointsBase.add(stored)
        invalidate()
        onPointsChanged?.invoke()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!::targetImageView.isInitialized) return
        if (showOcrRectanglePreview && pointsBase.size >= 2) {
            val (b1x, b1y) = pointsBase[0]
            val (b2x, b2y) = pointsBase[1]
            val p1 = CoordinateImageMapping.baseToView(targetImageView, b1x, b1y, false)
            val p2 = CoordinateImageMapping.baseToView(targetImageView, b2x, b2y, false)
            if (p1 != null && p2 != null) {
                val l = min(p1.first, p2.first)
                val r = max(p1.first, p2.first)
                val t = min(p1.second, p2.second)
                val b = max(p1.second, p2.second)
                canvas.drawRect(l, t, r, b, ocrRectFill)
                canvas.drawRect(l, t, r, b, ocrRectStroke)
            }
        }
        pointsBase.forEachIndexed { index, (bx, by) ->
            val drawBy = when {
                cardXOnly -> (ScreenGeometry.BASE_HEIGHT / 2).toInt()
                else -> by
            }
            val pt = CoordinateImageMapping.baseToView(targetImageView, bx, drawBy, yOnly)
                ?: return@forEachIndexed
            val r = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 18f, resources.displayMetrics)
            canvas.drawCircle(pt.first, pt.second, r, fill)
            canvas.drawCircle(pt.first, pt.second, r, stroke)
            canvas.drawText("${index + 1}", pt.first, pt.second + textPaint.textSize / 3f, textPaint)
        }
    }
}
