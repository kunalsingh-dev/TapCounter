package com.example.tapcounter.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tapcounter.TapCounterApp
import com.example.tapcounter.face.EnrollmentCollector
import com.example.tapcounter.face.FaceCamera
import com.example.tapcounter.face.FaceFrame

private enum class Phase { IDLE, ENROLL, TEST }

/** Enrol my face (8 samples), and a live test that shows exactly what the app sees. */
@Composable
fun FaceSetupScreen(app: TapCounterApp, onBack: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val display by app.counterStore.display.collectAsStateWithLifecycle()
    val templates by app.faceStore.templates.collectAsStateWithLifecycle()

    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
    }

    var phase by remember { mutableStateOf(Phase.IDLE) }
    var status by remember { mutableStateOf("") }
    var progress by remember { mutableIntStateOf(0) }
    val collector = remember { EnrollmentCollector() }
    val previewView = remember { PreviewView(context) }
    val engineReady = remember { app.faceEngine.ready }
    var strictness by remember(display.faceThresholdPct) { mutableFloatStateOf(display.faceThresholdPct.toFloat()) }

    DisposableEffect(phase, hasPermission) {
        if (phase == Phase.IDLE || !hasPermission || !engineReady) return@DisposableEffect onDispose { }

        val main = Handler(Looper.getMainLooper())
        val mode = phase
        val threshold = display.faceThresholdPct / 100f
        val camera = FaceCamera(context, app.faceEngine)
        val enrolled = if (mode == Phase.TEST) templates else emptyList()

        camera.start(owner, previewView, enrolled, augment = mode == Phase.ENROLL) { frame ->
            if (mode == Phase.ENROLL) {
                val emb = frame.embedding
                if (emb != null && frame.faceCount == 1 && frame.quality) collector.offer(SystemClock.uptimeMillis(), emb, true, frame.extraEmbeddings)
                main.post {
                    if (phase != Phase.ENROLL) return@post
                    progress = collector.count
                    status = when {
                        frame.faceCount == 0 -> "No face found. Hold the phone at arm's length, face in good light."
                        frame.faceCount > 1 -> "Only YOU should be in view."
                        !frame.quality -> "Look straight at the screen, eyes open, a little closer."
                        else -> "Good. Keep looking at the screen and turn your head slightly now and then."
                    }
                    if (collector.done) {
                        if (collector.isConsistent()) {
                            app.faceStore.save(collector.templates())
                            app.counterStore.updateDisplay { it.copy(faceUnlock = true) }
                            status = "Saved. Face unlock is ON. Use Test to check it."
                        } else {
                            status = "The samples did not look like one person. Try again, alone, in good light."
                        }
                        collector.reset()
                        progress = 0
                        phase = Phase.IDLE
                    }
                }
            } else {
                main.post { if (phase == Phase.TEST) status = describe(frame, threshold, display.faceLockOnMultiple) }
            }
        }
        onDispose { camera.stop() }
    }

    ScreenPage("Face unlock", { phase = Phase.IDLE; onBack() }) {
        Text(
            "Your face is turned into a list of numbers that stays on this phone. No photo is saved and nothing is " +
                "uploaded. This is a convenience disguise, not bank-grade security: a very similar-looking person or " +
                "a photo of you could fool it, and bad light can make it miss you. The secret calculator code always works as a backup.",
            style = MaterialTheme.typography.bodySmall,
        )

        if (!engineReady) {
            Text("The face model could not be loaded on this phone, so face unlock is unavailable.")
        } else if (!hasPermission) {
            Text("The camera is needed to see faces. It is used only while this app is open and on screen.")
            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
        } else {
            Text(if (templates.isEmpty()) "No face enrolled yet." else "Face enrolled (${templates.size} samples).")

            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxWidth().height(if (phase == Phase.IDLE) 1.dp else 320.dp),
            )
            if (phase == Phase.ENROLL) Text("Samples: $progress / ${collector.target}")
            if (status.isNotEmpty()) Text(status)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    collector.reset(); progress = 0
                    status = "Look at the screen."
                    phase = Phase.ENROLL
                }) { Text(if (templates.isEmpty()) "Enroll my face" else "Re-enroll") }
                OutlinedButton(
                    enabled = templates.isNotEmpty(),
                    onClick = { status = "Look at the screen."; phase = Phase.TEST },
                ) { Text("Test") }
                if (phase != Phase.IDLE) OutlinedButton(onClick = { phase = Phase.IDLE; status = "" }) { Text("Stop") }
            }

            Text("Match strictness: ${strictness.toInt()}%  (higher = stricter)")
            Slider(
                value = strictness,
                onValueChange = { strictness = it },
                valueRange = 35f..85f,
                onValueChangeFinished = { app.counterStore.updateDisplay { it.copy(faceThresholdPct = strictness.toInt()) } },
            )
            Text(
                "Tip: run Test, look at the phone normally and note the similarity. Set strictness a bit below that " +
                    "number. Then have someone else try: their number should stay clearly lower.",
                style = MaterialTheme.typography.bodySmall,
            )

            if (templates.isNotEmpty()) {
                OutlinedButton(onClick = {
                    phase = Phase.IDLE
                    app.faceStore.clear()
                    app.counterStore.updateDisplay { it.copy(faceUnlock = false) }
                    status = "Face data deleted."
                }) { Text("Delete my face data") }
            }
        }
    }
}

private fun describe(frame: FaceFrame, threshold: Float, multiIsStranger: Boolean): String {
    val sim = frame.similarity
    val result = when {
        frame.faceCount == 0 -> "no face seen"
        frame.faceCount >= 2 && multiIsStranger -> "NOT OK: 2+ faces in view"
        sim == null -> "no profile to compare"
        sim >= threshold -> "YOU (unlocks)"
        else -> "NOT YOU (calculator stays)"
    }
    val simText = if (sim == null) "-" else "%.2f".format(sim)
    return "Faces in view: ${frame.faceCount}\nSimilarity to you: $simText  (needs %.2f)\nResult: $result".format(threshold)
}
