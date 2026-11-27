package com.tracolivre.automator

import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

object VectorDrawingFactory {
    val templates = listOf("Sol", "Coração", "Estrela", "Casa", "Flor", "Gato")

    fun create(template: String, label: String): List<TraceStroke> {
        require(template in templates) { "Escolha um desenho da lista." }
        val name = label.trim()
        require(name.length <= 12) { "O nome pode ter no máximo 12 caracteres." }

        val strokes = pathsFor(template).flatMap(::samplePath).toMutableList()
        if (name.isNotEmpty()) strokes += textPaths(name)
        require(strokes.isNotEmpty()) { "Não foi possível criar esse desenho." }
        return strokes
    }

    private fun pathsFor(template: String): List<Path> = when (template) {
        "Sol" -> sun()
        "Coração" -> listOf(heart())
        "Estrela" -> listOf(star())
        "Casa" -> house()
        "Flor" -> flower()
        "Gato" -> cat()
        else -> error("Desenho vetorial não suportado.")
    }

    private fun sun(): List<Path> {
        val paths = mutableListOf(Path().apply { addCircle(0.5f, 0.43f, 0.19f, Path.Direction.CW) })
        for (index in 0 until 8) {
            val angle = index * (Math.PI / 4.0)
            val innerRadius = 0.25f
            val outerRadius = 0.34f
            paths += line(
                0.5f + cos(angle).toFloat() * innerRadius,
                0.43f + sin(angle).toFloat() * innerRadius,
                0.5f + cos(angle).toFloat() * outerRadius,
                0.43f + sin(angle).toFloat() * outerRadius
            )
        }
        return paths
    }

    private fun heart() = Path().apply {
        moveTo(0.5f, 0.84f)
        cubicTo(0.43f, 0.77f, 0.16f, 0.59f, 0.16f, 0.39f)
        cubicTo(0.16f, 0.19f, 0.39f, 0.17f, 0.5f, 0.34f)
        cubicTo(0.61f, 0.17f, 0.84f, 0.19f, 0.84f, 0.39f)
        cubicTo(0.84f, 0.59f, 0.57f, 0.77f, 0.5f, 0.84f)
        close()
    }

    private fun star() = Path().apply {
        for (index in 0 until 10) {
            val angle = -Math.PI / 2.0 + index * Math.PI / 5.0
            val radius = if (index % 2 == 0) 0.34f else 0.15f
            val x = 0.5f + cos(angle).toFloat() * radius
            val y = 0.47f + sin(angle).toFloat() * radius
            if (index == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

    private fun house(): List<Path> = listOf(
        line(0.18f, 0.47f, 0.5f, 0.19f, 0.82f, 0.47f),
        polygon(0.25f to 0.44f, 0.75f to 0.44f, 0.75f to 0.82f, 0.25f to 0.82f),
        polygon(0.43f to 0.82f, 0.43f to 0.62f, 0.57f to 0.62f, 0.57f to 0.82f),
        polygon(0.31f to 0.51f, 0.39f to 0.51f, 0.39f to 0.59f, 0.31f to 0.59f),
        polygon(0.61f to 0.51f, 0.69f to 0.51f, 0.69f to 0.59f, 0.61f to 0.59f)
    )

    private fun flower(): List<Path> {
        val paths = ArrayList<Path>()
        val centerX = 0.5f
        val centerY = 0.35f
        for (index in 0 until 6) {
            val angle = index * (Math.PI / 3.0)
            val x = centerX + cos(angle).toFloat() * 0.13f
            val y = centerY + sin(angle).toFloat() * 0.13f
            paths += Path().apply {
                addOval(RectF(x - 0.085f, y - 0.12f, x + 0.085f, y + 0.12f), Path.Direction.CW)
            }
        }
        paths += Path().apply { addCircle(centerX, centerY, 0.095f, Path.Direction.CW) }
        paths += line(0.5f, 0.48f, 0.5f, 0.85f)
        paths += Path().apply {
            moveTo(0.5f, 0.7f)
            cubicTo(0.33f, 0.55f, 0.27f, 0.7f, 0.5f, 0.76f)
            moveTo(0.5f, 0.78f)
            cubicTo(0.66f, 0.61f, 0.76f, 0.7f, 0.5f, 0.84f)
        }
        return paths
    }

    private fun cat(): List<Path> = listOf(
        Path().apply {
            moveTo(0.29f, 0.47f)
            lineTo(0.27f, 0.23f)
            lineTo(0.44f, 0.33f)
            cubicTo(0.48f, 0.32f, 0.52f, 0.32f, 0.56f, 0.33f)
            lineTo(0.73f, 0.23f)
            lineTo(0.71f, 0.47f)
            cubicTo(0.78f, 0.7f, 0.64f, 0.82f, 0.5f, 0.82f)
            cubicTo(0.36f, 0.82f, 0.22f, 0.7f, 0.29f, 0.47f)
            close()
        },
        Path().apply { addCircle(0.4f, 0.53f, 0.018f, Path.Direction.CW) },
        Path().apply { addCircle(0.6f, 0.53f, 0.018f, Path.Direction.CW) },
        line(0.47f, 0.61f, 0.5f, 0.64f, 0.53f, 0.61f, 0.5f, 0.59f, 0.47f, 0.61f),
        line(0.34f, 0.63f, 0.2f, 0.6f),
        line(0.34f, 0.68f, 0.19f, 0.7f),
        line(0.66f, 0.63f, 0.8f, 0.6f),
        line(0.66f, 0.68f, 0.81f, 0.7f)
    )

    private fun textPaths(text: String): List<TraceStroke> {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = 100f
            style = Paint.Style.FILL
        }
        val path = Path()
        paint.getTextPath(text, 0, text.length, 0f, 0f, path)
        val textWidth = paint.measureText(text)
        val fontMetrics = paint.fontMetrics
        val textHeight = fontMetrics.descent - fontMetrics.ascent
        if (textWidth <= 0f || textHeight <= 0f) return emptyList()

        val scale = min(0.76f / textWidth, 0.14f / textHeight)
        val centerY = (fontMetrics.ascent + fontMetrics.descent) / 2f
        return samplePath(path) { x, y ->
            TracePoint(
                0.5f + (x - textWidth / 2f) * scale,
                0.8f + (y - centerY) * scale
            )
        }
    }

    private fun samplePath(path: Path): List<TraceStroke> = samplePath(path) { x, y ->
        TracePoint(x, y)
    }

    private fun samplePath(
        path: Path,
        transform: (Float, Float) -> TracePoint
    ): List<TraceStroke> {
        val result = ArrayList<TraceStroke>()
        val measure = PathMeasure(path, false)
        val position = FloatArray(2)
        do {
            val length = measure.length
            if (length > 0f) {
                val count = ceil(length / 0.012f).toInt().coerceIn(2, 120)
                val points = ArrayList<TracePoint>(count + 1)
                for (index in 0..count) {
                    val distance = length * index / count
                    if (measure.getPosTan(distance, position, null)) {
                        points += transform(position[0], position[1])
                    }
                }
                if (points.size >= 2) result += TraceStroke(points)
            }
        } while (measure.nextContour())
        return result
    }

    private fun line(vararg coordinates: Float): Path = Path().apply {
        require(coordinates.size >= 4 && coordinates.size % 2 == 0)
        moveTo(coordinates[0], coordinates[1])
        for (index in 2 until coordinates.size step 2) {
            lineTo(coordinates[index], coordinates[index + 1])
        }
    }

    private fun polygon(vararg points: Pair<Float, Float>): Path = Path().apply {
        require(points.size >= 3)
        moveTo(points.first().first, points.first().second)
        points.drop(1).forEach { lineTo(it.first, it.second) }
        close()
    }
}
