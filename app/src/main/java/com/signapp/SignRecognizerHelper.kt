package com.signapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizer
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult

class SignRecognizerHelper(
    var minHandDetectionConfidence: Float = DEFAULT_HAND_DETECTION_CONFIDENCE,
    var minHandTrackingConfidence: Float = DEFAULT_HAND_TRACKING_CONFIDENCE,
    var minHandPresenceConfidence: Float = DEFAULT_HAND_PRESENCE_CONFIDENCE,
    var currentDelegate: Int = DELEGATE_CPU,
    var runningMode: RunningMode = RunningMode.LIVE_STREAM,
    private val context: Context,
    private val gestureRecognizerListener: GestureRecognizerListener? = null
) {
    private var gestureRecognizer: GestureRecognizer? = null

    init {
        setupGestureRecognizer()
    }

    fun clearGestureRecognizer() {
        gestureRecognizer?.close()
        gestureRecognizer = null
    }

    fun setupGestureRecognizer() {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(MODEL_ASSET)
            .apply {
                when (currentDelegate) {
                    DELEGATE_GPU -> setDelegate(Delegate.GPU)
                    else -> setDelegate(Delegate.CPU)
                }
            }
            .build()

        val options = GestureRecognizer.GestureRecognizerOptions.builder()
            .setBaseOptions(baseOptions)
            .setMinHandDetectionConfidence(minHandDetectionConfidence)
            .setMinTrackingConfidence(minHandTrackingConfidence)
            .setMinHandPresenceConfidence(minHandPresenceConfidence)
            .setRunningMode(runningMode)
            .setNumHands(1)
            .also { builder ->
                if (runningMode == RunningMode.LIVE_STREAM) {
                    builder.setResultListener(this::handleResult)
                    builder.setErrorListener { error ->
                        gestureRecognizerListener?.onError(
                            error.message ?: "MediaPipe error", OTHER_ERROR
                        )
                        Log.e(TAG, "MediaPipe error", error)
                    }
                }
            }
            .build()

        try {
            gestureRecognizer = GestureRecognizer.createFromOptions(context, options)
        } catch (e: Exception) {
            gestureRecognizerListener?.onError(
                "Failed to initialize: ${e.message}", OTHER_ERROR
            )
            Log.e(TAG, "Setup failed", e)
        }
    }

    fun recognizeLiveStream(imageProxy: ImageProxy, isFrontCamera: Boolean) {
        val frameTime = SystemClock.uptimeMillis()

        val bitmapBuffer = Bitmap.createBitmap(
            imageProxy.width, imageProxy.height, Bitmap.Config.ARGB_8888
        )
        imageProxy.use { bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer) }
        imageProxy.close()

        val matrix = Matrix().apply {
            postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
            if (isFrontCamera) postScale(-1f, 1f, imageProxy.width / 2f, imageProxy.height / 2f)
        }
        val rotatedBitmap = Bitmap.createBitmap(
            bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height, matrix, true
        )
        bitmapBuffer.recycle()

        gestureRecognizer?.recognizeAsync(BitmapImageBuilder(rotatedBitmap).build(), frameTime)
    }

    private fun handleResult(result: GestureRecognizerResult, image: MPImage) {
        val gesture: String
        val confidence: Float

        if (result.gestures().isNotEmpty() && result.gestures()[0].isNotEmpty()) {
            val top = result.gestures()[0][0]
            val modelGesture = top.categoryName() ?: "none"
            if (modelGesture.equals("none", ignoreCase = true)) {
                // Hand detected but no standard gesture — try custom landmark detection
                val custom = CustomGestureDetector.detect(result)
                gesture = custom ?: "none"
                confidence = if (custom != null) 1f else 0f
            } else {
                gesture = modelGesture
                confidence = top.score()
            }
        } else {
            gesture = "none"
            confidence = 0f
        }

        gestureRecognizerListener?.onResults(
            ResultBundle(gesture, confidence, result, image.height, image.width)
        )
    }

    fun isClosed(): Boolean = gestureRecognizer == null

    data class ResultBundle(
        val results: String,
        val confidence: Float,
        val gestureRecognizerResult: GestureRecognizerResult,
        val inputImageHeight: Int,
        val inputImageWidth: Int,
    )

    interface GestureRecognizerListener {
        fun onError(error: String, errorCode: Int = OTHER_ERROR)
        fun onResults(resultBundle: ResultBundle)
    }

    companion object {
        const val TAG = "SignRecognizer"
        const val MODEL_ASSET = "gesture_recognizer.task"
        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1
        const val DEFAULT_HAND_DETECTION_CONFIDENCE = 0.6f
        const val DEFAULT_HAND_TRACKING_CONFIDENCE = 0.5f
        const val DEFAULT_HAND_PRESENCE_CONFIDENCE = 0.5f
        const val OTHER_ERROR = 0
        const val GPU_ERROR = 1
    }
}