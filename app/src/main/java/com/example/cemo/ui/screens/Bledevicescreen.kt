package com.example.cemo.ui.screens

import androidx.compose.runtime.collectAsState

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cemo.data.model.BleStatus
import com.example.cemo.data.model.ScannedDevice
import com.example.cemo.ui.dialog.BluetoothOffDialog
import com.example.cemo.ui.findActivity
import com.example.cemo.ui.theme.PrimaryGreen
import com.example.cemo.viewmodel.WasteViewModel
import kotlinx.coroutines.delay

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Brush
import com.example.cemo.ui.components.*
import com.example.cemo.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BleDeviceScreen(
    viewModel: WasteViewModel = viewModel(),
    innerPadding: PaddingValues = PaddingValues(0.dp)
) {
    val bleStatus      by viewModel.bleStatus.collectAsStateWithLifecycle()
    val scannedDevices by viewModel.scannedDevices.collectAsStateWithLifecycle()
    val uiState        by viewModel.uiState.collectAsStateWithLifecycle()

    val isScanning     = bleStatus is BleStatus.Scanning
    val isConnected    = bleStatus is BleStatus.Connected
    val isConnecting   = bleStatus is BleStatus.Connecting
    val isBluetoothOff = bleStatus is BleStatus.BluetoothOff
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
    var buttonCooldown by remember { mutableStateOf(false) }
    var pendingConnectAddress by remember { mutableStateOf<String?>(null) }
    var pendingStartScan by remember { mutableStateOf(false) }
    var permissionRequestAttempted by remember { mutableStateOf(false) }
    var showPermissionSettingsDialog by remember { mutableStateOf(false) }
    fun shouldShowBleRationale(): Boolean {
        val activity = context.findActivity() ?: return true
        return com.example.cemo.AppState.requiredBlePermissions().any { permission ->
            ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        }
    }
    val requestBlePermissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        com.example.cemo.AppState.setBlePermissionGranted(allGranted)
        val address = pendingConnectAddress
        val startScan = pendingStartScan
        pendingConnectAddress = null
        pendingStartScan = false
        if (allGranted) {
            when {
                address != null -> {
                    buttonCooldown = true
                    viewModel.connectToDevice(address)
                }
                startScan -> {
                    buttonCooldown = true
                    viewModel.startScan()
                }
            }
        } else if (permissionRequestAttempted && !shouldShowBleRationale()) {
            showPermissionSettingsDialog = true
        }
    }

    fun isBluetoothOn(): Boolean {
        return try {
            val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            bm.adapter?.isEnabled == true
        } catch (e: SecurityException) {
            false
        }
    }

    fun requestBlePermissionOrOpenSettings() {
        val canRequest = !permissionRequestAttempted || shouldShowBleRationale()
        if (canRequest) {
            permissionRequestAttempted = true
            requestBlePermissions.launch(com.example.cemo.AppState.requiredBlePermissions())
        } else {
            showPermissionSettingsDialog = true
        }
    }

    fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        )
        context.startActivity(intent)
    }

    // Auto-show dialog when BT turns off while on this screen
    LaunchedEffect(bleStatus) {
        if (bleStatus is BleStatus.BluetoothOff) {
            showBluetoothAlert = true
        }
    }

    if (showPermissionSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionSettingsDialog = false },
            title = { TranslatedText("Permission required") },
            text = { TranslatedText("Enable Nearby devices/Location permission in settings to use Bluetooth features.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPermissionSettingsDialog = false
                        openAppSettings()
                    }
                ) {
                    TranslatedText("Open Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionSettingsDialog = false }) {
                    TranslatedText("Cancel")
                }
            }
        )
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
    // ─────────────────────────────────────────────────────────────────────

    LaunchedEffect(bleStatus) {
        when (bleStatus) {
            is BleStatus.Scanning,
            is BleStatus.Connected,
            is BleStatus.Disconnected,
            is BleStatus.BluetoothOff,
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

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = { viewModel.refresh() },
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier            = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(top = innerPadding.calculateTopPadding() + 16.dp)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── Header card ───────────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .cemoSurface(shape = MaterialTheme.shapes.extraLarge)
                ) {
                    Column(
                        modifier            = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .background(
                                    color = when {
                                        isConnected    -> MaterialTheme.colorScheme.primaryContainer
                                        isBluetoothOff -> MaterialTheme.colorScheme.errorContainer
                                        else           -> MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    shape = CircleShape
                                )
                                .border(
                                    width = Size.hairline,
                                    color = when {
                                        isConnected    -> MaterialTheme.colorScheme.primary
                                        isBluetoothOff -> MaterialTheme.colorScheme.error
                                        else           -> MaterialTheme.colorScheme.outline
                                    },
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when {
                                    isConnected    -> Icons.Default.BluetoothConnected
                                    isBluetoothOff -> Icons.Default.BluetoothDisabled
                                    else           -> Icons.Default.Bluetooth
                                },
                                contentDescription = null,
                                tint = when {
                                    isConnected    -> MaterialTheme.colorScheme.primary
                                    isBluetoothOff -> MaterialTheme.colorScheme.error
                                    else           -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .then(if (isScanning) Modifier.rotate(rotation) else Modifier)
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            TranslatedText(
                                text = when (bleStatus) {
                                    is BleStatus.Connected    -> "Node Connected"
                                    is BleStatus.Scanning     -> "Searching for nodes..."
                                    is BleStatus.Connecting   -> "Connecting..."
                                    is BleStatus.BluetoothOff -> "Bluetooth Required"
                                    is BleStatus.Error        -> "Connection Failed"
                                    else                      -> "CEMO Network"
                                },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isBluetoothOff) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurface
                            )
                            TranslatedText(
                                text = when (bleStatus) {
                                    is BleStatus.Connected    -> (bleStatus as BleStatus.Connected).deviceName
                                    is BleStatus.Scanning     -> "Scanning nearby"
                                    is BleStatus.Connecting   -> "Wait a moment"
                                    is BleStatus.BluetoothOff -> "Please enable Bluetooth"
                                    is BleStatus.Error        -> (bleStatus as BleStatus.Error).message
                                    else                      -> "Connect to a sensor node"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // ── Action buttons ────────────────────────────────────────
                        val buttonModifier = Modifier.fillMaxWidth().height(56.dp)
                        val buttonShape = MaterialTheme.shapes.medium

                        when {
                            isBluetoothOff -> {
                                Button(
                                    onClick  = { showBluetoothAlert = true },
                                    modifier = buttonModifier,
                                    shape    = buttonShape,
                                    colors   = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(Icons.Default.BluetoothDisabled, null)
                                    Spacer(Modifier.width(12.dp))
                                    TranslatedText("Enable Bluetooth", fontWeight = FontWeight.ExtraBold)
                                }
                            }

                            isConnected -> {
                                OutlinedButton(
                                    onClick  = {
                                        if (!buttonCooldown) {
                                            buttonCooldown = true
                                            viewModel.disconnectBle()
                                        }
                                    },
                                    modifier = buttonModifier,
                                    shape    = buttonShape,
                                    enabled  = !buttonCooldown,
                                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(Icons.Default.BluetoothDisabled, null)
                                    Spacer(Modifier.width(12.dp))
                                    TranslatedText("Disconnect Node", fontWeight = FontWeight.ExtraBold)
                                }
                            }

                            isConnecting -> {
                                Button(
                                    onClick  = { viewModel.disconnectBle() },
                                    modifier = buttonModifier,
                                    shape    = buttonShape,
                                    colors   = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                                ) {
                                    CircularProgressIndicator(
                                        modifier    = Modifier.size(24.dp),
                                        color       = Color.White,
                                        strokeWidth = 3.dp
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    TranslatedText("Connecting...", fontWeight = FontWeight.ExtraBold)
                                }
                            }

                            else -> {
                                Button(
                                    onClick  = {
                                        if (!buttonCooldown) {
                                            if (isScanning) {
                                                viewModel.stopScan()
                                            } else if (!com.example.cemo.AppState.hasBlePermissions(context)) {
                                                pendingStartScan = true
                                                requestBlePermissionOrOpenSettings()
                                            } else if (!isBluetoothOn()) {
                                                showBluetoothAlert = true
                                            } else {
                                                buttonCooldown = true
                                                viewModel.startScan()
                                            }
                                        }
                                    },
                                    enabled  = !buttonCooldown,
                                    modifier = buttonModifier,
                                    shape    = buttonShape,
                                    colors   = ButtonDefaults.buttonColors(
                                        containerColor = if (isScanning)
                                            MaterialTheme.colorScheme.error
                                        else
                                            MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Icon(
                                        if (isScanning) Icons.Default.Stop else Icons.Default.Search,
                                        null
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    TranslatedText(
                                        if (isScanning) "Stop Scanning" else "Start Scan",
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Device list ───────────────────────────────────────────────────
                when {
                    isBluetoothOff -> { /* nothing shown */ }

                    isConnected -> {
                        val deviceName = (bleStatus as BleStatus.Connected).deviceName
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .cemoSurface(shape = MaterialTheme.shapes.large)
                        ) {
                            Row(
                                modifier          = Modifier.padding(24.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    modifier = Modifier.size(56.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.BluetoothConnected,
                                            null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.width(20.dp))
                                Column {
                                    TranslatedText(
                                        deviceName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color      = MaterialTheme.colorScheme.onSurface
                                    )
                                    TranslatedText(
                                        "Connected • Receiving Data",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color    = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    scannedDevices.isEmpty() && !isScanning -> EmptyState()

                    else -> {
                        if (scannedDevices.isNotEmpty()) {
                            Row(modifier = Modifier.padding(start = 4.dp, top = 8.dp)) {
                                TranslatedText(
                                    "FOUND DEVICES",
                                    style = MaterialTheme.typography.bodySmall,
                                    color      = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    " (${scannedDevices.size})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color      = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding() + 16.dp)
                        ) {
                            items(scannedDevices, key = { it.address }) { device ->
                                DeviceCard(
                                    device       = device,
                                    isCemo       = true,
                                    isConnecting = isConnecting || buttonCooldown,
                                    onConnect    = {
                                        if (!buttonCooldown && !isConnecting) {
                                            if (!com.example.cemo.AppState.hasBlePermissions(context)) {
                                                pendingConnectAddress = device.address
                                                requestBlePermissionOrOpenSettings()
                                            } else if (!isBluetoothOn()) {
                                                showBluetoothAlert = true
                                            } else {
                                                buttonCooldown = true
                                                viewModel.connectToDevice(device.address)
                                            }
                                        }
                                    }
                                )
                            }
                        }
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .cemoSurface(shape = MaterialTheme.shapes.medium)
    ) {
        Row(
            modifier          = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector        = rssiIcon(device.rssi),
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.primary,
                modifier           = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                TranslatedText(
                    device.name,
                    fontWeight = FontWeight.ExtraBold,
                    color      = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "${device.address} • ${device.rssi} dBm",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick        = onConnect,
                enabled        = !isConnecting,
                shape          = MaterialTheme.shapes.extraSmall,
                colors         = ButtonDefaults.buttonColors(
                    containerColor = PrimaryGreen
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                TranslatedText("Connect", style = MaterialTheme.typography.bodySmall)
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
        TranslatedText(
            "No devices found",
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        TranslatedText(
            "1. Make sure your ESP32 is powered on\n2. Check if Location services are enabled\n3. Ensure your ESP32 is advertising",
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun rssiIcon(rssi: Int) = when {
    rssi >= -60 -> Icons.Default.SignalCellular4Bar
    rssi >= -75 -> Icons.Default.SignalCellularAlt
    else        -> Icons.Default.SignalCellularAlt1Bar
}
