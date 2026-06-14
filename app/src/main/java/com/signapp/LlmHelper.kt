package com.signapp

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicReference

class LlmHelper(private val context: Context) {

    private var llm: LlmInference? = null

    private val pendingCallback = AtomicReference<((String, Boolean) -> Unit)?>()

    // Blocking — call from a background thread.
    // onProgress: 0..1 during download/copy; returns false if model unavailable.
    fun initialize(onProgress: (Float) -> Unit): Boolean {
        if (llm != null) return true

        val modelFile = File(context.filesDir, MODEL_FILENAME)

        if (!modelFile.exists()) {
            // Try assets first (dev convenience), then fall back to URL download.
            val ok = copyFromAssets(modelFile, onProgress)
                ?: downloadFromUrl(modelFile, onProgress)
            if (!ok) return false
        } else {
            onProgress(1f)
        }

        return try {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(300)
                .setMaxTopK(40)
                .setResultListener { partial, done ->
                    pendingCallback.get()?.invoke(partial, done)
                    if (done) pendingCallback.set(null)
                }
                .setErrorListener { error ->
                    Log.e(TAG, "LLM error", error)
                    pendingCallback.getAndSet(null)?.invoke("[Error: ${error?.message}]", true)
                }
                .build()
            llm = LlmInference.createFromOptions(context, options)
            true
        } catch (e: Exception) {
            Log.e(TAG, "LLM init failed", e)
            modelFile.delete()
            false
        }
    }

    fun isReady() = llm != null

    fun generateAsync(prompt: String, onResult: (partial: String, done: Boolean) -> Unit) {
        val instance = llm
        if (instance == null) {
            onResult("[LLM not ready]", true)
            return
        }
        pendingCallback.set(onResult)
        try {
            instance.generateResponseAsync(prompt)
        } catch (e: Exception) {
            Log.e(TAG, "generateResponseAsync failed", e)
            pendingCallback.set(null)
            onResult("[Error: ${e.message}]", true)
        }
    }

    fun close() {
        pendingCallback.set(null)
        llm?.close()
        llm = null
    }

    // Returns true on success, null if assets don't contain the model (not an error).
    private fun copyFromAssets(target: File, onProgress: (Float) -> Unit): Boolean? {
        return try {
            val afd = context.assets.openFd(MODEL_FILENAME)
            val totalBytes = afd.length
            // Use FileInputStream via the raw FD — avoids assets.open() buffering the whole file
            afd.createInputStream().use { src ->
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
            afd.close()
            true
        } catch (_: Exception) {
            // Model not in assets — that's fine, we'll try the download URL.
            target.delete()
            null
        }
    }

    private fun downloadFromUrl(target: File, onProgress: (Float) -> Unit): Boolean {
        if (DOWNLOAD_URL.isBlank()) {
            Log.e(TAG, "No model in assets and DOWNLOAD_URL is not set in LlmHelper.")
            return false
        }
        return try {
            Log.i(TAG, "Downloading model from $DOWNLOAD_URL")
            val conn = URL(DOWNLOAD_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 0       // large file — no read timeout
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

        // Paste a direct download link to the MediaPipe-format Gemma model here.
        // Get it from: https://www.kaggle.com/models/google/gemma/tfLite/gemma-2b-it-cpu-int4
        // Then host it on Firebase Storage, Google Drive (direct link), or any CDN.
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