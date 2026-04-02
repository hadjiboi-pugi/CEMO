package com.example.cemo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cemo.data.model.BleStatus
import com.example.cemo.ui.components.InputField
import com.example.cemo.ui.components.MetricSmallCard
import com.example.cemo.ui.components.SectionTitle
import com.example.cemo.ui.theme.AllocationRed
import com.example.cemo.ui.theme.MetricBlue
import com.example.cemo.ui.theme.MetricRed
import com.example.cemo.ui.theme.PrimaryGreen
import com.example.cemo.viewmodel.WasteViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DashboardScreen(viewModel: WasteViewModel = viewModel()) {
    val state     by viewModel.uiState.collectAsStateWithLifecycle()
    val bleStatus by viewModel.bleStatus.collectAsStateWithLifecycle()
    val sdf       = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.US) }
    var showAddDialog by remember { mutableStateOf(false) }

    if (showAddDialog) {
        AddWasteDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { weight ->
                viewModel.addWaste(weight)
                showAddDialog = false
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── BLE Status Banner ─────────────────────────────────────────────
        item {
            BleBanner(
                status       = bleStatus,
                onConnect    = { viewModel.connectBle() },
                onDisconnect = { viewModel.disconnectBle() }
            )
        }

        // ── Main Weight Card ──────────────────────────────────────────────
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(80.dp), contentAlignment = Alignment.Center) {
                            val progress = (state.currentWeight / state.maxCapacity).toFloat()
                            CircularProgressIndicator(
                                progress    = { progress },
                                color       = if (progress > 0.8f) AllocationRed else PrimaryGreen,
                                strokeWidth = 8.dp,
                                trackColor  = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Text(
                                "${(progress * 100).toInt()}%",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.width(24.dp))
                        Column {
                            Text(
                                "CURRENT LOAD",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "${String.format(Locale.US, "%.2f", state.currentWeight)} kg",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Methane Yield: ${String.format(Locale.US, "%.3f", state.totalMethane)} kg",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showAddDialog = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.onPrimary)
                            Spacer(Modifier.width(4.dp))
                            Text("Add Waste", color = MaterialTheme.colorScheme.onPrimary)
                        }
                        OutlinedButton(
                            onClick = { viewModel.resetBin() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) { Text("Empty Bin") }
                    }
                }
            }
        }

        // ── Environmental Metrics ─────────────────────────────────────────
        item { SectionTitle("Environmental Metrics") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MetricSmallCard(
                    "Temp",
                    "${String.format("%.1f", state.temperature)}°C",
                    Icons.Default.Thermostat,
                    MetricRed,
                    Modifier.weight(1f)
                )
                MetricSmallCard(
                    "Humidity",
                    "${String.format("%.1f", state.humidity)}%",
                    Icons.Default.WaterDrop,
                    MetricBlue,
                    Modifier.weight(1f)
                )
            }
        }

        // ── Recent Activity ───────────────────────────────────────────────
        item { SectionTitle("Recent Activity") }
        items(state.history) { entry ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            sdf.format(Date(entry.timestamp)),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Weight: ${entry.weightAdded}kg | CH₄: ${String.format("%.3f", entry.methanePotential)}kg",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

// ── BLE Banner ────────────────────────────────────────────────────────────────

private data class BleColors(
    val bg: Color, val fg: Color, val label: String, val showConnect: Boolean
)

@Composable
fun BleBanner(
    status: BleStatus,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    val c = when (status) {
        is BleStatus.Disconnected -> BleColors(
            bg = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
            fg = MaterialTheme.colorScheme.error,
            label = "ESP32 not connected",
            showConnect = true
        )
        is BleStatus.Scanning -> BleColors(
            bg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            fg = MaterialTheme.colorScheme.primary,
            label = "Scanning for ESP32…",
            showConnect = false
        )
        is BleStatus.Connecting -> BleColors(
            bg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            fg = MaterialTheme.colorScheme.primary,
            label = "Connecting…",
            showConnect = false
        )
        is BleStatus.Connected -> BleColors(
            bg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            fg = MaterialTheme.colorScheme.primary,
            label = "Connected: ${status.deviceName}",
            showConnect = false
        )
        is BleStatus.Error -> BleColors(
            bg = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
            fg = MaterialTheme.colorScheme.error,
            label = "BLE Error: ${status.message}",
            showConnect = true
        )
    }

    Surface(
        color = c.bg,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(8.dp).background(c.fg, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(c.label, color = c.fg, fontSize = 12.sp, modifier = Modifier.weight(1f))
            if (c.showConnect) {
                TextButton(onClick = onConnect, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("Connect", color = c.fg, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            } else if (status is BleStatus.Connected) {
                TextButton(onClick = onDisconnect, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("Disconnect", color = c.fg, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

// ── Add Waste Dialog ──────────────────────────────────────────────────────────

@Composable
fun AddWasteDialog(onDismiss: () -> Unit, onAdd: (Double) -> Unit) {
    var weightStr by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = { Text("Record New Waste") },
        text = {
            Column {
                Text("Enter the weight of organic waste added (kg)")
                Spacer(Modifier.height(8.dp))
                InputField("Weight", Icons.Default.Scale, isNumeric = true, value = weightStr) { weightStr = it }
            }
        },
        confirmButton = {
            Button(
                onClick = { weightStr.toDoubleOrNull()?.let { onAdd(it) } },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) { Text("Add", color = MaterialTheme.colorScheme.onPrimary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}