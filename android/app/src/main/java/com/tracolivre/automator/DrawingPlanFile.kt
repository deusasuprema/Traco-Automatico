package com.tracolivre.automator

import android.content.Context
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

object DrawingPlanFile {
    private const val FILE_NAME = "drawing-plan.bin"
    private const val FORMAT_VERSION = 3
    private const val MAX_STROKES = 1600
    private const val MAX_POINTS = 120

    fun write(context: Context, strokes: List<TraceStroke>) {
        DataOutputStream(BufferedOutputStream(context.openFileOutput(FILE_NAME, Context.MODE_PRIVATE))).use { output ->
            output.writeInt(FORMAT_VERSION)
            output.writeInt(strokes.size)
            strokes.forEach { stroke ->
                output.writeInt(stroke.points.size)
                stroke.points.forEach { point ->
                    output.writeFloat(point.x)
                    output.writeFloat(point.y)
                }
            }
        }
    }

    fun read(context: Context): List<TraceStroke> {
        DataInputStream(BufferedInputStream(context.openFileInput(FILE_NAME))).use { input ->
            val formatVersion = input.readInt()
            require(formatVersion in 1..FORMAT_VERSION) { "Formato de desenho incompatível." }
            val strokeCount = input.readInt()
            require(strokeCount in 1..MAX_STROKES) { "Quantidade de traços inválida." }
            return List(strokeCount) {
                if (formatVersion == 2) input.readInt()
                val pointCount = input.readInt()
                require(pointCount in 2..MAX_POINTS) { "Traço inválido." }
                TraceStroke(List(pointCount) {
                    TracePoint(input.readFloat(), input.readFloat())
                })
            }
        }
    }
}
