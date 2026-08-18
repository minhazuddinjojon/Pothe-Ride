package com.potheride.app.ui.screens

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
import androidx.compose.material3.OutlinedTextField
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
import com.potheride.app.core.format.AppLanguage
import com.potheride.app.core.format.Formatters
import com.potheride.app.core.geo.LatLng
import com.potheride.app.core.pricing.VehicleClass
import com.potheride.app.core.validation.ValidationResult
import com.potheride.app.data.local.DhakaPlaces
import com.potheride.app.ui.PotheRideViewModel
import com.potheride.app.ui.components.ChoiceRow
import com.potheride.app.ui.components.PotheTopBar
import com.potheride.app.ui.components.PrimaryButton
import com.potheride.app.ui.components.RouteMapView
import com.potheride.app.ui.components.SectionHeader
import com.potheride.app.ui.components.vehicleLabel
import com.potheride.app.ui.theme.LocalAppLanguage
import com.potheride.app.ui.theme.LocalStrings

/** Wireframe 3: the driver publishes the route they were already taking. */
@Composable
fun DriverCreateTripScreen(
    vm: PotheRideViewModel,
    onBack: () -> Unit,
    onPublished: () -> Unit
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val strings = LocalStrings.current
    val language = LocalAppLanguage.current

    var startQuery by remember { mutableStateOf("Mirpur-10, Dhaka") }
    var startPoint by remember { mutableStateOf<LatLng?>(DhakaPlaces.mirpur10) }
    var endQuery by remember { mutableStateOf("Gazipur Chowrasta") }
    var endPoint by remember { mutableStateOf<LatLng?>(DhakaPlaces.gazipur) }

    var plate by remember { mutableStateOf("") }
    var licence by remember { mutableStateOf("") }
    var vehicleType by remember { mutableStateOf(VehicleClass.CAR) }
    var seats by remember { mutableStateOf(2f) }
    var detour by remember { mutableStateOf(1.5f) }
    var minutes by remember { mutableStateOf(30f) }
    var showErrors by remember { mutableStateOf(false) }

    val plateCheck = vm.validatePlate(plate)
    val previewRoute = remember(startPoint, endPoint) {
        val s = startPoint; val e = endPoint
        if (s == null || e == null) emptyList()
        else listOf(s) + (1..3).map { i ->
            com.potheride.app.core.geo.GeoUtils.interpolate(s, e, i / 4.0)
        } + listOf(e)
    }

    fun err(result: ValidationResult): String? =
        (result as? ValidationResult.Invalid)?.let {
            if (language == AppLanguage.BANGLA) it.messageBn else it.messageEn
        }

    Column(Modifier.fillMaxSize()) {
        PotheTopBar(strings.createTripTitle, onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            RouteMapView(
                route = previewRoute,
                pickup = startPoint,
                drop = endPoint,
                height = 200.dp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                strings.routePreviewHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(20.dp))
            PlaceField(
                label = strings.startLocation,
                value = startQuery,
                suggestions = if (startPoint == null) vm.placeSuggestions(startQuery) else emptyList(),
                onQueryChange = { startQuery = it; startPoint = null },
                onSelect = { name, point -> startQuery = name; startPoint = point }
            )
            Spacer(Modifier.height(12.dp))
            PlaceField(
                label = strings.destination,
                value = endQuery,
                suggestions = if (endPoint == null) vm.placeSuggestions(endQuery) else emptyList(),
                onQueryChange = { endQuery = it; endPoint = null },
                onSelect = { name, point -> endQuery = name; endPoint = point }
            )

            Spacer(Modifier.height(20.dp))
            SectionHeader(strings.vehicleType)
            ChoiceRow(
                options = VehicleClass.values().toList(),
                selected = vehicleType,
                onSelect = { vehicleType = it },
                label = { vehicleLabel(it, language) }
            )

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = plate,
                onValueChange = { plate = it },
                label = { Text(strings.plateNumber) },
                placeholder = { Text("DHA-15-2231") },
                singleLine = true,
                isError = showErrors && !plateCheck.isValid,
                supportingText = { if (showErrors) err(plateCheck)?.let { Text(it) } },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            if (state.driverProfile == null) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = licence,
                    onValueChange = { licence = it },
                    label = { Text(strings.licenseNumber) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "${strings.seatsAvailable}: ${Formatters.localizeDigits(seats.toInt().toString(), language)}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Slider(
                value = seats,
                onValueChange = { seats = it },
                valueRange = 1f..8f,
                steps = 6
            )

            Text(
                "${strings.acceptableDetour}: ${Formatters.distance(detour.toDouble(), language)}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Slider(
                value = detour,
                onValueChange = { detour = it },
                valueRange = 0.5f..5f,
                steps = 8
            )

            Text(
                "${strings.departureTime}: ${Formatters.duration(minutes.toInt(), language)}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Slider(
                value = minutes,
                onValueChange = { minutes = it },
                valueRange = 5f..180f,
                steps = 34
            )

            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                text = strings.publishRoute,
                enabled = startPoint != null && endPoint != null,
                onClick = {
                    showErrors = true
                    val s = startPoint ?: return@PrimaryButton
                    val e = endPoint ?: return@PrimaryButton
                    if (!plateCheck.isValid) return@PrimaryButton
                    vm.publishTrip(
                        licenseNumber = licence,
                        startAddress = startQuery,
                        start = s,
                        endAddress = endQuery,
                        end = e,
                        minutesFromNow = minutes.toInt(),
                        seats = seats.toInt(),
                        detourKm = detour.toDouble(),
                        vehicleType = vehicleType,
                        plate = plate,
                        onPublished = onPublished
                    )
                }
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}
