package com.example.tapcounter.face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Front camera -> [FaceEngine] -> [listener] (called on a background thread, ~10 frames/s).
 * With [preview] == null nothing is drawn: it analyses silently. Tied to the Activity lifecycle,
 * so the camera stops by itself when the app goes to the background. One instance = one session.
 */
class FaceCamera(private val context: Context, private val engine: FaceEngine) {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private var provider: ProcessCameraProvider? = null
    @Volatile
    private var stopped = false

    fun start(
        owner: LifecycleOwner,
        preview: PreviewView?,
        templates: List<FloatArray>,
        augment: Boolean = false,
        listener: (FaceFrame) -> Unit,
    ) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            {
                try {
                    if (stopped) return@addListener
                    val cameraProvider = future.get()
                    provider = cameraProvider

                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analysis.setAnalyzer(executor, FaceAnalyzer(engine, templates, augment, listener))

                    val useCases = mutableListOf<UseCase>()
                    if (preview != null) {
                        val p = Preview.Builder().build()
                        p.setSurfaceProvider(preview.surfaceProvider)
                        useCases.add(p)
                    }
                    useCases.add(analysis)

                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(owner, CameraSelector.DEFAULT_FRONT_CAMERA, *useCases.toTypedArray())
                } catch (t: Throwable) {
                    // No front camera / camera busy: the face check simply never succeeds (stays on the calculator).
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    fun stop() {
        stopped = true
        try {
            provider?.unbindAll()
        } catch (_: Throwable) {
        }
        executor.shutdown()
    }
}

private class FaceAnalyzer(
    private val engine: FaceEngine,
    private val templates: List<FloatArray>,
    private val augment: Boolean,
    private val listener: (FaceFrame) -> Unit,
) : ImageAnalysis.Analyzer {
    private var lastAt = 0L

    override fun analyze(image: ImageProxy) {
        try {
            val now = SystemClock.uptimeMillis()
            if (now - lastAt < MIN_INTERVAL_MS) return
            lastAt = now
            val upright = rotate(image.toBitmap(), image.imageInfo.rotationDegrees)
            listener(engine.process(upright, templates, augment))
        } catch (t: Throwable) {
            // drop this frame
        } finally {
            image.close()
        }
    }

    private fun rotate(src: Bitmap, degrees: Int): Bitmap =
        if (degrees == 0) {
            src
        } else {
            Bitmap.createBitmap(src, 0, 0, src.width, src.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)
        }

    private companion object {
        const val MIN_INTERVAL_MS = 100L
    }
}
