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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.potheride.app.core.format.Formatters
import com.potheride.app.data.local.entities.SafetyEventKind
import com.potheride.app.ui.PotheRideViewModel
import com.potheride.app.ui.components.Pill
import com.potheride.app.ui.components.PotheCard
import com.potheride.app.ui.components.PotheTopBar
import com.potheride.app.ui.components.SectionHeader
import com.potheride.app.ui.components.StatTile
import com.potheride.app.ui.theme.AlertRed
import com.potheride.app.ui.theme.AlertRedSoft
import com.potheride.app.ui.theme.LocalAppLanguage
import com.potheride.app.ui.theme.LocalStrings
import com.potheride.app.ui.theme.RouteGreen
import com.potheride.app.ui.theme.RouteGreenSoft
import com.potheride.app.ui.theme.SignalAmberSoft

/**
 * Operator console: platform totals, driver verification, and the safety queue.
 *
 * Reachable from any profile in this build because there is no server to hold roles.
 * A real deployment gates this behind a role claim — see docs/BACKEND.md.
 */
@Composable
fun AdminScreen(vm: PotheRideViewModel, onBack: () -> Unit) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val strings = LocalStrings.current
    val language = LocalAppLanguage.current

    LaunchedEffect(Unit) { vm.openAdmin() }

    Column(Modifier.fillMaxSize()) {
        PotheTopBar(strings.adminTitle, onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            val stats = state.platformStats
            if (stats != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(
                        strings.adminUsers,
                        Formatters.localizeDigits(stats.totalUsers.toString(), language),
                        Modifier.weight(1f)
                    )
                    StatTile(
                        strings.adminDrivers,
                        "${Formatters.localizeDigits(stats.verifiedDrivers.toString(), language)}/" +
                            Formatters.localizeDigits(stats.totalDrivers.toString(), language),
                        Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(
                        strings.adminTrips,
                        Formatters.localizeDigits(stats.totalTrips.toString(), language),
                        Modifier.weight(1f)
                    )
                    StatTile(
                        strings.adminBookings,
                        Formatters.localizeDigits(stats.totalBookings.toString(), language),
                        Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(12.dp))
                StatTile(
                    strings.adminRevenue,
                    Formatters.money(stats.platformRevenue, language),
                    accent = RouteGreen
                )
            }

            Spacer(Modifier.height(26.dp))
            SectionHeader(strings.adminDrivers)
            state.allDrivers.forEach { driver ->
                val owner = state.allUsers.firstOrNull { it.id == driver.userId }
                PotheCard(Modifier.padding(bottom = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                owner?.name ?: driver.licenseNumber,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${driver.licenseNumber} · " +
                                    "${Formatters.localizeDigits(driver.totalTrips.toString(), language)} ${strings.tripsCompleted}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Pill(
                            if (driver.verified) strings.verified else strings.unverified,
                            if (driver.verified) RouteGreenSoft else SignalAmberSoft,
                            if (driver.verified) RouteGreen else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { vm.setDriverVerified(driver.id, !driver.verified) },
                        modifier = Modifier.fillMaxWidth().height(46.dp)
                    ) {
                        Text(if (driver.verified) strings.adminUnverify else strings.adminVerifyDriver)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionHeader(strings.adminSafety)
            val openEvents = state.safetyEvents.filter { !it.resolved }
            if (openEvents.isEmpty()) {
                Text(
                    strings.adminNoIssues,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            openEvents.forEach { event ->
                PotheCard(Modifier.padding(bottom = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Pill(
                            event.kind.name,
                            if (event.kind == SafetyEventKind.SOS) AlertRedSoft
                            else MaterialTheme.colorScheme.surfaceVariant,
                            if (event.kind == SafetyEventKind.SOS) AlertRed
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            state.allUsers.firstOrNull { it.id == event.raisedByUserId }?.name ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    event.details?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { vm.resolveSafetyEvent(event.id) },
                        modifier = Modifier.fillMaxWidth().height(46.dp)
                    ) { Text(strings.adminResolve) }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
