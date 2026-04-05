package com.example.cemo.data.model

import java.util.UUID

data class WasteEntry(
    val id: String = UUID.randomUUID().toString(),
    val weightAdded: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val methanePotential: Double
)

data class DashboardState(
    val currentWeight: Double = 0.0,
    val temperature: Double = 0.0,
    val humidity: Double = 0.0,
    val voltage: Double = 0.0,
    val current: Double = 0.0,
    val totalMethane: Double = 0.0,
    val history: List<WasteEntry> = emptyList(),
    val maxCapacity: Double = 20.0
)

/** Represents a BLE device found during a scan. */
data class ScannedDevice(
    val name: String,
    val address: String,
    val rssi: Int          // signal strength in dBm
)

sealed class BleStatus {
    object Disconnected : BleStatus()
    object Scanning : BleStatus()
    object Connecting : BleStatus()
    object BluetoothOff : BleStatus()
    data class Connected(val deviceName: String) : BleStatus()
    data class Error(val message: String) : BleStatus()
}

/**
 * Parsed sensor payload from the ESP32.
 * Format: "WEIGHT:7.25,TEMP:28.3,HUM:61.0,VOLT:3.72,CURR:0.450"
 */
data class Esp32SensorData(
    val weightKg: Double,
    val temperatureC: Double,
    val humidityPct: Double,
    val voltage: Double,
    val current: Double
) {
    companion object {
        fun parse(raw: String): Esp32SensorData? = runCatching {
            val map = raw.split(",").associate {
                val (k, v) = it.split(":")
                k.trim() to v.trim().toDouble()
            }
            Esp32SensorData(
                weightKg     = map["WEIGHT"] ?: return@runCatching null,
                temperatureC = map["TEMP"]   ?: return@runCatching null,
                humidityPct  = map["HUM"]    ?: return@runCatching null,
                voltage      = map.getOrDefault("VOLT", 0.0),
                current      = map.getOrDefault("CURR", 0.0)
            )
        }.getOrNull()
    }
}