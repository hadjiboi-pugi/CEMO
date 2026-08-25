package com.example.cemo.data.model

import java.util.UUID

data class WasteEntry(
    val id: String = UUID.randomUUID().toString(),
    val weightAdded: Double = 0.0,
    val category: String = "Others",
    val timestamp: Long = System.currentTimeMillis(),
    val humidity: Double = 0.0,
    val temperature: Double = 0.0
)

