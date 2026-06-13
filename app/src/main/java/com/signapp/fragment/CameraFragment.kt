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
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult
import com.signapp.MainViewModel
import com.signapp.R
import com.signapp.SignRecognizerHelper
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

    private val session = StringBuilder()
    private val sessionWords = ArrayDeque<String>()
    private var totalCount = 0

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

        binding.resetButton.setOnClickListener {
            session.clear()
            sessionWords.clear()
            totalCount = 0
            committedGesture = ""
            binding.subtitleText.text = "—"
            binding.gestureCountText.text = "0 gestures this session"
            binding.candidatesContainer.removeAllViews()
            binding.gestureTextView.text = "—"
            binding.confidenceTextView.text = ""
            binding.confidenceBar.progress = 0
            resetStability()
        }

        binding.speakButton.setOnClickListener {
            val text = session.toString().trim()
            if (text.isEmpty()) {
                Toast.makeText(requireContext(), "Nothing to speak yet", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (ttsReady) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
            binding.speakButton.animate()
                .scaleX(0.93f).scaleY(0.93f).setDuration(70)
                .withEndAction {
                    binding.speakButton.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                }.start()
        }

        binding.moreButton.setOnClickListener { anchor ->
            val popup = PopupMenu(requireContext(), anchor)
            popup.menuInflater.inflate(R.menu.menu_more, popup.menu)
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_back_word -> {
                        if (sessionWords.isNotEmpty()) {
                            sessionWords.removeLast()
                            rebuildSession()
                            committedGesture = ""
                            resetStability()
                        }
                        true
                    }
                    R.id.action_space -> {
                        if (session.isNotEmpty() && session.last() != ' ') session.append(' ')
                        true
                    }
                    R.id.action_copy -> {
                        val text = session.toString().trim()
                        if (text.isEmpty()) {
                            Toast.makeText(requireContext(), "Nothing to copy", Toast.LENGTH_SHORT).show()
                        } else {
                            val cb = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cb.setPrimaryClip(ClipData.newPlainText("Handy", text))
                            Toast.makeText(requireContext(), "Copied", Toast.LENGTH_SHORT).show()
                        }
                        true
                    }
                    R.id.action_share -> {
                        val text = session.toString().trim()
                        if (text.isNotEmpty()) {
                            startActivity(Intent.createChooser(
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, text)
                                }, "Share via"
                            ))
                        }
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }

    private fun rebuildSession() {
        session.clear()
        sessionWords.forEachIndexed { i, word ->
            if (i > 0) session.append(' ')
            session.append(word)
        }
        binding.subtitleText.text = session.toString().trim().ifEmpty { "—" }
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
        preview = Preview.Builder().setTargetRotation(binding.viewFinder.display.rotation).build()
        imageAnalyzer = ImageAnalysis.Builder()
            .setTargetResolution(Size(480, 640))
            .setTargetRotation(binding.viewFinder.display.rotation)
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

            if (gesture != "none" && gesture != "None") {
                val label = formatGestureName(gesture)
                binding.gestureTextView.text = label
                binding.confidenceTextView.text = "${(confidence * 100).toInt()}% CONFIDENCE"
                binding.confidenceBar.progress = (confidence * 100).toInt()
                updateCandidates(resultBundle.gestureRecognizerResult)
                binding.overlay.setResults(
                    resultBundle.gestureRecognizerResult,
                    resultBundle.inputImageHeight,
                    resultBundle.inputImageWidth
                )
                if (gesture == lastGesture) {
                    gestureCount++
                    if (gestureCount == stabilityThreshold) commitGesture(label)
                } else {
                    lastGesture = gesture
                    gestureCount = 1
                }
            } else {
                binding.gestureTextView.text = "—"
                binding.confidenceTextView.text = ""
                binding.confidenceBar.progress = 0
                binding.candidatesContainer.removeAllViews()
                committedGesture = ""
                resetStability()
                binding.overlay.clear()
            }
        }
    }

    private fun commitGesture(label: String) {
        if (committedGesture == label) return
        committedGesture = label

        sessionWords.addLast(label)
        if (sessionWords.size > 30) sessionWords.removeFirst()
        totalCount++

        rebuildSession()
        binding.gestureCountText.text = "$totalCount gesture${if (totalCount == 1) "" else "s"} this session"

        if (ttsReady) tts.speak(label, TextToSpeech.QUEUE_FLUSH, null, null)
        playCommitFlash()
        binding.root.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    private fun updateCandidates(result: GestureRecognizerResult) {
        binding.candidatesContainer.removeAllViews()
        if (result.gestures().isEmpty()) return

        val candidates = result.gestures()[0]
            .filter { !it.categoryName().isNullOrEmpty() && it.categoryName() != "none" }
            .take(4)
        if (candidates.isEmpty()) return

        candidates.forEachIndexed { i, category ->
            val name = formatGestureName(category.categoryName()!!)
            val pct = (category.score() * 100).toInt()
            val isTop = i == 0

            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { lp -> lp.topMargin = if (i == 0) 0 else 10.dp }
            }

            row.addView(TextView(requireContext()).apply {
                text = name
                setTextColor(if (isTop) 0xFFFFFFFF.toInt() else 0xFFA0A0A0.toInt())
                textSize = if (isTop) 14f else 13f
                if (isTop) typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })

            row.addView(TextView(requireContext()).apply {
                text = "$pct%"
                setTextColor(if (isTop) 0xFF4DA3FF.toInt() else 0xFF555555.toInt())
                textSize = if (isTop) 14f else 13f
                if (isTop) typeface = Typeface.DEFAULT_BOLD
                gravity = android.view.Gravity.END
            })

            binding.candidatesContainer.addView(row)
        }
    }

    private fun playCommitFlash() {
        binding.flashOverlay.alpha = 0.06f
        binding.flashOverlay.animate().alpha(0f).setDuration(500).start()
        binding.gestureTextView.animate()
            .scaleX(1.03f).scaleY(1.03f).setDuration(70)
            .withEndAction {
                binding.gestureTextView.animate().scaleX(1f).scaleY(1f).setDuration(140).start()
            }.start()
    }

    private fun resetStability() {
        lastGesture = "none"
        gestureCount = 0
    }

    private fun formatGestureName(raw: String): String =
        raw.replace('_', ' ')
            .replace(Regex("([a-z])([A-Z])")) { "${it.groupValues[1]} ${it.groupValues[2]}" }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    override fun onError(error: String, errorCode: Int) {
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val TAG = "CameraFragment"
    }
}