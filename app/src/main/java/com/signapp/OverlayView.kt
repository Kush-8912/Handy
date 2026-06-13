package com.signapp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import kotlin.math.max
import kotlin.math.min

class OverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    private var landmarkSets: List<List<NormalizedLandmark>> = emptyList()
    private var scaleFactor: Float = 1f
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1

    // Per-finger colors (thumb → index → middle → ring → pinky → wrist/palm)
    private val fingerColors = intArrayOf(
        Color.parseColor("#FF6B6B"), // 0 – wrist / palm
        Color.parseColor("#FF6B6B"), // 1-4  thumb   – coral
        Color.parseColor("#FFD93D"), // 5-8  index   – gold
        Color.parseColor("#6BCB77"), // 9-12 middle  – green
        Color.parseColor("#4D96FF"), // 13-16 ring   – blue
        Color.parseColor("#C77DFF")  // 17-20 pinky  – lavender
    )

    private val bonePaint = Paint().apply {
        strokeWidth = 9f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }
    private val jointPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val glowPaint = Paint().apply {
        strokeWidth = 18f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
        alpha = 80
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    private fun landmarkColor(index: Int): Int = when {
        index in 1..4  -> fingerColors[1]
        index in 5..8  -> fingerColors[2]
        index in 9..12 -> fingerColors[3]
        index in 13..16 -> fingerColors[4]
        index in 17..20 -> fingerColors[5]
        else           -> fingerColors[0]
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        for (landmarks in landmarkSets) {
            // Draw glow bones first (underneath)
            HandLandmarker.HAND_CONNECTIONS.forEach { connection ->
                val start = connection!!.start()
                val end = connection.end()
                val color = landmarkColor(maxOf(start, end))
                glowPaint.color = color
                glowPaint.setShadowLayer(20f, 0f, 0f, color)
                canvas.drawLine(
                    landmarks[start].x() * imageWidth * scaleFactor,
                    landmarks[start].y() * imageHeight * scaleFactor,
                    landmarks[end].x() * imageWidth * scaleFactor,
                    landmarks[end].y() * imageHeight * scaleFactor,
                    glowPaint
                )
            }
            // Draw solid bones on top
            HandLandmarker.HAND_CONNECTIONS.forEach { connection ->
                val start = connection!!.start()
                val end = connection.end()
                val color = landmarkColor(maxOf(start, end))
                bonePaint.color = color
                canvas.drawLine(
                    landmarks[start].x() * imageWidth * scaleFactor,
                    landmarks[start].y() * imageHeight * scaleFactor,
                    landmarks[end].x() * imageWidth * scaleFactor,
                    landmarks[end].y() * imageHeight * scaleFactor,
                    bonePaint
                )
            }
            // Draw glowing joint dots
            for ((i, landmark) in landmarks.withIndex()) {
                val x = landmark.x() * imageWidth * scaleFactor
                val y = landmark.y() * imageHeight * scaleFactor
                val color = landmarkColor(i)
                // Outer glow
                jointPaint.color = color
                jointPaint.alpha = 60
                jointPaint.setShadowLayer(16f, 0f, 0f, color)
                canvas.drawCircle(x, y, 14f, jointPaint)
                // Bright core
                jointPaint.alpha = 255
                jointPaint.setShadowLayer(8f, 0f, 0f, Color.WHITE)
                canvas.drawCircle(x, y, 6f, jointPaint)
            }
        }
    }

    fun setResults(
        result: GestureRecognizerResult,
        imageHeight: Int,
        imageWidth: Int,
        runningMode: RunningMode = RunningMode.LIVE_STREAM
    ) {
        landmarkSets = try {
            @Suppress("UNCHECKED_CAST")
            val method = result.javaClass.getMethod("handLandmarks")
            method.invoke(result) as? List<List<NormalizedLandmark>> ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        this.imageHeight = imageHeight
        this.imageWidth = imageWidth
        scaleFactor = when (runningMode) {
            RunningMode.IMAGE, RunningMode.VIDEO ->
                min(width * 1f / imageWidth, height * 1f / imageHeight)
            RunningMode.LIVE_STREAM ->
                max(width * 1f / imageWidth, height * 1f / imageHeight)
        }
        invalidate()
    }

    fun clear() {
        landmarkSets = emptyList()
        invalidate()
    }
}