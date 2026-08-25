package com.example.cemo.ui.screens

import androidx.compose.runtime.collectAsState

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cemo.data.model.BleStatus
import com.example.cemo.ui.dialog.BluetoothOffDialog
import com.example.cemo.ui.components.*
import com.example.cemo.ui.theme.AllocationRed
import com.example.cemo.ui.theme.MetricBlue
import com.example.cemo.ui.theme.MetricRed
import com.example.cemo.ui.theme.PrimaryGreen
import com.example.cemo.viewmodel.WasteViewModel
import java.text.SimpleDateFormat
import java.util.*
import com.example.cemo.R

import com.example.cemo.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: WasteViewModel = viewModel(),
    innerPadding: PaddingValues = PaddingValues(0.dp),
    onSeeAllHistory: () -> Unit = {}
) {
    val state     by viewModel.uiState.collectAsStateWithLifecycle()
    val bleStatus by viewModel.bleStatus.collectAsStateWithLifecycle()
    val sdf       = remember<SimpleDateFormat> { SimpleDateFormat("MMM dd, HH:mm", Locale.US) }
    var showAddDialog by remember { mutableStateOf(false) }
    val themeMode by com.example.cemo.AppState.themeMode.collectAsState()
    val isSystemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        com.example.cemo.ThemeMode.LIGHT -> false
        com.example.cemo.ThemeMode.DARK -> true
        com.example.cemo.ThemeMode.SYSTEM -> isSystemDark
    }

    // ── Bluetooth off alert ───────────────────────────────────────────────
    val context = LocalContext.current
    var showBluetoothAlert by remember { mutableStateOf(false) }
    var entryToDelete by remember { mutableStateOf<com.example.cemo.data.model.WasteEntry?>(null) }

    fun isBluetoothOn(): Boolean {
        return try {
            val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            bm.adapter?.isEnabled == true
        } catch (e: SecurityException) {
            false
        }
    }

    LaunchedEffect(bleStatus) {
        if (bleStatus is BleStatus.BluetoothOff) {
            showBluetoothAlert = true
        }
    }

    if (showBluetoothAlert) {
        BluetoothOffDialog(
            onDismiss = { showBluetoothAlert = false },
            onEnable  = {
                showBluetoothAlert = false
                context.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }
        )
    }

    // Deletion Confirmation Dialog
    if (entryToDelete != null) {
        AlertDialog(
            onDismissRequest = { entryToDelete = null },
            title = { TranslatedText("Confirm Deletion", style = MaterialTheme.typography.titleLarge) },
            text = { TranslatedText("Are you sure you want to delete this waste entry? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        entryToDelete?.let { viewModel.deleteWasteEntry(it) }
                        entryToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    TranslatedText("Delete", style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(onClick = { entryToDelete = null }) {
                    TranslatedText("Cancel", style = MaterialTheme.typography.labelLarge)
                }
            },
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        )
    }

    if (showAddDialog) {
        AddWasteDialog(
            currentSensorWeight = state.currentWeight,
            currentHumidity = state.humidity,
            currentTemperature = state.temperature,
            onDismiss = { showAddDialog = false },
            onAdd     = { weight ->
                viewModel.addWaste(weight)
                showAddDialog = false
            }
        )
    }

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = { viewModel.refresh() },
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier       = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start  = Space.lg,
                    end    = Space.lg,
                    top    = innerPadding.calculateTopPadding() + Space.lg,
                    // Clear the extended FAB so the last card is never trapped under it.
                    bottom = innerPadding.calculateBottomPadding() + 96.dp
                ),
                verticalArrangement = Arrangement.spacedBy(Space.md)
            ) {
                // ── BLE Status Banner ─────────────────────────────────────────────
                item {
                    Box(modifier = Modifier.cemoSurface(shape = MaterialTheme.shapes.medium)) {
                        BleBanner(
                            status       = bleStatus,
                            onConnect    = {
                                if (!isBluetoothOn()) showBluetoothAlert = true
                                else viewModel.connectBle()
                            },
                            onDisconnect = { viewModel.disconnectBle() },
                            onStop       = { viewModel.stopScan() }
                        )
                    }
                }

                // ── Main Weight Card ──────────────────────────────────────────────
                item {
                    val progress = (state.currentWeight / state.maxCapacity).toFloat().coerceIn(0f, 1f)
                    val nearCapacity = progress > 0.85f
                    val loadColor = if (nearCapacity) {
                        MaterialTheme.dataPalette.critical
                    } else {
                        MaterialTheme.dataPalette.load
                    }

                    // The one raised element on the screen: current load is what the
                    // user opened the app to see, so it gets the visual lead and
                    // every other card stays flat beneath it.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .cemoSurface(SurfaceLevel.Raised, MaterialTheme.shapes.large)
                    ) {
                        Column(modifier = Modifier.padding(Space.card)) {
                            Text(
                                stringResource(R.string.current_load),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(Space.sm))

                            // Value and unit share a baseline: the number carries the
                            // weight, the unit stays quiet beside it.
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    String.format(Locale.US, "%.2f", state.currentWeight),
                                    style = MaterialTheme.typography.displayMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(Space.xs))
                                Text(
                                    "kg",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = Space.xs)
                                )
                            }

                            Spacer(modifier = Modifier.height(Space.lg))

                            // A linear track reads capacity far better than a ring:
                            // it maps "how full" onto length, which is the easiest
                            // visual channel to judge proportionally.
                            LinearProgressIndicator(
                                progress = { progress },
                                color = loadColor,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                strokeCap = StrokeCap.Round,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(Space.sm)
                            )

                            Spacer(modifier = Modifier.height(Space.sm))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                TranslatedText(
                                    if (nearCapacity) "Almost full" else "Capacity used",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (nearCapacity) loadColor
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "${(progress * 100).toInt()}% of ${
                                        String.format(Locale.US, "%.0f", state.maxCapacity)
                                    } kg",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // ── Environmental Metrics ─────────────────────────────────────────
                item { TranslatedSectionTitle("Live Metrics") }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                        TranslatedMetricSmallCard(
                            "Temperature",
                            "${String.format(Locale.US, "%.1f", state.temperature)}°C",
                            Icons.Default.Thermostat,
                            MaterialTheme.dataPalette.temperature,
                            Modifier.weight(1f)
                        )
                        TranslatedMetricSmallCard(
                            "Humidity",
                            "${String.format(Locale.US, "%.1f", state.humidity)}%",
                            Icons.Default.WaterDrop,
                            MaterialTheme.dataPalette.humidity,
                            Modifier.weight(1f)
                        )
                    }
                }

                // ── Waste Entry ──────────────────────────────────────────────────
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TranslatedSectionTitle("Waste Entry")
                        TextButton(onClick = onSeeAllHistory) {
                            TranslatedText(
                                "See All",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                items(state.history.take(3)) { entry ->
                    UnifiedWasteEntryCard(
                        entry = entry,
                        onDelete = { entryToDelete = entry }
                    )
                }
            }

            // ── Floating Action Button ────────────────────────────────────────────
            val isConnected = bleStatus is BleStatus.Connected
            
            ExtendedFloatingActionButton(
                onClick = { if (isConnected) showAddDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
                    .padding(bottom = innerPadding.calculateBottomPadding()),
                containerColor = if (isConnected) MaterialTheme.colorScheme.primary 
                                 else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                contentColor = if (isConnected) Color.White 
                               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                shape = MaterialTheme.shapes.large,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = if (isConnected) 12.dp else 0.dp
                )
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(12.dp))
                TranslatedText(
                    if (isConnected) "LOG WASTE" else "NOT CONNECTED", 
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

// ── BLE Banner ────────────────────────────────────────────────────────────────

private data class BleColors(
    val bg: Color,
    val fg: Color,
    val label: String,
    val showConnect: Boolean
)

@Composable
fun BleBanner(
    status: BleStatus,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onStop: () -> Unit
) {
    val c = when (status) {
        is BleStatus.BluetoothOff -> BleColors(
            bg = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
            fg = MaterialTheme.colorScheme.error,
            label = "Bluetooth is off",
            showConnect = true
        )
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
        color    = c.bg,
        shape    = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier
                .size(8.dp)
                .background(c.fg, CircleShape))
            Spacer(Modifier.width(8.dp))
            TranslatedText(
                c.label,
                color    = c.fg,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            when {
                c.showConnect -> {
                    TextButton(
                        onClick        = onConnect,
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        TranslatedText(
                            if (status is BleStatus.BluetoothOff) "Enable" else "Connect",
                            color      = c.fg,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                status is BleStatus.Scanning -> {
                    TextButton(
                        onClick        = onStop,
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        TranslatedText(
                            "Stop",
                            color      = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                status is BleStatus.Connected -> {
                    TextButton(
                        onClick        = onDisconnect,
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        TranslatedText(
                            "Disconnect",
                            color      = c.fg,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

// ── Add Waste Dialog ──────────────────────────────────────────────────────────

@Composable
fun AddWasteDialog(
    currentSensorWeight: Double,
    currentHumidity: Double,
    currentTemperature: Double,
    onDismiss: () -> Unit,
    onAdd: (Double) -> Unit
) {
    val canAdd = currentSensorWeight > 0.01 // Minimal threshold to prevent ghost logging

    AlertDialog(
        onDismissRequest  = onDismiss,
        containerColor    = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor  = MaterialTheme.colorScheme.onSurfaceVariant,
        title             = { 
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Scale, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                TranslatedText("Record New Waste")
            }
        },
        text              = {
            Column {
                TranslatedText(
                    if (canAdd) "Confirm the sensed weight."
                    else "Waiting for stable sensor data...",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(24.dp))

                // Display Sensed Data prominently instead of Input
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f), MaterialTheme.shapes.medium)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${String.format(Locale.US, "%.2f", currentSensorWeight)} kg",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        TranslatedText(
                            "SENSED WEIGHT",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.WaterDrop, null, tint = MetricBlue, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    TranslatedText(
                        "Humidity",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${String.format(Locale.US, "%.0f", currentHumidity)}%",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Thermostat, null, tint = MetricRed, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    TranslatedText(
                        "Temperature",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${String.format(Locale.US, "%.1f", currentTemperature)}°C",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(currentSensorWeight) },
                enabled = canAdd,
                colors  = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) { TranslatedText("Add", color = MaterialTheme.colorScheme.onPrimary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                TranslatedText("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}
