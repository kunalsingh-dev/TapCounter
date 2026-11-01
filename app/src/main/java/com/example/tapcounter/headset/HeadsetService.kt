package com.example.tapcounter.headset

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.example.tapcounter.MainActivity
import com.example.tapcounter.R
import com.example.tapcounter.TapCounterApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service (type connectedDevice) that owns the MediaSession, the keep-alive audio
 * and the Bluetooth/noisy receivers, so headphone buttons work with the screen off and the app
 * in the background.
 */
class HeadsetService : Service() {

    private lateinit var controller: HeadsetController
    private lateinit var repo: HeadsetSettingsRepository
    private lateinit var sessionManager: HeadsetSessionManager
    private lateinit var conflictDetector: PlaybackConflictDetector
    private var initialised = false

    private val keepAlive = KeepAliveAudio()
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val connectedDevices = mutableSetOf<String>()
    private var routePaused = false
    private var stopping = false

    @Volatile
    private var latest = HeadsetSettings()

    private val retakeRunnable = Runnable { retake() }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    deviceAddress(intent)?.let { connectedDevices.add(it) }
                    controller.updateStatus { it.copy(bluetoothConnected = true) }
                    // Audio routing needs a moment after the link comes up.
                    handler.removeCallbacks(retakeRunnable)
                    handler.postDelayed(retakeRunnable, RECONNECT_DELAY_MS)
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    deviceAddress(intent)?.let { connectedDevices.remove(it) }
                    controller.updateStatus { it.copy(bluetoothConnected = connectedDevices.isNotEmpty()) }
                }
                AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                    // Headset vanished: stop streaming silence to the speaker, drop half-finished gestures.
                    routePaused = true
                    keepAlive.stop()
                    controller.resetGestureState()
                    controller.updateStatus { it.copy(keepAliveRunning = false) }
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val app = TapCounterApp.from(this)
        controller = app.headsetController
        repo = app.headsetSettings
        createChannel()

        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } catch (e: Exception) {
            // e.g. started from the background on Android 12+ or missing permission.
            stopping = true
            stopSelf()
            return
        }

        sessionManager = HeadsetSessionManager(this, controller, contentIntent())
        conflictDetector = PlaybackConflictDetector(
            context = this,
            ownMediaPlayers = { if (keepAlive.isPlaying) 1 else 0 },
            onChange = { conflict -> controller.updateStatus { it.copy(possibleConflict = conflict) } },
        )
        initialised = true

        sessionManager.start()
        conflictDetector.start()
        registerReceivers()
        readInitialBluetoothState()
        controller.updateStatus { it.copy(serviceRunning = true) }

        scope.launch {
            repo.settings.collect { s ->
                latest = s
                applyKeepAlive()
                refreshNotification()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (stopping) return START_NOT_STICKY
        when (intent?.action) {
            ACTION_STOP -> {
                stopping = true
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_RETAKE -> retake()
            ACTION_TOGGLE_LOCK -> scope.launch { repo.setHardwareLock(!latest.hardwareLock) }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (initialised) {
            runCatching { unregisterReceiver(receiver) }
            conflictDetector.stop()
            keepAlive.stop()
            sessionManager.stop()
            controller.release()
        }
        if (::controller.isInitialized) {
            controller.updateStatus {
                it.copy(serviceRunning = false, sessionActive = false, keepAliveRunning = false, possibleConflict = false)
            }
        }
        scope.cancel()
        super.onDestroy()
    }

    // ---- behaviour -----------------------------------------------------------------

    private fun retake() {
        if (!initialised) return
        routePaused = false
        keepAlive.stop()
        sessionManager.restart()
        applyKeepAlive()
        controller.resetGestureState()
        controller.updateStatus { it.copy(possibleConflict = false) }
    }

    private fun applyKeepAlive() {
        if (!initialised) return
        val wanted = latest.keepAliveAudio && !routePaused
        if (wanted) keepAlive.start() else keepAlive.stop()
        controller.updateStatus { it.copy(keepAliveRunning = keepAlive.isPlaying) }
    }

    private fun registerReceivers() {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    private fun readInitialBluetoothState() {
        try {
            val adapter = getSystemService(BluetoothManager::class.java)?.adapter
            val connected = adapter != null &&
                (adapter.getProfileConnectionState(BluetoothProfile.A2DP) == BluetoothProfile.STATE_CONNECTED ||
                    adapter.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothProfile.STATE_CONNECTED)
            controller.updateStatus { it.copy(bluetoothConnected = connected) }
        } catch (_: SecurityException) {
            // BLUETOOTH_CONNECT not granted: state stays "unknown".
        }
    }

    private fun deviceAddress(intent: Intent): String? =
        IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)?.address

    // ---- notification --------------------------------------------------------------

    private fun createChannel() {
        NotificationManagerCompat.from(this).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName("Headset control")
                .build(),
        )
    }

    private fun contentIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun servicePending(action: String, requestCode: Int): PendingIntent = PendingIntent.getService(
        this, requestCode, Intent(this, HeadsetService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun buildNotification(): Notification {
        val locked = latest.hardwareLock
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_headset)
            .setContentTitle("Headset buttons active")
            .setContentText(if (locked) "Hardware lock ON: buttons are ignored" else "Headphone buttons are counting")
            .setContentIntent(contentIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, if (locked) "Unlock" else "Lock", servicePending(ACTION_TOGGLE_LOCK, 1))
            .addAction(0, "Stop", servicePending(ACTION_STOP, 2))
            .build()
    }

    @SuppressLint("MissingPermission")
    private fun refreshNotification() {
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification())
    }

    companion object {
        private const val CHANNEL_ID = "headset_control"
        private const val NOTIFICATION_ID = 4711
        private const val RECONNECT_DELAY_MS = 1500L
        private const val ACTION_STOP = "com.example.tapcounter.action.STOP"
        private const val ACTION_RETAKE = "com.example.tapcounter.action.RETAKE"
        private const val ACTION_TOGGLE_LOCK = "com.example.tapcounter.action.TOGGLE_LOCK"

        /** Call from a visible Activity (Android 12+ forbids starting foreground services from the background). */
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, HeadsetService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, HeadsetService::class.java))
        }

        fun retakeControl(context: Context) {
            context.startService(Intent(context, HeadsetService::class.java).setAction(ACTION_RETAKE))
        }
    }
}
