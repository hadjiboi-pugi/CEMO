package com.example.cemo.data.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.cemo.AppState
import com.example.cemo.MainActivity
import com.example.cemo.R

object NotificationHelper {

    private const val TAG = "NotificationHelper"
    const val WEIGHT_ALERT_CHANNEL_ID = "cemo_weight_threshold_channel"
    const val WEIGHT_ALERT_NOTIFICATION_ID = 2001

    private const val ALERT_COOLDOWN_MS = 60_000L // 1 minute cooldown for re-alerting if continuously over threshold
    private var hasAlertedThreshold = false
    private var lastAlertTimeMs: Long = 0L

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return

            // High-priority Weight Threshold Alert Channel
            val alertChannel = NotificationChannel(
                WEIGHT_ALERT_CHANNEL_ID,
                context.getString(R.string.weight_threshold_alert),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.weight_threshold_alert_desc)
                enableVibration(true)
                enableLights(true)
                setShowBadge(true)
            }
            manager.createNotificationChannel(alertChannel)
        }
    }

    /**
     * Checks if current weight reaches or exceeds the user-configured maximum threshold.
     * Uses hysteresis and cooldown to avoid notification spam.
     */
    fun checkWeightThreshold(context: Context, currentWeight: Double) {
        val threshold = AppState.maxWeightThreshold.value
        val isAlertEnabled = AppState.weightAlertEnabled.value

        if (!isAlertEnabled || threshold <= 0.0) return

        val now = System.currentTimeMillis()

        if (currentWeight >= threshold) {
            val shouldAlert = !hasAlertedThreshold || (now - lastAlertTimeMs > ALERT_COOLDOWN_MS)
            if (shouldAlert) {
                hasAlertedThreshold = true
                lastAlertTimeMs = now
                Log.d(TAG, "Weight threshold reached: current=$currentWeight kg >= threshold=$threshold kg. Sending notification.")
                showWeightThresholdAlert(context, currentWeight, threshold)
            }
        } else if (currentWeight < (threshold * 0.95)) {
            // Reset latch when weight drops below 95% of threshold
            if (hasAlertedThreshold) {
                Log.d(TAG, "Weight dropped below threshold: current=$currentWeight kg < threshold=$threshold kg. Resetting alert state.")
                hasAlertedThreshold = false
            }
        }
    }

    /**
     * Resets the alert state (e.g., when the user changes threshold in Settings).
     */
    fun resetAlertState() {
        hasAlertedThreshold = false
        lastAlertTimeMs = 0L
    }

    fun showWeightThresholdAlert(context: Context, currentWeight: Double, threshold: Double) {
        // Android 13+ permission check
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "Cannot show notification: POST_NOTIFICATIONS permission not granted.")
                return
            }
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            WEIGHT_ALERT_NOTIFICATION_ID,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = context.getString(R.string.threshold_reached_title)
        val shortBody = context.getString(
            R.string.threshold_reached_body,
            currentWeight,
            threshold
        )
        val bigBody = context.getString(
            R.string.threshold_reached_big_body,
            currentWeight,
            threshold
        )

        val notification = NotificationCompat.Builder(context, WEIGHT_ALERT_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(shortBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigBody))
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(WEIGHT_ALERT_NOTIFICATION_ID, notification)
    }
}
