package com.example.cemo.data.model

/** Represents a BLE device found during a scan. */
data class ScannedDevice(
	val name: String,
	val address: String,
	val rssi: Int // signal strength in dBm
)
