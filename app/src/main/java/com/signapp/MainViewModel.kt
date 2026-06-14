package com.signapp

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LlmPhase { IDLE, TRANSLATING, TRANSLATED, REPLYING, REPLIED }

data class HandyUiState(
    val currentGesture: String = "—",
    val confidence: Float = 0f,
    val sessionWords: List<String> = emptyList(),
    val gestureCount: Int = 0,
    // LLM
    val modelReady: Boolean = false,
    val modelCopyProgress: Float? = null,   // null = idle, 0..1 = copying in progress
    val modelError: String? = null,
    val llmPhase: LlmPhase = LlmPhase.IDLE,
    val llmTranslation: String = "",
    val llmReply: String = ""
) {
    val sessionText: String
        get() = sessionWords.filter { it.isNotEmpty() }.joinToString(" ")
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val llmHelper = LlmHelper(application)

    // Recognition confidence settings
    private var _delegate: Int = SignRecognizerHelper.DELEGATE_CPU
    private var _minHandDetectionConfidence: Float = SignRecognizerHelper.DEFAULT_HAND_DETECTION_CONFIDENCE
    private var _minHandTrackingConfidence: Float = SignRecognizerHelper.DEFAULT_HAND_TRACKING_CONFIDENCE
    private var _minHandPresenceConfidence: Float = SignRecognizerHelper.DEFAULT_HAND_PRESENCE_CONFIDENCE

    val currentDelegate: Int get() = _delegate
    val currentMinHandDetectionConfidence: Float get() = _minHandDetectionConfidence
    val currentMinHandTrackingConfidence: Float get() = _minHandTrackingConfidence
    val currentMinHandPresenceConfidence: Float get() = _minHandPresenceConfidence

    fun setDelegate(delegate: Int) { _delegate = delegate }
    fun setMinHandDetectionConfidence(c: Float) { _minHandDetectionConfidence = c }
    fun setMinHandTrackingConfidence(c: Float) { _minHandTrackingConfidence = c }
    fun setMinHandPresenceConfidence(c: Float) { _minHandPresenceConfidence = c }

    // UI state
    private val _uiState = MutableStateFlow(HandyUiState())
    val uiState: StateFlow<HandyUiState> = _uiState.asStateFlow()

    init {
        loadModel()
    }

    private fun loadModel() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(modelCopyProgress = 0f) }
            val ok = llmHelper.initialize { progress ->
                _uiState.update { it.copy(modelCopyProgress = progress) }
            }
            if (ok) {
                _uiState.update { it.copy(modelReady = true, modelCopyProgress = null, modelError = null) }
            } else {
                _uiState.update { it.copy(
                    modelReady = false,
                    modelCopyProgress = null,
                    modelError = "Model not found — run: adb push ${LlmHelper.MODEL_FILENAME} /sdcard/Android/data/com.signapp/files/"
                )}
            }
        }
    }

    // ── Gesture recognition ───────────────────────────────────────────────

    fun updateRecognitionResult(gesture: String, confidence: Float) {
        _uiState.update { it.copy(currentGesture = gesture, confidence = confidence) }
    }

    fun clearRecognition() {
        _uiState.update { it.copy(currentGesture = "—", confidence = 0f) }
    }

    fun commitGestureToSession(word: String) {
        _uiState.update { state ->
            val newWords = (state.sessionWords + word).takeLast(30)
            state.copy(sessionWords = newWords, gestureCount = state.gestureCount + 1)
        }
    }

    fun deleteLastWord() {
        _uiState.update { state ->
            val trimmed = state.sessionWords.dropLastWhile { it.isEmpty() }
            state.copy(sessionWords = if (trimmed.isNotEmpty()) trimmed.dropLast(1) else trimmed)
        }
    }

    fun resetSession() {
        // Preserve model state across resets
        _uiState.update { current ->
            HandyUiState(
                modelReady = current.modelReady,
                modelCopyProgress = current.modelCopyProgress,
                modelError = current.modelError
            )
        }
    }

    // ── LLM ──────────────────────────────────────────────────────────────

    fun translateSession() {
        val tokens = _uiState.value.sessionWords.filter { it.isNotEmpty() }
        if (tokens.isEmpty() || !llmHelper.isReady()) return

        _uiState.update { it.copy(
            llmPhase = LlmPhase.TRANSLATING,
            llmTranslation = "",
            llmReply = ""
        )}

        val prompt = LlmHelper.translatePrompt(tokens)
        llmHelper.generateAsync(prompt) { partial, done ->
            _uiState.update { it.copy(
                llmTranslation = it.llmTranslation + partial,
                llmPhase = if (done) LlmPhase.TRANSLATED else LlmPhase.TRANSLATING
            )}
        }
    }

    fun generateReply() {
        val sentence = _uiState.value.llmTranslation.trim()
        if (sentence.isBlank() || !llmHelper.isReady()) return

        _uiState.update { it.copy(llmPhase = LlmPhase.REPLYING, llmReply = "") }

        val prompt = LlmHelper.replyPrompt(sentence)
        llmHelper.generateAsync(prompt) { partial, done ->
            _uiState.update { it.copy(
                llmReply = it.llmReply + partial,
                llmPhase = if (done) LlmPhase.REPLIED else LlmPhase.REPLYING
            )}
        }
    }

    override fun onCleared() {
        super.onCleared()
        llmHelper.close()
    }
}