package com.signapp

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import java.io.File
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class LlmHelper(private val context: Context) {

    private var llm: LlmInference? = null

    fun initialize(onProgress: (Float) -> Unit): Boolean {
        if (llm != null) return true
        val modelFile = resolveModelFile(onProgress) ?: return false
        return try {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(512)
                .setMaxTopK(40)
                .setPreferredBackend(LlmInference.Backend.CPU)
                .build()
            llm = LlmInference.createFromOptions(context, options)
            true
        } catch (e: Exception) {
            Log.e(TAG, "LLM init failed", e)
            false
        }
    }

    private fun resolveModelFile(onProgress: (Float) -> Unit): File? {
        val internal = File(context.filesDir, MODEL_FILENAME)
        if (internal.exists()) { onProgress(1f); return internal }
        val external = File(context.getExternalFilesDir(null), MODEL_FILENAME)
        if (external.exists()) { onProgress(1f); return external }
        Log.e(TAG, "Model not found. Push via adb:\nadb push $MODEL_FILENAME /sdcard/Android/data/${context.packageName}/files/")
        return null
    }

    fun isReady() = llm != null

    /** Suspending, returns full response after generation completes. */
    suspend fun generateSync(prompt: String): String {
        val instance = llm ?: return "[LLM not ready]"
        return suspendCancellableCoroutine { cont ->
            val buffer = StringBuilder()
            try {
                instance.generateResponseAsync(prompt) { partial, done ->
                    buffer.append(partial ?: "")
                    if (done) cont.resume(buffer.toString())
                }
            } catch (e: Exception) {
                Log.e(TAG, "generateSync failed", e)
                cont.resume("[Error: ${e.message}]")
            }
        }
    }

    fun close() { llm?.close(); llm = null }

    companion object {
        private const val TAG = "LlmHelper"
        const val MODEL_FILENAME = "gemma-3n-E2B-it-int4.task"

        /** Combined translation + intent detection. Includes conversation history for context. */
        fun translateAndIntentPrompt(tokens: List<String>, history: List<String>): String {
            val historySection = if (history.isNotEmpty())
                "Prior conversation:\n${history.takeLast(6).joinToString("\n")}\n\n" else ""
            return "<start_of_turn>user\n" +
                "${historySection}ASL gesture tokens: ${tokens.joinToString(", ")}\n\n" +
                "Reply in EXACTLY this format, nothing else:\n" +
                "SENTENCE: [one natural English sentence]\n" +
                "INTENT: [one word: Question/Request/Greeting/Farewell/Statement/Emotion]\n" +
                "<end_of_turn>\n<start_of_turn>model\n"
        }

        /** Generates 3 reply options — formal, casual, empathetic. */
        fun replyOptionsPrompt(sentence: String, intent: String): String =
            "<start_of_turn>user\n" +
            "A deaf person said: \"$sentence\" (intent: $intent)\n\n" +
            "Write exactly 3 short replies (max 10 words each):\n" +
            "1. [formal reply]\n2. [casual reply]\n3. [empathetic reply]\n" +
            "<end_of_turn>\n<start_of_turn>model\n"

        /** Suggests hand gestures/signs a hearing person can use to respond. */
        fun gestureSuggestionPrompt(phrase: String): String =
            "<start_of_turn>user\n" +
            "A hearing person wants to communicate: \"$phrase\"\n" +
            "Suggest simple hand gestures or signs they can make. Be brief and practical.\n" +
            "<end_of_turn>\n<start_of_turn>model\n"

        fun parseTranslationAndIntent(response: String): Pair<String, String> {
            var sentence = ""; var intent = "Statement"
            for (line in response.trim().lines()) {
                if (line.startsWith("SENTENCE:", ignoreCase = true))
                    sentence = line.substringAfter(":").trim()
                else if (line.startsWith("INTENT:", ignoreCase = true))
                    intent = line.substringAfter(":").trim()
                        .split(" ").first().replaceFirstChar { it.uppercase() }
            }
            if (sentence.isBlank())
                sentence = response.trim().lines().firstOrNull()?.trim() ?: response.trim()
            return sentence to intent.ifBlank { "Statement" }
        }

        fun parseReplyOptions(response: String): List<String> {
            val numbered = response.trim().lines()
                .filter { it.matches(Regex("^[1-3][.)].+")) }
                .map { it.replace(Regex("^[1-3][.)]\\s*"), "").trim() }
                .filter { it.isNotBlank() }
                .take(3)
            return numbered.ifEmpty {
                response.trim().lines()
                    .map { it.trim() }.filter { it.isNotBlank() }.take(3)
                    .ifEmpty { listOf(response.trim()) }
            }
        }
    }
}