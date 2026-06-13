package com.signapp.fragment

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Typeface
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.util.Size
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.signapp.SignRecognizerHelper
import com.signapp.MainViewModel
import com.signapp.R
import com.signapp.databinding.FragmentCameraBinding
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CameraFragment : Fragment(), SignRecognizerHelper.GestureRecognizerListener {

    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!

    private lateinit var gestureRecognizerHelper: SignRecognizerHelper
    private val viewModel: MainViewModel by activityViewModels()

    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraFacing = CameraSelector.LENS_FACING_FRONT

    private lateinit var backgroundExecutor: ExecutorService
    private lateinit var tts: TextToSpeech
    private var ttsReady = false

    private val sentence = StringBuilder()
    private val gestureHistory = ArrayDeque<String>()
    private var totalGestureCount = 0

    private var lastGesture = "none"
    private var gestureCount = 0
    private val stabilityThreshold = 4
    private var committedGesture = ""

    override fun onResume() {
        super.onResume()
        backgroundExecutor.execute {
            if (gestureRecognizerHelper.isClosed()) {
                gestureRecognizerHelper.setupGestureRecognizer()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        backgroundExecutor.execute { gestureRecognizerHelper.clearGestureRecognizer() }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
        backgroundExecutor.shutdown()
        backgroundExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)
        tts.stop()
        tts.shutdown()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
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

        binding.viewFinder.post { setUpCamera() }
        setupButtons()
    }

    private fun setupButtons() {
        binding.cameraSwitchButton.setOnClickListener {
            cameraFacing = if (cameraFacing == CameraSelector.LENS_FACING_FRONT)
                CameraSelector.LENS_FACING_BACK else CameraSelector.LENS_FACING_FRONT
            bindCameraUseCases()
        }

        binding.deleteButton.setOnClickListener {
            sentence.clear()
            gestureHistory.clear()
            binding.subtitleText.text = ""
            binding.historyChipsContainer.removeAllViews()
            totalGestureCount = 0
            binding.counterBadge.text = "0 SIGNS"
            resetStability()
        }

        binding.backButton.setOnClickListener {
            if (sentence.isNotEmpty()) {
                // Remove last word
                val trimmed = sentence.trimEnd()
                val lastSpace = trimmed.lastIndexOf(' ')
                sentence.clear()
                if (lastSpace >= 0) sentence.append(trimmed.substring(0, lastSpace + 1))
                binding.subtitleText.text = sentence.toString()
                resetStability()
                committedGesture = ""
            }
        }

        binding.spaceButton.setOnClickListener {
            if (sentence.isNotEmpty() && sentence.last() != ' ') {
                sentence.append(' ')
                binding.subtitleText.text = sentence.toString()
                resetStability()
            }
        }

        binding.copyButton.setOnClickListener {
            val text = binding.subtitleText.text.toString().trim()
            if (text.isEmpty()) {
                Toast.makeText(requireContext(), "Nothing to copy yet", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Handy", text))
            Toast.makeText(requireContext(), "Copied!", Toast.LENGTH_SHORT).show()
        }

        binding.shareButton.setOnClickListener {
            val text = binding.subtitleText.text.toString().trim()
            if (text.isEmpty()) {
                Toast.makeText(requireContext(), "Nothing to share yet", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    },
                    "Share via"
                )
            )
        }

        binding.fabSpeak.setOnClickListener {
            val text = binding.subtitleText.text.toString().trim()
            if (text.isEmpty()) {
                Toast.makeText(requireContext(), "Nothing to speak yet", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (ttsReady) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
            binding.fabSpeak.animate().scaleX(0.88f).scaleY(0.88f).setDuration(80)
                .withEndAction { binding.fabSpeak.animate().scaleX(1f).scaleY(1f).setDuration(120).start() }
                .start()
        }
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
        val cameraProvider = cameraProvider
            ?: throw IllegalStateException("Camera initialization failed.")
        val cameraSelector = CameraSelector.Builder().requireLensFacing(cameraFacing).build()
        preview = Preview.Builder()
            .setTargetRotation(binding.viewFinder.display.rotation)
            .build()
        imageAnalyzer = ImageAnalysis.Builder()
            .setTargetResolution(Size(480, 640))
            .setTargetRotation(binding.viewFinder.display.rotation)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also {
                it.setAnalyzer(backgroundExecutor) { image ->
                    if (::gestureRecognizerHelper.isInitialized) {
                        gestureRecognizerHelper.recognizeLiveStream(
                            image,
                            cameraFacing == CameraSelector.LENS_FACING_FRONT
                        )
                    } else {
                        image.close()
                    }
                }
            }
        cameraProvider.unbindAll()
        try {
            camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
            preview?.setSurfaceProvider(binding.viewFinder.surfaceProvider)
        } catch (e: Exception) {
            Log.e(TAG, "Camera binding failed", e)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        imageAnalyzer?.targetRotation = binding.viewFinder.display.rotation
    }

    override fun onResults(resultBundle: SignRecognizerHelper.ResultBundle) {
        activity?.runOnUiThread {
            val gesture = resultBundle.results
            val confidence = resultBundle.confidence

            binding.confidenceBar.progress = if (gesture != "none" && gesture != "None")
                (confidence * 100).toInt() else 0

            if (gesture != "none" && gesture != "None") {
                val label = formatGestureName(gesture)
                val pct = String.format("%.0f%%", confidence * 100)
                binding.gestureTextView.text = "$label  $pct"

                if (gesture == lastGesture) {
                    gestureCount++
                    if (gestureCount == stabilityThreshold) {
                        commitGesture(label)
                    }
                } else {
                    lastGesture = gesture
                    gestureCount = 1
                }
            } else {
                binding.gestureTextView.text = "…"
                committedGesture = ""
                resetStability()
                binding.overlay.clear()
            }

            if (gesture != "none" && gesture != "None") {
                binding.overlay.setResults(
                    resultBundle.gestureRecognizerResult,
                    resultBundle.inputImageHeight,
                    resultBundle.inputImageWidth
                )
            }
        }
    }

    private fun commitGesture(label: String) {
        if (committedGesture == label) return
        committedGesture = label
        if (!sentence.trimEnd().endsWith(label)) {
            if (sentence.isNotEmpty() && sentence.last() != ' ') sentence.append(' ')
            sentence.append(label)
            binding.subtitleText.text = sentence.toString()
        }
        if (ttsReady) tts.speak(label, TextToSpeech.QUEUE_FLUSH, null, null)
        addToHistory(label)
        playCommitFlash()
        binding.root.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        totalGestureCount++
        binding.counterBadge.text = "$totalGestureCount SIGNS"
    }

    private fun addToHistory(label: String) {
        gestureHistory.addLast(label)
        if (gestureHistory.size > 10) gestureHistory.removeFirst()

        binding.historyChipsContainer.removeAllViews()
        for (word in gestureHistory) {
            val chip = TextView(requireContext()).apply {
                text = word
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(28, 10, 28, 10)
                background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_chip)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.setMargins(6, 0, 6, 0)
                layoutParams = lp
                setOnClickListener {
                    if (ttsReady) tts.speak(word, TextToSpeech.QUEUE_FLUSH, null, null)
                }
            }
            binding.historyChipsContainer.addView(chip)
        }
        binding.historyScroll.post {
            binding.historyScroll.fullScroll(View.FOCUS_RIGHT)
        }
    }

    private fun playCommitFlash() {
        binding.flashOverlay.alpha = 0.25f
        binding.flashOverlay.animate()
            .alpha(0f)
            .setDuration(350)
            .start()
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