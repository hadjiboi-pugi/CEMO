package com.example.cemo.data.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.*
import android.util.Log
import com.example.cemo.data.model.*
import kotlinx.coroutines.flow.*
import java.util.UUID
import kotlinx.coroutines.*

@SuppressLint("MissingPermission")
class BleManager(private val context: Context) {

    companion object {
        const val ESP32_NAME = "CEMO-ESP32"

        val SERVICE_UUID: UUID = UUID.fromString("12345678-1234-1234-1234-123456789012")
        val CHAR_UUID: UUID    = UUID.fromString("abcd1234-ab12-ab12-ab12-abcdef123456")

        private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private val adapter =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private var scanner: BluetoothLeScanner? = null
    private var gatt: BluetoothGatt? = null

    // Guard: prevents overlapping connect attempts
    @Volatile private var isConnecting = false

    private val deviceList = mutableListOf<ScannedDevice>()

    // ───────────────────────── FLOWS ─────────────────────────

    private val _status = MutableStateFlow<BleStatus>(BleStatus.Disconnected)
    val status: StateFlow<BleStatus> = _status.asStateFlow()

    private val _sensorData = MutableStateFlow<Esp32SensorData?>(null)
    val sensorData: StateFlow<Esp32SensorData?> = _sensorData.asStateFlow()

    private val _scannedDevices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val scannedDevices: StateFlow<List<ScannedDevice>> = _scannedDevices.asStateFlow()

    // ───────────────────────── SCAN ─────────────────────────

    fun startScan() {
        // Don't scan if already connected or connecting
        if (_status.value is BleStatus.Connected || isConnecting) return

        fullReset()

        scanner = adapter.bluetoothLeScanner
        deviceList.clear()
        _scannedDevices.value = emptyList()

        _status.value = BleStatus.Scanning
        scanner?.startScan(scanCallback)
    }

    fun stopScan() {
        scanner?.stopScan(scanCallback)
        scanner = null
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name   = device.name ?: "Unknown"

            val scanned = ScannedDevice(
                name    = name,
                address = device.address,
                rssi    = result.rssi
            )

            if (deviceList.none { it.address == scanned.address }) {
                deviceList.add(scanned)
                _scannedDevices.value = deviceList.toList()
            }

            if (name == ESP32_NAME && !isConnecting) {
                stopScan()
                connectWithBond(device)
            }
        }
    }

    // ───────────────────────── MANUAL CONNECT ─────────────────────────

    fun connectToAddress(address: String) {
        if (isConnecting || _status.value is BleStatus.Connected) return
        val device = adapter.getRemoteDevice(address)
        stopScan()
        connectWithBond(device)
    }

    // ───────────────────────── BOND ─────────────────────────

    private fun connectWithBond(device: BluetoothDevice) {
        if (isConnecting) return
        isConnecting = true
        _status.value = BleStatus.Connecting

        if (device.bondState == BluetoothDevice.BOND_BONDED) {
            connectGatt(device)
            return
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val d = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                val state = intent.getIntExtra(
                    BluetoothDevice.EXTRA_BOND_STATE,
                    BluetoothDevice.ERROR
                )

                if (d?.address != device.address) return

                when (state) {
                    BluetoothDevice.BOND_BONDED -> {
                        context.unregisterReceiver(this)
                        connectGatt(device)
                    }
                    BluetoothDevice.BOND_NONE -> {
                        context.unregisterReceiver(this)
                        isConnecting = false
                        _status.value = BleStatus.Error("Pairing failed")
                    }
                }
            }
        }

        context.registerReceiver(
            receiver,
            IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        )

        CoroutineScope(Dispatchers.Main).launch {
            delay(500)
            device.createBond()
        }
    }

    // ───────────────────────── CONNECT ─────────────────────────

    private fun connectGatt(device: BluetoothDevice) {
        // Close any stale GATT before opening a new one
        gatt?.disconnect()
        gatt?.close()
        gatt = null

        gatt = device.connectGatt(context, false, gattCallback)
    }

    fun disconnect() {
        isConnecting = false
        fullReset()
        _status.value = BleStatus.Disconnected
    }

    // Closes GATT + stops scan without touching _status
    private fun fullReset() {
        stopScan()
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (e: Exception) {
            Log.e("BLE", "fullReset error", e)
        } finally {
            gatt = null
        }
    }

    // ───────────────────────── GATT CALLBACK ─────────────────────────

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    isConnecting = false
                    _status.value = BleStatus.Connected(g.device.name ?: "ESP32")
                    g.requestMtu(185)
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    isConnecting = false
                    _status.value = BleStatus.Disconnected
                    g.close()
                    if (gatt == g) gatt = null
                }
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            g.discoverServices()
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val service = g.getService(SERVICE_UUID) ?: return
            val char    = service.getCharacteristic(CHAR_UUID) ?: return

            g.setCharacteristicNotification(char, true)

            val descriptor = char.getDescriptor(CCCD_UUID) ?: return
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            g.writeDescriptor(descriptor)
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            handleData(characteristic.value)
        }

        @Deprecated("Old API")
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleData(value)
        }
    }

    // ───────────────────────── DATA ─────────────────────────

    private fun handleData(bytes: ByteArray) {
        val raw = String(bytes, Charsets.UTF_8).trim()
        Log.d("BLE", "RAW: $raw")
        Esp32SensorData.parse(raw)?.let {
            _sensorData.value = it
            Log.d("BLE", "PARSED: $it")
        }
    }
}