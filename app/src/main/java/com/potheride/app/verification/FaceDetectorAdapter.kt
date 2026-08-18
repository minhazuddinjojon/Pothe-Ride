package com.potheride.app.verification

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.potheride.app.core.verification.DetectedFace
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The only file in the app that imports ML Kit's face types.
 *
 * Everything downstream of this — [com.potheride.app.core.verification.FaceChecks],
 * [com.potheride.app.core.verification.FaceMatchResult] — works on the plain
 * [DetectedFace] data class and has no ML Kit dependency, which is what keeps the
 * acceptance rules unit-testable on the JVM. This adapter's only job is the translation.
 */
object FaceDetectorAdapter {

    /**
     * `ENABLE_CLASSIFICATION` is what makes ML Kit populate the eye-open probabilities at
     * all — without it [DetectedFace.leftEyeOpenProbability] would be null for every
     * frame, which `FaceChecks.evaluate` correctly treats as "not checked" rather than
     * "closed", but that would silently disable the liveness check board 03 asks for.
     */
    private val detector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setMinFaceSize(0.15f)
                .build()
        )
    }

    suspend fun detect(image: InputImage): List<DetectedFace> =
        suspendCancellableCoroutine { continuation ->
            detector.process(image)
                .addOnSuccessListener { faces: List<Face> ->
                    continuation.resume(faces.map { it.toDomain(image) })
                }
                .addOnFailureListener { continuation.resumeWithException(it) }
        }

    private fun Face.toDomain(image: InputImage): DetectedFace {
        val frameArea = (image.width * image.height).toFloat().coerceAtLeast(1f)
        val box = boundingBox
        return DetectedFace(
            leftEyeOpenProbability = leftEyeOpenProbability,
            rightEyeOpenProbability = rightEyeOpenProbability,
            boundingBoxAreaFraction = (box.width().toFloat() * box.height().toFloat()) / frameArea,
            headEulerAngleY = headEulerAngleY,
            headEulerAngleZ = headEulerAngleZ
        )
    }
}
