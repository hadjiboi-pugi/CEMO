package com.example.cemo.data.ble

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.cemo.AppState
import com.example.cemo.MainActivity
import com.example.cemo.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class BleForegroundService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    // ───────────────────────── BINDER ─────────────────────────

    inner class LocalBinder : Binder() {
        fun getService(): BleForegroundService = this@BleForegroundService
    }

    private val binder = LocalBinder()
    override fun onBind(intent: Intent): IBinder = binder

    // ───────────────────────── BLE MANAGER ─────────────────────────

    lateinit var bleManager: BleManager
        private set

    // ───────────────────────── NOTIFICATION STATE ─────────────────────────

    private var notificationTitle: String = "CEMO – ESP32"
    private var notificationBody:  String = "BLE service running…"

    // ───────────────────────── LIFECYCLE ─────────────────────────

    override fun onCreate() {
        super.onCreate()
        Log.d("BleForegroundService", "onCreate")
        bleManager = BleManager(applicationContext)
        bleManager.registerBluetoothReceiver()  // ← start watching BT state
        createNotificationChannel()

        // Observe the foreground notification setting
        serviceScope.launch {
            AppState.foregroundNotifEnabled.collectLatest { enabled ->
                if (enabled) {
                    startAsForeground()
                } else {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                }
            }
        }
    }

    private fun startAsForeground() {
        val hasPermissions = AppState.hasBlePermissions(this)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                if (hasPermissions) {
                    startForeground(
                        NOTIFICATION_ID,
                        buildNotification(),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                    )
                } else {
                    Log.e("BleForegroundService", "Missing permissions for FGS type connectedDevice")
                    startForeground(NOTIFICATION_ID, buildNotification())
                }
            } else {
                startForeground(NOTIFICATION_ID, buildNotification())
            }
        } catch (e: Exception) {
            Log.e("BleForegroundService", "Error in startAsForeground", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            bleManager.disconnect()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        // Return START_NOT_STICKY to prevent the system from restarting the service
        // into a crash loop if permissions are revoked while it's running.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceJob.cancel()
        bleManager.unregisterBluetoothReceiver()  // ← stop watching BT state
        bleManager.disconnect()  // ← cleanup includes bonding state
        super.onDestroy()
    }

    // ───────────────────────── NOTIFICATION API ─────────────────────────

    fun updateStatus(status: String) {
        notificationTitle = status
        refresh()
    }

    fun updateSensorValues(body: String) {
        notificationBody = body
        refresh()
    }

    private fun refresh() {
        if (AppState.foregroundNotifEnabled.value) {
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, buildNotification())
        }
    }

    // ───────────────────────── NOTIFICATION BUILD ─────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "BLE Connection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Live sensor readings from the ESP32"
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopSvc = PendingIntent.getService(
            this, 1,
            Intent(this, BleForegroundService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(notificationTitle)
            .setContentText(notificationBody)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(notificationBody)
            )
            .setContentIntent(openApp)
            .addAction(0, "Disconnect", stopSvc)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    // ───────────────────────── COMPANION ─────────────────────────

    companion object {
        const val CHANNEL_ID      = "ble_foreground_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP     = "com.example.cemo.BLE_STOP"
    }
}
