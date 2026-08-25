package com.example.cemo.data.model

sealed class BleStatus {
	object Disconnected : BleStatus()
	object Scanning : BleStatus()
	object Connecting : BleStatus()
	object BluetoothOff : BleStatus()
	data class Connected(val deviceName: String) : BleStatus()
	data class Error(val message: String) : BleStatus()
}
