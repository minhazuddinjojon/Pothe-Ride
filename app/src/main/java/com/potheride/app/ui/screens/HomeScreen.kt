package com.potheride.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.potheride.app.core.format.AppLanguage
import com.potheride.app.core.format.Formatters
import com.potheride.app.data.local.entities.SavedPlaceEntity
import com.potheride.app.ui.AppMode
import com.potheride.app.ui.PotheRideViewModel
import com.potheride.app.ui.components.HeroCard
import com.potheride.app.ui.components.JourneyRow
import com.potheride.app.ui.components.ModeToggle
import com.potheride.app.ui.components.PotheCard
import com.potheride.app.ui.components.PrimaryButton
import com.potheride.app.ui.components.SecondaryButton
import com.potheride.app.ui.components.SectionHeader
import com.potheride.app.ui.components.rideStateLabel
import com.potheride.app.ui.theme.AlertRed
import com.potheride.app.ui.theme.LocalAppLanguage
import com.potheride.app.ui.theme.LocalStrings
import com.potheride.app.ui.theme.RouteGreen
import com.potheride.app.ui.theme.RouteGreenSoft
import com.potheride.app.ui.theme.Snow

/**
 * The passenger/driver switch plus whatever is most actionable right now, which is
 * an in-flight ride if there is one. Matches wireframe 2.
 */
@Composable
fun HomeScreen(
    vm: PotheRideViewModel,
    onSearch: () -> Unit,
    onPublish: () -> Unit,
    onDashboard: () -> Unit,
    onActiveRide: () -> Unit,
    onNotifications: () -> Unit,
    onPlaceSelected: (SavedPlaceEntity) -> Unit
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val strings = LocalStrings.current
    val language = LocalAppLanguage.current
    val isDriver = state.mode == AppMode.DRIVER

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        // Board 01B puts the mode switch at the very top, above the greeting — it is
        // the control that changes what every other element on the screen means, so it
        // reads first.
        ModeToggle(
            leftLabel = strings.passengerMode,
            rightLabel = strings.driverMode,
            leftSelected = !isDriver,
            onSelectLeft = { vm.setMode(AppMode.PASSENGER) },
            onSelectRight = { vm.setMode(AppMode.DRIVER) }
        )

        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${strings.greeting}, ${state.currentUser?.name ?: ""}",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(onClick = { vm.toggleLanguage() }) {
                Text(if (language == AppLanguage.ENGLISH) "বাংলা" else "EN")
            }
            Box {
                IconButton(onClick = onNotifications) {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = strings.notifications,
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (state.unreadNotifications > 0) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(AlertRed)
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // The hero. Board 01B gives the passenger a single black card that starts the
        // whole flow; the driver's equivalent is publishing a route.
        HeroCard(
            title = if (isDriver) strings.publishRoute else strings.searchRide,
            subtitle = if (isDriver) strings.publishRouteHint else strings.searchRideHint,
            onClick = if (isDriver) onPublish else onSearch
        )

        Spacer(Modifier.height(20.dp))

        // An in-flight ride outranks everything else on this screen.
        val active = state.activeBooking
        if (!isDriver && active != null && !active.booking.status.isTerminal) {
            PotheCard(onClick = onActiveRide) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(RouteGreenSoft)
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            rideStateLabel(active.booking.status, strings),
                            style = MaterialTheme.typography.labelSmall,
                            color = RouteGreen
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        Formatters.money(active.total, language),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.height(14.dp))
                JourneyRow(active.booking.pickupAddress, active.booking.dropAddress)
                Spacer(Modifier.height(14.dp))
                PrimaryButton(strings.trackRide, onActiveRide)
            }
            Spacer(Modifier.height(18.dp))
        }

        if (!isDriver && state.savedPlaces.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            SectionHeader(strings.savedPlaces)
            state.savedPlaces.forEach { place ->
                SavedPlaceRow(place) { onPlaceSelected(place) }
            }
        }

        Spacer(Modifier.height(24.dp))
        SecondaryButton(
            text = if (isDriver) strings.earningsDashboard else strings.myDashboard,
            onClick = onDashboard
        )
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SavedPlaceRow(place: SavedPlaceEntity, onClick: () -> Unit) {
    val strings = LocalStrings.current
    val icon = when (place.label.lowercase()) {
        "home", strings.home.lowercase() -> Icons.Default.Home
        "work", strings.work.lowercase() -> Icons.Default.Work
        else -> Icons.Default.Place
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(19.dp)
            )
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                place.label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                place.address,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
