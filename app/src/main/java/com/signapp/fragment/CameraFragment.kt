package com.signapp.fragment

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Bundle
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.signapp.SignRecognizerHelper
import com.signapp.MainViewModel
import com.signapp.databinding.FragmentCameraBinding
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

    private val sentence = StringBuilder()

    // Stability: same gesture must appear this many times before committing
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
            binding.subtitleText.text = ""
            resetStability()
        }

        binding.backButton.setOnClickListener {
            if (sentence.isNotEmpty()) {
                sentence.deleteCharAt(sentence.length - 1)
                binding.subtitleText.text = sentence.toString()
                resetStability()
            }
        }

        binding.spaceButton.setOnClickListener {
            if (sentence.isNotEmpty() && sentence.last() != ' ') {
                sentence.append(' ')
                binding.subtitleText.text = sentence.toString()
                resetStability()
            }
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
            android.util.Log.e(TAG, "Camera binding failed", e)
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
        if (committedGesture == label) return  // still holding same sign — wait for hand to drop
        committedGesture = label
        if (!sentence.trimEnd().endsWith(label)) {
            if (sentence.isNotEmpty() && sentence.last() != ' ') sentence.append(' ')
            sentence.append(label)
            binding.subtitleText.text = sentence.toString()
        }
        // Leave gestureCount at stabilityThreshold so == check never fires again
        // until a different gesture resets lastGesture.
    }

    private fun resetStability() {
        lastGesture = "none"
        gestureCount = 0
    }

    // "Thumb_Up" → "Thumb Up", "ILoveYou" → "I Love You"
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