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
    private val linePaint = Paint().apply {
        color = Color.GREEN
        strokeWidth = LANDMARK_STROKE_WIDTH
        style = Paint.Style.STROKE
    }
    private val pointPaint = Paint().apply {
        color = Color.YELLOW
        strokeWidth = LANDMARK_STROKE_WIDTH
        style = Paint.Style.FILL
    }

    private var scaleFactor: Float = 1f
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        for (landmarks in landmarkSets) {
            for (landmark in landmarks) {
                canvas.drawPoint(
                    landmark.x() * imageWidth * scaleFactor,
                    landmark.y() * imageHeight * scaleFactor,
                    pointPaint
                )
            }
            HandLandmarker.HAND_CONNECTIONS.forEach { connection ->
                canvas.drawLine(
                    landmarks[connection!!.start()].x() * imageWidth * scaleFactor,
                    landmarks[connection.start()].y() * imageHeight * scaleFactor,
                    landmarks[connection.end()].x() * imageWidth * scaleFactor,
                    landmarks[connection.end()].y() * imageHeight * scaleFactor,
                    linePaint
                )
            }
        }
    }

    fun setResults(
        result: GestureRecognizerResult,
        imageHeight: Int,
        imageWidth: Int,
        runningMode: RunningMode = RunningMode.LIVE_STREAM
    ) {
        // GestureRecognizerResult exposes hand landmarks via handLandmarks() in newer builds;
        // fall back to an empty list if the method isn't available at runtime.
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

    companion object {
        private const val LANDMARK_STROKE_WIDTH = 8F
    }
}