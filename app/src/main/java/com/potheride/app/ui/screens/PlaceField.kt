package com.potheride.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.potheride.app.core.geo.LatLng
import com.potheride.app.ui.components.PotheCard

/**
 * Text field with an inline place picker.
 *
 * The suggestion list comes from a fixed table of Dhaka landmarks rather than a
 * geocoding service, so the app resolves a typed name to real coordinates offline.
 * That table is the swap point for a Places API later — see docs/MAPS.md — and it
 * matters that a name always resolves to a *coordinate*, since the matcher works on
 * geometry and would silently fail on a free-text address.
 */
@Composable
fun PlaceField(
    label: String,
    value: String,
    suggestions: List<Pair<String, LatLng>>,
    onQueryChange: (String) -> Unit,
    onSelect: (String, LatLng) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: String? = null
) {
    var focused by remember { mutableStateOf(false) }

    Column(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onQueryChange(it)
                focused = true
            },
            label = { Text(label) },
            singleLine = true,
            isError = isError,
            supportingText = supportingText?.let { { Text(it) } },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        // Only shown while choosing, and hidden as soon as a coordinate is locked in.
        if (focused && suggestions.isNotEmpty()) {
            PotheCard(Modifier.padding(top = 6.dp)) {
                LazyColumn(Modifier.heightIn(max = 190.dp)) {
                    items(suggestions) { (name, point) ->
                        Text(
                            name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelect(name, point)
                                    focused = false
                                }
                                .padding(vertical = 11.dp)
                        )
                    }
                }
            }
        }
    }
}
