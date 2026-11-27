package com.tracolivre.automator

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class TracePoint(val x: Float, val y: Float)
data class TraceStroke(val points: List<TracePoint>)

object TraceProcessor {
    private const val MAX_SIDE = 220
    private const val MAX_STROKES = 1600
    private const val MAX_POINTS_PER_STROKE = 120

    fun recommendDetail(bitmap: Bitmap): Int {
        val scale = min(1f, MAX_SIDE.toFloat() / max(bitmap.width, bitmap.height))
        val width = max(2, (bitmap.width * scale).roundToInt())
        val height = max(2, (bitmap.height * scale).roundToInt())
        val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        if (scaled !== bitmap) scaled.recycle()

        val gray = IntArray(pixels.size) { index ->
            val color = pixels[index]
            val luminance = (android.graphics.Color.red(color) * 299 +
                android.graphics.Color.green(color) * 587 +
                android.graphics.Color.blue(color) * 114) / 1000
            val alpha = android.graphics.Color.alpha(color)
            (luminance * alpha + 255 * (255 - alpha)) / 255
        }
        val magnitude = FloatArray(pixels.size)
        val gradientX = FloatArray(pixels.size)
        val gradientY = FloatArray(pixels.size)
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val index = y * width + x
                val gx = -gray[index - width - 1] + gray[index - width + 1] -
                    2 * gray[index - 1] + 2 * gray[index + 1] -
                    gray[index + width - 1] + gray[index + width + 1]
                val gy = -gray[index - width - 1] - 2 * gray[index - width] -
                    gray[index - width + 1] + gray[index + width - 1] +
                    2 * gray[index + width] + gray[index + width + 1]
                gradientX[index] = gx.toFloat()
                gradientY[index] = gy.toFloat()
                magnitude[index] = sqrt((gx * gx + gy * gy).toFloat())
            }
        }

        val eligiblePixels = max(1, (width - 2) * (height - 2))
        val targetEdgeDensity = 0.022f
        var bestDetail = 55
        var bestDifference = Float.MAX_VALUE
        for (candidate in 15..85 step 5) {
            val threshold = 18f + (100 - candidate) * 0.72f
            var edgeCount = 0
            for (y in 1 until height - 1) {
                for (x in 1 until width - 1) {
                    val index = y * width + x
                    val current = magnitude[index]
                    if (current < threshold) continue
                    val horizontal = abs(gradientX[index]) >= abs(gradientY[index])
                    val first = if (horizontal) index - 1 else index - width
                    val second = if (horizontal) index + 1 else index + width
                    if (current >= magnitude[first] && current >= magnitude[second]) {
                        edgeCount++
                    }
                }
            }
            val edgeDensity = edgeCount.toFloat() / eligiblePixels
            val difference = abs(edgeDensity - targetEdgeDensity)
            if (difference < bestDifference) {
                bestDifference = difference
                bestDetail = candidate
            }
        }
        return bestDetail
    }

    fun trace(bitmap: Bitmap, detail: Int): List<TraceStroke> {
        val scale = min(1f, MAX_SIDE.toFloat() / max(bitmap.width, bitmap.height))
        val width = max(2, (bitmap.width * scale).roundToInt())
        val height = max(2, (bitmap.height * scale).roundToInt())
        val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        if (scaled !== bitmap) scaled.recycle()

        val gray = IntArray(pixels.size) { index ->
            val color = pixels[index]
            val luminance = (android.graphics.Color.red(color) * 299 +
                android.graphics.Color.green(color) * 587 +
                android.graphics.Color.blue(color) * 114) / 1000
            val alpha = android.graphics.Color.alpha(color)
            (luminance * alpha + 255 * (255 - alpha)) / 255
        }
        val magnitude = FloatArray(pixels.size)
        val gradientX = FloatArray(pixels.size)
        val gradientY = FloatArray(pixels.size)

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val index = y * width + x
                val gx = -gray[index - width - 1] + gray[index - width + 1] -
                    2 * gray[index - 1] + 2 * gray[index + 1] -
                    gray[index + width - 1] + gray[index + width + 1]
                val gy = -gray[index - width - 1] - 2 * gray[index - width] -
                    gray[index - width + 1] + gray[index + width - 1] +
                    2 * gray[index + width] + gray[index + width + 1]
                gradientX[index] = gx.toFloat()
                gradientY[index] = gy.toFloat()
                magnitude[index] = sqrt((gx * gx + gy * gy).toFloat())
            }
        }

        val threshold = 18f + (100 - detail.coerceIn(0, 100)) * 0.72f
        val edges = BooleanArray(pixels.size)
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val index = y * width + x
                val current = magnitude[index]
                if (current < threshold) continue
                val horizontal = abs(gradientX[index]) >= abs(gradientY[index])
                val first = if (horizontal) index - 1 else index - width
                val second = if (horizontal) index + 1 else index + width
                if (current >= magnitude[first] && current >= magnitude[second]) {
                    edges[index] = true
                }
            }
        }

        val visited = BooleanArray(edges.size)
        val result = ArrayList<TraceStroke>()

        fun neighbors(index: Int): List<Int> {
            val x = index % width
            val y = index / width
            val found = ArrayList<Int>(8)
            for (dy in -1..1) {
                for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = x + dx
                    val ny = y + dy
                    if (nx in 0 until width && ny in 0 until height) {
                        val next = ny * width + nx
                        if (edges[next]) found.add(next)
                    }
                }
            }
            return found
        }

        fun traceFrom(start: Int) {
            if (visited[start] || result.size >= MAX_STROKES) return
            val indices = ArrayList<Int>()
            var previous = -1
            var current = start
            while (!visited[current] && indices.size < edges.size) {
                visited[current] = true
                indices.add(current)
                val choices = neighbors(current).filter { !visited[it] }
                if (choices.isEmpty()) break
                val next = if (previous < 0 || choices.size == 1) {
                    choices.first()
                } else {
                    val previousX = previous % width
                    val previousY = previous / width
                    choices.maxBy { candidate ->
                        val directionX = current % width - previousX
                        val directionY = current / width - previousY
                        val candidateX = candidate % width - current % width
                        val candidateY = candidate / width - current / width
                        directionX * candidateX + directionY * candidateY
                    }
                }
                previous = current
                current = next
            }

            if (indices.size < 2) return
            val pixelPoints = indices.map { index ->
                TracePoint((index % width).toFloat(), (index / width).toFloat())
            }
            val simplified = simplify(pixelPoints, 0.8f).map { point ->
                TracePoint(point.x / (width - 1), point.y / (height - 1))
            }
            val limited = if (simplified.size <= MAX_POINTS_PER_STROKE) {
                simplified
            } else {
                List(MAX_POINTS_PER_STROKE) { pointIndex ->
                    simplified[pointIndex * (simplified.size - 1) / (MAX_POINTS_PER_STROKE - 1)]
                }
            }
            if (limited.size >= 2) result.add(TraceStroke(limited))
        }

        for (index in edges.indices) {
            if (!edges[index] || visited[index]) continue
            if (neighbors(index).size != 2) traceFrom(index)
            if (result.size >= MAX_STROKES) break
        }
        for (index in edges.indices) {
            if (edges[index] && !visited[index]) traceFrom(index)
            if (result.size >= MAX_STROKES) break
        }
        return result
    }

    private fun simplify(points: List<TracePoint>, tolerance: Float): List<TracePoint> {
        if (points.size <= 2) return points
        val keep = BooleanArray(points.size)
        keep[0] = true
        keep[points.lastIndex] = true
        val ranges = java.util.ArrayDeque<Pair<Int, Int>>()
        ranges.push(0 to points.lastIndex)
        val toleranceSquared = tolerance * tolerance

        while (ranges.isNotEmpty()) {
            val (first, last) = ranges.pop()
            var greatestDistance = toleranceSquared
            var greatestIndex = -1
            val start = points[first]
            val end = points[last]
            val dx = end.x - start.x
            val dy = end.y - start.y
            val lengthSquared = dx * dx + dy * dy

            for (index in first + 1 until last) {
                val point = points[index]
                val distanceSquared = if (lengthSquared == 0f) {
                    val px = point.x - start.x
                    val py = point.y - start.y
                    px * px + py * py
                } else {
                    val projection = (((point.x - start.x) * dx + (point.y - start.y) * dy) /
                        lengthSquared).coerceIn(0f, 1f)
                    val px = point.x - (start.x + projection * dx)
                    val py = point.y - (start.y + projection * dy)
                    px * px + py * py
                }
                if (distanceSquared > greatestDistance) {
                    greatestDistance = distanceSquared
                    greatestIndex = index
                }
            }

            if (greatestIndex >= 0) {
                keep[greatestIndex] = true
                ranges.push(first to greatestIndex)
                ranges.push(greatestIndex to last)
            }
        }
        return points.indices.filter { keep[it] }.map(points::get)
    }
}
