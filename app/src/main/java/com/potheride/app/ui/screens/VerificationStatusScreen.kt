package com.potheride.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.potheride.app.core.verification.DriverDocumentKind
import com.potheride.app.core.verification.DriverDocumentStatus
import com.potheride.app.core.verification.VerificationRules
import com.potheride.app.ui.PotheRideViewModel
import com.potheride.app.ui.components.BadgeTone
import com.potheride.app.ui.components.DepthCard
import com.potheride.app.ui.components.MetaText
import com.potheride.app.ui.components.PotheTopBar
import com.potheride.app.ui.components.SecondaryButton
import com.potheride.app.ui.components.StatusBadge
import com.potheride.app.ui.theme.Depth

/**
 * Board 02C — three review cards (NID, licence, vehicle photo) and a re-upload action.
 *
 * Deliberately reads only [PotheRideViewModel.uiState]'s `driverDocuments`, never
 * `driverProfile.verified` — see the note on `VerificationRules.canPublishRoute` for why
 * that flag must never be treated as the source of truth.
 */
@Composable
fun VerificationStatusScreen(
    vm: PotheRideViewModel,
    onBack: () -> Unit,
    onReupload: (DriverDocumentKind) -> Unit
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val documents = state.driverDocuments.map { it.toDomain() }
    val current = VerificationRules.current(documents)

    Column(Modifier.fillMaxSize()) {
        PotheTopBar(title = "Verification status", onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            listOf(
                DriverDocumentKind.NID_FRONT to "NID document",
                DriverDocumentKind.LICENCE to "Driving licence",
                DriverDocumentKind.REGISTRATION to "Vehicle registration"
            ).forEach { (kind, caption) ->
                val document = current[kind]
                DepthCard(level = Depth.RESTING, modifier = Modifier.padding(bottom = 12.dp)) {
                    StatusBadge(
                        text = badgeLabel(document?.status),
                        tone = badgeTone(document?.status)
                    )
                    Spacer(Modifier.height(6.dp))
                    MetaText(caption)
                    if (document?.status == DriverDocumentStatus.REJECTED && document.rejectionReason != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            document.rejectionReason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            MetaText("Usually reviewed within 24 hours.")
            Spacer(Modifier.height(20.dp))

            SecondaryButton(
                text = "Re-upload a document",
                onClick = {
                    val toReupload = VerificationRules.rejectedDocuments(documents)
                        .firstOrNull()?.kind ?: DriverDocumentKind.NID_FRONT
                    onReupload(toReupload)
                }
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun badgeLabel(status: DriverDocumentStatus?): String = when (status) {
    null -> "Not submitted"
    DriverDocumentStatus.PENDING -> "Pending review"
    DriverDocumentStatus.APPROVED -> "Approved"
    DriverDocumentStatus.REJECTED -> "Rejected"
}

private fun badgeTone(status: DriverDocumentStatus?): BadgeTone = when (status) {
    null -> BadgeTone.NEUTRAL
    DriverDocumentStatus.PENDING -> BadgeTone.PENDING
    DriverDocumentStatus.APPROVED -> BadgeTone.POSITIVE
    DriverDocumentStatus.REJECTED -> BadgeTone.NEGATIVE
}
