package com.example.cemo

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode {
    LIGHT, DARK, SYSTEM
}

object AppState {
    var isInForeground: Boolean = false

    private const val PREFS_NAME = "cemo_prefs"
    private const val THEME_KEY = "theme_mode"
    private const val LANG_KEY = "selected_language"
    private const val NOTIF_KEY = "foreground_notif_enabled"
    private const val MAX_WEIGHT_KEY = "max_weight_threshold"
    private const val WEIGHT_ALERT_KEY = "weight_alert_enabled"

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode

    private val _selectedLanguage = MutableStateFlow("English")
    val selectedLanguage: StateFlow<String> = _selectedLanguage

    private val _foregroundNotifEnabled = MutableStateFlow(true)
    val foregroundNotifEnabled: StateFlow<Boolean> = _foregroundNotifEnabled

    private val _maxWeightThreshold = MutableStateFlow(20.0)
    val maxWeightThreshold: StateFlow<Double> = _maxWeightThreshold

    private val _weightAlertEnabled = MutableStateFlow(true)
    val weightAlertEnabled: StateFlow<Boolean> = _weightAlertEnabled

    private val _isDemoMode = MutableStateFlow(false)
    val isDemoMode: StateFlow<Boolean> = _isDemoMode

    private val _blePermissionGranted = MutableStateFlow(false)
    val blePermissionGranted: StateFlow<Boolean> = _blePermissionGranted

    // Cache for DashboardState to persist through activity recreations (e.g. language switch)
    val dashboardStateCache = MutableStateFlow(com.example.cemo.data.model.DashboardState())

    // Last known Firestore profile. Kept here so re-entering Profile renders the
    // user immediately instead of blanking while the fetch round-trips; the write
    // paths in GoogleAuthUiClient keep it current.
    val cachedUser = MutableStateFlow<com.example.cemo.data.model.User?>(null)

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        val savedTheme = prefs.getString(THEME_KEY, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        _themeMode.value = try {
            ThemeMode.valueOf(savedTheme)
        } catch (e: Exception) {
            ThemeMode.SYSTEM
        }

        val lang = prefs.getString(LANG_KEY, "English") ?: "English"
        _selectedLanguage.value = lang
        _foregroundNotifEnabled.value = prefs.getBoolean(NOTIF_KEY, true)
        _maxWeightThreshold.value = prefs.getFloat(MAX_WEIGHT_KEY, 20.0f).toDouble().coerceIn(0.5, 50.0)
        _weightAlertEnabled.value = prefs.getBoolean(WEIGHT_ALERT_KEY, true)

        dashboardStateCache.value = dashboardStateCache.value.copy(maxCapacity = _maxWeightThreshold.value)

        // Initializing permission state
        _blePermissionGranted.value = hasBlePermissions(context)

        // Language is applied in-place via ProvideLocalizedResources (Compose),
        // not through AppCompatDelegate — that recreates the Activity and causes a
        // black-screen flicker on every toggle.
    }

    fun setThemeMode(context: Context, mode: ThemeMode) {
        _themeMode.value = mode
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(THEME_KEY, mode.name)
            .apply()
    }

    fun setSelectedLanguage(context: Context, lang: String) {
        if (_selectedLanguage.value == lang) return
        
        _selectedLanguage.value = lang
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(LANG_KEY, lang)
            .apply()
        // The selectedLanguage flow drives ProvideLocalizedResources, which swaps
        // the locale in-place — no Activity recreation, no flicker.
    }

    fun setForegroundNotifEnabled(context: Context, enabled: Boolean) {
        _foregroundNotifEnabled.value = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(NOTIF_KEY, enabled)
            .apply()
    }

    fun setMaxWeightThreshold(context: Context, threshold: Double) {
        val clamped = threshold.coerceIn(0.5, 50.0)
        _maxWeightThreshold.value = clamped
        dashboardStateCache.value = dashboardStateCache.value.copy(maxCapacity = clamped)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat(MAX_WEIGHT_KEY, clamped.toFloat())
            .apply()
    }

    fun setWeightAlertEnabled(context: Context, enabled: Boolean) {
        _weightAlertEnabled.value = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(WEIGHT_ALERT_KEY, enabled)
            .apply()
    }

    fun setDemoMode(enabled: Boolean) {
        _isDemoMode.value = enabled
    }

    fun toggleDemoMode(): Boolean {
        val next = !_isDemoMode.value
        _isDemoMode.value = next
        return next
    }

    fun setBlePermissionGranted(granted: Boolean) {
        _blePermissionGranted.value = granted
    }

    fun hasBlePermissions(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val scanGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
            val connectGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
            val advertiseGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_ADVERTISE
            ) == PackageManager.PERMISSION_GRANTED
            scanGranted && connectGranted && advertiseGranted
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun requiredBlePermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
}
