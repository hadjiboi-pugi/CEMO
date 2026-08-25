package com.example.cemo.data.model

import android.util.Log

/**
 * Parsed sensor payload from the ESP32.
 * Format: "WEIGHT:7.25,TEMP:28.3,HUM:61.0" with an optional trailing ",EVENT:TARE_DONE".
 * WEIGHT is in kilograms, TEMP in °C, HUM in %.
 */
data class Esp32SensorData(
	val weightKg: Double,
	val temperatureC: Double,
	val humidityPct: Double,
	val event: String? = null
) {
	companion object {
		fun parse(raw: String): Esp32SensorData? = runCatching {
			fun String.cleanValue(): Double =
				this.replace(Regex("[^0-9.-]"), "").toDoubleOrNull() ?: 0.0

			val map = raw.split(",").associate {
				val parts = it.split(":")
				if (parts.size != 2) return@associate "" to ""
				val key = parts[0].trim().uppercase()
				val value = parts[1].trim()
				key to value
			}.filterKeys { it.isNotEmpty() }

			val weightKg = (map["WEIGHT"] ?: map["LOAD"] ?: map["W"] ?: map["L"])?.cleanValue() ?: 0.0
			val tempC = (map["TEMP"] ?: map["T"])?.cleanValue() ?: 0.0
			val humPct = (map["HUM"] ?: map["H"])?.cleanValue() ?: 0.0
			val event = map["EVENT"] ?: map["EVT"] ?: map["CMD"]

			Esp32SensorData(
				weightKg = weightKg,
				temperatureC = tempC,
				humidityPct = humPct,
				event = event
			)
		}.onFailure {
			Log.e("BLE", "Parse error: ${it.message} for raw: $raw")
		}.getOrNull()
	}
}
