package com.example.coc_automator

import android.widget.ImageView
import kotlin.math.min

/**
 * Maps between touches on an [ImageView] (fit center) and base automation coordinates.
 */
object CoordinateImageMapping {

    private data class Fit(
        val scale: Float,
        val left: Float,
        val top: Float,
        val imgW: Int,
        val imgH: Int,
    )

    private fun fit(iv: ImageView): Fit? {
        val d = iv.drawable ?: return null
        val iw = d.intrinsicWidth
        val ih = d.intrinsicHeight
        if (iw <= 0 || ih <= 0) return null
        val viewW = (iv.width - iv.paddingLeft - iv.paddingRight).toFloat()
        val viewH = (iv.height - iv.paddingTop - iv.paddingBottom).toFloat()
        if (viewW <= 0f || viewH <= 0f) return null
        val scale = min(viewW / iw, viewH / ih)
        val drawW = iw * scale
        val drawH = ih * scale
        val left = iv.paddingLeft + (viewW - drawW) / 2f
        val top = iv.paddingTop + (viewH - drawH) / 2f
        return Fit(scale, left, top, iw, ih)
    }

    fun touchToBase(iv: ImageView, touchX: Float, touchY: Float): Pair<Int, Int>? {
        val f = fit(iv) ?: return null
        val imgX = (touchX - f.left) / f.scale
        val imgY = (touchY - f.top) / f.scale
        if (imgX < 0 || imgY < 0 || imgX > f.imgW || imgY > f.imgH) return null
        val bx = (imgX / f.imgW * ScreenGeometry.BASE_WIDTH).toInt()
            .coerceIn(0, ScreenGeometry.BASE_WIDTH.toInt())
        val by = (imgY / f.imgH * ScreenGeometry.BASE_HEIGHT).toInt()
            .coerceIn(0, ScreenGeometry.BASE_HEIGHT.toInt())
        return bx to by
    }

    /** Map base coords to view coords for drawing markers. */
    fun baseToView(iv: ImageView, bx: Int, by: Int, yOnly: Boolean): Pair<Float, Float>? {
        val f = fit(iv) ?: return null
        val effBx = if (yOnly) (ScreenGeometry.BASE_WIDTH / 2f).toInt() else bx
        val imgX = effBx / ScreenGeometry.BASE_WIDTH * f.imgW
        val imgY = by / ScreenGeometry.BASE_HEIGHT * f.imgH
        val vx = f.left + imgX * f.scale
        val vy = f.top + imgY * f.scale
        return vx to vy
    }
}
