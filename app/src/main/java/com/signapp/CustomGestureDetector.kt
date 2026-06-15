package com.signapp

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult
import kotlin.math.sqrt

object CustomGestureDetector {

    // MediaPipe hand landmark indices
    private const val WRIST = 0
    private const val THUMB_MCP = 2
    private const val THUMB_TIP = 4
    private const val INDEX_MCP = 5
    private const val INDEX_PIP = 6
    private const val INDEX_TIP = 8
    private const val MIDDLE_MCP = 9
    private const val MIDDLE_PIP = 10
    private const val MIDDLE_TIP = 12
    private const val RING_MCP = 13
    private const val RING_PIP = 14
    private const val RING_TIP = 16
    private const val PINKY_MCP = 17
    private const val PINKY_PIP = 18
    private const val PINKY_TIP = 20

    fun detect(result: GestureRecognizerResult): String? {
        val landmarks = getLandmarks(result)?.firstOrNull() ?: return null
        if (landmarks.size < 21) return null

        val wrist = landmarks[WRIST]
        val thumbExtended = isThumbExtended(landmarks)
        val indexExtended = isExtended(landmarks[INDEX_TIP], landmarks[INDEX_PIP], wrist)
        val middleExtended = isExtended(landmarks[MIDDLE_TIP], landmarks[MIDDLE_PIP], wrist)
        val ringExtended = isExtended(landmarks[RING_TIP], landmarks[RING_PIP], wrist)
        val pinkyExtended = isExtended(landmarks[PINKY_TIP], landmarks[PINKY_PIP], wrist)

        return when {
            // OK: thumb + index pinch, remaining three fingers extended
            isOkSign(landmarks, middleExtended, ringExtended, pinkyExtended) -> "OK"
            // Rock On: index + pinky up, middle + ring folded
            indexExtended && pinkyExtended && !middleExtended && !ringExtended -> "Rock On"
            // Call Me: thumb + pinky extended, index + middle + ring folded
            thumbExtended && pinkyExtended && !indexExtended && !middleExtended && !ringExtended -> "Call Me"
            // Four: all four fingers extended, thumb folded
            indexExtended && middleExtended && ringExtended && pinkyExtended && !thumbExtended -> "Four"
            // Three: index + middle + ring extended, pinky + thumb folded
            indexExtended && middleExtended && ringExtended && !pinkyExtended && !thumbExtended -> "Three"
            // Gun: index + thumb extended, middle + ring + pinky folded
            indexExtended && thumbExtended && !middleExtended && !ringExtended && !pinkyExtended -> "Gun"
            else -> null
        }
    }

    // Tip farther from wrist than pip → finger is extended (works across orientations)
    private fun isExtended(
        tip: NormalizedLandmark,
        pip: NormalizedLandmark,
        wrist: NormalizedLandmark
    ): Boolean = dist(tip, wrist) > dist(pip, wrist)

    // Thumb tip farther from index MCP than the thumb's own MCP is → thumb is spread open
    private fun isThumbExtended(landmarks: List<NormalizedLandmark>): Boolean {
        val thumbTip = landmarks[THUMB_TIP]
        val thumbMcp = landmarks[THUMB_MCP]
        val indexMcp = landmarks[INDEX_MCP]
        return dist(thumbTip, indexMcp) > dist(thumbMcp, indexMcp)
    }

    private fun isOkSign(
        landmarks: List<NormalizedLandmark>,
        middleExtended: Boolean,
        ringExtended: Boolean,
        pinkyExtended: Boolean
    ): Boolean {
        val pinchDist = dist(landmarks[THUMB_TIP], landmarks[INDEX_TIP])
        return pinchDist < 0.06f && middleExtended && ringExtended && pinkyExtended
    }

    private fun dist(a: NormalizedLandmark, b: NormalizedLandmark): Float {
        val dx = a.x() - b.x()
        val dy = a.y() - b.y()
        return sqrt(dx * dx + dy * dy)
    }

    private fun getLandmarks(result: GestureRecognizerResult): List<List<NormalizedLandmark>>? =
        result.landmarks().takeIf { it.isNotEmpty() }
}