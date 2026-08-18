package com.potheride.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.potheride.app.core.format.AppLanguage
import com.potheride.app.core.format.Formatters
import com.potheride.app.ui.PotheRideViewModel
import com.potheride.app.ui.components.ChoiceRow
import com.potheride.app.ui.components.InitialsAvatar
import com.potheride.app.ui.components.Pill
import com.potheride.app.ui.components.PotheCard
import com.potheride.app.ui.components.PotheTopBar
import com.potheride.app.ui.components.PrimaryButton
import com.potheride.app.ui.components.SecondaryButton
import com.potheride.app.ui.components.SectionHeader
import com.potheride.app.ui.theme.AlertRed
import com.potheride.app.ui.theme.LocalAppLanguage
import com.potheride.app.ui.theme.LocalStrings
import com.potheride.app.ui.theme.RouteGreen
import com.potheride.app.ui.theme.RouteGreenSoft
import com.potheride.app.ui.theme.SignalAmberSoft

/** Identity, language, driver status, and the safety contacts that back SOS. */
@Composable
fun ProfileScreen(
    vm: PotheRideViewModel,
    onBack: (() -> Unit)? = null,
    onAdmin: () -> Unit,
    onSignedOut: () -> Unit
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val strings = LocalStrings.current
    val language = LocalAppLanguage.current
    val user = state.currentUser

    var showAddContact by remember { mutableStateOf(false) }
    var contactName by remember { mutableStateOf("") }
    var contactPhone by remember { mutableStateOf("") }
    var licence by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        PotheTopBar(strings.profileTitle, onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                InitialsAvatar(
                    initials = user?.name?.trim()?.take(2)?.uppercase() ?: "?",
                    size = 60.dp
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        user?.name ?: "",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        user?.phone?.let { Formatters.localizeDigits(it, language) } ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionHeader(strings.language)
            ChoiceRow(
                options = AppLanguage.values().toList(),
                selected = language,
                onSelect = { vm.setLanguage(it) },
                label = { if (it == AppLanguage.BANGLA) "বাংলা" else "English" }
            )

            Spacer(Modifier.height(24.dp))
            SectionHeader(strings.driverProfile)
            val driver = state.driverProfile
            if (driver == null) {
                PotheCard {
                    Text(
                        strings.shareRouteBody,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = licence,
                        onValueChange = { licence = it },
                        label = { Text(strings.licenseNumber) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton(strings.becomeDriver, { vm.becomeDriver(licence) })
                }
            } else {
                PotheCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            driver.licenseNumber,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        if (driver.verified) {
                            Pill(strings.verified, RouteGreenSoft, RouteGreen)
                        } else {
                            Pill(
                                strings.unverified, SignalAmberSoft,
                                MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${Formatters.localizeDigits(driver.totalTrips.toString(), language)} ${strings.tripsCompleted}" +
                            (driver.rating?.let { " · ★ ${Formatters.rating(it, language)}" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionHeader(strings.trustedContacts)
            if (state.trustedContacts.isEmpty()) {
                Text(
                    strings.noTrustedContacts,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            state.trustedContacts.forEach { contact ->
                PotheCard(Modifier.padding(bottom = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                contact.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                Formatters.localizeDigits(contact.phone, language),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { vm.deleteTrustedContact(contact.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = AlertRed)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            SecondaryButton(strings.addTrustedContact, { showAddContact = true })

            Spacer(Modifier.height(28.dp))
            SecondaryButton(strings.adminTitle, onAdmin)
            Spacer(Modifier.height(10.dp))
            TextButton(
                onClick = { vm.signOut(); onSignedOut() },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) { Text(strings.logOut, color = AlertRed) }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showAddContact) {
        AlertDialog(
            onDismissRequest = { showAddContact = false },
            title = { Text(strings.addTrustedContact) },
            text = {
                Column {
                    OutlinedTextField(
                        value = contactName,
                        onValueChange = { contactName = it },
                        label = { Text(strings.nameLabel) },
                        singleLine = true
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = contactPhone,
                        onValueChange = { contactPhone = it },
                        label = { Text(strings.phoneLabel) },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (contactName.isNotBlank() && contactPhone.isNotBlank()) {
                        vm.addTrustedContact(contactName, contactPhone)
                        contactName = ""; contactPhone = ""
                        showAddContact = false
                    }
                }) { Text(strings.save) }
            },
            dismissButton = {
                TextButton(onClick = { showAddContact = false }) { Text(strings.cancel) }
            }
        )
    }
}
