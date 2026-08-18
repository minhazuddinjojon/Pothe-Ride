package com.potheride.app.core.verification

/**
 * Driver verification — the domain model and the rules that gate publishing.
 *
 * This file has **no Android and no Room dependency on purpose**. The Room record lives
 * in `data/local/entities/DriverDocuments.kt` and refers *up* to these enums; the rules
 * never refer *down* to the entity. That is what lets the whole approval workflow be
 * covered by plain JVM unit tests, and it is the same split `core/matching` and
 * `core/ride` already use.
 *
 * The gate itself matters more than it looks: an unverified driver publishing a route is
 * not a cosmetic bug, it is a stranger collecting passengers in Dhaka on the strength of
 * a form they filled in themselves. The roadmap is explicit that the check must live
 * below the UI, because the UI is the one layer an attacker does not have to go through.
 */

/** The document kinds a driver can be asked for. Stored by `name`, so **do not rename**. */
enum class DriverDocumentKind {
    NID_FRONT,
    NID_BACK,
    LICENCE,
    REGISTRATION,
    PROFILE_PHOTO;

    companion object {
        fun fromNameOrNull(value: String?): DriverDocumentKind? =
            values().firstOrNull { it.name == value }
    }
}

/** Where a single uploaded document sits in review. Stored by `name` — do not rename. */
enum class DriverDocumentStatus {
    PENDING,
    APPROVED,
    REJECTED;

    companion object {
        fun fromNameOrNull(value: String?): DriverDocumentStatus? =
            values().firstOrNull { it.name == value }
    }
}

/**
 * One uploaded document, reduced to the fields the rules actually need.
 *
 * [uploadedAt] is here only to break ties: a driver who re-uploads a rejected NID leaves
 * two rows of the same kind behind, and the rules must read the *newest* one or a
 * long-since-fixed rejection keeps the driver locked out forever.
 */
data class DriverDocument(
    val kind: DriverDocumentKind,
    val status: DriverDocumentStatus,
    val uploadedAt: Long = 0L,
    val rejectionReason: String? = null
)

/** The single roll-up a driver sees on board 02C, and the app gates on. */
enum class VerificationState {
    /** Nothing uploaded yet. */
    NOT_STARTED,

    /** Some required documents have been uploaded, others have not. */
    INCOMPLETE,

    /** Everything required is uploaded and at least one item is still with a reviewer. */
    PENDING_REVIEW,

    /** At least one required document came back rejected — the driver must act. */
    REJECTED,

    /** Every required document is approved. Only this state may publish a route. */
    APPROVED
}

/** Why publishing is refused, in a form the UI can turn into a sentence. */
sealed interface PublishBlock {
    /** Required kinds that have never been uploaded at all. */
    data class MissingDocuments(val kinds: List<DriverDocumentKind>) : PublishBlock

    /** Uploaded, complete, but a human has not signed it off yet. */
    data class AwaitingReview(val kinds: List<DriverDocumentKind>) : PublishBlock

    /** Rejected documents, with the reviewer's reason where one was given. */
    data class Rejected(val documents: List<DriverDocument>) : PublishBlock
}

object VerificationRules {

    /**
     * What a driver must have approved before they can carry anyone.
     *
     * `PROFILE_PHOTO` is deliberately **not** in this set. Board 02C lists exactly three
     * review cards — NID, driving licence, vehicle photo — and the profile photo is a
     * courtesy to passengers, not a legal document. Gating on it would strand drivers who
     * are otherwise fully documented, which is the failure mode that makes people bypass
     * the app entirely.
     */
    val REQUIRED_DOCUMENTS: Set<DriverDocumentKind> = setOf(
        DriverDocumentKind.NID_FRONT,
        DriverDocumentKind.NID_BACK,
        DriverDocumentKind.LICENCE,
        DriverDocumentKind.REGISTRATION
    )

    /** Optional extras. Uploaded and reviewed like the rest; simply never a blocker. */
    val OPTIONAL_DOCUMENTS: Set<DriverDocumentKind> =
        DriverDocumentKind.values().toSet() - REQUIRED_DOCUMENTS

    /**
     * Collapses a driver's upload history to one current document per kind.
     *
     * Newest wins, by [DriverDocument.uploadedAt]. Where two rows share a timestamp — the
     * realistic case being a batch insert that stamped them all in the same millisecond —
     * the *later element in the list* wins, so a caller that appends re-uploads gets the
     * intuitive answer rather than a coin flip.
     */
    fun current(documents: List<DriverDocument>): Map<DriverDocumentKind, DriverDocument> {
        val latest = LinkedHashMap<DriverDocumentKind, DriverDocument>()
        for (document in documents) {
            val held = latest[document.kind]
            if (held == null || document.uploadedAt >= held.uploadedAt) {
                latest[document.kind] = document
            }
        }
        return latest
    }

    /** Required kinds with no upload at all. Rejected uploads are *not* counted here. */
    fun missingDocuments(documents: List<DriverDocument>): List<DriverDocumentKind> {
        val held = current(documents).keys
        return REQUIRED_DOCUMENTS.filterNot { it in held }.sortedBy { it.ordinal }
    }

    /** Required kinds whose newest upload is still `PENDING`. */
    fun awaitingReview(documents: List<DriverDocument>): List<DriverDocumentKind> =
        current(documents).filterValues { it.status == DriverDocumentStatus.PENDING }
            .keys
            .filter { it in REQUIRED_DOCUMENTS }
            .sortedBy { it.ordinal }

    /**
     * Required documents whose newest upload was rejected.
     *
     * Only the *newest* counts. A driver whose first NID scan was rejected and whose
     * replacement is pending is awaiting review, not rejected — showing them the old
     * rejection would tell them to fix something they have already fixed.
     */
    fun rejectedDocuments(documents: List<DriverDocument>): List<DriverDocument> =
        current(documents).values
            .filter { it.status == DriverDocumentStatus.REJECTED && it.kind in REQUIRED_DOCUMENTS }
            .sortedBy { it.kind.ordinal }

    /** The roll-up shown on board 02C. */
    fun state(documents: List<DriverDocument>): VerificationState {
        val held = current(documents)
        if (held.keys.none { it in REQUIRED_DOCUMENTS }) return VerificationState.NOT_STARTED
        // Rejection outranks everything else that is still outstanding: it is the only
        // state the driver can actually do something about, so it is the one to surface.
        if (rejectedDocuments(documents).isNotEmpty()) return VerificationState.REJECTED
        if (missingDocuments(documents).isNotEmpty()) return VerificationState.INCOMPLETE
        if (awaitingReview(documents).isNotEmpty()) return VerificationState.PENDING_REVIEW
        return VerificationState.APPROVED
    }

    /**
     * The gate. `true` only when every required document is `APPROVED`.
     *
     * Note what is *not* consulted: `DriverProfileEntity.verified`. That flag becomes a
     * cache of this function's answer, never the source of it — a stale boolean in a row
     * somewhere is exactly how an unverified driver ends up on the road.
     */
    fun canPublishRoute(documents: List<DriverDocument>): Boolean =
        state(documents) == VerificationState.APPROVED

    /**
     * `null` when publishing is allowed; otherwise the single most actionable reason.
     *
     * One reason, not a list, because a driver blocked for three overlapping causes needs
     * to be told the first thing to do, not given an audit report.
     */
    fun publishBlock(documents: List<DriverDocument>): PublishBlock? {
        val rejected = rejectedDocuments(documents)
        if (rejected.isNotEmpty()) return PublishBlock.Rejected(rejected)
        val missing = missingDocuments(documents)
        if (missing.isNotEmpty()) return PublishBlock.MissingDocuments(missing)
        val pending = awaitingReview(documents)
        if (pending.isNotEmpty()) return PublishBlock.AwaitingReview(pending)
        return null
    }

    /**
     * 0f..1f progress across the *required* set, counting only approvals.
     *
     * Drives the progress read-out on board 02C. Pending uploads score zero deliberately:
     * a bar that jumps to full the moment files are attached tells the driver they are
     * finished when in fact nothing has been checked.
     */
    fun approvalProgress(documents: List<DriverDocument>): Float {
        if (REQUIRED_DOCUMENTS.isEmpty()) return 1f
        val approved = current(documents).count {
            it.key in REQUIRED_DOCUMENTS && it.value.status == DriverDocumentStatus.APPROVED
        }
        return approved.toFloat() / REQUIRED_DOCUMENTS.size
    }
}
