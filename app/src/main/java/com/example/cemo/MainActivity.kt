package com.example.cemo

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.cemo.ui.ProvideLocalizedResources
import com.example.cemo.ui.navigation.AppNavigation
import com.example.cemo.ui.theme.CemoTheme
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import androidx.activity.enableEdgeToEdge

class MainActivity : BaseActivity() {

    // ── Permission launchers ──────────────────────────────────────────────────

    private var permissionsRequestedThisSession = false
    private var pendingNotificationRequest = false

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // granted or denied — service notification shows if granted
    }

    private val requestBlePermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        AppState.setBlePermissionGranted(allGranted)
        if (pendingNotificationRequest && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pendingNotificationRequest = false
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (!allGranted) {
            // Optional: show a snackbar/dialog telling user BLE won't work
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        // Only show the animated splash on a genuine cold start. On a warm
        // recreation (config change, process restore) installSplashScreen
        // re-displays the splash window (bg_dark is near-black), which reads as a
        // black-screen flash. On recreation, skip the splash and apply the normal
        // theme directly so the window background is the real app background.
        // (Language changes no longer recreate the Activity — see
        // ProvideLocalizedResources — so they don't hit this path at all.)
        if (savedInstanceState == null) {
            installSplashScreen()
        } else {
            setTheme(R.style.Theme_CEMO)
        }
        enableEdgeToEdge()

        super.onCreate(savedInstanceState)
        
        AppState.setBlePermissionGranted(AppState.hasBlePermissions(this))

        requestPermissionsIfNeeded()

        setContent {
            val language by AppState.selectedLanguage.collectAsState()
            ProvideLocalizedResources(language) {
                CemoTheme {
                    AppNavigation()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        AppState.isInForeground = true
        AppState.setBlePermissionGranted(AppState.hasBlePermissions(this))
        requestPermissionsIfNeeded()
    }

    override fun onStop() {
        super.onStop()
        AppState.isInForeground = false
    }

    // ── Permission requests ───────────────────────────────────────────────────

    private fun requestPermissionsIfNeeded() {
        if (permissionsRequestedThisSession) return
        var requested = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pendingNotificationRequest =
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (!AppState.hasBlePermissions(this)) {
            requestBlePermissions.launch(AppState.requiredBlePermissions())
            requested = true
        } else if (pendingNotificationRequest) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            pendingNotificationRequest = false
            requested = true
        }

        if (requested) {
            permissionsRequestedThisSession = true
        }
    }
}
