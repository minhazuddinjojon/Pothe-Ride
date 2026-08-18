package com.potheride.app.core.verification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceChecksTest {

    private fun goodFace(
        leftEye: Float? = 0.9f,
        rightEye: Float? = 0.9f,
        area: Float = 0.2f,
        yaw: Float = 0f,
        roll: Float = 0f
    ) = DetectedFace(leftEye, rightEye, area, yaw, roll)

    @Test
    fun `a single well-framed frontal face with open eyes is accepted`() {
        val result = FaceChecks.evaluate(listOf(goodFace()))
        assertTrue(result is FaceCheck.Accepted)
    }

    @Test
    fun `no face is refused as NO_FACE`() {
        val result = FaceChecks.evaluate(emptyList())
        assertEquals(FaceCheckFailure.NO_FACE, (result as FaceCheck.Refused).reason)
    }

    @Test
    fun `two faces is refused as MULTIPLE_FACES, not silently picking one`() {
        val result = FaceChecks.evaluate(listOf(goodFace(), goodFace()))
        assertEquals(FaceCheckFailure.MULTIPLE_FACES, (result as FaceCheck.Refused).reason)
    }

    @Test
    fun `a distant small face is refused as FACE_TOO_SMALL`() {
        val result = FaceChecks.evaluate(listOf(goodFace(area = 0.01f)))
        assertEquals(FaceCheckFailure.FACE_TOO_SMALL, (result as FaceCheck.Refused).reason)
    }

    @Test
    fun `closed eyes are refused as EYES_CLOSED`() {
        val result = FaceChecks.evaluate(listOf(goodFace(leftEye = 0.1f, rightEye = 0.1f)))
        assertEquals(FaceCheckFailure.EYES_CLOSED, (result as FaceCheck.Refused).reason)
    }

    @Test
    fun `one closed eye is enough to refuse`() {
        val result = FaceChecks.evaluate(listOf(goodFace(leftEye = 0.9f, rightEye = 0.1f)))
        assertEquals(FaceCheckFailure.EYES_CLOSED, (result as FaceCheck.Refused).reason)
    }

    @Test
    fun `null eye probabilities mean unclassified, not closed, and do not block acceptance`() {
        // A detector built without ENABLE_CLASSIFICATION reports null. Treating that as
        // "closed" would fail every capture on a misconfigured detector.
        val result = FaceChecks.evaluate(listOf(goodFace(leftEye = null, rightEye = null)))
        assertTrue(result is FaceCheck.Accepted)
    }

    @Test
    fun `a face turned too far is refused as FACE_NOT_FRONTAL`() {
        val turnedAway = FaceChecks.evaluate(listOf(goodFace(yaw = 40f)))
        assertEquals(FaceCheckFailure.FACE_NOT_FRONTAL, (turnedAway as FaceCheck.Refused).reason)

        val tilted = FaceChecks.evaluate(listOf(goodFace(roll = 40f)))
        assertEquals(FaceCheckFailure.FACE_NOT_FRONTAL, (tilted as FaceCheck.Refused).reason)
    }

    @Test
    fun `angles right at the threshold are accepted, just past it are refused`() {
        val atLimit = FaceChecks.evaluate(listOf(goodFace(yaw = FaceChecks.MAX_HEAD_ANGLE_DEGREES)))
        assertTrue(atLimit is FaceCheck.Accepted)

        val justOver = FaceChecks.evaluate(listOf(goodFace(yaw = FaceChecks.MAX_HEAD_ANGLE_DEGREES + 0.1f)))
        assertTrue(justOver is FaceCheck.Refused)
    }

    @Test
    fun `every failure reason has non-blank guidance in both languages`() {
        FaceCheckFailure.values().forEach { reason ->
            assertTrue(FaceChecks.guidanceEn(reason).isNotBlank())
            assertTrue(FaceChecks.guidanceBn(reason).isNotBlank())
        }
    }

    // ---- FaceMatchResult / LivenessOnlyFaceMatchProvider — the honesty requirement ----

    @Test
    fun `the liveness-only provider never returns a confidence figure`() = kotlinx.coroutines.test.runTest {
        val accepted = LivenessOnlyFaceMatchProvider(FaceCheck.Accepted(goodFace()))
            .match(ByteArray(0), reference = null)
        assertEquals(null, accepted.confidence)
        assertTrue(accepted.passed)
        assertTrue(!accepted.isRealMatch)

        val refused = LivenessOnlyFaceMatchProvider(FaceCheck.Refused(FaceCheckFailure.NO_FACE))
            .match(ByteArray(0), reference = null)
        assertEquals(null, refused.confidence)
        assertTrue(!refused.passed)
        assertEquals(FaceCheckFailure.NO_FACE, refused.failure)
    }

    @Test
    fun `isRealMatch is keyed off confidence being present, not off passed`() {
        // A provider that DID perform a real comparison could still fail the match; the
        // UI's wording must switch on whether a comparison happened, not on the verdict.
        val failedRealMatch = FaceMatchResult(passed = false, confidence = 0.2f, provider = "real")
        assertTrue(failedRealMatch.isRealMatch)

        val passedLivenessOnly = FaceMatchResult(passed = true, confidence = null, provider = "liveness")
        assertTrue(!passedLivenessOnly.isRealMatch)
    }
}
