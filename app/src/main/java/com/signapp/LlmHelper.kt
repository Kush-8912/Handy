package com.signapp

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
class LlmHelper(private val context: Context) {

    private var llm: LlmInference? = null

    fun initialize(onProgress: (Float) -> Unit): Boolean {
        if (llm != null) return true

        // 1. Internal storage (already copied on a previous run)
        // 2. App-specific external storage — push here via adb, no permissions needed:
        //    adb push gemma-3n-E2B-it-int4.task /sdcard/Android/data/com.signapp/files/
        // 3. Download from DOWNLOAD_URL if set
        val modelFile = resolveModelFile(onProgress) ?: return false

        return try {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(300)
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
        // Already in internal storage
        val internal = File(context.filesDir, MODEL_FILENAME)
        if (internal.exists()) { onProgress(1f); return internal }

        // Pushed via adb to app external storage (no permissions needed on Android 10+)
        val external = File(context.getExternalFilesDir(null), MODEL_FILENAME)
        if (external.exists()) { onProgress(1f); return external }

        // Download from URL if configured
        if (DOWNLOAD_URL.isNotBlank()) {
            return if (downloadFromUrl(internal, onProgress)) internal else null
        }

        Log.e(TAG, "Model not found. Push it via adb:\n" +
            "adb push $MODEL_FILENAME /sdcard/Android/data/${context.packageName}/files/")
        return null
    }

    fun isReady() = llm != null

    fun generateAsync(prompt: String, onResult: (partial: String, done: Boolean) -> Unit) {
        val instance = llm
        if (instance == null) {
            onResult("[LLM not ready]", true)
            return
        }
        try {
            instance.generateResponseAsync(prompt) { partial, done ->
                onResult(partial ?: "", done)
            }
        } catch (e: Exception) {
            Log.e(TAG, "generateResponseAsync failed", e)
            onResult("[Error: ${e.message}]", true)
        }
    }

    fun close() {
        llm?.close()
        llm = null
    }

    private fun downloadFromUrl(target: File, onProgress: (Float) -> Unit): Boolean {
        return try {
            Log.i(TAG, "Downloading model from $DOWNLOAD_URL")
            val conn = URL(DOWNLOAD_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 0
            conn.connect()
            val totalBytes = conn.contentLengthLong
            conn.inputStream.use { src ->
                target.outputStream().use { dst ->
                    val buf = ByteArray(65_536)
                    var written = 0L
                    var n: Int
                    while (src.read(buf).also { n = it } != -1) {
                        dst.write(buf, 0, n)
                        written += n
                        if (totalBytes > 0) onProgress(written.toFloat() / totalBytes)
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Model download failed", e)
            target.delete()
            false
        }
    }

    companion object {
        private const val TAG = "LlmHelper"

        const val MODEL_FILENAME = "gemma-3n-E2B-it-int4.task"

        // Optional: set a direct download URL to fetch the model at first launch
        const val DOWNLOAD_URL = ""

        fun translatePrompt(tokens: List<String>): String =
            "<start_of_turn>user\n" +
            "You are an ASL interpreter. Convert these recognized gesture tokens into one natural, fluent English sentence. Output only the sentence.\n\n" +
            "Tokens: ${tokens.joinToString(", ")}\n" +
            "<end_of_turn>\n" +
            "<start_of_turn>model\n"

        fun replyPrompt(sentence: String): String =
            "<start_of_turn>user\n" +
            "A person using sign language communicated: \"$sentence\"\n\n" +
            "Write a short, natural reply a hearing person could say in response. Output only the reply.\n" +
            "<end_of_turn>\n" +
            "<start_of_turn>model\n"
    }
}