package com.potheride.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.potheride.app.core.verification.FaceCheck
import com.potheride.app.core.verification.FaceChecks
import com.potheride.app.core.verification.FaceMatchResult
import com.potheride.app.core.verification.LivenessOnlyFaceMatchProvider
import com.potheride.app.ui.components.CtaButton
import com.potheride.app.ui.components.DepthCard
import com.potheride.app.ui.components.MetaText
import com.potheride.app.ui.components.PotheTopBar
import com.potheride.app.ui.theme.RouteGreen
import com.potheride.app.verification.CameraPreview
import com.potheride.app.verification.FaceCaptureController
import kotlinx.coroutines.launch

/**
 * Board 03 — capture, then result.
 *
 * The result card never shows a fabricated confidence figure — see
 * `FaceMatchResult`'s KDoc for why the wireframe's "98% confidence against NID photo"
 * cannot be produced honestly by this build. What is shown instead is exactly what was
 * checked: a face was present, alone, facing the camera, with both eyes open.
 */
@Composable
fun FaceVerificationScreen(
    onBack: () -> Unit,
    onVerified: (FaceMatchResult) -> Unit
) {
    var liveCheck by remember { mutableStateOf<FaceCheck?>(null) }
    var controller by remember { mutableStateOf<FaceCaptureController?>(null) }
    var result by remember { mutableStateOf<FaceMatchResult?>(null) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        PotheTopBar(title = "Face verification", onBack = onBack)

        if (result == null) {
            Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                MetaText("Match your face to your NID photo to finish registration")
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black)
                ) {
                    CameraPreview(
                        modifier = Modifier.fillMaxSize(),
                        onFrameAnalyzed = { faces -> liveCheck = FaceChecks.evaluate(faces) },
                        onReady = { controller = it }
                    )
                }
                Spacer(Modifier.height(10.dp))
                val guidance = (liveCheck as? FaceCheck.Refused)?.reason
                    ?.let { FaceChecks.guidanceEn(it) }
                    ?: "Center your face in the frame"
                Text(
                    guidance,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                CtaButton(
                    text = "Capture",
                    enabled = liveCheck is FaceCheck.Accepted && controller != null,
                    onClick = {
                        val check = liveCheck
                        val capture = controller
                        if (check == null || capture == null) return@CtaButton
                        scope.launch {
                            val jpeg = runCatching { capture.captureJpeg() }.getOrDefault(ByteArray(0))
                            result = LivenessOnlyFaceMatchProvider(check).match(jpeg, reference = null)
                        }
                    }
                )
                Spacer(Modifier.height(20.dp))
            }
        } else {
            FaceResultCard(result = result!!, onContinue = { onVerified(result!!) })
        }
    }
}

@Composable
private fun FaceResultCard(result: FaceMatchResult, onContinue: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(8.dp))
        DepthCard {
            Box(
                Modifier.fillMaxWidth().height(160.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (result.passed) "Face detected · liveness passed" else "Verification failed",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (result.passed) RouteGreen else MaterialTheme.colorScheme.error
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        DepthCard {
            MetaText(
                if (result.isRealMatch) {
                    // Reserved for a real matcher — see FaceMatchResult.isRealMatch.
                    "${(result.confidence!! * 100).toInt()}% confidence against NID photo"
                } else {
                    "Face ID · ${if (result.passed) "Passed" else "Refused"} · just now"
                }
            )
        }
        Spacer(Modifier.height(20.dp))
        CtaButton(text = "Continue", onClick = onContinue, enabled = result.passed)
        Spacer(Modifier.height(24.dp))
    }
}
