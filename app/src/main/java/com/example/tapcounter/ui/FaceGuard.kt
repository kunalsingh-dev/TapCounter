package com.example.tapcounter.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tapcounter.TapCounterApp
import com.example.tapcounter.face.FaceCamera
import com.example.tapcounter.face.FaceGate
import com.example.tapcounter.face.FaceGateConfig
import com.example.tapcounter.face.FaceVerdict

/**
 * Invisible: runs the front camera in the background (no preview is drawn).
 *  - Calculator showing (locked): my face for a few frames unlocks the counter. Another face,
 *    two faces, or no face keeps the calculator.
 *  - Counter showing: another face (or two faces) appearing switches back to the calculator.
 * Fail-closed: if anything is missing or breaks (permission, model, camera) nothing unlocks;
 * the secret code on the calculator always still works. Only runs while the app is on screen.
 */
@Composable
fun FaceGuard(app: TapCounterApp, paused: Boolean) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val counter by app.counterStore.state.collectAsStateWithLifecycle()
    val display by app.counterStore.display.collectAsStateWithLifecycle()
    val templates by app.faceStore.templates.collectAsStateWithLifecycle()

    val locked = counter.locked
    val watching = !locked && !counter.stealth && display.faceWatch
    val cameraAllowed =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    // app.faceEngine is only touched when everything else says "go" (loads the model lazily).
    val active = !paused && display.faceUnlock && display.calcLock && cameraAllowed &&
        templates.isNotEmpty() && (locked || watching) && app.faceEngine.ready

    DisposableEffect(active, locked, templates, display.faceThresholdPct, display.faceLockOnMultiple, display.faceRequireBlink) {
        if (!active) return@DisposableEffect onDispose { }

        val gate = FaceGate(
            FaceGateConfig(
                threshold = display.faceThresholdPct.coerceIn(30, 95) / 100f,
                lockOnMultipleFaces = display.faceLockOnMultiple,
                requireBlink = display.faceRequireBlink,
            ),
        )
        val main = Handler(Looper.getMainLooper())
        val camera = FaceCamera(context, app.faceEngine)
        camera.start(owner, null, templates) { frame ->
            val verdict = gate.onFrame(SystemClock.uptimeMillis(), frame.toObservation())
            if (locked && verdict == FaceVerdict.OWNER) {
                main.post { app.counterStore.unlock() }
            } else if (!locked && verdict == FaceVerdict.STRANGER) {
                main.post { app.counterStore.lockNow() }
            }
        }
        onDispose { camera.stop() }
    }
}
