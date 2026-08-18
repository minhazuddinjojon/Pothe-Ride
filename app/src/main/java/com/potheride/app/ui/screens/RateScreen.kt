package com.potheride.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.potheride.app.ui.PotheRideViewModel
import com.potheride.app.ui.components.PotheTopBar
import com.potheride.app.ui.components.PrimaryButton
import com.potheride.app.ui.components.StarRating
import com.potheride.app.ui.theme.LocalStrings

/** Post-ride rating. Ratings feed the driver's public average via the repository. */
@Composable
fun RateScreen(
    vm: PotheRideViewModel,
    bookingId: String,
    rateeId: String,
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    val strings = LocalStrings.current
    var stars by remember { mutableStateOf(5) }
    var comment by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        PotheTopBar(strings.rateTitle, onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(40.dp))
            Text(
                strings.rateBody,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(24.dp))
            StarRating(stars = stars, onSelect = { stars = it }, size = 40.dp)
            Spacer(Modifier.height(28.dp))
            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it },
                label = { Text(strings.feedbackHint) },
                shape = RoundedCornerShape(12.dp),
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(24.dp))
            PrimaryButton(
                text = strings.submitRating,
                onClick = {
                    vm.submitRating(bookingId, rateeId, stars, comment.ifBlank { null }, onDone)
                }
            )
        }
    }
}
