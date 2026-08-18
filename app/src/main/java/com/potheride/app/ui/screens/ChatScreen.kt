package com.potheride.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.potheride.app.ui.PotheRideViewModel
import com.potheride.app.ui.components.EmptyState
import com.potheride.app.ui.components.PotheTopBar
import com.potheride.app.ui.theme.LocalStrings
import com.potheride.app.ui.theme.RouteGreen
import com.potheride.app.ui.theme.Snow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** In-app chat, available only once the driver has accepted the request. */
@Composable
fun ChatScreen(
    vm: PotheRideViewModel,
    bookingId: String,
    onBack: () -> Unit
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val strings = LocalStrings.current
    val myId = state.currentUser?.id
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.US) }

    DisposableEffect(bookingId) {
        vm.openChat(bookingId)
        onDispose { vm.closeChat() }
    }

    // Keep the newest message in view as the conversation grows.
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }

    Column(Modifier.fillMaxSize()) {
        PotheTopBar(strings.chatTitle, onBack = onBack)

        if (state.messages.isEmpty()) {
            Box(Modifier.weight(1f)) {
                EmptyState(strings.chatTitle, strings.messageHint)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 20.dp, vertical = 12.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.messages) { msg ->
                    val mine = msg.senderId == myId
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
                    ) {
                        Column(
                            Modifier
                                .widthIn(max = 280.dp)
                                .clip(
                                    RoundedCornerShape(
                                        topStart = 16.dp, topEnd = 16.dp,
                                        bottomStart = if (mine) 16.dp else 4.dp,
                                        bottomEnd = if (mine) 4.dp else 16.dp
                                    )
                                )
                                .background(
                                    if (mine) RouteGreen else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text(
                                msg.content,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (mine) Snow else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                timeFormat.format(Date(msg.sentAt)),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (mine) Snow.copy(alpha = 0.75f)
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text(strings.messageHint) },
                shape = RoundedCornerShape(24.dp),
                maxLines = 4,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.padding(3.dp))
            IconButton(
                onClick = {
                    if (draft.isNotBlank()) {
                        vm.sendMessage(bookingId, draft)
                        draft = ""
                    }
                }
            ) {
                Icon(
                    Icons.Default.Send,
                    contentDescription = strings.send,
                    tint = if (draft.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else RouteGreen
                )
            }
        }
    }
}
