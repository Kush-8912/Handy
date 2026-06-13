package com.signapp.fragment

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.util.Size
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult
import com.signapp.MainViewModel
import com.signapp.OverlayView
import com.signapp.SignRecognizerHelper
import com.signapp.ui.HandyScreen
import com.signapp.ui.theme.HandyTheme
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CameraFragment : Fragment(), SignRecognizerHelper.GestureRecognizerListener {

    private val viewModel: MainViewModel by activityViewModels()

    private lateinit var gestureRecognizerHelper: SignRecognizerHelper
    private lateinit var backgroundExecutor: ExecutorService
    private lateinit var tts: TextToSpeech
    private var ttsReady = false

    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraFacing = CameraSelector.LENS_FACING_FRONT

    // Both views are created once and embedded via AndroidView in Compose
    private val previewView: PreviewView by lazy {
        PreviewView(requireContext()).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    private val overlayView: OverlayView by lazy {
        OverlayView(requireContext(), null)
    }

    // Gesture stability tracking
    private var lastGesture = "none"
    private var gestureCount = 0
    private val stabilityThreshold = 4
    private var committedGesture = ""

    override fun onResume() {
        super.onResume()
        backgroundExecutor.execute {
            if (gestureRecognizerHelper.isClosed()) gestureRecognizerHelper.setupGestureRecognizer()
        }
    }

    override fun onPause() {
        super.onPause()
        backgroundExecutor.execute { gestureRecognizerHelper.clearGestureRecognizer() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        backgroundExecutor.shutdown()
        backgroundExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)
        tts.stop()
        tts.shutdown()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                HandyTheme {
                    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                    HandyScreen(
                        uiState = uiState,
                        previewView = previewView,
                        overlayView = overlayView,
                        onReset = { resetSession() },
                        onSpeak = { speakSession(uiState.sessionText) },
                        onCameraSwitch = { switchCamera() },
                        onDeleteLastWord = { viewModel.deleteLastWord(); committedGesture = "" },
                        onAddSpace = { viewModel.addSpace() },
                        onCopy = { copyToClipboard(uiState.sessionText) },
                        onShare = { shareSession(uiState.sessionText) },
                        onTranslate = { viewModel.translateSession() },
                        onGenerateReply = { viewModel.generateReply() }
                    )
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        backgroundExecutor = Executors.newSingleThreadExecutor()

        tts = TextToSpeech(requireContext()) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = Locale.US
                ttsReady = true
            } else {
                Log.e(TAG, "TTS init failed: $status")
            }
        }

        backgroundExecutor.execute {
            gestureRecognizerHelper = SignRecognizerHelper(
                minHandDetectionConfidence = viewModel.currentMinHandDetectionConfidence,
                minHandTrackingConfidence = viewModel.currentMinHandTrackingConfidence,
                minHandPresenceConfidence = viewModel.currentMinHandPresenceConfidence,
                currentDelegate = viewModel.currentDelegate,
                context = requireContext(),
                gestureRecognizerListener = this
            )
        }

        previewView.post { setUpCamera() }
    }

    private fun switchCamera() {
        cameraFacing = if (cameraFacing == CameraSelector.LENS_FACING_FRONT)
            CameraSelector.LENS_FACING_BACK else CameraSelector.LENS_FACING_FRONT
        bindCameraUseCases()
    }

    private fun resetSession() {
        viewModel.resetSession()
        committedGesture = ""
        resetStability()
        overlayView.clear()
    }

    private fun speakSession(text: String) {
        if (text.isBlank()) {
            Toast.makeText(requireContext(), "Nothing to speak yet", Toast.LENGTH_SHORT).show()
            return
        }
        if (ttsReady) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun copyToClipboard(text: String) {
        if (text.isBlank()) {
            Toast.makeText(requireContext(), "Nothing to copy", Toast.LENGTH_SHORT).show()
            return
        }
        val cb = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cb.setPrimaryClip(ClipData.newPlainText("Handy", text))
        Toast.makeText(requireContext(), "Copied", Toast.LENGTH_SHORT).show()
    }

    private fun shareSession(text: String) {
        if (text.isBlank()) return
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }, "Share via"
            )
        )
    }

    private fun setUpCamera() {
        ProcessCameraProvider.getInstance(requireContext()).also { future ->
            future.addListener({
                cameraProvider = future.get()
                bindCameraUseCases()
            }, ContextCompat.getMainExecutor(requireContext()))
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun bindCameraUseCases() {
        val cp = cameraProvider ?: throw IllegalStateException("Camera init failed.")
        val selector = CameraSelector.Builder().requireLensFacing(cameraFacing).build()
        preview = Preview.Builder()
            .setTargetRotation(previewView.display?.rotation ?: 0)
            .build()
        imageAnalyzer = ImageAnalysis.Builder()
            .setTargetResolution(Size(480, 640))
            .setTargetRotation(previewView.display?.rotation ?: 0)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build().also {
                it.setAnalyzer(backgroundExecutor) { image ->
                    if (::gestureRecognizerHelper.isInitialized) {
                        gestureRecognizerHelper.recognizeLiveStream(
                            image, cameraFacing == CameraSelector.LENS_FACING_FRONT
                        )
                    } else {
                        image.close()
                    }
                }
            }
        cp.unbindAll()
        try {
            camera = cp.bindToLifecycle(this, selector, preview, imageAnalyzer)
            preview?.setSurfaceProvider(previewView.surfaceProvider)
        } catch (e: Exception) {
            Log.e(TAG, "Camera binding failed", e)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        imageAnalyzer?.targetRotation = previewView.display?.rotation ?: 0
    }

    override fun onResults(resultBundle: SignRecognizerHelper.ResultBundle) {
        activity?.runOnUiThread {
            val gesture = resultBundle.results
            val confidence = resultBundle.confidence
            val result: GestureRecognizerResult = resultBundle.gestureRecognizerResult

            if (gesture != "none" && gesture != "None") {
                val label = formatGestureName(gesture)

                viewModel.updateRecognitionResult(label, confidence)

                overlayView.setResults(result, resultBundle.inputImageHeight, resultBundle.inputImageWidth)

                if (gesture == lastGesture) {
                    gestureCount++
                    if (gestureCount == stabilityThreshold) commitGesture(label)
                } else {
                    lastGesture = gesture
                    gestureCount = 1
                }
            } else {
                viewModel.clearRecognition()
                overlayView.clear()
                committedGesture = ""
                resetStability()
            }
        }
    }

    private fun commitGesture(label: String) {
        if (committedGesture == label) return
        committedGesture = label
        viewModel.commitGestureToSession(label)
        if (ttsReady) tts.speak(label, TextToSpeech.QUEUE_FLUSH, null, null)
        view?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    private fun resetStability() {
        lastGesture = "none"
        gestureCount = 0
    }

    private fun formatGestureName(raw: String): String =
        raw.replace('_', ' ')
            .replace(Regex("([a-z])([A-Z])")) { "${it.groupValues[1]} ${it.groupValues[2]}" }

    override fun onError(error: String, errorCode: Int) {
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val TAG = "CameraFragment"
    }
}