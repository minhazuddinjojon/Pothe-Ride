package com.potheride.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.potheride.app.core.format.Formatters
import com.potheride.app.ui.PotheRideViewModel
import com.potheride.app.ui.components.DriverRow
import com.potheride.app.ui.components.EmptyState
import com.potheride.app.ui.components.LoadingBlock
import com.potheride.app.ui.components.OverlapBadge
import com.potheride.app.ui.components.PotheCard
import com.potheride.app.ui.components.PotheTopBar
import com.potheride.app.ui.components.PrimaryButton
import com.potheride.app.ui.theme.LocalAppLanguage
import com.potheride.app.ui.theme.LocalStrings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Wireframe 5: each match shows the three numbers that drive the decision. */
@Composable
fun PassengerResultsScreen(
    vm: PotheRideViewModel,
    onBack: () -> Unit,
    onSelect: () -> Unit
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val strings = LocalStrings.current
    val language = LocalAppLanguage.current
    val timeFormat = rememberTimeFormat()

    Column(Modifier.fillMaxSize()) {
        PotheTopBar(strings.matchesTitle, onBack = onBack)

        when {
            state.searching -> LoadingBlock(strings.searching)
            state.searchResults.isEmpty() ->
                EmptyState(strings.noMatchesTitle, strings.noMatchesBody)
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp, end = 20.dp, bottom = 32.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.searchResults) { match ->
                    PotheCard {
                        DriverRow(match.driver, strings, language)
                        Spacer(Modifier.height(14.dp))

                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            OverlapBadge(match.overlapPercent, language, strings.routeOverlap)
                            Spacer(Modifier.weight(1f))
                            Text(
                                Formatters.money(match.perSeatFare, language),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                " / ${strings.perSeat}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(Modifier.height(10.dp))
                        Text(
                            "${strings.pickupEta} ${timeFormat.format(Date(match.pickupEtaMillis))}" +
                                " · ${strings.extraDetour} ${Formatters.distance(match.match.detourKm, language)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(Modifier.height(14.dp))
                        PrimaryButton(
                            text = strings.viewRoute,
                            onClick = { vm.selectMatch(match); onSelect() }
                        )
                    }
                }
            }
        }
    }
}

/** 24-hour clock keeps times unambiguous across both languages. */
@Composable
private fun rememberTimeFormat(): SimpleDateFormat =
    androidx.compose.runtime.remember { SimpleDateFormat("HH:mm", Locale.US) }
