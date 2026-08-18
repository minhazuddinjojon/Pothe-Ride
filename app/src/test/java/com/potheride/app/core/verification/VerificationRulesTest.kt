package com.potheride.app.core.verification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VerificationRulesTest {

    private fun doc(
        kind: DriverDocumentKind,
        status: DriverDocumentStatus,
        uploadedAt: Long = 1000L,
        reason: String? = null
    ) = DriverDocument(kind, status, uploadedAt, reason)

    private val allRequiredApproved = VerificationRules.REQUIRED_DOCUMENTS.map {
        doc(it, DriverDocumentStatus.APPROVED)
    }

    @Test
    fun `no documents at all is NOT_STARTED`() {
        assertEquals(VerificationState.NOT_STARTED, VerificationRules.state(emptyList()))
        assertTrue(VerificationRules.missingDocuments(emptyList()).containsAll(VerificationRules.REQUIRED_DOCUMENTS))
    }

    @Test
    fun `every required document approved is APPROVED and unlocks publishing`() {
        assertEquals(VerificationState.APPROVED, VerificationRules.state(allRequiredApproved))
        assertTrue(VerificationRules.canPublishRoute(allRequiredApproved))
        assertNull(VerificationRules.publishBlock(allRequiredApproved))
    }

    @Test
    fun `PROFILE_PHOTO is optional and never blocks publishing`() {
        // A driver with every required document approved and no profile photo at all
        // must still be allowed to publish — the photo is a courtesy to passengers,
        // not a legal document (see the wireframe: board 02C lists only three cards).
        assertTrue(VerificationRules.canPublishRoute(allRequiredApproved))
        assertTrue(DriverDocumentKind.PROFILE_PHOTO !in VerificationRules.REQUIRED_DOCUMENTS)
    }

    @Test
    fun `one missing required document is INCOMPLETE and blocks publishing`() {
        val docs = allRequiredApproved.filterNot { it.kind == DriverDocumentKind.LICENCE }
        assertEquals(VerificationState.INCOMPLETE, VerificationRules.state(docs))
        assertTrue(!VerificationRules.canPublishRoute(docs))
        assertEquals(
            listOf(DriverDocumentKind.LICENCE),
            VerificationRules.missingDocuments(docs)
        )
    }

    @Test
    fun `a pending required document is PENDING_REVIEW and blocks publishing`() {
        val docs = allRequiredApproved.map {
            if (it.kind == DriverDocumentKind.NID_FRONT) doc(it.kind, DriverDocumentStatus.PENDING) else it
        }
        assertEquals(VerificationState.PENDING_REVIEW, VerificationRules.state(docs))
        assertTrue(!VerificationRules.canPublishRoute(docs))
    }

    @Test
    fun `a rejected required document is REJECTED and blocks publishing`() {
        val docs = allRequiredApproved.map {
            if (it.kind == DriverDocumentKind.REGISTRATION) {
                doc(it.kind, DriverDocumentStatus.REJECTED, reason = "blurry photo")
            } else it
        }
        assertEquals(VerificationState.REJECTED, VerificationRules.state(docs))
        assertTrue(!VerificationRules.canPublishRoute(docs))
        assertEquals("blurry photo", VerificationRules.rejectedDocuments(docs).single().rejectionReason)
    }

    @Test
    fun `rejection outranks missing and pending when several are outstanding`() {
        val docs = listOf(
            doc(DriverDocumentKind.NID_FRONT, DriverDocumentStatus.REJECTED),
            doc(DriverDocumentKind.NID_BACK, DriverDocumentStatus.PENDING)
            // LICENCE and REGISTRATION missing entirely.
        )
        assertEquals(VerificationState.REJECTED, VerificationRules.state(docs))
        assertTrue(VerificationRules.publishBlock(docs) is PublishBlock.Rejected)
    }

    @Test
    fun `newest upload wins over an earlier rejection for the same kind`() {
        val docs = listOf(
            doc(DriverDocumentKind.NID_FRONT, DriverDocumentStatus.REJECTED, uploadedAt = 100L),
            doc(DriverDocumentKind.NID_FRONT, DriverDocumentStatus.PENDING, uploadedAt = 200L),
            doc(DriverDocumentKind.NID_BACK, DriverDocumentStatus.APPROVED),
            doc(DriverDocumentKind.LICENCE, DriverDocumentStatus.APPROVED),
            doc(DriverDocumentKind.REGISTRATION, DriverDocumentStatus.APPROVED)
        )
        // The old rejection must not resurface once a replacement has been uploaded.
        assertTrue(VerificationRules.rejectedDocuments(docs).isEmpty())
        assertEquals(VerificationState.PENDING_REVIEW, VerificationRules.state(docs))
    }

    @Test
    fun `publishBlock reports one reason, the most actionable one`() {
        val rejectedAndMissing = listOf(
            doc(DriverDocumentKind.NID_FRONT, DriverDocumentStatus.REJECTED)
            // everything else missing
        )
        val block = VerificationRules.publishBlock(rejectedAndMissing)
        assertTrue("expected Rejected to take priority", block is PublishBlock.Rejected)
    }

    @Test
    fun `approval progress counts only approvals across the required set`() {
        assertEquals(0f, VerificationRules.approvalProgress(emptyList()))
        val halfApproved = listOf(
            doc(DriverDocumentKind.NID_FRONT, DriverDocumentStatus.APPROVED),
            doc(DriverDocumentKind.NID_BACK, DriverDocumentStatus.APPROVED),
            doc(DriverDocumentKind.LICENCE, DriverDocumentStatus.PENDING),
            doc(DriverDocumentKind.REGISTRATION, DriverDocumentStatus.PENDING)
        )
        assertEquals(0.5f, VerificationRules.approvalProgress(halfApproved), 0.001f)
        assertEquals(1f, VerificationRules.approvalProgress(allRequiredApproved), 0.001f)
    }

    @Test
    fun `awaitingReview ignores optional documents`() {
        val docs = allRequiredApproved + doc(DriverDocumentKind.PROFILE_PHOTO, DriverDocumentStatus.PENDING)
        assertTrue(VerificationRules.awaitingReview(docs).isEmpty())
        assertEquals(VerificationState.APPROVED, VerificationRules.state(docs))
    }
}
