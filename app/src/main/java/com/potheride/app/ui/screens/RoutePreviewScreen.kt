package com.potheride.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.potheride.app.core.format.Formatters
import com.potheride.app.core.pricing.Taka
import com.potheride.app.ui.PotheRideViewModel
import com.potheride.app.ui.components.DriverRow
import com.potheride.app.ui.components.EmptyState
import com.potheride.app.ui.components.InfoRow
import com.potheride.app.ui.components.JourneyRow
import com.potheride.app.ui.components.PotheCard
import com.potheride.app.ui.components.PotheTopBar
import com.potheride.app.ui.components.PrimaryButton
import com.potheride.app.ui.components.RouteMapView
import com.potheride.app.ui.components.SectionHeader
import com.potheride.app.ui.theme.LocalAppLanguage
import com.potheride.app.ui.theme.LocalStrings
import com.potheride.app.ui.theme.RouteGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Wireframe 6: the full picture before committing — who the driver is, the exact
 * stretch shared with them, and every line item behind the fare.
 */
@Composable
fun RoutePreviewScreen(
    vm: PotheRideViewModel,
    pickupAddress: String,
    dropAddress: String,
    onBack: () -> Unit,
    onRequest: () -> Unit
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val strings = LocalStrings.current
    val language = LocalAppLanguage.current
    val match = state.selectedMatch
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.US) }

    Column(Modifier.fillMaxSize()) {
        PotheTopBar(strings.routePreviewTitle, onBack = onBack)

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
            RouteMapView(
                route = match.route,
                pickup = match.pickup,
                drop = match.drop,
                sharedPath = match.sharedPath,
                driverPosition = match.trip.livePoint,
                height = 250.dp
            )

            Spacer(Modifier.height(18.dp))
            PotheCard {
                DriverRow(match.driver, strings, language)
            }

            Spacer(Modifier.height(16.dp))
            PotheCard {
                JourneyRow(pickupAddress, dropAddress)
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(8.dp))
                InfoRow(strings.routeOverlap, Formatters.percent(match.match.overlapRatio, language))
                InfoRow(strings.extraDetour, Formatters.distance(match.match.detourKm, language))
                InfoRow(strings.pickupEta, timeFormat.format(Date(match.pickupEtaMillis)))
                InfoRow(strings.dropoffEta, timeFormat.format(Date(match.dropoffEtaMillis)))
            }

            Spacer(Modifier.height(16.dp))
            SectionHeader(strings.fareBreakdown)
            PotheCard {
                val fare = match.fare
                InfoRow(strings.baseFare, Formatters.money(fare.baseFare, language))
                InfoRow(
                    "${strings.distanceFare} (${Formatters.distance(fare.distanceKm, language)})",
                    Formatters.money(fare.distanceFare, language)
                )
                InfoRow(
                    "${strings.timeFare} (${Formatters.duration(fare.durationMinutes, language)})",
                    Formatters.money(fare.timeFare, language)
                )
                if (fare.nightSurcharge > Taka.ZERO) {
                    InfoRow(strings.nightSurcharge, Formatters.money(fare.nightSurcharge, language))
                }
                InfoRow(
                    "${strings.sharedDiscount} (${Formatters.localizeDigits(fare.sharedDiscountPercent.toString(), language)}%)",
                    "− ${Formatters.money(fare.sharedDiscount, language)}",
                    valueColor = RouteGreen
                )
                Spacer(Modifier.height(6.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(6.dp))
                InfoRow(
                    "${strings.fareEstimate} · ${strings.perSeat}",
                    Formatters.money(fare.perSeatFare, language),
                    emphasise = true
                )
            }

            Spacer(Modifier.height(20.dp))
            PrimaryButton(strings.requestSeat, onRequest)
            Spacer(Modifier.height(32.dp))
        }
    }
}
