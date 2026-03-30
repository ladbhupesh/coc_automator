package com.example.coc_automator

/**
 * Maps recorded coordinates from the Python automation (assumed capture size) to the current display.
 */
data class ScreenGeometry(val screenWidth: Int, val screenHeight: Int) {

    companion object {
        /** Reference resolution used when recording tap positions in automated_attack.py */
        const val BASE_WIDTH = 2340f
        const val BASE_HEIGHT = 1080f
    }

    private val scaleX = screenWidth / BASE_WIDTH
    private val scaleY = screenHeight / BASE_HEIGHT

    fun x(baseX: Int): Int = (baseX * scaleX).toInt().coerceIn(0, screenWidth - 1)

    fun y(baseY: Int): Int = (baseY * scaleY).toInt().coerceIn(0, screenHeight - 1)

    fun cropLeft(baseLeft: Int) = x(baseLeft)

    fun cropTop(baseTop: Int) = y(baseTop)

    fun cropWidth(baseLeft: Int, baseRight: Int) =
        (x(baseRight) - x(baseLeft)).coerceAtLeast(1)

    fun cropHeight(baseTop: Int, baseBottom: Int) =
        (y(baseBottom) - y(baseTop)).coerceAtLeast(1)
}
