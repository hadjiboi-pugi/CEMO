package com.example.cemo

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.example.cemo.ui.navigation.AppNavigation
import com.example.cemo.ui.theme.CemoTheme
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

    private var appReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()

        splashScreen.setKeepOnScreenCondition { !appReady }

        super.onCreate(savedInstanceState)

        requestPermissions()

        lifecycleScope.launch {
            delay(2000L)
            appReady = true
        }

        setContent {
            CemoTheme {
                AppNavigation()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        AppState.isInForeground = true
    }

    override fun onStop() {
        super.onStop()
        AppState.isInForeground = false
    }

    // ── Permission requests ───────────────────────────────────────────────────

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

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