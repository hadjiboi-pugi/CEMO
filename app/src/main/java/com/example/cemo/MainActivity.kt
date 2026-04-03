package com.example.cemo

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.example.cemo.ui.navigation.AppNavigation
import com.example.cemo.ui.theme.CemoTheme

class MainActivity : ComponentActivity() {

    // ── Permission launchers ──────────────────────────────────────────────────

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // granted or denied — service notification shows if granted
    }

    private val requestBlePermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (!allGranted) {
            // Optional: show a snackbar/dialog telling user BLE won't work
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestPermissions()

        setContent {
            CemoTheme {
                AppNavigation()
            }
        }
    }

    // ── Permission requests ───────────────────────────────────────────────────

    private fun requestPermissions() {
        // Android 13+ requires POST_NOTIFICATIONS to show foreground service notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Android 12+ requires BLUETOOTH_SCAN + BLUETOOTH_CONNECT at runtime
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestBlePermissions.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
                )
            )
        }
    }
}