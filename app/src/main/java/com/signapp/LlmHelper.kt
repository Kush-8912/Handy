package com.signapp

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import java.io.File
import java.util.concurrent.atomic.AtomicReference

class LlmHelper(private val context: Context) {

    private var llm: LlmInference? = null

    // Swapped before each generateAsync call; the constructor-time listener delegates here.
    private val pendingCallback = AtomicReference<((String, Boolean) -> Unit)?>()

    // Blocking — call from a background thread.
    // onProgress is called with 0..1 while copying the model from assets on first run.
    fun initialize(onProgress: (Float) -> Unit): Boolean {
        if (llm != null) return true

        val modelFile = File(context.filesDir, MODEL_FILENAME)
        if (!modelFile.exists()) {
            val ok = copyFromAssets(modelFile, onProgress)
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
            modelFile.delete()   // remove so next launch retries a fresh copy
            false
        }
    }

    fun isReady() = llm != null

    // Non-blocking. MediaPipe calls the listener on its own thread.
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

    private fun copyFromAssets(target: File, onProgress: (Float) -> Unit): Boolean {
        return try {
            val totalBytes = context.assets.openFd(MODEL_FILENAME).use { it.length }
            context.assets.open(MODEL_FILENAME).use { src ->
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
            Log.e(TAG, "Model copy failed — is '$MODEL_FILENAME' in app/src/main/assets/?", e)
            target.delete()
            false
        }
    }

    companion object {
        private const val TAG = "LlmHelper"
        const val MODEL_FILENAME = "gemma-2b-it-cpu-int4.bin"

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