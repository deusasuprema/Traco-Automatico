package com.tracolivre.automator

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

data class ColorPickerControls(
    val button: PointF,
    val hueBar: RectF,
    val colorField: RectF
) {
    fun huePoint(color: Int): PointF {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        return PointF(
            hueBar.left + hueBar.width() * (hsv[0] / 360f),
            hueBar.centerY()
        )
    }

    fun shadePoint(color: Int): PointF {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        return PointF(
            colorField.left + colorField.width() * hsv[1],
            colorField.top + colorField.height() * (1f - hsv[2])
        )
    }
}

object ColorPickerDetector {
    fun find(bitmap: Bitmap): ColorPickerControls? {
        val width = bitmap.width
        val height = bitmap.height
        if (width < 400 || height < 300) return null

        var bestLeft = -1
        var bestRight = -1
        var bestY = -1
        var bestScore = 0
        val hsv = FloatArray(3)
        for (y in 0 until height step 2) {
            var runStart = -1
            var runBins = BooleanArray(12)
            for (x in 0 until width step 2) {
                Color.colorToHSV(bitmap.getPixel(x, y), hsv)
                val colorful = hsv[1] > 0.72f && hsv[2] > 0.7f
                if (colorful) {
                    if (runStart < 0) {
                        runStart = x
                        runBins = BooleanArray(12)
                    }
                    runBins[(floor(hsv[0] / 30f).toInt()).coerceIn(0, 11)] = true
                } else if (runStart >= 0) {
                    val score = x - runStart
                    if (score > bestScore && runBins.count { it } >= 8) {
                        bestLeft = runStart
                        bestRight = x
                        bestY = y
                        bestScore = score
                    }
                    runStart = -1
                }
            }
            if (runStart >= 0) {
                val score = width - runStart
                if (score > bestScore && runBins.count { it } >= 8) {
                    bestLeft = runStart
                    bestRight = width
                    bestY = y
                    bestScore = score
                }
            }
        }
        if (bestScore < width * 0.25f) return null

        val barHeightLimit = maxOf(2, (height * 0.08f).toInt())
        var top = bestY
        var bottom = bestY
        while (top > bestY - barHeightLimit && isColorfulRow(bitmap, top, bestLeft, bestRight)) top--
        while (bottom < bestY + barHeightLimit && isColorfulRow(bitmap, bottom, bestLeft, bestRight)) bottom++
        val hueBar = RectF(
            (bestLeft - height * 0.03f).coerceAtLeast(0f),
            top.toFloat(),
            bestRight.toFloat(),
            bottom.toFloat()
        )

        val fieldTop = hueBar.bottom + height * 0.035f
        val fieldHeight = min(height * 0.35f, hueBar.width() * 0.43f)
        val colorField = RectF(
            hueBar.left,
            fieldTop,
            hueBar.right,
            fieldTop + fieldHeight
        )
        if (colorField.bottom >= height) return null
        if (!looksLikeColorField(bitmap, colorField)) return null

        val button = PointF(hueBar.right - width * 0.022f, height * 0.902f)
        if (!hasColorRing(bitmap, button, height * 0.04f)) return null
        return ColorPickerControls(button, hueBar, colorField)
    }

    private fun isColorfulRow(bitmap: Bitmap, y: Int, left: Int, right: Int): Boolean {
        if (y !in 0 until bitmap.height) return false
        val start = left + (right - left) / 10
        val end = right - (right - left) / 10
        val step = maxOf(1, (end - start) / 64)
        val hsv = FloatArray(3)
        var colorful = 0
        var total = 0
        for (x in start until end step step) {
            Color.colorToHSV(bitmap.getPixel(x, y), hsv)
            if (hsv[1] > 0.6f && hsv[2] > 0.55f) colorful++
            total++
        }
        return total > 0 && colorful.toFloat() / total >= 0.65f
    }

    private fun looksLikeColorField(bitmap: Bitmap, bounds: RectF): Boolean {
        val insetX = bounds.width() * 0.04f
        val insetY = bounds.height() * 0.04f
        val leftTop = bitmap.getPixel((bounds.left + insetX).toInt(), (bounds.top + insetY).toInt())
        val leftBottom = bitmap.getPixel(
            (bounds.left + insetX).toInt(),
            (bounds.bottom - insetY).toInt()
        )
        val rightTop = bitmap.getPixel(
            (bounds.right - insetX).toInt(),
            (bounds.top + insetY).toInt()
        )
        val topHsv = FloatArray(3)
        val bottomHsv = FloatArray(3)
        val rightHsv = FloatArray(3)
        Color.colorToHSV(leftTop, topHsv)
        Color.colorToHSV(leftBottom, bottomHsv)
        Color.colorToHSV(rightTop, rightHsv)
        return topHsv[1] < 0.35f && topHsv[2] > 0.6f &&
            bottomHsv[2] < 0.35f &&
            rightHsv[1] > 0.55f && rightHsv[2] > 0.55f
    }

    private fun hasColorRing(bitmap: Bitmap, center: PointF, radius: Float): Boolean {
        val hsv = FloatArray(3)
        var colorful = 0
        for (angle in 0 until 360 step 15) {
            val radians = Math.toRadians(angle.toDouble())
            val x = (center.x + cos(radians) * radius).toInt()
            val y = (center.y + sin(radians) * radius).toInt()
            if (x !in 0 until bitmap.width || y !in 0 until bitmap.height) continue
            Color.colorToHSV(bitmap.getPixel(x, y), hsv)
            if (hsv[1] > 0.35f && hsv[2] > 0.25f) colorful++
        }
        return colorful >= 12
    }
}
