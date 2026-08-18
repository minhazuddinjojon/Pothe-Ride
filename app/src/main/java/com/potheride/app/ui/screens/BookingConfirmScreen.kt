package com.potheride.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.potheride.app.core.format.AppLanguage
import com.potheride.app.core.format.Formatters
import com.potheride.app.core.pricing.PaymentMethod
import com.potheride.app.ui.PotheRideViewModel
import com.potheride.app.ui.components.ChoiceRow
import com.potheride.app.ui.components.EmptyState
import com.potheride.app.ui.components.InfoRow
import com.potheride.app.ui.components.JourneyRow
import com.potheride.app.ui.components.PotheCard
import com.potheride.app.ui.components.PotheTopBar
import com.potheride.app.ui.components.PrimaryButton
import com.potheride.app.ui.components.SectionHeader
import com.potheride.app.ui.theme.LocalAppLanguage
import com.potheride.app.ui.theme.LocalStrings
import com.potheride.app.ui.theme.SignalAmberSoft

/** Wireframe 6, second step: pick how to pay, then send the request. */
@Composable
fun BookingConfirmScreen(
    vm: PotheRideViewModel,
    pickupAddress: String,
    dropAddress: String,
    seats: Int,
    onBack: () -> Unit,
    onSent: () -> Unit
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val strings = LocalStrings.current
    val language = LocalAppLanguage.current
    val match = state.selectedMatch
    var method by remember { mutableStateOf(PaymentMethod.CASH) }
    var sending by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        PotheTopBar(strings.confirmRequest, onBack = onBack)

        if (match == null) {
            EmptyState(strings.emptyHere, strings.noMatchesBody)
            return@Column
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            PotheCard {
                JourneyRow(pickupAddress, dropAddress)
                Spacer(Modifier.height(14.dp))
                InfoRow(
                    "${Formatters.localizeDigits(seats.toString(), language)} × ${strings.perSeat}",
                    Formatters.money(match.perSeatFare, language)
                )
                InfoRow(
                    strings.total,
                    Formatters.money(match.fare.totalFare, language),
                    emphasise = true
                )
            }

            Spacer(Modifier.height(22.dp))
            SectionHeader(strings.payWith)
            ChoiceRow(
                options = PaymentMethod.enabledForMvp,
                selected = method,
                onSelect = { method = it },
                label = { if (language == AppLanguage.BANGLA) it.displayBn else it.displayEn }
            )

            if (method.requiresGateway) {
                Spacer(Modifier.height(12.dp))
                Text(
                    strings.gatewayPendingNotice,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SignalAmberSoft)
                        .padding(14.dp)
                )
            }

            Spacer(Modifier.height(24.dp))
            PrimaryButton(
                text = strings.confirmRequest,
                loading = sending,
                onClick = {
                    sending = true
                    vm.requestSeat(pickupAddress, dropAddress, seats, method) {
                        sending = false
                        onSent()
                    }
                }
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}
