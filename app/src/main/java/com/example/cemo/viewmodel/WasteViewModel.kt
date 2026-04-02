package com.example.cemo.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cemo.data.ble.BleManager
import com.example.cemo.data.model.BleStatus
import com.example.cemo.data.model.DashboardState
import com.example.cemo.data.model.ScannedDevice
import com.example.cemo.data.model.WasteEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class WasteViewModel(application: Application) : AndroidViewModel(application) {

    // ── BLE ───────────────────────────────────────────────────────────────────
    val bleManager      = BleManager(application)
    val bleStatus:       StateFlow<BleStatus>            = bleManager.status
    val scannedDevices:  StateFlow<List<ScannedDevice>>  = bleManager.scannedDevices

    // ── Dashboard State ───────────────────────────────────────────────────────
    private val _uiState = MutableStateFlow(DashboardState())
    val uiState: StateFlow<DashboardState> = _uiState.asStateFlow()

    init {
        observeBleData()
    }

    // ── BLE data → state (realtime) ───────────────────────────────────────────

    private fun observeBleData() {
        viewModelScope.launch {
            bleManager.sensorData.collect { data ->

                // Always update all sensor readings immediately
                _uiState.update { state ->
                    state.copy(
                        currentWeight = data.weightKg.coerceAtMost(state.maxCapacity),
                        temperature   = data.temperatureC,
                        humidity      = data.humidityPct,
                        voltage       = data.voltage,
                        current       = data.current
                    )
                }

                // Only add a history entry when weight increases significantly
                val previousWeight = _uiState.value.currentWeight
                val weightDelta    = data.weightKg - previousWeight
                if (weightDelta > 0.05) {
                    val entry = createEntry(
                        weight = weightDelta,
                        temp   = data.temperatureC,
                        hum    = data.humidityPct
                    )
                    _uiState.update { state ->
                        val newHistory = (listOf(entry) + state.history).take(20)
                        state.copy(
                            history      = newHistory,
                            totalMethane = state.totalMethane + entry.methanePotential
                        )
                    }
                }
            }
        }
    }

    // ── BLE control ───────────────────────────────────────────────────────────

    fun startScan()                      = bleManager.startScan()
    fun stopScan()                       = bleManager.stopScan()
    fun connectToDevice(address: String) = bleManager.connectToAddress(address)
    fun disconnectBle()                  = bleManager.disconnect()
    fun connectBle()                     = bleManager.startScan()

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
            val newHistory = (listOf(newEntry) + state.history).take(20)
            state.copy(
                history       = newHistory,
                currentWeight = (state.currentWeight + weight).coerceAtMost(state.maxCapacity),
                totalMethane  = state.totalMethane + newEntry.methanePotential
            )
        }
    }

    fun resetBin() {
        _uiState.update { it.copy(currentWeight = 0.0) }
    }

    override fun onCleared() {
        super.onCleared()
        bleManager.disconnect()
    }
}