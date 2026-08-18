package com.potheride.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.potheride.app.core.format.Formatters
import com.potheride.app.core.geo.LatLng
import com.potheride.app.ui.PotheRideViewModel
import com.potheride.app.ui.components.ChoiceRow
import com.potheride.app.ui.components.PotheTopBar
import com.potheride.app.ui.components.PrimaryButton
import com.potheride.app.ui.components.SectionHeader
import com.potheride.app.ui.theme.LocalAppLanguage
import com.potheride.app.ui.theme.LocalStrings

/** Wireframe 4. Both endpoints must resolve to coordinates before search is enabled. */
@Composable
fun PassengerSearchScreen(
    vm: PotheRideViewModel,
    initialPickup: Pair<String, LatLng>? = null,
    onBack: (() -> Unit)? = null,
    onResults: (String, String, Int) -> Unit
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val strings = LocalStrings.current
    val language = LocalAppLanguage.current

    var pickupQuery by remember { mutableStateOf(initialPickup?.first ?: "") }
    var pickupPoint by remember { mutableStateOf(initialPickup?.second) }
    var dropQuery by remember { mutableStateOf("") }
    var dropPoint by remember { mutableStateOf<LatLng?>(null) }
    var seats by remember { mutableStateOf(1f) }
    var window by remember { mutableStateOf(120f) }

    Column(Modifier.fillMaxSize()) {
        PotheTopBar(strings.searchTitle, onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            PlaceField(
                label = strings.pickupPoint,
                value = pickupQuery,
                suggestions = if (pickupPoint == null) vm.placeSuggestions(pickupQuery) else emptyList(),
                onQueryChange = { pickupQuery = it; pickupPoint = null },
                onSelect = { name, point -> pickupQuery = name; pickupPoint = point }
            )
            Spacer(Modifier.height(12.dp))
            PlaceField(
                label = strings.dropOffPoint,
                value = dropQuery,
                suggestions = if (dropPoint == null) vm.placeSuggestions(dropQuery) else emptyList(),
                onQueryChange = { dropQuery = it; dropPoint = null },
                onSelect = { name, point -> dropQuery = name; dropPoint = point }
            )

            Spacer(Modifier.height(20.dp))
            SectionHeader(strings.seatsNeeded)
            ChoiceRow(
                options = listOf(1, 2, 3, 4),
                selected = seats.toInt(),
                onSelect = { seats = it.toFloat() },
                label = { Formatters.localizeDigits(it.toString(), language) }
            )

            Spacer(Modifier.height(20.dp))
            Text(
                "${strings.leavingWithin} ${Formatters.duration(window.toInt(), language)}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Slider(
                value = window,
                onValueChange = { window = it },
                valueRange = 30f..360f,
                steps = 10
            )

            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                text = strings.searchMatchingRides,
                enabled = pickupPoint != null && dropPoint != null,
                loading = state.searching,
                onClick = {
                    val p = pickupPoint ?: return@PrimaryButton
                    val d = dropPoint ?: return@PrimaryButton
                    vm.searchMatches(p, d, seats.toInt(), window.toInt())
                    onResults(pickupQuery, dropQuery, seats.toInt())
                }
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}
