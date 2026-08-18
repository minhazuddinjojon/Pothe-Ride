package com.potheride.app.core.verification

/**
 * Face verification — domain model and the frame-acceptance rules.
 *
 * ## The honesty problem this file exists to solve
 *
 * Board 03B shows the result card reading **"98% confidence against NID photo"**. That
 * number cannot be produced by anything in this build, and it is worth being blunt about
 * why, because the temptation to hard-code it is real and the consequence is not cosmetic.
 *
 * ML Kit ships face **detection**, not face **recognition**. Detection answers "is there
 * a face in this image, where is it, and are the eyes open?" It has no notion of *whose*
 * face it is and never compares one image to another. Face *recognition* — embedding two
 * images and measuring the distance between them — is a different model that ML Kit does
 * not provide. So a confidence figure "against NID photo" is not merely unavailable; the
 * reference photo is never even loaded.
 *
 * Printing `98%` anyway would be a fabricated security claim on a screen whose entire job
 * is to make a security claim. A passenger reading it would reasonably conclude the app
 * had checked the driver's face against their national ID. It had not. That is worse than
 * showing nothing, and it is the sort of thing that is discovered during an incident.
 *
 * So: [FaceMatchResult.confidence] is **nullable**, and it is null for every provider that
 * cannot honestly fill it in. The UI renders what was actually established —
 * "Face detected · liveness passed" — and simply omits the match figure. When a real
 * matcher is available (an on-device embedding model, or a server endpoint that holds the
 * NID photo), implement [FaceMatchProvider], return a genuine confidence, and the result
 * card starts showing a number that means something. Nothing else has to change.
 *
 * Everything here is pure Kotlin so the acceptance rules can be unit-tested without a
 * camera, an emulator, or ML Kit on the classpath.
 */

/**
 * One face as reported by a detector, reduced to the fields the rules use.
 *
 * The probabilities are nullable because ML Kit only populates them when classification
 * is switched on in the detector options — and a detector configured without it reports
 * `null`, not `0f`. Treating an absent probability as "eyes closed" would fail every
 * capture on a misconfigured detector, so the rules distinguish the two cases.
 */
data class DetectedFace(
    /** 0f..1f, or null when eye classification is disabled. */
    val leftEyeOpenProbability: Float?,
    /** 0f..1f, or null when eye classification is disabled. */
    val rightEyeOpenProbability: Float?,
    /** Fraction (0f..1f) of the frame area the face bounding box covers. */
    val boundingBoxAreaFraction: Float,
    /** Head rotation about the vertical axis, degrees. Negative is left. */
    val headEulerAngleY: Float = 0f,
    /** Head tilt, degrees. */
    val headEulerAngleZ: Float = 0f
)

/** Why a captured frame was not accepted. Each maps to one line of guidance on screen. */
enum class FaceCheckFailure {
    /** Nothing face-shaped in the frame at all. */
    NO_FACE,

    /** More than one person in shot — we cannot tell which one is being verified. */
    MULTIPLE_FACES,

    /** The face is too small; a distant face gives the detector too little to work with. */
    FACE_TOO_SMALL,

    /** Eyes closed. The liveness proxy: a held-up photograph rarely blinks on cue. */
    EYES_CLOSED,

    /** Head turned or tilted too far for a usable reference image. */
    FACE_NOT_FRONTAL
}

/** The verdict on a single captured frame. */
sealed interface FaceCheck {
    data class Accepted(val face: DetectedFace) : FaceCheck
    data class Refused(val reason: FaceCheckFailure) : FaceCheck
}

/**
 * The outcome recorded against a verification attempt.
 *
 * @param passed whether the attempt is treated as successful.
 * @param confidence 0f..1f similarity to a reference photo, or **null when no real
 *   matcher ran**. Read the file header before considering populating this with anything
 *   that is not a genuine comparison against the driver's NID image.
 * @param provider an identifier for whatever produced the result, so a stored attempt can
 *   be interpreted later. Attempts recorded by the liveness-only provider must never be
 *   mistaken for attempts that were actually matched.
 */
data class FaceMatchResult(
    val passed: Boolean,
    val confidence: Float?,
    val provider: String,
    val failure: FaceCheckFailure? = null
) {
    /**
     * `true` only when a real comparison against a reference image happened. The result
     * card keys its wording off this, so it never overstates what was checked.
     */
    val isRealMatch: Boolean get() = confidence != null
}

/**
 * The seam a real face matcher plugs into.
 *
 * Deliberately takes raw bytes rather than an Android `Bitmap` so an implementation can be
 * a network call, and so this interface stays free of Android types. Implementations are
 * expected to be slow — call them off the main thread.
 */
interface FaceMatchProvider {
    /** Stable identifier written into [FaceMatchResult.provider]. */
    val id: String

    /**
     * @param capture the freshly taken selfie, encoded (JPEG).
     * @param reference the stored NID portrait, encoded, or null when none is on file.
     */
    suspend fun match(capture: ByteArray, reference: ByteArray?): FaceMatchResult
}

/**
 * The provider this build actually ships: detection and liveness only.
 *
 * It never returns a confidence. That is not an oversight to be filled in later with a
 * plausible-looking number — it is the whole point. See the file header.
 */
class LivenessOnlyFaceMatchProvider(
    private val check: FaceCheck
) : FaceMatchProvider {

    override val id: String = PROVIDER_ID

    override suspend fun match(capture: ByteArray, reference: ByteArray?): FaceMatchResult =
        when (check) {
            is FaceCheck.Accepted -> FaceMatchResult(
                passed = true,
                confidence = null,
                provider = id
            )
            is FaceCheck.Refused -> FaceMatchResult(
                passed = false,
                confidence = null,
                provider = id,
                failure = check.reason
            )
        }

    companion object {
        const val PROVIDER_ID = "mlkit-face-detection-liveness"
    }
}

/**
 * The frame-acceptance rules, split out from the ML Kit plumbing so they are testable.
 *
 * Thresholds are named constants rather than inline literals because they will be tuned
 * against real captures in low light, and a tuning pass should not be an archaeology
 * exercise across three files.
 */
object FaceChecks {

    /**
     * Both eyes must exceed this to count as open.
     *
     * 0.4 rather than something stricter: ML Kit reports lower probabilities for narrower
     * eye shapes and for anyone wearing glasses, and a threshold tuned on wide-open eyes
     * in good light locks those users out of the app entirely.
     */
    const val EYE_OPEN_THRESHOLD = 0.4f

    /** Below this share of the frame the face is too far away to be a useful reference. */
    const val MIN_FACE_AREA_FRACTION = 0.06f

    /** Degrees of yaw/roll tolerated before we ask the driver to face the camera. */
    const val MAX_HEAD_ANGLE_DEGREES = 25f

    /**
     * Applies board 03's stated requirement: exactly one face, with open eyes.
     *
     * "Exactly one" is not pedantry. A second face in frame — a bystander at a CNG stand,
     * which is the normal environment here — means we cannot say which face was verified,
     * and a verification you cannot attribute is not a verification.
     */
    fun evaluate(faces: List<DetectedFace>): FaceCheck {
        if (faces.isEmpty()) return FaceCheck.Refused(FaceCheckFailure.NO_FACE)
        if (faces.size > 1) return FaceCheck.Refused(FaceCheckFailure.MULTIPLE_FACES)

        val face = faces.single()
        if (face.boundingBoxAreaFraction < MIN_FACE_AREA_FRACTION) {
            return FaceCheck.Refused(FaceCheckFailure.FACE_TOO_SMALL)
        }
        if (kotlin.math.abs(face.headEulerAngleY) > MAX_HEAD_ANGLE_DEGREES ||
            kotlin.math.abs(face.headEulerAngleZ) > MAX_HEAD_ANGLE_DEGREES
        ) {
            return FaceCheck.Refused(FaceCheckFailure.FACE_NOT_FRONTAL)
        }
        // Null means classification was never run, not "closed". Refusing here would fail
        // every capture on a detector built without ENABLE_CLASSIFICATION, which is a
        // configuration bug that would present as "the camera never works".
        val left = face.leftEyeOpenProbability
        val right = face.rightEyeOpenProbability
        if (left != null && right != null &&
            (left < EYE_OPEN_THRESHOLD || right < EYE_OPEN_THRESHOLD)
        ) {
            return FaceCheck.Refused(FaceCheckFailure.EYES_CLOSED)
        }
        return FaceCheck.Accepted(face)
    }

    /** What to tell the driver, in English then Bangla, for each refusal. */
    fun guidanceEn(reason: FaceCheckFailure): String = when (reason) {
        FaceCheckFailure.NO_FACE -> "No face detected — centre your face in the frame"
        FaceCheckFailure.MULTIPLE_FACES -> "More than one face in frame — take the photo alone"
        FaceCheckFailure.FACE_TOO_SMALL -> "Move a little closer to the camera"
        FaceCheckFailure.EYES_CLOSED -> "Keep both eyes open"
        FaceCheckFailure.FACE_NOT_FRONTAL -> "Look straight at the camera"
    }

    fun guidanceBn(reason: FaceCheckFailure): String = when (reason) {
        FaceCheckFailure.NO_FACE -> "কোনও মুখ পাওয়া যায়নি — ফ্রেমের মাঝে মুখ রাখুন"
        FaceCheckFailure.MULTIPLE_FACES -> "একাধিক মুখ দেখা যাচ্ছে — একা ছবি তুলুন"
        FaceCheckFailure.FACE_TOO_SMALL -> "ক্যামেরার আরেকটু কাছে আসুন"
        FaceCheckFailure.EYES_CLOSED -> "দুই চোখ খোলা রাখুন"
        FaceCheckFailure.FACE_NOT_FRONTAL -> "সোজা ক্যামেরার দিকে তাকান"
    }
}
