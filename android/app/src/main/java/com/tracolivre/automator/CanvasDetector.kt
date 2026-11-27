package com.tracolivre.automator

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

object CanvasDetector {
    fun find(bitmap: Bitmap): RectF? {
        val width = bitmap.width
        val height = bitmap.height
        if (width < 200 || height < 200) return null

        val leftEdges = ArrayList<Int>()
        val rightEdges = ArrayList<Int>()
        val detectedRows = ArrayList<Int>()
        val firstRow = (height * 0.005f).toInt()
        val lastRow = (height * 0.88f).toInt()
        val minimumRun = (width * 0.52f).toInt()

        for (y in firstRow..lastRow) {
            var runStart = -1
            var bestStart = -1
            var bestEnd = -1
            var x = 0
            while (x < width) {
                if (isBoardPixel(bitmap.getPixel(x, y))) {
                    if (runStart < 0) runStart = x
                } else if (runStart >= 0) {
                    if (x - runStart > bestEnd - bestStart) {
                        bestStart = runStart
                        bestEnd = x
                    }
                    runStart = -1
                }
                x++
            }
            if (runStart >= 0 && width - runStart > bestEnd - bestStart) {
                bestStart = runStart
                bestEnd = width
            }
            if (bestEnd - bestStart >= minimumRun) {
                leftEdges.add(bestStart)
                rightEdges.add(bestEnd)
                detectedRows.add(y)
            }
        }

        if (leftEdges.size < height * 0.30f) return null
        leftEdges.sort()
        rightEdges.sort()
        val trim = leftEdges.size / 20
        val left = leftEdges[trim].toFloat() + width * 0.002f
        val right = rightEdges[rightEdges.lastIndex - trim].toFloat() - width * 0.002f
        val top = detectedRows.first().toFloat() + height * 0.002f
        val bottom = detectedRows.last().toFloat() - height * 0.002f

        if (right - left < width * 0.52f || bottom - top < height * 0.35f) return null
        return RectF(left, top, right, bottom)
    }

    private fun isBoardPixel(pixel: Int): Boolean {
        if (Color.alpha(pixel) < 200) return false
        val red = Color.red(pixel)
        val green = Color.green(pixel)
        val blue = Color.blue(pixel)
        val brightness = (red + green + blue) / 3
        return brightness in 165..247 &&
            max(red, max(green, blue)) - min(red, min(green, blue)) <= 24
    }
}
