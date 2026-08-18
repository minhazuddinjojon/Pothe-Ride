package com.potheride.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.potheride.app.core.format.AppLanguage
import com.potheride.app.ui.PotheRideViewModel
import com.potheride.app.ui.components.EmptyState
import com.potheride.app.ui.components.PotheCard
import com.potheride.app.ui.components.PotheTopBar
import com.potheride.app.ui.theme.LocalAppLanguage
import com.potheride.app.ui.theme.LocalStrings
import com.potheride.app.ui.theme.SignalAmber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The notification feed. Entries are written bilingually at the moment they are
 * created, so switching language re-renders the history correctly instead of
 * leaving a trail of messages in whichever language was active at the time.
 */
@Composable
fun NotificationsScreen(vm: PotheRideViewModel, onBack: () -> Unit) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val strings = LocalStrings.current
    val language = LocalAppLanguage.current
    val dateFormat = remember { SimpleDateFormat("d MMM, HH:mm", Locale.US) }

    LaunchedEffect(Unit) { vm.markNotificationsRead() }

    Column(Modifier.fillMaxSize()) {
        PotheTopBar(strings.notifications, onBack = onBack)

        if (state.notifications.isEmpty()) {
            EmptyState(strings.notifications, strings.noNotifications)
            return@Column
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp, end = 20.dp, bottom = 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(state.notifications) { item ->
                PotheCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (item.readAt == null) {
                            Box(
                                Modifier.size(8.dp).clip(CircleShape).background(SignalAmber)
                            )
                            Spacer(Modifier.size(8.dp))
                        }
                        Text(
                            if (language == AppLanguage.BANGLA) item.titleBn else item.titleEn,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            dateFormat.format(Date(item.createdAt)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (language == AppLanguage.BANGLA) item.bodyBn else item.bodyEn,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
