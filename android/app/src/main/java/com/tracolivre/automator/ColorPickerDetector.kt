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

    fun hueStartPoint(): PointF = PointF(
        hueBar.left + hueBar.width() * 0.01f,
        hueBar.centerY()
    )

    fun shadePoint(color: Int): PointF {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        return PointF(
            colorField.left + colorField.width() * hsv[1],
            colorField.top + colorField.height() * (1f - hsv[2])
        )
    }

    fun shadeStartPoint(): PointF = PointF(
        colorField.left + colorField.width() * 0.02f,
        colorField.top + colorField.height() * 0.02f
    )
}

data class ColorPickerDetection(
    val controls: ColorPickerControls?,
    val isOpen: Boolean,
    val closedButton: PointF?
)

object ColorPickerDetector {
    fun detect(bitmap: Bitmap): ColorPickerDetection {
        val width = bitmap.width
        val height = bitmap.height
        if (width < 400 || height < 300) return ColorPickerDetection(null, false, null)

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
        if (bestScore < width * 0.25f) {
            return ColorPickerDetection(null, false, findColorButton(bitmap))
        }

        val barHeightLimit = maxOf(2, (height * 0.08f).toInt())
        var top = bestY
        var bottom = bestY
        while (top > bestY - barHeightLimit && isColorfulRow(bitmap, top, bestLeft, bestRight)) top--
        while (bottom < bestY + barHeightLimit && isColorfulRow(bitmap, bottom, bestLeft, bestRight)) bottom++
        val hueBar = RectF(
            bestLeft.toFloat(),
            top.toFloat(),
            bestRight.toFloat(),
            bottom.toFloat()
        )
        val button = findColorButton(bitmap)
            ?: return ColorPickerDetection(null, true, null)

        val fieldTop = hueBar.bottom + height * 0.035f
        val fieldHeight = min(height * 0.35f, hueBar.width() * 0.43f)
        val colorField = RectF(
            hueBar.left,
            fieldTop,
            hueBar.right,
            fieldTop + fieldHeight
        )
        if (colorField.bottom >= height || !looksLikeColorField(bitmap, colorField)) {
            return ColorPickerDetection(null, true, null)
        }
        if (!hasColorRing(bitmap, button, height * 0.04f)) {
            return ColorPickerDetection(null, true, null)
        }
        return ColorPickerDetection(ColorPickerControls(button, hueBar, colorField), true, null)
    }

    private fun findColorButton(bitmap: Bitmap): PointF? {
        val width = bitmap.width
        val height = bitmap.height
        val expected = PointF(width * 0.661f, height * 0.902f)
        val step = maxOf(2, (height * 0.008f).toInt())
        val left = (width * 0.61f).toInt()
        val right = min(width - 1, (width * 0.71f).toInt())
        val top = (height * 0.82f).toInt()
        val bottom = min(height - 1, (height * 0.98f).toInt())
        val radiusScales = floatArrayOf(0.022f, 0.03f, 0.04f, 0.05f)
        var bestPoint: PointF? = null
        var bestScore = 0
        var bestDistance = Float.MAX_VALUE

        for (y in top..bottom step step) {
            for (x in left..right step step) {
                val distance = (x - expected.x) * (x - expected.x) +
                    (y - expected.y) * (y - expected.y)
                for (scale in radiusScales) {
                    val score = colorRingScore(
                        bitmap,
                        PointF(x.toFloat(), y.toFloat()),
                        height * scale
                    )
                    if (score > bestScore || score == bestScore && distance < bestDistance) {
                        bestPoint = PointF(x.toFloat(), y.toFloat())
                        bestScore = score
                        bestDistance = distance
                    }
                }
            }
        }
        return bestPoint.takeIf { bestScore >= 12 }
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
        return colorRingScore(bitmap, center, radius) >= 12
    }

    private fun colorRingScore(bitmap: Bitmap, center: PointF, radius: Float): Int {
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
        return colorful
    }
}
