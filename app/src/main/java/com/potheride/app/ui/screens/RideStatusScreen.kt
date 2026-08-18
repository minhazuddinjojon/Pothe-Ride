package com.potheride.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.potheride.app.core.format.AppLanguage
import com.potheride.app.core.format.Formatters
import com.potheride.app.core.pricing.PaymentMethod
import com.potheride.app.core.ride.RideState
import com.potheride.app.ui.PotheRideViewModel
import com.potheride.app.ui.components.ChoiceRow
import com.potheride.app.ui.components.DriverRow
import com.potheride.app.ui.components.EmptyState
import com.potheride.app.ui.components.IconTextButton
import com.potheride.app.ui.components.InfoRow
import com.potheride.app.ui.components.JourneyRow
import com.potheride.app.ui.components.PotheCard
import com.potheride.app.ui.components.PotheTopBar
import com.potheride.app.ui.components.PrimaryButton
import com.potheride.app.ui.components.RideProgressTracker
import com.potheride.app.ui.components.RouteMapView
import com.potheride.app.ui.components.SectionHeader
import com.potheride.app.ui.theme.AlertRed
import com.potheride.app.ui.theme.LocalAppLanguage
import com.potheride.app.ui.theme.LocalStrings

/**
 * Wireframe 7. The passenger's view of a live ride, with the safety controls always
 * reachable rather than buried behind a menu.
 */
@Composable
fun RideStatusScreen(
    vm: PotheRideViewModel,
    onBack: () -> Unit,
    onChat: (String) -> Unit,
    onRate: (String, String) -> Unit,
    onFindRide: () -> Unit
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val strings = LocalStrings.current
    val language = LocalAppLanguage.current
    val detail = state.activeBooking

    var showSosDialog by remember { mutableStateOf(false) }
    var payMethod by remember { mutableStateOf(PaymentMethod.CASH) }

    Column(Modifier.fillMaxSize()) {
        PotheTopBar(strings.rideStatusTitle, onBack = onBack)

        if (detail == null) {
            EmptyState(
                strings.noActiveRide,
                strings.noActiveRideBody,
                action = { PrimaryButton(strings.searchRides, onFindRide) }
            )
            return@Column
        }

        val booking = detail.booking
        val chatUnlocked = booking.status != RideState.REQUESTED && !booking.status.isTerminal

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            RouteMapView(
                route = detail.route,
                pickup = booking.pickupPoint,
                drop = booking.dropPoint,
                driverPosition = detail.trip?.livePoint,
                height = 220.dp
            )

            Spacer(Modifier.height(18.dp))
            detail.driver?.let { driver ->
                PotheCard { DriverRow(driver, strings, language) }
                Spacer(Modifier.height(16.dp))
            }

            PotheCard {
                JourneyRow(booking.pickupAddress, booking.dropAddress)
                Spacer(Modifier.height(14.dp))
                InfoRow(
                    strings.total,
                    Formatters.money(detail.total, language),
                    emphasise = true
                )
            }

            Spacer(Modifier.height(20.dp))
            SectionHeader(strings.rideStatusTitle)
            RideProgressTracker(booking.status, strings)

            Spacer(Modifier.height(20.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconTextButton(
                    icon = Icons.Default.Chat,
                    text = strings.chat,
                    onClick = { if (chatUnlocked) onChat(booking.id) },
                    tint = if (chatUnlocked) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconTextButton(
                    icon = Icons.Default.Call,
                    text = strings.call,
                    onClick = { },
                    tint = if (chatUnlocked) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconTextButton(
                    icon = Icons.Default.Share,
                    text = strings.shareTrip,
                    onClick = { vm.shareTrip(booking.id) { } }
                )
                IconTextButton(
                    icon = Icons.Default.Warning,
                    text = strings.sos,
                    onClick = { showSosDialog = true },
                    tint = AlertRed
                )
            }

            if (!chatUnlocked && booking.status == RideState.REQUESTED) {
                Text(
                    strings.chatLockedNotice,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (chatUnlocked) {
                Text(
                    strings.maskedCallNotice,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(22.dp))

            when (booking.status) {
                RideState.COMPLETED -> {
                    SectionHeader(strings.payWith)
                    ChoiceRow(
                        options = PaymentMethod.enabledForMvp,
                        selected = payMethod,
                        onSelect = { payMethod = it },
                        label = { if (language == AppLanguage.BANGLA) it.displayBn else it.displayEn }
                    )
                    Spacer(Modifier.height(14.dp))
                    PrimaryButton(
                        text = "${strings.payNow} · ${Formatters.money(detail.total, language)}",
                        onClick = { vm.pay(booking.id, payMethod) }
                    )
                }

                RideState.PAID -> PrimaryButton(
                    text = strings.rateTrip,
                    onClick = {
                        detail.driver?.let { onRate(booking.id, it.userId) }
                    }
                )

                RideState.REQUESTED, RideState.ACCEPTED, RideState.DRIVER_ARRIVING ->
                    OutlinedButton(
                        onClick = { vm.cancelRide(booking.id) },
                        modifier = Modifier.fillMaxWidth().height(54.dp)
                    ) { Text(strings.cancelRide, color = AlertRed) }

                else -> Unit
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showSosDialog) {
        AlertDialog(
            onDismissRequest = { showSosDialog = false },
            title = { Text(strings.sosConfirmTitle) },
            text = { Text(strings.sosConfirmBody) },
            confirmButton = {
                TextButton(onClick = {
                    vm.raiseSos(detail?.booking?.id)
                    showSosDialog = false
                }) { Text(strings.sos, color = AlertRed) }
            },
            dismissButton = {
                TextButton(onClick = { showSosDialog = false }) { Text(strings.cancel) }
            }
        )
    }
}
