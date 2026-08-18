package com.potheride.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.potheride.app.core.verification.DriverDocumentKind
import com.potheride.app.core.verification.VerificationRules
import com.potheride.app.ui.PotheRideViewModel
import com.potheride.app.ui.components.CtaButton
import com.potheride.app.ui.components.Eyebrow
import com.potheride.app.ui.components.PotheTopBar
import com.potheride.app.ui.components.UploadRow
import com.potheride.app.ui.theme.LocalStrings

/**
 * Board 02B — driver registration: full name, then the document uploads.
 *
 * File picking is intentionally the caller's problem: this screen calls [onPickDocument]
 * for each row and gets back whatever local path the picker produced, then hands it
 * straight to [PotheRideViewModel.uploadDriverDocument]. Keeping `Intent`/`ActivityResult`
 * plumbing at the navigation layer, rather than inside this composable, is what the rest
 * of the screen package already does for every other picker in the app.
 */
@Composable
fun DriverRegistrationScreen(
    vm: PotheRideViewModel,
    onBack: () -> Unit,
    onPickDocument: (DriverDocumentKind, onPicked: (String) -> Unit) -> Unit,
    onSubmitted: () -> Unit
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val strings = LocalStrings.current
    var fullName by remember { mutableStateOf(state.currentUser?.name.orEmpty()) }

    val current = remember(state.driverDocuments) {
        VerificationRules.current(state.driverDocuments.map { it.toDomain() })
    }

    Column(Modifier.fillMaxSize()) {
        PotheTopBar(title = "Driver registration", onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Eyebrow("Full name")
            OutlinedTextField(
                value = fullName,
                onValueChange = { fullName = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(20.dp))
            Eyebrow("National ID (NID)")
            UploadRow(
                label = "Upload front",
                attachedName = current[DriverDocumentKind.NID_FRONT]?.let { "Attached" },
                onPick = { onPickDocument(DriverDocumentKind.NID_FRONT) { path -> vm.uploadDriverDocument(DriverDocumentKind.NID_FRONT, path) } }
            )
            Spacer(Modifier.height(10.dp))
            UploadRow(
                label = "Upload back",
                attachedName = current[DriverDocumentKind.NID_BACK]?.let { "Attached" },
                onPick = { onPickDocument(DriverDocumentKind.NID_BACK) { path -> vm.uploadDriverDocument(DriverDocumentKind.NID_BACK, path) } }
            )

            Spacer(Modifier.height(20.dp))
            Eyebrow("Driving licence")
            UploadRow(
                label = "Upload licence",
                attachedName = current[DriverDocumentKind.LICENCE]?.let { "Attached" },
                onPick = { onPickDocument(DriverDocumentKind.LICENCE) { path -> vm.uploadDriverDocument(DriverDocumentKind.LICENCE, path) } }
            )

            Spacer(Modifier.height(20.dp))
            Eyebrow("Vehicle registration")
            UploadRow(
                label = "Upload registration",
                attachedName = current[DriverDocumentKind.REGISTRATION]?.let { "Attached" },
                onPick = { onPickDocument(DriverDocumentKind.REGISTRATION) { path -> vm.uploadDriverDocument(DriverDocumentKind.REGISTRATION, path) } }
            )

            Spacer(Modifier.height(28.dp))
            CtaButton(
                text = "Submit for review",
                onClick = {
                    if (fullName.isNotBlank()) vm.becomeDriver(licenseNumber = "")
                    onSubmitted()
                }
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
