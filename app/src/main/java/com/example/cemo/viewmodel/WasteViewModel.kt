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
import com.example.cemo.data.ble.BleForegroundService
import com.example.cemo.data.model.BleStatus
import com.example.cemo.data.model.DashboardState
import com.example.cemo.data.model.ScannedDevice
import com.example.cemo.data.model.WasteEntry
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class WasteViewModel(application: Application) : AndroidViewModel(application) {

    // ── Service binding ───────────────────────────────────────────────────────

    private var bleService: BleForegroundService? = null
    private var sensorJob: Job? = null

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

    private val _uiState = MutableStateFlow(DashboardState())
    val uiState: StateFlow<DashboardState> = _uiState.asStateFlow()

    // ── Init ──────────────────────────────────────────────────────────────────

    init {
        val ctx = application.applicationContext
        val intent = Intent(ctx, BleForegroundService::class.java)
        ContextCompat.startForegroundService(ctx, intent)
        ctx.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    // ── Observe BleManager flows ──────────────────────────────────────────────

    private fun observeBleData() {
        val manager = bleService?.bleManager ?: return

        // 1. Mirror status flow + update notification title
        viewModelScope.launch {
            manager.status.collect { status ->
                _bleStatus.value = status

                val title = when (status) {
                    is BleStatus.Connected    -> "Connected · ${status.deviceName}"
                    is BleStatus.Connecting   -> "Connecting…"
                    is BleStatus.Scanning     -> "Scanning for devices…"
                    is BleStatus.Disconnected -> "CEMO – Disconnected"
                    is BleStatus.BluetoothOff -> "CEMO – Bluetooth Off"  // ← ADDED
                    is BleStatus.Error        -> "Error: ${status.message}"
                }
                bleService?.updateStatus(title)
            }
        }

        // 2. Mirror scanned devices
        viewModelScope.launch {
            manager.scannedDevices.collect { _scannedDevices.value = it }
        }

        // 3. Process sensor data → dashboard state + notification body
        sensorJob = viewModelScope.launch {
            manager.sensorData.collect { data ->
                data ?: return@collect

                val previousWeight = _uiState.value.currentWeight
                val weightDelta    = data.weightKg - previousWeight

                // Update dashboard state
                _uiState.update { state ->
                    state.copy(
                        currentWeight = data.weightKg.coerceAtMost(state.maxCapacity),
                        temperature   = data.temperatureC,
                        humidity      = data.humidityPct,
                        voltage       = data.voltage,
                        current       = data.current
                    )
                }

                // Auto-log a waste entry when weight increases meaningfully
                if (weightDelta > 0.05) {
                    val entry = createEntry(
                        weight = weightDelta,
                        temp   = data.temperatureC,
                        hum    = data.humidityPct
                    )
                    _uiState.update { state ->
                        state.copy(
                            history      = (listOf(entry) + state.history).take(20),
                            totalMethane = state.totalMethane + entry.methanePotential
                        )
                    }
                }

                // Push live readings to the notification body
                bleService?.updateSensorValues(
                    "⚖ ${"%.2f".format(data.weightKg)} kg  " +
                            "🌡 ${"%.1f".format(data.temperatureC)}°C  " +
                            "💧 ${"%.1f".format(data.humidityPct)}%  "
                )
            }
        }
    }

    // ── BLE control ───────────────────────────────────────────────────────────

    fun startScan()                      = bleService?.bleManager?.startScan()
    fun stopScan()                       = bleService?.bleManager?.stopScan()
    fun connectToDevice(address: String) = bleService?.bleManager?.connectToAddress(address)
    fun disconnectBle()                  = bleService?.bleManager?.disconnect()
    fun connectBle()                     = bleService?.bleManager?.startScan()

    // ── Waste operations ──────────────────────────────────────────────────────

    private fun calculateMethane(weight: Double, temp: Double, humidity: Double): Double {
        val baseFactor       = 0.045
        val tempModifier     = (temp / 25.0).coerceIn(0.8, 1.2)
        val moistureModifier = (humidity / 50.0).coerceIn(0.9, 1.1)
        return weight * baseFactor * tempModifier * moistureModifier
    }

    private fun createEntry(
        weight: Double,
        timestamp: Long = System.currentTimeMillis(),
        temp: Double    = _uiState.value.temperature,
        hum: Double     = _uiState.value.humidity
    ) = WasteEntry(
        weightAdded      = weight,
        timestamp        = timestamp,
        methanePotential = calculateMethane(weight, temp, hum)
    )

    fun addWaste(weight: Double) {
        val newEntry = createEntry(weight)
        _uiState.update { state ->
            state.copy(
                history       = (listOf(newEntry) + state.history).take(20),
                currentWeight = (state.currentWeight + weight).coerceAtMost(state.maxCapacity),
                totalMethane  = state.totalMethane + newEntry.methanePotential
            )
        }
    }

    fun resetBin() {
        _uiState.update { it.copy(currentWeight = 0.0) }
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        sensorJob?.cancel()
        getApplication<Application>().unbindService(serviceConnection)
    }
}