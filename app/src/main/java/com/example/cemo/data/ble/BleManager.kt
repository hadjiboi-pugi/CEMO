package com.example.cemo.data.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.*
import android.os.ParcelUuid
import android.util.Log
import com.example.cemo.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID
import com.example.cemo.AppState

@SuppressLint("MissingPermission")
class BleManager(private val context: Context) {

    companion object {
        val SERVICE_UUID: UUID =
            UUID.fromString("12345678-1234-1234-1234-123456789012")

        val CHAR_UUID: UUID =
            UUID.fromString("abcd1234-ab12-ab12-ab12-abcdef123456")

        private val CCCD_UUID: UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        private const val TAG = "BLE"
    }

    private val adapter =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private var scanner: BluetoothLeScanner? = null
    private var gatt: BluetoothGatt? = null

    private val deviceList = mutableListOf<ScannedDevice>()
    private var isConnecting = false

    // ───────────────────────── FLOWS ─────────────────────────

    private val _status = MutableStateFlow<BleStatus>(
        if (adapter?.isEnabled == true) BleStatus.Disconnected else BleStatus.BluetoothOff
    )
    val status: StateFlow<BleStatus> = _status.asStateFlow()

    private val _sensorData = MutableStateFlow<Esp32SensorData?>(null)
    val sensorData: StateFlow<Esp32SensorData?> = _sensorData.asStateFlow()

    private val _scannedDevices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val scannedDevices: StateFlow<List<ScannedDevice>> = _scannedDevices.asStateFlow()

    // ───────────────────────── BLUETOOTH STATE RECEIVER ─────────────────────────

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val state = intent.getIntExtra(
                BluetoothAdapter.EXTRA_STATE,
                BluetoothAdapter.ERROR
            )
            when (state) {
                BluetoothAdapter.STATE_OFF -> {
                    Log.d(TAG, "Bluetooth turned OFF — stopping scan and closing GATT")
                    stopScan()
                    try {
                        gatt?.disconnect()
                        gatt?.close()
                    } catch (e: Exception) {
                        Log.e(TAG, "GATT close error on BT off", e)
                    } finally {
                        gatt = null
                    }
                    deviceList.clear()
                    _scannedDevices.value = emptyList()
                    _sensorData.value = null
                    _status.value = BleStatus.BluetoothOff
                }

                BluetoothAdapter.STATE_ON -> {
                    Log.d(TAG, "Bluetooth turned ON")
                    _status.value = BleStatus.Disconnected
                }
            }
        }
    }

    fun registerBluetoothReceiver() {
        context.registerReceiver(
            bluetoothStateReceiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        )
        if (adapter?.isEnabled == false) {
            _status.value = BleStatus.BluetoothOff
        }
    }

    fun unregisterBluetoothReceiver() {
        try {
            context.unregisterReceiver(bluetoothStateReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Receiver not registered", e)
        }
    }

    // ───────────────────────── SCAN ─────────────────────────

    fun startScan() {
        if (adapter?.isEnabled == false) {
            _status.value = BleStatus.BluetoothOff
            return
        }

        scanner = adapter.bluetoothLeScanner
        deviceList.clear()
        _scannedDevices.value = emptyList()
        _status.value = BleStatus.Scanning

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner?.startScan(listOf(filter), settings, scanCallback)
    }

    fun stopScan() {
        scanner?.stopScan(scanCallback)
        scanner = null
        if (_status.value is BleStatus.Scanning) {
            _status.value = BleStatus.Disconnected
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name = device.name ?: "Unknown Device"

            val scanned = ScannedDevice(
                name    = name,
                address = device.address,
                rssi    = result.rssi
            )

            if (deviceList.none { it.address == scanned.address }) {
                deviceList.add(scanned)
                _scannedDevices.value = deviceList.toList()
            }

            val serviceUuids = result.scanRecord?.serviceUuids
            val hasTargetService = serviceUuids?.contains(ParcelUuid(SERVICE_UUID)) == true

            if (hasTargetService) {
                stopScan()
                connectWithBond(device)
            }
        }
    }

    // ───────────────────────── MANUAL CONNECT ─────────────────────────

    fun connectToAddress(address: String) {
        if (adapter?.isEnabled == false) {
            _status.value = BleStatus.BluetoothOff
            return
        }
        val device = adapter.getRemoteDevice(address)
        stopScan()
        connectWithBond(device)
    }

    // ───────────────────────── BOND ─────────────────────────

    private fun connectWithBond(device: BluetoothDevice) {

        if (!AppState.isInForeground) {
            Log.d(TAG, "App is in background — pairing blocked")
            isConnecting = false
            return
        }

        _status.value = BleStatus.Connecting
        val bondState = device.bondState
        Log.d(TAG, "Bond state: $bondState (10=NONE, 11=BONDING, 12=BONDED)")

        when (bondState) {
            BluetoothDevice.BOND_BONDED -> {
                connectGatt(device, autoConnect = true)
            }

            BluetoothDevice.BOND_BONDING -> {
                waitForBond(device)
            }

            BluetoothDevice.BOND_NONE -> {
                Log.d(TAG, "Not bonded — ESP32 will initiate pairing, just waiting")
                refreshGattCache(device)
                waitForBond(device)
                // No createBond() — ESP32 drives pairing via ESP_LE_AUTH_REQ_SC_BOND
                // Android will receive the bond request and show ONE system dialog
                connectGatt(device, autoConnect = false)
            }
        }
    }

    /**
     * Registers a BroadcastReceiver that waits for BOND_BONDED or BOND_NONE
     * and acts accordingly. Safe to call when bonding is already in progress.
     */
    private fun waitForBond(device: BluetoothDevice) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val d = intent.getParcelableExtra<BluetoothDevice>(
                    BluetoothDevice.EXTRA_DEVICE
                )
                val state = intent.getIntExtra(
                    BluetoothDevice.EXTRA_BOND_STATE,
                    BluetoothDevice.ERROR
                )

                if (d?.address != device.address) return

                Log.d(TAG, "Bond state changed → $state for ${device.address}")

                when (state) {
                    BluetoothDevice.BOND_BONDED -> {
                        context.unregisterReceiver(this)
                        // FIX 3: Short delay after bonding on Android 14 to let
                        // the system finish writing the bond to disk before
                        // opening GATT — skipping this causes a 133 error.
                        CoroutineScope(Dispatchers.Main).launch {
                            delay(600)
                            connectGatt(device, autoConnect = true)
                        }
                    }

                    BluetoothDevice.BOND_NONE -> {
                        Log.e(TAG, "Bonding failed for ${device.address}")
                        _status.value = BleStatus.Error("Pairing failed")
                        context.unregisterReceiver(this)
                    }
                }
            }
        }

        context.registerReceiver(
            receiver,
            IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        )
    }

    /**
     * Attempts to clear the local GATT service cache for a device using the
     * hidden BluetoothGatt#refresh() method. This prevents stale service
     * discovery from a previous connection attempt.
     *
     * Only works when a GATT instance exists. Safe to call even if it fails —
     * it's a best-effort operation.
     */
    private fun refreshGattCache(device: BluetoothDevice) {
        try {
            // Open a temporary GATT connection just to call refresh()
            val tempGatt = device.connectGatt(context, false, object : BluetoothGattCallback() {})
            val refreshMethod = tempGatt.javaClass.getMethod("refresh")
            val refreshed = refreshMethod.invoke(tempGatt) as? Boolean
            Log.d(TAG, "GATT cache refresh: $refreshed")
            tempGatt.close()
        } catch (e: Exception) {
            Log.w(TAG, "GATT cache refresh unavailable (harmless): ${e.message}")
        }
    }

    // ───────────────────────── CONNECT ─────────────────────────

    /**
     * @param autoConnect true for bonded devices (Android 14 requires this to
     *                    show the device as connected in Settings > Bluetooth).
     *                    false for first-time or direct connections.
     */
    private fun connectGatt(device: BluetoothDevice, autoConnect: Boolean = false) {
        Log.d(TAG, "connectGatt() autoConnect=$autoConnect")
        _status.value = BleStatus.Connecting
        gatt = device.connectGatt(context, autoConnect, gattCallback)
    }

    fun disconnect() {
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (e: Exception) {
            Log.e(TAG, "disconnect error", e)
        } finally {
            gatt = null
        }

        stopScan()
        _status.value = BleStatus.Disconnected
    }

    // ───────────────────────── GATT CALLBACK ─────────────────────────

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(
            g: BluetoothGatt,
            status: Int,
            newState: Int
        ) {
            Log.d(TAG, "onConnectionStateChange status=$status newState=$newState")

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "GATT connected to ${g.device.address}")
                    _status.value = BleStatus.Connected(g.device.name ?: "CEMO Node")
                    g.requestMtu(185)
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "GATT disconnected (status=$status)")

                    // Status 133 = GATT_ERROR — common on Android 14 when
                    // connecting too soon after bonding. Log it clearly.
                    if (status == 133) {
                        Log.e(TAG, "GATT error 133 — possible timing issue after bond. " +
                                "Increase delay in waitForBond() if this persists.")
                    }

                    if (_status.value !is BleStatus.BluetoothOff) {
                        _status.value = BleStatus.Disconnected
                    }
                    gatt?.close()
                    gatt = null
                }
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            Log.d(TAG, "MTU changed to $mtu (status=$status)")
            g.discoverServices()
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            Log.d(TAG, "Services discovered (status=$status)")

            val service = g.getService(SERVICE_UUID)
            if (service == null) {
                Log.e(TAG, "Target service not found — bond may be stale or ESP32 not advertising")
                _status.value = BleStatus.Error("Service not found")
                return
            }

            val char = service.getCharacteristic(CHAR_UUID)
            if (char == null) {
                Log.e(TAG, "Target characteristic not found")
                _status.value = BleStatus.Error("Characteristic not found")
                return
            }

            g.setCharacteristicNotification(char, true)

            val descriptor = char.getDescriptor(CCCD_UUID)
            if (descriptor == null) {
                Log.e(TAG, "CCCD descriptor not found — notifications will not work")
                return
            }

            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            val writeResult = g.writeDescriptor(descriptor)
            Log.d(TAG, "CCCD write initiated: $writeResult")
        }

        // Android 13+ callback
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            Log.d(TAG, "onCharacteristicChanged (new API)")
            handleData(value)
        }

        // Android 12 and below fallback
        @Deprecated("Used on Android 12 and below")
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            Log.d(TAG, "onCharacteristicChanged (deprecated API)")
            handleData(characteristic.value)
        }
    }

    // ───────────────────────── DATA ─────────────────────────

    private fun handleData(bytes: ByteArray) {
        val raw = String(bytes, Charsets.UTF_8).trim()
        Log.d(TAG, "RAW: $raw")

        Esp32SensorData.parse(raw)?.let {
            _sensorData.value = it
            Log.d(TAG, "PARSED: $it")
        }
    }
}