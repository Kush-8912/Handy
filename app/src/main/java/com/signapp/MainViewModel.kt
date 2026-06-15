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

enum class LlmPhase { IDLE, TRANSLATING, TRANSLATED }

data class HandyUiState(
    val currentGesture: String = "—",
    val confidence: Float = 0f,
    val sessionWords: List<String> = emptyList(),
    val gestureCount: Int = 0,
    // model loading
    val modelReady: Boolean = false,
    val modelCopyProgress: Float? = null,
    val modelError: String? = null,
    // translation flow
    val llmPhase: LlmPhase = LlmPhase.IDLE,
    val llmTranslation: String = "",
    val llmIntent: String = "",
    val llmReplyOptions: List<String> = emptyList(),
    val llmSelectedReply: String = "",
    // gesture suggestion (independent of translation)
    val isSuggesting: Boolean = false,
    val gestureSuggestion: String = "",
    // conversation history for context-aware prompts
    val conversationHistory: List<String> = emptyList()
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
        val wasTranslated = _uiState.value.llmPhase == LlmPhase.TRANSLATED
        _uiState.update { state ->
            val trimmed = state.sessionWords.dropLastWhile { it.isEmpty() }
            state.copy(sessionWords = if (trimmed.isNotEmpty()) trimmed.dropLast(1) else trimmed)
        }
        if (wasTranslated) {
            val remaining = _uiState.value.sessionWords.filter { it.isNotEmpty() }
            if (remaining.isEmpty()) {
                _uiState.update { it.copy(
                    llmPhase = LlmPhase.IDLE,
                    llmTranslation = "", llmIntent = "",
                    llmReplyOptions = emptyList(), llmSelectedReply = ""
                )}
            } else {
                translateSession()  // re-translate with the updated session
            }
        }
    }

    fun resetSession() {
        _uiState.update { current ->
            HandyUiState(
                modelReady = current.modelReady,
                modelCopyProgress = current.modelCopyProgress,
                modelError = current.modelError,
                conversationHistory = current.conversationHistory  // preserve across sessions
            )
        }
    }

    // ── LLM: translation + intent + reply chips ──────────────────────────

    fun translateSession() {
        val tokens = _uiState.value.sessionWords.filter { it.isNotEmpty() }
        if (tokens.isEmpty() || !llmHelper.isReady()) return
        val state = _uiState.value
        if (state.llmPhase == LlmPhase.TRANSLATING || state.isSuggesting) return

        viewModelScope.launch(Dispatchers.IO) {
            val history = _uiState.value.conversationHistory
            _uiState.update { it.copy(
                llmPhase = LlmPhase.TRANSLATING,
                llmTranslation = "",
                llmIntent = "",
                llmReplyOptions = emptyList(),
                llmSelectedReply = ""
            )}

            // Step 1: translate + detect intent (combined)
            val raw1 = llmHelper.generateSync(LlmHelper.translateAndIntentPrompt(tokens, history))
            val (sentence, intent) = LlmHelper.parseTranslationAndIntent(raw1)
            _uiState.update { it.copy(llmTranslation = sentence, llmIntent = intent) }

            // Step 2: generate 3 reply chips
            val raw2 = llmHelper.generateSync(LlmHelper.replyOptionsPrompt(sentence, intent))
            val options = LlmHelper.parseReplyOptions(raw2)
            _uiState.update { it.copy(llmPhase = LlmPhase.TRANSLATED, llmReplyOptions = options) }
        }
    }

    /** Selects a reply chip and records the exchange in conversation history. */
    fun selectReply(reply: String) {
        _uiState.update { state ->
            val updatedHistory = (state.conversationHistory +
                listOf("Signer: ${state.llmTranslation}", "Reply: $reply")
            ).takeLast(8)
            state.copy(llmSelectedReply = reply, conversationHistory = updatedHistory)
        }
    }

    // ── LLM: gesture suggestion ──────────────────────────────────────────

    /** Runs independently — doesn't disturb translation results already shown. */
    fun suggestGestures(phrase: String) {
        if (phrase.isBlank() || !llmHelper.isReady()) return
        val state = _uiState.value
        if (state.llmPhase == LlmPhase.TRANSLATING || state.isSuggesting) return

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isSuggesting = true, gestureSuggestion = "") }
            val response = llmHelper.generateSync(LlmHelper.gestureSuggestionPrompt(phrase))
            _uiState.update { it.copy(isSuggesting = false, gestureSuggestion = response.trim()) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        llmHelper.close()
    }
}