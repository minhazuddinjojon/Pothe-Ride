package com.potheride.app.verification

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.potheride.app.core.verification.DetectedFace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The front-camera preview plus the two operations board 03 needs: a live face read for
 * on-screen guidance, and a still capture for the record.
 *
 * Built on [LifecycleCameraController] rather than `ProcessCameraProvider`. The provider
 * API hands back a Guava `ListenableFuture`, which drags a dependency this project does
 * not otherwise need onto the compile classpath purely to call `.addListener` on it.
 * The controller binds itself to the lifecycle and exposes the same preview, analysis and
 * capture use cases with none of that.
 */
class FaceCaptureController internal constructor(
    private val controller: LifecycleCameraController,
    private val context: Context
) {
    /** Captures a still frame and returns it as JPEG bytes, ready to store. */
    suspend fun captureJpeg(): ByteArray = suspendCancellableCoroutine { continuation ->
        val target = File.createTempFile("face_capture", ".jpg", context.cacheDir)
        controller.takePicture(
            ImageCapture.OutputFileOptions.Builder(target).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    continuation.resume(runCatching { target.readBytes() }.getOrDefault(ByteArray(0)))
                }

                override fun onError(exception: ImageCaptureException) {
                    continuation.resumeWithException(exception)
                }
            }
        )
    }
}

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    onFrameAnalyzed: (List<DetectedFace>) -> Unit,
    onReady: (FaceCaptureController) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }

    DisposableEffect(lifecycleOwner) {
        val controller = LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
            setEnabledUseCases(CameraController.IMAGE_CAPTURE or CameraController.IMAGE_ANALYSIS)
            setImageAnalysisAnalyzer(ContextCompat.getMainExecutor(context)) { proxy ->
                analyzeFrame(proxy, onFrameAnalyzed)
            }
            bindToLifecycle(lifecycleOwner)
        }
        previewView.controller = controller
        onReady(FaceCaptureController(controller, context))

        onDispose {
            controller.unbind()
            previewView.controller = null
        }
    }

    AndroidView(modifier = modifier.fillMaxSize(), factory = { previewView })
}

/**
 * One short-lived scope for the analyzer, not [kotlinx.coroutines.GlobalScope]. Frames
 * arrive continuously while the camera is bound, so an untracked coroutine per frame
 * would leak without bound.
 */
private val analyzerScope = CoroutineScope(Dispatchers.Default)

@androidx.camera.core.ExperimentalGetImage
private fun analyzeFrame(proxy: ImageProxy, onResult: (List<DetectedFace>) -> Unit) {
    val mediaImage = proxy.image
    if (mediaImage == null) {
        proxy.close()
        return
    }
    val input = InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees)
    analyzerScope.launch {
        try {
            onResult(FaceDetectorAdapter.detect(input))
        } catch (e: Exception) {
            Log.w("CameraPreview", "Frame analysis failed", e)
        } finally {
            proxy.close()
        }
    }
}
