package com.example.cemo.ui.screens

import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cemo.data.model.BleStatus
import com.example.cemo.data.model.ScannedDevice
import com.example.cemo.viewmodel.WasteViewModel
import kotlinx.coroutines.delay

@Composable
fun BleDeviceScreen(viewModel: WasteViewModel = viewModel()) {
    val bleStatus      by viewModel.bleStatus.collectAsStateWithLifecycle()
    val scannedDevices by viewModel.scannedDevices.collectAsStateWithLifecycle()

    val isScanning   = bleStatus is BleStatus.Scanning
    val isConnected  = bleStatus is BleStatus.Connected
    val isConnecting = bleStatus is BleStatus.Connecting

    // Cooldown guard — prevents button spam causing race conditions
    var buttonCooldown by remember { mutableStateOf(false) }

    // Reset cooldown whenever status settles to a final state
    LaunchedEffect(bleStatus) {
        when (bleStatus) {
            is BleStatus.Connected,
            is BleStatus.Disconnected,
            is BleStatus.Error -> { delay(300); buttonCooldown = false }
            else -> Unit
        }
    }

    val rotation by rememberInfiniteTransition(label = "scan").animateFloat(
        initialValue  = 0f,
        targetValue   = 360f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing)),
        label         = "rotation"
    )

    Column(
        modifier            = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Header card ───────────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier            = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(
                            if (isConnected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector        = if (isConnected) Icons.Default.BluetoothConnected
                        else Icons.Default.Bluetooth,
                        contentDescription = null,
                        tint               = if (isConnected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier           = Modifier
                            .size(36.dp)
                            .then(if (isScanning) Modifier.rotate(rotation) else Modifier)
                    )
                }

                Text(
                    text = when (bleStatus) {
                        is BleStatus.Connected  -> "Connected to ${(bleStatus as BleStatus.Connected).deviceName}"
                        is BleStatus.Scanning   -> "Scanning for devices…"
                        is BleStatus.Connecting -> "Connecting…"
                        is BleStatus.Error      -> "Error: ${(bleStatus as BleStatus.Error).message}"
                        else                    -> "Tap Scan to find your ESP32"
                    },
                    fontWeight = FontWeight.SemiBold,
                    color      = MaterialTheme.colorScheme.onSurface,
                    fontSize   = 14.sp
                )

                // ── Action buttons ────────────────────────────────────────
                when {
                    isConnected -> {
                        OutlinedButton(
                            onClick  = {
                                if (!buttonCooldown) {
                                    buttonCooldown = true
                                    viewModel.disconnectBle()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled  = !buttonCooldown,
                            colors   = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Default.BluetoothDisabled, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Disconnect")
                        }

                    }

                    isConnecting -> {
                        Button(
                            onClick  = {},
                            enabled  = false,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CircularProgressIndicator(
                                modifier    = Modifier.size(16.dp),
                                color       = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Connecting…")
                        }
                    }

                    else -> {
                        Button(
                            onClick  = {
                                if (!buttonCooldown) {
                                    buttonCooldown = true
                                    if (isScanning) viewModel.stopScan()
                                    else viewModel.startScan()
                                }
                            },
                            enabled  = !buttonCooldown,
                            modifier = Modifier.fillMaxWidth(),
                            colors   = ButtonDefaults.buttonColors(
                                containerColor = if (isScanning)
                                    MaterialTheme.colorScheme.error
                                else
                                    MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                if (isScanning) Icons.Default.Stop else Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (isScanning) "Stop" else "Scan",
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }

                    }
                }
            }
        }

        // ── Device list ───────────────────────────────────────────────────
        when {
            isConnected -> {
                val deviceName = (bleStatus as BleStatus.Connected).deviceName
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors   = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier          = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.BluetoothConnected,
                            contentDescription = null,
                            tint     = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(
                                deviceName,
                                fontWeight = FontWeight.Bold,
                                color      = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                "Receiving sensor data",
                                fontSize = 12.sp,
                                color    = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
                            initialValue  = 0.4f,
                            targetValue   = 1f,
                            animationSpec = infiniteRepeatable(
                                tween(800, easing = FastOutSlowInEasing),
                                RepeatMode.Reverse
                            ),
                            label = "alpha"
                        )
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = pulse),
                                    CircleShape
                                )
                        )
                    }
                }
            }

            scannedDevices.isEmpty() && !isScanning -> EmptyState()

            else -> {
                if (scannedDevices.isNotEmpty()) {
                    Text(
                        "Nearby Devices (${scannedDevices.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize   = 14.sp,
                        color      = MaterialTheme.colorScheme.onBackground
                    )
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(scannedDevices, key = { it.address }) { device ->
                        DeviceCard(
                            device       = device,
                            isCemo       = device.name == "CEMO-ESP32",
                            isConnecting = isConnecting || buttonCooldown,
                            onConnect    = {
                                if (!buttonCooldown && !isConnecting) {
                                    buttonCooldown = true
                                    viewModel.connectToDevice(device.address)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

// ── Device list item ──────────────────────────────────────────────────────────

@Composable
private fun DeviceCard(
    device: ScannedDevice,
    isCemo: Boolean,
    isConnecting: Boolean,
    onConnect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(
            containerColor = if (isCemo)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier          = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector        = rssiIcon(device.rssi),
                contentDescription = null,
                tint               = if (isCemo) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier           = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    device.name,
                    fontWeight = FontWeight.Bold,
                    color      = if (isCemo) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "${device.address}  •  ${device.rssi} dBm",
                    fontSize = 11.sp,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isCemo) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "CEMO Sensor Node",
                        fontSize   = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Button(
                onClick        = onConnect,
                enabled        = !isConnecting,
                colors         = ButtonDefaults.buttonColors(
                    containerColor = if (isCemo) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor   = if (isCemo) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("Connect", fontSize = 12.sp)
            }
        }
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
private fun EmptyState() {
    Column(
        modifier            = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            Icons.Default.BluetoothSearching,
            contentDescription = null,
            tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(64.dp)
        )
        Text(
            "No devices found",
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp
        )
        Text(
            "Make sure your ESP32 is powered on",
            color    = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            fontSize = 12.sp
        )
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun rssiIcon(rssi: Int) = when {
    rssi >= -60 -> Icons.Default.SignalCellular4Bar
    rssi >= -75 -> Icons.Default.SignalCellularAlt
    else        -> Icons.Default.SignalCellularAlt1Bar
}
