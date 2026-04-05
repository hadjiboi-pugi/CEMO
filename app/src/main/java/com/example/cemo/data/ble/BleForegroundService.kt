package com.example.cemo.data.ble

import android.app.*
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.cemo.MainActivity
import com.example.cemo.R

class BleForegroundService : Service() {

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
        bleManager = BleManager(applicationContext)
        bleManager.registerBluetoothReceiver()  // ← start watching BT state
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            bleManager.disconnect()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        bleManager.unregisterBluetoothReceiver()  // ← stop watching BT state
        bleManager.disconnect()
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
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification())
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
            .setSmallIcon(R.drawable.icon)
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