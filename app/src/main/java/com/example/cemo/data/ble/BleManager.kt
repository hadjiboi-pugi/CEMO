package com.example.cemo.data.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Build
import com.example.cemo.data.model.BleStatus
import com.example.cemo.data.model.Esp32SensorData
import com.example.cemo.data.model.ScannedDevice
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

@SuppressLint("MissingPermission")
class BleManager(private val context: Context) {

    companion object {
        const val ESP32_NAME = "CEMO-ESP32"
        val SENSOR_SERVICE_UUID: UUID = UUID.fromString("12345678-1234-1234-1234-123456789012")
        val SENSOR_CHAR_UUID:    UUID = UUID.fromString("abcd1234-ab12-ab12-ab12-abcdef123456")
        private val CCCD_UUID:   UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private val bluetoothAdapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    // ── Public state flows ────────────────────────────────────────────────────

    private val _status = MutableStateFlow<BleStatus>(BleStatus.Disconnected)
    val status: StateFlow<BleStatus> = _status.asStateFlow()

    private val _scannedDevices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val scannedDevices: StateFlow<List<ScannedDevice>> = _scannedDevices.asStateFlow()

    private val _sensorData = MutableSharedFlow<Esp32SensorData>(replay = 1)
    val sensorData: SharedFlow<Esp32SensorData> = _sensorData.asSharedFlow()

    // ── Internal ──────────────────────────────────────────────────────────────

    private var bluetoothGatt: BluetoothGatt? = null
    private var bleScanner: BluetoothLeScanner? = null

    // ── Scan ──────────────────────────────────────────────────────────────────

    fun startScan() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            _status.value = BleStatus.Error("Bluetooth is disabled")
            return
        }
        _scannedDevices.value = emptyList()
        _status.value = BleStatus.Scanning
        bleScanner = bluetoothAdapter.bluetoothLeScanner
        bleScanner?.startScan(scanCallback)
    }

    fun stopScan() {
        bleScanner?.stopScan(scanCallback)
        bleScanner = null
        if (_status.value is BleStatus.Scanning) {
            _status.value = BleStatus.Disconnected
        }
    }

    fun connectToAddress(address: String) {
        val device = bluetoothAdapter?.getRemoteDevice(address) ?: return
        stopScan()
        connect(device)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name    = result.device.name ?: return
            val address = result.device.address
            val rssi    = result.rssi

            _scannedDevices.update { current ->
                if (current.any { it.address == address }) current
                else current + ScannedDevice(name, address, rssi)
            }

            if (name == ESP32_NAME) {
                stopScan()
                connect(result.device)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            _status.value = BleStatus.Error("Scan failed: code $errorCode")
        }
    }

    // ── Connect ───────────────────────────────────────────────────────────────

    private fun connect(device: BluetoothDevice) {
        _status.value = BleStatus.Connecting
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        _status.value = BleStatus.Disconnected
    }

    // ── GATT Callbacks ────────────────────────────────────────────────────────

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _status.value = BleStatus.Connected(gatt.device.name ?: ESP32_NAME)
                    gatt.requestMtu(185)  // negotiate MTU before discovering services
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _status.value = BleStatus.Disconnected
                    bluetoothGatt?.close()
                    bluetoothGatt = null
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            android.util.Log.d("BLE_DATA", "MTU changed to $mtu, status=$status")
            gatt.discoverServices()  // discover services AFTER MTU is negotiated
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                android.util.Log.e("BLE_DATA", "Service discovery failed: $status")
                return
            }

            val service = gatt.getService(SENSOR_SERVICE_UUID)
            if (service == null) {
                android.util.Log.e("BLE_DATA", "Service NOT found: $SENSOR_SERVICE_UUID")
                return
            }

            val characteristic = service.getCharacteristic(SENSOR_CHAR_UUID)
            if (characteristic == null) {
                android.util.Log.e("BLE_DATA", "Characteristic NOT found: $SENSOR_CHAR_UUID")
                return
            }

            android.util.Log.d("BLE_DATA", "Found characteristic, enabling notifications...")
            gatt.setCharacteristicNotification(characteristic, true)

            val descriptor = characteristic.getDescriptor(CCCD_UUID)
            if (descriptor == null) {
                android.util.Log.e("BLE_DATA", "CCCD descriptor NOT found!")
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val result = gatt.writeDescriptor(
                    descriptor,
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                )
                android.util.Log.d("BLE_DATA", "writeDescriptor (API33+) result: $result")
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                val result = gatt.writeDescriptor(descriptor)
                android.util.Log.d("BLE_DATA", "writeDescriptor (legacy) result: $result")
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                android.util.Log.d("BLE_DATA", "Notifications ENABLED successfully")
            } else {
                android.util.Log.e("BLE_DATA", "Descriptor write FAILED: $status")
            }
        }

        @Deprecated("Required for API < 33")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            handleCharacteristicValue(characteristic.value)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleCharacteristicValue(value)
        }
    }

    private fun handleCharacteristicValue(bytes: ByteArray) {
        val raw = String(bytes, Charsets.UTF_8)
        android.util.Log.d("BLE_DATA", "Raw payload: $raw")
        Esp32SensorData.parse(raw)?.let {
            android.util.Log.d("BLE_DATA", "Parsed OK: $it")
            _sensorData.tryEmit(it)
        } ?: android.util.Log.e("BLE_DATA", "Parse FAILED for: $raw")
    }
}