package com.potheride.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.potheride.app.core.format.Formatters
import com.potheride.app.core.geo.GeoUtils
import com.potheride.app.core.pricing.Taka
import com.potheride.app.core.ride.RideState
import com.potheride.app.core.ride.RideStateMachine
import com.potheride.app.ui.PotheRideViewModel
import com.potheride.app.ui.TrackingSource
import com.potheride.app.ui.components.EmptyState
import com.potheride.app.ui.components.JourneyRow
import com.potheride.app.ui.components.OverlapBadge
import com.potheride.app.ui.components.Pill
import com.potheride.app.ui.components.PotheCard
import com.potheride.app.ui.components.PotheTopBar
import com.potheride.app.ui.components.PrimaryButton
import com.potheride.app.ui.components.ProgressBar
import com.potheride.app.ui.components.RouteMapView
import com.potheride.app.ui.components.SectionHeader
import com.potheride.app.ui.components.rideStateLabel
import com.potheride.app.ui.theme.AlertRed
import com.potheride.app.ui.theme.LocalAppLanguage
import com.potheride.app.ui.theme.LocalStrings
import com.potheride.app.ui.theme.RouteGreen
import com.potheride.app.ui.theme.RouteGreenSoft
import com.potheride.app.ui.theme.SignalAmberSoft

/**
 * The driver's cockpit: their live position on the published route, and the queue of
 * passengers asking for a seat.
 *
 * The tracking source is always stated on screen. A simulated drive that looked like
 * real GPS would be a lie told to the person whose safety depends on knowing the
 * difference.
 */
@Composable
fun DriverLiveScreen(
    vm: PotheRideViewModel,
    onBack: () -> Unit,
    onRequestPermission: () -> Unit
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val strings = LocalStrings.current
    val language = LocalAppLanguage.current
    val trip = state.activeTrip

    Column(Modifier.fillMaxSize()) {
        PotheTopBar(strings.liveRouteTitle, onBack = onBack)

        if (trip == null) {
            EmptyState(strings.emptyHere, strings.shareRouteBody)
            return@Column
        }

        val routeLength = GeoUtils.polylineLengthKm(state.activeRoute)
        val progress = if (routeLength <= 0) 0f else (state.travelledKm / routeLength).toFloat()

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            RouteMapView(
                route = state.activeRoute,
                driverPosition = state.driverPosition,
                pickup = trip.startPoint,
                drop = trip.endPoint,
                height = 250.dp,
                label = when (state.trackingSource) {
                    TrackingSource.REAL_GPS -> strings.usingRealGps
                    TrackingSource.SIMULATED -> strings.usingSimulatedGps
                    TrackingSource.NONE -> null
                }
            )

            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    strings.progressAlongRoute,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${Formatters.distance(state.travelledKm, language)} / ${Formatters.distance(routeLength, language)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(6.dp))
            ProgressBar(progress)

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(
                    "${Formatters.localizeDigits(trip.availableSeats.toString(), language)} ${strings.seatsLeft}",
                    RouteGreenSoft, RouteGreen
                )
                Pill(
                    "${strings.departingIn} ${Formatters.duration(
                        ((trip.departureTime - System.currentTimeMillis()) / 60_000L).toInt().coerceAtLeast(0),
                        language
                    )}",
                    SignalAmberSoft, MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(16.dp))
            if (!vm.hasLocationPermission()) {
                Text(
                    strings.locationPermissionNeeded,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        if (vm.hasLocationPermission()) vm.startRealTracking() else onRequestPermission()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        if (vm.hasLocationPermission()) strings.usingRealGps else strings.grantPermission,
                        maxLines = 1
                    )
                }
                OutlinedButton(
                    onClick = {
                        if (state.trackingSource == TrackingSource.SIMULATED) vm.stopTracking()
                        else vm.startSimulatedTracking()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        if (state.trackingSource == TrackingSource.SIMULATED) strings.stopSimulation
                        else strings.simulateDriving,
                        maxLines = 1
                    )
                }
            }

            Spacer(Modifier.height(26.dp))
            SectionHeader(strings.incomingRequests)

            if (state.incomingRequests.isEmpty()) {
                Text(
                    strings.noRequestsYet,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            state.incomingRequests.forEach { booking ->
                PotheCard(Modifier.padding(bottom = 12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        OverlapBadge(
                            percent = (booking.routeOverlapRatio * 100).toInt(),
                            language = language,
                            label = strings.onYourRoute
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            Formatters.money(Taka.ofPoisha(booking.totalPoisha), language),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    JourneyRow(booking.pickupAddress, booking.dropAddress)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "${Formatters.localizeDigits(booking.seatsRequested.toString(), language)} " +
                            (if (booking.seatsRequested == 1) strings.seat else strings.seats) +
                            " · ${strings.extraDetour} ${Formatters.distance(booking.detourKm, language)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(14.dp))

                    when (booking.status) {
                        RideState.REQUESTED -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PrimaryButton(
                                text = strings.accept,
                                onClick = { vm.respondToRequest(booking.id, true) },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedButton(
                                onClick = { vm.respondToRequest(booking.id, false) },
                                modifier = Modifier.weight(1f).height(54.dp)
                            ) { Text(strings.decline, color = AlertRed, maxLines = 1) }
                        }

                        RideState.ACCEPTED, RideState.DRIVER_ARRIVING, RideState.PICKED_UP -> {
                            val next = RideStateMachine.nextHappyPathState(booking.status)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Pill(
                                    rideStateLabel(booking.status, strings),
                                    RouteGreenSoft, RouteGreen
                                )
                                Spacer(Modifier.width(10.dp))
                            }
                            if (next != null && next != RideState.PAID) {
                                Spacer(Modifier.height(10.dp))
                                PrimaryButton(
                                    text = "${strings.advanceRide}: ${rideStateLabel(next, strings)}",
                                    onClick = { vm.driverAdvance(booking.id, booking.status) }
                                )
                            }
                        }

                        else -> Pill(
                            rideStateLabel(booking.status, strings),
                            MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            OutlinedButton(
                onClick = { vm.finishTrip(); onBack() },
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) { Text(strings.done) }
            Spacer(Modifier.height(32.dp))
        }
    }
}
