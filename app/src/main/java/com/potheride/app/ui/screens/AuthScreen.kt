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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.potheride.app.core.format.AppLanguage
import com.potheride.app.core.validation.ValidationResult
import com.potheride.app.ui.PotheRideViewModel
import com.potheride.app.ui.components.CtaButton
import com.potheride.app.ui.components.Eyebrow
import com.potheride.app.ui.components.OtpBoxes
import com.potheride.app.ui.components.SecondaryButton
import com.potheride.app.ui.theme.LocalAppLanguage
import com.potheride.app.ui.theme.LocalStrings
import com.potheride.app.ui.theme.RouteGreen
import com.potheride.app.ui.theme.SignalAmber
import com.potheride.app.ui.theme.SignalAmberSoft

/**
 * Phone entry then code entry, matching wireframe 1.
 *
 * The screen is explicit that no SMS is sent in this build. Silently accepting any
 * code while *looking* like real verification is the kind of thing that gets
 * discovered at the worst possible moment.
 */
@Composable
fun AuthScreen(vm: PotheRideViewModel, onSignedIn: () -> Unit) {
    val strings = LocalStrings.current
    val language = LocalAppLanguage.current

    var phone by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var codeSent by remember { mutableStateOf(false) }
    var showErrors by remember { mutableStateOf(false) }
    val otpFocus = remember { FocusRequester() }

    val phoneCheck = vm.validatePhone(phone)
    val nameCheck = vm.validateName(name)
    val otpCheck = vm.validateOtp(otp)

    fun errorFor(result: ValidationResult): String? =
        (result as? ValidationResult.Invalid)?.let {
            if (language == AppLanguage.BANGLA) it.messageBn else it.messageEn
        }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Spacer(Modifier.height(72.dp))

        // Brand mark: two stacked bars reading as a shared route, in brand colours.
        Column {
            Box(
                Modifier
                    .size(width = 46.dp, height = 8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(RouteGreen)
            )
            Spacer(Modifier.height(5.dp))
            Box(
                Modifier
                    .size(width = 28.dp, height = 8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(SignalAmber)
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            strings.appName,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            strings.tagline,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(40.dp))

        Eyebrow(strings.phoneLabel)
        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            placeholder = { Text(strings.phoneHint) },
            singleLine = true,
            enabled = !codeSent,
            isError = showErrors && !phoneCheck.isValid,
            supportingText = {
                if (showErrors) errorFor(phoneCheck)?.let { Text(it) }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            placeholder = { Text(strings.nameHint) },
            singleLine = true,
            enabled = !codeSent,
            isError = showErrors && !nameCheck.isValid,
            supportingText = {
                if (showErrors) errorFor(nameCheck)?.let { Text(it) }
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(20.dp))
        CtaButton(
            text = strings.sendOtp,
            onClick = {
                showErrors = true
                if (phoneCheck.isValid && nameCheck.isValid) {
                    codeSent = true
                    showErrors = false
                }
            }
        )

        Spacer(Modifier.height(28.dp))

        // The code block stays on screen but inert until a code has been "sent", so the
        // shape of the flow is visible from the first frame instead of the layout
        // jumping when the number is accepted — which is what the wireframe shows.
        Eyebrow(strings.otpLabel)
        Box {
            OtpBoxes(code = otp, length = 4, onClick = { otpFocus.requestFocus() })
            // The single real field that owns the input. Four visible fields fighting
            // over focus is the standard way this component breaks.
            BasicTextField(
                value = otp,
                onValueChange = { input ->
                    if (codeSent) otp = input.filter { it.isDigit() }.take(4)
                },
                enabled = codeSent,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier
                    .matchParentSize()
                    .focusRequester(otpFocus)
                    .alpha(0f),
                decorationBox = { Box(Modifier.fillMaxWidth()) }
            )
        }
        if (showErrors && codeSent) {
            errorFor(otpCheck)?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }

        Spacer(Modifier.height(16.dp))
        SecondaryButton(
            text = strings.verifyContinue,
            enabled = codeSent,
            onClick = {
                showErrors = true
                if (otpCheck.isValid) {
                    vm.verifyAndSignIn(phone, name)
                    onSignedIn()
                }
            }
        )
        if (codeSent) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = { codeSent = false; otp = "" }) { Text(strings.changeNumber) }
                TextButton(onClick = { }) { Text(strings.resendCode) }
            }
        }

        Spacer(Modifier.height(28.dp))

        // Honest about what this build does, right where the user would otherwise
        // form the wrong assumption.
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SignalAmberSoft)
                .padding(14.dp)
        ) {
            Text(
                strings.demoOtpNotice,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = { vm.toggleLanguage() }) {
                Text(
                    if (language == AppLanguage.ENGLISH) "বাংলায় দেখুন" else "View in English",
                    color = RouteGreen
                )
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}
