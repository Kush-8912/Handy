/*
 * Copyright 2022 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.signapp

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class GestureCandidate(val name: String, val confidence: Float)

data class HandyUiState(
    val currentGesture: String = "—",
    val confidence: Float = 0f,
    val candidates: List<GestureCandidate> = emptyList(),
    val sessionWords: List<String> = emptyList(),
    val gestureCount: Int = 0
) {
    val sessionText: String
        get() = sessionWords.filter { it.isNotEmpty() }.joinToString(" ")
}

class MainViewModel : ViewModel() {

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
    fun setMinHandDetectionConfidence(confidence: Float) { _minHandDetectionConfidence = confidence }
    fun setMinHandTrackingConfidence(confidence: Float) { _minHandTrackingConfidence = confidence }
    fun setMinHandPresenceConfidence(confidence: Float) { _minHandPresenceConfidence = confidence }

    // UI state
    private val _uiState = MutableStateFlow(HandyUiState())
    val uiState: StateFlow<HandyUiState> = _uiState.asStateFlow()

    fun updateRecognitionResult(gesture: String, confidence: Float, candidates: List<GestureCandidate>) {
        _uiState.update { it.copy(currentGesture = gesture, confidence = confidence, candidates = candidates) }
    }

    fun clearRecognition() {
        _uiState.update { it.copy(currentGesture = "—", confidence = 0f, candidates = emptyList()) }
    }

    fun commitGestureToSession(word: String) {
        _uiState.update { state ->
            val newWords = (state.sessionWords + word).takeLast(30)
            state.copy(sessionWords = newWords, gestureCount = state.gestureCount + 1)
        }
    }

    fun deleteLastWord() {
        _uiState.update { state ->
            val newWords = state.sessionWords.dropLastWhile { it.isEmpty() }
            val trimmed = if (newWords.isNotEmpty()) newWords.dropLast(1) else newWords
            state.copy(sessionWords = trimmed)
        }
    }

    fun addSpace() {
        _uiState.update { state ->
            if (state.sessionWords.isEmpty()) state
            else state.copy(sessionWords = state.sessionWords + "")
        }
    }

    fun resetSession() {
        _uiState.update { HandyUiState() }
    }
}