package com.potheride.app.ui.screens

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.potheride.app.ui.PotheRideViewModel
import com.potheride.app.ui.components.DepthCard
import com.potheride.app.ui.components.Eyebrow
import com.potheride.app.ui.components.EmptyState
import com.potheride.app.ui.components.InitialsAvatar
import com.potheride.app.ui.components.MetaText
import com.potheride.app.ui.components.SecondaryButton
import com.potheride.app.ui.theme.LocalStrings

/**
 * The conversation list behind the Chat tab.
 *
 * Wireframe board 01B gives the bottom bar a Chat tab, but no board draws what it opens
 * onto — every chat in the wireframes is reached from inside a ride. Rather than invent
 * a messaging product, this lists the rides that *have* a counterpart to talk to and
 * routes into the existing per-ride [ChatScreen]. That keeps the tab honest: a chat in
 * this app belongs to a journey, and there is nobody to message without one.
 */
@Composable
fun ChatsScreen(
    vm: PotheRideViewModel,
    onOpenChat: (bookingId: String) -> Unit,
    onFindRide: () -> Unit
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val strings = LocalStrings.current

    // A ride you can still talk about: accepted through to completed. REQUESTED has no
    // counterpart yet (nobody has accepted), and a terminal state (declined, cancelled,
    // paid-and-done) has nothing left to say — filtering to the happy path minus
    // REQUESTED and PAID covers exactly that live window.
    val conversations = remember(state.bookingHistory, state.activeBooking) {
        (listOfNotNull(state.activeBooking) + state.bookingHistory)
            .distinctBy { it.booking.id }
            .filter {
                val status = it.booking.status
                status.isOnHappyPath && status != com.potheride.app.core.ride.RideState.REQUESTED &&
                    status != com.potheride.app.core.ride.RideState.PAID
            }
            .sortedByDescending { it.booking.requestedAt }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            strings.navChat,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(16.dp))

        if (conversations.isEmpty()) {
            EmptyState(
                title = strings.noMessagesTitle,
                body = strings.noMessagesBody,
                action = { SecondaryButton(strings.findRideTitle, onFindRide) }
            )
            return@Column
        }

        Eyebrow(strings.navActivity)

        conversations.forEach { detail ->
            val counterpartName = detail.driver?.name ?: detail.passengerName.orEmpty()
            DepthCard(
                onClick = { onOpenChat(detail.booking.id) },
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InitialsAvatar(
                        initials = counterpartName.trim()
                            .split(" ").filter { it.isNotBlank() }.take(2)
                            .joinToString("") { it.first().uppercase() }
                            .ifBlank { "?" }
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            counterpartName.ifBlank { strings.navChat },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        MetaText(
                            "${detail.booking.pickupAddress} → ${detail.booking.dropAddress}",
                            maxLines = 1
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    MetaText(relativeTime(detail.booking.requestedAt), maxLines = 1)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

/** A short "3h ago" / "2d ago" read-out. Kept local: no other screen needs this shape yet. */
private fun relativeTime(atMillis: Long): String {
    val minutes = (System.currentTimeMillis() - atMillis) / 60_000
    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 24 * 60 -> "${minutes / 60}h ago"
        else -> "${minutes / (24 * 60)}d ago"
    }
}
