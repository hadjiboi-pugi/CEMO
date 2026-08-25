package com.example.cemo.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cemo.AppState
import com.example.cemo.data.ble.BleForegroundService
import com.example.cemo.data.model.BleStatus
import com.example.cemo.data.model.DashboardState
import com.example.cemo.data.model.ScannedDevice
import com.example.cemo.data.model.WasteEntry
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class WasteViewModel(application: Application) : AndroidViewModel(application) {

    // ── Database ──────────────────────────────────────────────────────────────

    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private val TAG = "WasteViewModel"

    // ── Service binding ───────────────────────────────────────────────────────
    private var bleService: BleForegroundService? = null
    private var sensorJob: Job? = null
    private var tareTimeoutJob: Job? = null
    private var lastNotificationUpdateMs: Long = 0
    private val NOTIFICATION_THROTTLE_MS = 250L
    // Fallback only — the tare loading is normally cleared by the ESP32's
    // TARE_DONE event. The firmware tare now takes ~1s, so keep this tight.
    private val TARE_TIMEOUT_MS = 4000L
    private var serviceBound: Boolean = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            bleService = (binder as BleForegroundService.LocalBinder).getService()
            observeBleData()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            sensorJob?.cancel()
            bleService = null
        }
    }

    // ── Exposed flows ─────────────────────────────────────────────────────────

    private val _bleStatus = MutableStateFlow<BleStatus>(BleStatus.Disconnected)
    val bleStatus: StateFlow<BleStatus> = _bleStatus.asStateFlow()

    private val _scannedDevices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val scannedDevices: StateFlow<List<ScannedDevice>> = _scannedDevices.asStateFlow()

    private val _uiState = MutableStateFlow(AppState.dashboardStateCache.value)
    val uiState: StateFlow<DashboardState> = _uiState.asStateFlow()

    // ── Init ──────────────────────────────────────────────────────────────────

    init {
        loadHistoryFromFirestore()
        
        // Keep the global cache updated for activity recreations
        viewModelScope.launch {
            _uiState.collect { AppState.dashboardStateCache.value = it }
        }

        viewModelScope.launch {
            AppState.blePermissionGranted.collect { granted ->
                if (granted) {
                    startBleService()
                } else {
                    _bleStatus.value = BleStatus.Error("Bluetooth permissions required")
                    stopBleService()
                }
            }
        }
    }

    private fun loadHistoryFromFirestore() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            android.util.Log.w(TAG, "Cannot load history: No user logged in")
            return
        }
        viewModelScope.launch {
            try {
                val snapshot = db.collection("users")
                    .document(uid)
                    .collection("waste_entries")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(50)
                    .get()
                    .await()

                val history = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(WasteEntry::class.java)
                }

                _uiState.update { state ->
                    state.copy(history = history)
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "FIRESTORE LOAD ERROR: ${e.message}", e)
            }
        }
    }

    private fun saveEntryToFirestore(entry: WasteEntry) {
        val uid = auth.currentUser?.uid
        if (uid == null) return
        viewModelScope.launch {
            try {
                db.collection("users")
                    .document(uid)
                    .collection("waste_entries")
                    .document(entry.id)
                    .set(entry)
                    .await()
            } catch (e: Exception) {
                android.util.Log.e(TAG, "FIRESTORE SAVE ERROR: ${e.message}", e)
            }
        }
    }

    private fun stopBleService() {
        val ctx = getApplication<Application>().applicationContext
        val intent = Intent(ctx, BleForegroundService::class.java).apply {
            action = BleForegroundService.ACTION_STOP
        }
        try {
            ctx.startService(intent)
        } catch (e: Exception) {}
        
        if (serviceBound) {
            try {
                ctx.unbindService(serviceConnection)
            } catch (e: Exception) {}
            serviceBound = false
            bleService = null
            sensorJob?.cancel()
        }
    }

    // ── Observe BleManager flows ──────────────────────────────────────────────

    private fun observeBleData() {
        val manager = bleService?.bleManager ?: return

        viewModelScope.launch {
            manager.status.collect { status ->
                _bleStatus.value = status

                if (status is BleStatus.Disconnected || status is BleStatus.Error || status is BleStatus.BluetoothOff) {
                    _uiState.update { it.copy(
                        currentWeight = 0.0,
                        rawWeight     = 0.0,
                        temperature   = 0.0,
                        humidity      = 0.0,
                        isTaring      = false
                    ) }
                }

                val title = when (status) {
                    is BleStatus.Connected    -> "Connected · ${status.deviceName}"
                    is BleStatus.Connecting   -> "Connecting…"
                    is BleStatus.Scanning     -> "Scanning for devices…"
                    is BleStatus.Disconnected -> "CEMO – Disconnected"
                    is BleStatus.BluetoothOff -> "CEMO – Bluetooth Off"
                    is BleStatus.Error        -> "Error: ${status.message}"
                }
                bleService?.updateStatus(title)
            }
        }

        viewModelScope.launch {
            manager.scannedDevices.collect { _scannedDevices.value = it }
        }

        sensorJob = viewModelScope.launch {
            manager.sensorData.collect { data ->
                data ?: return@collect
                
                val event = data.event?.uppercase()
                _uiState.update { state ->
                    val displayedWeight = (data.weightKg - state.tareOffset).coerceAtLeast(0.0)
                    val nextIsTaring = when (event) {
                        "TARE_DONE" -> false
                        else -> state.isTaring
                    }
                    state.copy(
                        rawWeight     = data.weightKg,
                        currentWeight = displayedWeight.coerceAtMost(state.maxCapacity),
                        temperature   = data.temperatureC,
                        humidity      = data.humidityPct,
                        isTaring      = nextIsTaring
                    )
                }
                if (event == "TARE_DONE") {
                    tareTimeoutJob?.cancel()
                }

                val currentState = _uiState.value
                val now = System.currentTimeMillis()
                if (now - lastNotificationUpdateMs >= NOTIFICATION_THROTTLE_MS) {
                    lastNotificationUpdateMs = now
                    bleService?.updateSensorValues(
                        "⚖ ${"%.2f".format(currentState.currentWeight)}kg " +
                        "🌡 ${"%.1f".format(currentState.temperature)}°C " +
                        "💧 ${"%.0f".format(currentState.humidity)}%"
                    )
                }
            }
        }
    }

    // ── BLE control ───────────────────────────────────────────────────────────

    private fun withBlePermission(action: () -> Unit) {
        val ctx = getApplication<Application>().applicationContext
        if (!AppState.hasBlePermissions(ctx)) {
            _bleStatus.value = BleStatus.Error("Bluetooth permissions required")
            return
        }
        action()
    }

    fun startScan() {
        withBlePermission {
            val manager = bleService?.bleManager
            if (manager != null) {
                manager.startScan()
            } else {
                startBleService()
            }
        }
    }
    fun stopScan()                       = bleService?.bleManager?.stopScan()
    fun connectToDevice(address: String) = withBlePermission { bleService?.bleManager?.connectToAddress(address) }
    fun disconnectBle()                  = bleService?.bleManager?.disconnect()
    fun connectBle()                     = startScan()

    // ── Waste operations ──────────────────────────────────────────────────────




    private fun createEntry(
        weight: Double,

        category: String = "Others",
        timestamp: Long = System.currentTimeMillis(),
        temp: Double    = _uiState.value.temperature,
        hum: Double     = _uiState.value.humidity
    ) = WasteEntry(
        weightAdded = weight,
        category    = category,
        timestamp   = timestamp,
        humidity    = hum,
        temperature = temp
    )

    fun addWaste(weight: Double, category: String = "Others") {
        val newEntry = createEntry(weight, category)
        saveEntryToFirestore(newEntry)
        _uiState.update { state ->
            state.copy(history = (listOf(newEntry) + state.history).take(50))
        }
    }

    fun deleteWasteEntry(entry: WasteEntry) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                db.collection("users")
                    .document(uid)
                    .collection("waste_entries")
                    .document(entry.id)
                    .delete()
                    .await()
                
                _uiState.update { state ->
                    state.copy(history = state.history.filter { it.id != entry.id })
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "FIRESTORE DELETE ERROR: ${e.message}", e)
            }
        }
    }

    /**
     * Local tare: Offsets the displayed weight in the app without affecting the hardware.
     */
    fun localTare() {
        _uiState.update { it.copy(
            tareOffset = it.rawWeight,
            currentWeight = 0.0 
        ) }
    }

    /**
     * Hardware tare: Sends "TARE" command to the ESP32 to zero the scale.
     */
    fun tareHardware() {
        _uiState.update { it.copy(isTaring = true) }
        tareTimeoutJob?.cancel()
        tareTimeoutJob = viewModelScope.launch {
            // Loading is normally cleared by the ESP32's TARE_DONE event (~1-2s).
            // This is only a safety net if that notify is dropped — keep it short
            // so the UI stays in sync with the (now fast) hardware tare.
            kotlinx.coroutines.delay(TARE_TIMEOUT_MS)
            _uiState.update { state ->
                if (state.isTaring) state.copy(isTaring = false) else state
            }
        }
        bleService?.bleManager?.writeCommand("TARE")
    }

    /**
     * Hardware reset: Sends "RESETCAL" command to the ESP32.
     */
    fun resetHardware() {
        bleService?.bleManager?.writeCommand("RESETCAL")
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            loadHistoryFromFirestore()
            if (_bleStatus.value is BleStatus.Disconnected || _bleStatus.value is BleStatus.Error) {
                startBleService()
            }
            kotlinx.coroutines.delay(1000)
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    private fun startBleService() {
        if (serviceBound) return
        val ctx = getApplication<Application>().applicationContext
        if (!AppState.hasBlePermissions(ctx)) return

        val intent = Intent(ctx, BleForegroundService::class.java)
        if (AppState.foregroundNotifEnabled.value) {
            ContextCompat.startForegroundService(ctx, intent)
        } else {
            ctx.startService(intent)
        }
        serviceBound = ctx.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onCleared() {
        super.onCleared()
        sensorJob?.cancel()
        if (serviceBound) {
            getApplication<Application>().applicationContext.unbindService(serviceConnection)
            serviceBound = false
        }
    }
}
