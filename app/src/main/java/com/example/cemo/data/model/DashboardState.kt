package com.example.cemo.data.model

data class DashboardState(
	val currentWeight: Double = 0.0,
	val rawWeight: Double = 0.0,
	val tareOffset: Double = 0.0,
	val temperature: Double = 0.0,
	val humidity: Double = 0.0,
	val history: List<WasteEntry> = emptyList(),
	val maxCapacity: Double = 20.0,
    val isRefreshing: Boolean = false,
    val isTaring: Boolean = false
)
