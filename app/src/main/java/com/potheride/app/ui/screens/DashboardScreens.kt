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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.potheride.app.core.format.Formatters
import com.potheride.app.core.pricing.Taka
import com.potheride.app.data.model.BookingDetail
import com.potheride.app.ui.PotheRideViewModel
import com.potheride.app.ui.components.EmptyState
import com.potheride.app.ui.components.InfoRow
import com.potheride.app.ui.components.JourneyRow
import com.potheride.app.ui.components.Pill
import com.potheride.app.ui.components.PotheCard
import com.potheride.app.ui.components.PotheTopBar
import com.potheride.app.ui.components.SectionHeader
import com.potheride.app.ui.components.StatTile
import com.potheride.app.ui.components.rideStateLabel
import com.potheride.app.ui.theme.LocalAppLanguage
import com.potheride.app.ui.theme.LocalStrings
import com.potheride.app.ui.theme.RouteGreen
import com.potheride.app.ui.theme.RouteGreenSoft
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Driver earnings. Every figure comes from settled payment rows, so a driver looking
 * at this can reconcile it against the rides they actually did — a dashboard of
 * plausible-looking constants is worse than no dashboard.
 */
@Composable
fun DriverEarningsScreen(vm: PotheRideViewModel, onBack: () -> Unit) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val strings = LocalStrings.current
    val language = LocalAppLanguage.current

    LaunchedEffect(Unit) { vm.refreshEarnings() }

    Column(Modifier.fillMaxSize()) {
        PotheTopBar(strings.earningsDashboard, onBack = onBack)
        val earnings = state.earnings

        if (earnings == null) {
            EmptyState(strings.emptyHere, strings.shareRouteBody)
            return@Column
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    strings.today,
                    Formatters.money(earnings.today, language),
                    Modifier.weight(1f),
                    accent = RouteGreen
                )
                StatTile(
                    strings.thisWeek,
                    Formatters.money(earnings.thisWeek, language),
                    Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    strings.thisMonth,
                    Formatters.money(earnings.thisMonth, language),
                    Modifier.weight(1f)
                )
                StatTile(
                    strings.pendingBalance,
                    Formatters.money(earnings.pending, language),
                    Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(20.dp))
            PotheCard {
                InfoRow(
                    strings.completedTrips,
                    Formatters.localizeDigits(earnings.completedTrips.toString(), language)
                )
                InfoRow(
                    strings.yourRating,
                    earnings.rating?.let {
                        "★ ${Formatters.rating(it, language)} (${Formatters.localizeDigits(earnings.ratingCount.toString(), language)})"
                    } ?: strings.newDriver
                )
            }

            Spacer(Modifier.height(20.dp))
            Text(
                strings.withdrawNotice,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

/**
 * Passenger activity: the current ride, then everything that came before it with a
 * receipt attached.
 */
@Composable
fun ActivityScreen(
    vm: PotheRideViewModel,
    onBack: (() -> Unit)? = null,
    onOpenRide: () -> Unit,
    onFindRide: () -> Unit
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val strings = LocalStrings.current
    val language = LocalAppLanguage.current
    val dateFormat = androidx.compose.runtime.remember {
        SimpleDateFormat("d MMM, HH:mm", Locale.US)
    }

    val active = state.bookingHistory.firstOrNull { !it.booking.status.isTerminal }
    val past = state.bookingHistory.filter { it.booking.status.isTerminal }

    Column(Modifier.fillMaxSize()) {
        PotheTopBar(strings.navActivity, onBack = onBack)

        if (state.bookingHistory.isEmpty()) {
            EmptyState(
                strings.noRideHistory,
                strings.noActiveRideBody,
                action = {
                    com.potheride.app.ui.components.PrimaryButton(strings.searchRides, onFindRide)
                }
            )
            return@Column
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            if (active != null) {
                SectionHeader(strings.activeRide)
                RideHistoryCard(active, language, dateFormat, onClick = onOpenRide)
                Spacer(Modifier.height(20.dp))
            }

            if (past.isNotEmpty()) {
                SectionHeader(strings.rideHistory)
                past.forEach { detail ->
                    RideHistoryCard(detail, language, dateFormat, onClick = null)
                    Spacer(Modifier.height(12.dp))
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun RideHistoryCard(
    detail: BookingDetail,
    language: com.potheride.app.core.format.AppLanguage,
    dateFormat: SimpleDateFormat,
    onClick: (() -> Unit)?
) {
    val strings = LocalStrings.current
    PotheCard(onClick = onClick) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Pill(
                rideStateLabel(detail.booking.status, strings),
                if (detail.booking.status.isTerminal) MaterialTheme.colorScheme.surfaceVariant
                else RouteGreenSoft,
                if (detail.booking.status.isTerminal) MaterialTheme.colorScheme.onSurfaceVariant
                else RouteGreen
            )
            Spacer(Modifier.weight(1f))
            Text(
                dateFormat.format(Date(detail.booking.requestedAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(14.dp))
        JourneyRow(detail.booking.pickupAddress, detail.booking.dropAddress)
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(6.dp))
        InfoRow(
            "${strings.receipt} · ${Formatters.localizeDigits(detail.booking.seatsRequested.toString(), language)} " +
                (if (detail.booking.seatsRequested == 1) strings.seat else strings.seats),
            Formatters.money(Taka.ofPoisha(detail.booking.totalPoisha), language),
            emphasise = true
        )
    }
}
