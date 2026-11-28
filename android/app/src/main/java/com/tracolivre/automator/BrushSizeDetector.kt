package com.tracolivre.automator

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import kotlin.math.abs
import kotlin.math.sqrt

data class BrushControls(
    val pencilButton: PointF,
    val sizeButtons: List<PointF>
)

object BrushSizeDetector {
    private val sizeButtonPositions = floatArrayOf(0.334f, 0.386f, 0.436f, 0.488f, 0.539f, 0.590f)

    fun detect(bitmap: Bitmap): BrushControls? {
        val width = bitmap.width
        val height = bitmap.height
        if (width < 800 || height < 400) return null

        val sizeButtons = sizeButtonPositions.map { x ->
            PointF(width * x, height * 0.900f)
        }
        if (sizeButtons.count { hasButtonContrast(bitmap, it) } < sizeButtons.size - 1) return null

        val pencilButton = PointF(width * 0.923f, height * 0.548f)
        if (!hasButtonContrast(bitmap, pencilButton, width * 0.016f, width * 0.045f)) return null
        return BrushControls(pencilButton, sizeButtons)
    }

    private fun hasButtonContrast(
        bitmap: Bitmap,
        center: PointF,
        innerRadius: Float = bitmap.width * 0.009f,
        outerRadius: Float = bitmap.width * 0.023f
    ): Boolean {
        val inner = averageLuminance(bitmap, center, 0f, innerRadius) ?: return false
        val outer = averageLuminance(bitmap, center, innerRadius * 1.4f, outerRadius) ?: return false
        return abs(inner - outer) >= 24f
    }

    private fun averageLuminance(
        bitmap: Bitmap,
        center: PointF,
        innerRadius: Float,
        outerRadius: Float
    ): Float? {
        val left = (center.x - outerRadius).toInt().coerceAtLeast(0)
        val right = (center.x + outerRadius).toInt().coerceAtMost(bitmap.width - 1)
        val top = (center.y - outerRadius).toInt().coerceAtLeast(0)
        val bottom = (center.y + outerRadius).toInt().coerceAtMost(bitmap.height - 1)
        var total = 0f
        var count = 0
        for (y in top..bottom step 2) {
            for (x in left..right step 2) {
                val distance = sqrt(
                    (x - center.x) * (x - center.x) + (y - center.y) * (y - center.y)
                )
                if (distance >= innerRadius && distance <= outerRadius) {
                    val color = bitmap.getPixel(x, y)
                    total += (Color.red(color) * 299 +
                        Color.green(color) * 587 +
                        Color.blue(color) * 114) / 1000f
                    count++
                }
            }
        }
        return if (count == 0) null else total / count
    }
}
