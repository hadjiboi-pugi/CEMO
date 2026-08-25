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
import kotlinx.coroutines.channels.BufferOverflow
import java.util.UUID
import com.example.cemo.AppState

@SuppressLint("MissingPermission")
class BleManager(private val context: Context) {

    companion object {
        val SERVICE_UUID: UUID =
            UUID.fromString("12345678-1234-1234-1234-123456789012")

        val CHAR_UUID: UUID =
            UUID.fromString("abcd1234-ab12-ab12-ab12-abcdef123456")

        val CMD_CHAR_UUID: UUID =
            UUID.fromString("abcd1234-ab12-ab12-ab12-abcdef123457")

        private val CCCD_UUID: UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        private const val TAG = "BLE"
        private const val DATA_FLOW_TIMEOUT_MS = 30000L  // tolerate slower notify cadence before recovery
        private const val DATA_FLOW_MONITOR_TICK_MS = 3000L
        private const val STARVATION_RECOVERY_STRIKES = 2
        private const val STARVATION_RECONNECT_STRIKES = 8
        private const val MAX_NOTIFICATION_RECOVERY_ATTEMPTS = 3
        private const val MAX_READ_QUEUE_FAILURES = 3
        private const val GATT_RECONNECT_DELAY_MS = 1200L
    }

    private val adapter =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private var scanner: BluetoothLeScanner? = null
    private var gatt: BluetoothGatt? = null

    /**
     * Safely checks if Bluetooth is enabled without throwing SecurityException on Android 12+
     */
    private fun isBluetoothEnabled(): Boolean {
        return try {
            adapter?.isEnabled == true
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: Missing BLUETOOTH_CONNECT permission", e)
            false
        }
    }

    private val deviceList = mutableListOf<ScannedDevice>()
    private var isConnecting = false

    // ───────────────────────── BONDING STATE ─────────────────────────
    private var bondingReceiver: BroadcastReceiver? = null
    private var bondingDevice: BluetoothDevice? = null
    private var bondingJob: Job? = null

    // ───────────────────────── DATA FLOW MONITORING ─────────────────────────
    private var lastDataReceivedMs: Long = 0
    private var dataFlowMonitorJob: Job? = null
    private var characteristicReadJob: Job? = null
    private var notificationRecoveryAttempts = 0
    private var consecutiveReadQueueFailures = 0
    private var isRecoveringNotifications = false
    private var waitingForDataAfterRecovery = false
    private var starvationStrikeCount = 0
    private var nextRecoveryStrike = STARVATION_RECOVERY_STRIKES
    private var reconnectJob: Job? = null

    // ───────────────────────── FLOWS ─────────────────────────

    private val _status = MutableStateFlow<BleStatus>(
        if (isBluetoothEnabled()) BleStatus.Disconnected else BleStatus.BluetoothOff
    )
    val status: StateFlow<BleStatus> = _status.asStateFlow()

    private val _sensorData = MutableSharedFlow<Esp32SensorData?>(
        replay = 1,  // ← Keep last emission for late subscribers
        extraBufferCapacity = 5,  // ← Increased buffer to prevent drops
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val sensorData: SharedFlow<Esp32SensorData?> = _sensorData.asSharedFlow()

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
                    reconnectJob?.cancel()
                    reconnectJob = null
                    stopScan()
                    stopDataFlowMonitoring()
                    stopCharacteristicReads()
                    cancelBondingProcess()
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
                    _sensorData.tryEmit(null)
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
        if (!isBluetoothEnabled()) {
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
        if (!isBluetoothEnabled()) {
            _status.value = BleStatus.BluetoothOff
            return
        }

        try {
            scanner = adapter?.bluetoothLeScanner
            deviceList.clear()
            _scannedDevices.value = emptyList()
            _status.value = BleStatus.Scanning

            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            // Removed ScanFilter for SERVICE_UUID to allow finding all devices.
            // This helps when the ESP32 is not advertising the service UUID in its scan record.
            scanner?.startScan(null, settings, scanCallback)
            Log.d(TAG, "Scan started (unfiltered)")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException in startScan", e)
            _status.value = BleStatus.Error("Missing Bluetooth permissions")
        }
    }

    fun stopScan() {
        try {
            scanner?.stopScan(scanCallback)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException in stopScan", e)
        }
        scanner = null
        if (_status.value is BleStatus.Scanning) {
            _status.value = BleStatus.Disconnected
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            try {
                val device = result.device
                val name = device.name ?: "Unknown Device"

                val scanned = ScannedDevice(
                    name    = name,
                    address = device.address,
                    rssi    = result.rssi
                )

                if (deviceList.none { it.address == scanned.address }) {
                    Log.d(TAG, "New device found: $name (${device.address}) RSSI: ${result.rssi}")
                    deviceList.add(scanned)
                    _scannedDevices.value = deviceList.toList()
                }

                val serviceUuids = result.scanRecord?.serviceUuids
                val hasTargetService = serviceUuids?.contains(ParcelUuid(SERVICE_UUID)) == true
                
                if (hasTargetService) {
                    Log.d(TAG, "✓ Target CEMO service found on ${device.address}. Auto-connecting...")
                    stopScan()
                    connectWithBond(device)
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException in scanCallback", e)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed with error code: $errorCode")
            _status.value = BleStatus.Error("Scan failed: error $errorCode")
        }
    }

    // ───────────────────────── MANUAL CONNECT ─────────────────────────

    fun connectToAddress(address: String) {
        if (!isBluetoothEnabled()) {
            _status.value = BleStatus.BluetoothOff
            return
        }
        try {
            val device = adapter?.getRemoteDevice(address) ?: return
            stopScan()
            connectWithBond(device)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException in connectToAddress", e)
            _status.value = BleStatus.Error("Missing Bluetooth permissions")
        }
    }

    // ───────────────────────── BOND ─────────────────────────

    private fun connectWithBond(device: BluetoothDevice) {

        if (!AppState.isInForeground) {
            Log.d(TAG, "App is in background — pairing blocked")
            isConnecting = false
            return
        }

        // Prevent concurrent bond attempts—cancel any existing bonding process
        if (bondingDevice?.address == device.address) {
            Log.d(TAG, "Already bonding with ${device.address} — ignoring duplicate request")
            return
        }

        cancelBondingProcess()

        _status.value = BleStatus.Connecting
        try {
            val bondState = device.bondState
            Log.d(TAG, "Bond state: $bondState (10=NONE, 11=BONDING, 12=BONDED)")

            when (bondState) {
                BluetoothDevice.BOND_BONDED -> {
                    connectGatt(device, autoConnect = false)
                }

                BluetoothDevice.BOND_BONDING -> {
                    waitForBond(device)
                }

                BluetoothDevice.BOND_NONE -> {
                    Log.d(TAG, "Not bonded — preparing for pairing")
                    waitForBond(device)
                    
                    // Initiate the bond process.
                    val bondInitiated = device.createBond()
                    if (!bondInitiated) {
                        Log.e(TAG, "Failed to initiate bond")
                        _status.value = BleStatus.Error("Failed to initiate bond")
                        cancelBondingProcess()
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException in connectWithBond", e)
            _status.value = BleStatus.Error("Missing Bluetooth permissions")
        }
    }


    /**
     * Cleans up any existing bonding process with proper receiver unregistration and job cancellation.
     */
    private fun cancelBondingProcess() {
        bondingJob?.cancel()
        bondingJob = null

        if (bondingReceiver != null) {
            try {
                context.unregisterReceiver(bondingReceiver!!)
                Log.d(TAG, "Bonding receiver unregistered")
            } catch (e: Exception) {
                Log.d(TAG, "Receiver was not registered: ${e.message}")
            }
            bondingReceiver = null
        }

        bondingDevice = null
    }

    /**
     * Registers a BroadcastReceiver that waits for BOND_BONDED or BOND_NONE
     * and acts accordingly. Includes a timeout mechanism.
     */
    private fun waitForBond(device: BluetoothDevice) {
        bondingDevice = device

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
                        Log.d(TAG, "Bonding succeeded for ${device.address}")
                        cancelBondingProcess()

                        // Connect to GATT after successful bond
                        CoroutineScope(Dispatchers.Main).launch {
                            delay(1000) // Android 14 needs time to persist bond
                            if (gatt == null) {
                                Log.d(TAG, "Bond complete — connecting GATT")
                                connectGatt(device, autoConnect = false)
                            } else {
                                Log.d(TAG, "Bonded while GATT connected — discovering services")
                                gatt?.discoverServices()
                            }
                        }
                    }

                    BluetoothDevice.BOND_NONE -> {
                        Log.e(TAG, "Bonding failed for ${device.address}")
                        cancelBondingProcess()
                        _status.value = BleStatus.Error("Pairing failed")

                        // Cleanup GATT if it exists
                        try {
                            gatt?.disconnect()
                            gatt?.close()
                        } catch (e: Exception) {
                            Log.e(TAG, "GATT cleanup after bond failure", e)
                        }
                        gatt = null
                    }
                }
            }
        }

        bondingReceiver = receiver
        context.registerReceiver(
            receiver,
            IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        )

        // Bonding timeout: 60 seconds
        bondingJob = CoroutineScope(Dispatchers.Main).launch {
            delay(60000) // 60 seconds

            if (bondingDevice?.address == device.address) {
                Log.e(TAG, "Bonding timeout for ${device.address} — cancelling")
                cancelBondingProcess()
                _status.value = BleStatus.Error("Pairing timeout")

                // Cleanup GATT
                try {
                    gatt?.disconnect()
                    gatt?.close()
                } catch (e: Exception) {
                    Log.e(TAG, "GATT cleanup after bond timeout", e)
                }
                gatt = null
            }
        }
    }

    /**
     * Monitors data flow and warns if notifications stop working.
     * Also falls back to periodic characteristic reads if notifications fail.
     */
    private fun startDataFlowMonitoring() {
        // Cancel any existing monitoring jobs
        dataFlowMonitorJob?.cancel()
        dataFlowMonitorJob = null
        characteristicReadJob?.cancel()
        characteristicReadJob = null

        lastDataReceivedMs = System.currentTimeMillis()
        consecutiveReadQueueFailures = 0
        isRecoveringNotifications = false
        waitingForDataAfterRecovery = false
        starvationStrikeCount = 0
        nextRecoveryStrike = STARVATION_RECOVERY_STRIKES

        // Monitor for data flow starvation
        dataFlowMonitorJob = CoroutineScope(Dispatchers.Main).launch {
            while (true) {
                delay(DATA_FLOW_MONITOR_TICK_MS)

                val timeSinceLastData = System.currentTimeMillis() - lastDataReceivedMs
                if (timeSinceLastData > DATA_FLOW_TIMEOUT_MS && _status.value is BleStatus.Connected) {
                    starvationStrikeCount++
                    Log.w(
                        TAG,
                        "⚠ No sensor data received for ${timeSinceLastData}ms (strike $starvationStrikeCount) — Notifications may have stopped"
                    )

                    if (waitingForDataAfterRecovery && starvationStrikeCount >= STARVATION_RECONNECT_STRIKES) {
                        reconnectGatt("notification resubscribe succeeded but no sensor packets arrived")
                        continue
                    }

                    if (
                        !isRecoveringNotifications &&
                        notificationRecoveryAttempts < MAX_NOTIFICATION_RECOVERY_ATTEMPTS &&
                        starvationStrikeCount >= nextRecoveryStrike
                    ) {
                        attemptNotificationRecovery("data timeout (${timeSinceLastData}ms)")
                        nextRecoveryStrike = starvationStrikeCount + STARVATION_RECOVERY_STRIKES
                    }

                    if (
                        notificationRecoveryAttempts >= MAX_NOTIFICATION_RECOVERY_ATTEMPTS &&
                        starvationStrikeCount >= STARVATION_RECONNECT_STRIKES
                    ) {
                        reconnectGatt("notification recovery exhausted: data timeout (${timeSinceLastData}ms)")
                    }
                } else {
                    starvationStrikeCount = 0
                    nextRecoveryStrike = STARVATION_RECOVERY_STRIKES
                }
            }
        }
    }

    /**
     * Falls back to periodic characteristic reads if notifications aren't working.
     */
    private fun startPeriodicCharacteristicReads() {
        characteristicReadJob?.cancel()
        characteristicReadJob = null

        characteristicReadJob = CoroutineScope(Dispatchers.Main).launch {
            while (true) {
                delay(1000)  // Read every 1 second

                val g = gatt
                if (g == null || _status.value !is BleStatus.Connected) {
                    break
                }

                try {
                    val service = g.getService(SERVICE_UUID)
                    val char = service?.getCharacteristic(CHAR_UUID)

                    if (char != null) {
                        if (!isReadableCharacteristic(char)) {
                            Log.w(TAG, "Characteristic is notify-only; cannot fallback to readCharacteristic()")
                            attemptNotificationRecovery("fallback read unsupported")
                            break
                        }

                        val readResult = g.readCharacteristic(char)
                        Log.d(TAG, "Characteristic read requested: $readResult")
                        if (!readResult) {
                            consecutiveReadQueueFailures++
                            if (consecutiveReadQueueFailures >= MAX_READ_QUEUE_FAILURES) {
                                Log.w(
                                    TAG,
                                    "readCharacteristic() rejected $consecutiveReadQueueFailures times; retrying notification setup"
                                )
                                attemptNotificationRecovery("read queue rejection")
                                break
                            }
                        } else {
                            consecutiveReadQueueFailures = 0
                        }
                    } else {
                        Log.w(TAG, "Characteristic not found for fallback read")
                        break
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading characteristic: ${e.message}")
                    break
                }
            }
        }
    }

    private fun stopDataFlowMonitoring() {
        dataFlowMonitorJob?.cancel()
        dataFlowMonitorJob = null
        stopCharacteristicReads()
    }

    private fun stopCharacteristicReads() {
        characteristicReadJob?.cancel()
        characteristicReadJob = null
        consecutiveReadQueueFailures = 0
        starvationStrikeCount = 0
        nextRecoveryStrike = STARVATION_RECOVERY_STRIKES
    }

    private fun isReadableCharacteristic(char: BluetoothGattCharacteristic): Boolean {
        return (char.properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0
    }

    private fun isNotifiableCharacteristic(char: BluetoothGattCharacteristic): Boolean {
        val supportsNotify = (char.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
        val supportsIndicate = (char.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
        return supportsNotify || supportsIndicate
    }

    private fun attemptNotificationRecovery(reason: String) {
        val g = gatt ?: return
        if (_status.value !is BleStatus.Connected) return
        if (isRecoveringNotifications) return

        if (notificationRecoveryAttempts >= MAX_NOTIFICATION_RECOVERY_ATTEMPTS) {
            Log.w(TAG, "Notification recovery budget exhausted; waiting for reconnect threshold. reason=$reason")
            return
        }

        val service = g.getService(SERVICE_UUID)
        val char = service?.getCharacteristic(CHAR_UUID)
        if (char == null) {
            Log.e(TAG, "Cannot recover notifications: characteristic missing")
            reconnectGatt("characteristic missing during recovery")
            return
        }

        if (!isNotifiableCharacteristic(char)) {
            Log.e(TAG, "Characteristic does not support notify/indicate; cannot recover notifications")
            if (isReadableCharacteristic(char) && characteristicReadJob == null) {
                Log.w(TAG, "Using periodic reads because notifications are unsupported")
                startPeriodicCharacteristicReads()
            } else {
                _status.value = BleStatus.Error("Characteristic doesn't support notifications")
            }
            return
        }

        val descriptor = char.getDescriptor(CCCD_UUID)
        if (descriptor == null) {
            Log.e(TAG, "Cannot recover notifications: CCCD not found")
            reconnectGatt("CCCD missing during recovery")
            return
        }

        notificationRecoveryAttempts++
        isRecoveringNotifications = true
        Log.w(
            TAG,
            "Attempting notification recovery (${notificationRecoveryAttempts}/${MAX_NOTIFICATION_RECOVERY_ATTEMPTS}) due to $reason"
        )

        val notificationSet = g.setCharacteristicNotification(char, true)
        if (!notificationSet) {
            isRecoveringNotifications = false
            Log.w(TAG, "setCharacteristicNotification failed during recovery; falling back to periodic reads")
            if (characteristicReadJob == null && isReadableCharacteristic(char)) {
                startPeriodicCharacteristicReads()
            }
            return
        }

        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        val writeQueued = g.writeDescriptor(descriptor)
        if (!writeQueued) {
            isRecoveringNotifications = false
            Log.w(TAG, "CCCD write not queued during recovery")
            if (characteristicReadJob == null && isReadableCharacteristic(char)) {
                startPeriodicCharacteristicReads()
            }
        }
    }

    private fun reconnectGatt(reason: String) {
        val currentGatt = gatt ?: return
        val device = currentGatt.device
        Log.w(TAG, "Reconnecting GATT: $reason")

        reconnectJob?.cancel()
        reconnectJob = null
        stopDataFlowMonitoring()
        stopCharacteristicReads()
        isRecoveringNotifications = false
        notificationRecoveryAttempts = 0
        waitingForDataAfterRecovery = false
        starvationStrikeCount = 0
        nextRecoveryStrike = STARVATION_RECOVERY_STRIKES

        try {
            refreshGattCache(currentGatt, "before reconnect")
            currentGatt.disconnect()
            currentGatt.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error while resetting GATT for recovery", e)
        } finally {
            gatt = null
        }

        reconnectJob = CoroutineScope(Dispatchers.Main).launch {
            _status.value = BleStatus.Connecting
            delay(GATT_RECONNECT_DELAY_MS)
            if (!isBluetoothEnabled() || _status.value is BleStatus.BluetoothOff || gatt != null) {
                return@launch
            }
            connectGatt(device, autoConnect = false)
        }
    }

    /**
     * Attempts to clear the local GATT service cache for a device using the
     * hidden BluetoothGatt#refresh() method. 
     * 
     * CALL THIS MANUALLY ONLY IF SERVICES ARE STALE.
     */
    fun refreshDeviceCache() {
        val g = gatt ?: return
        refreshGattCache(g, "manual")
    }

    private fun refreshGattCache(gattInstance: BluetoothGatt, contextTag: String) {
        try {
            val refreshMethod = gattInstance.javaClass.getMethod("refresh")
            val refreshed = refreshMethod.invoke(gattInstance) as? Boolean
            Log.d(TAG, "GATT cache refresh ($contextTag): $refreshed")
        } catch (e: Exception) {
            Log.w(TAG, "GATT cache refresh failed ($contextTag): ${e.message}")
        }
    }

    // ───────────────────────── CONNECT ─────────────────────────

    /**
     * Keep autoConnect=false for stable active sessions and live notification flow.
     */
    private fun connectGatt(device: BluetoothDevice, autoConnect: Boolean = false) {
        Log.d(TAG, "connectGatt() autoConnect=$autoConnect")
        _status.value = BleStatus.Connecting
        reconnectJob?.cancel()
        reconnectJob = null

        // CLEANUP: Close existing GATT before opening a new one to prevent leaks
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (e: Exception) {
            // ignore
        }
        gatt = null

        try {
            // USE TRANSPORT_LE: Explicitly tell Android to use BLE
            gatt = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                device.connectGatt(context, autoConnect, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(context, autoConnect, gattCallback)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException in connectGatt", e)
            _status.value = BleStatus.Error("Missing Bluetooth permissions")
        }
    }

    fun disconnect() {
        // Cancel any ongoing bonding, data monitoring, and characteristic reads
        reconnectJob?.cancel()
        reconnectJob = null
        cancelBondingProcess()
        stopDataFlowMonitoring()
        stopCharacteristicReads()
        isRecoveringNotifications = false
        notificationRecoveryAttempts = 0
        waitingForDataAfterRecovery = false

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
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        Log.d(TAG, "GATT connected to ${g.device.address}")
                        _status.value = BleStatus.Connected(g.device.name ?: "CEMO Node")
                        // Request high priority for REAL-TIME low latency
                        g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                        // Try to use 2M PHY for faster data transfer
                        try {
                            g.setPreferredPhy(
                                BluetoothDevice.PHY_LE_2M_MASK,
                                BluetoothDevice.PHY_LE_2M_MASK,
                                BluetoothDevice.PHY_OPTION_NO_PREFERRED
                            )
                        } catch (e: Exception) {
                            Log.d(TAG, "2M PHY not available: ${e.message}")
                        }
                        g.requestMtu(185)
                    } else {
                        Log.e(TAG, "Connect failed with status=$status")
                        handleDisconnect(status, g.device)
                    }
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "GATT disconnected (status=$status)")
                    handleDisconnect(status, g.device)
                }
            }
        }

        private fun handleDisconnect(status: Int, device: BluetoothDevice) {
            // Status 133 (0x85): GATT_ERROR - Common on Android 14 timing
            // Status 22 (0x16): HCI_ERR_CONN_TERM_LOCAL_HOST - Local host terminated (pairing fail/timeout)
            // Status 19 (0x13): HCI_ERR_REMOTE_USER_TERM_CONN - Remote device terminated
            // Status 8 (0x08): GATT_CONN_TIMEOUT - Link loss
            
            when (status) {
                133 -> Log.e(TAG, "GATT 133: Timing error on ${device.address}. Will retry.")
                22  -> Log.e(TAG, "GATT 22: Local termination on ${device.address}. Likely bonding mismatch or stale bond.")
                19  -> Log.e(TAG, "GATT 19: Peer terminated on ${device.address}. ESP32 forced disconnect.")
                8   -> Log.e(TAG, "GATT 8: Connection timeout on ${device.address} (range/interference).")
                0   -> Log.d(TAG, "Graceful disconnect from ${device.address}")
            }

            stopDataFlowMonitoring()
            stopCharacteristicReads()
            reconnectJob?.cancel()
            reconnectJob = null
            isRecoveringNotifications = false
            notificationRecoveryAttempts = 0
            waitingForDataAfterRecovery = false
            starvationStrikeCount = 0
            nextRecoveryStrike = STARVATION_RECOVERY_STRIKES

            if (_status.value !is BleStatus.BluetoothOff) {
                _status.value = BleStatus.Disconnected
            }
            
            try {
                gatt?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing GATT on disconnect", e)
            }
            gatt = null
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

            // Enable client-side notification listening
            val notificationSet = g.setCharacteristicNotification(char, true)
            Log.d(TAG, "setCharacteristicNotification: $notificationSet")

            val descriptor = char.getDescriptor(CCCD_UUID)
            if (descriptor == null) {
                Log.e(TAG, "CCCD descriptor not found — notifications will not work")
                _status.value = BleStatus.Error("CCCD descriptor not found")
                return
            }

            // Enable notifications on the device side (via CCCD)
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            val writeResult = g.writeDescriptor(descriptor)
            Log.d(TAG, "CCCD write queued: $writeResult for ${char.uuid}")
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            Log.d(TAG, "onDescriptorWrite status=$status for ${descriptor.uuid}")

            if (descriptor.uuid == CCCD_UUID) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val wasRecovering = isRecoveringNotifications
                    Log.d(TAG, "✓ CCCD descriptor write SUCCESSFUL — notifications are now enabled")
                    isRecoveringNotifications = false
                    if (wasRecovering) {
                        waitingForDataAfterRecovery = true
                    }
                    if (characteristicReadJob != null) {
                        stopCharacteristicReads()
                    }
                    // Start monitoring data flow to detect if notifications stop working
                    startDataFlowMonitoring()
                } else {
                    Log.e(TAG, "✗ CCCD descriptor write FAILED with status=$status")
                    isRecoveringNotifications = false
                    waitingForDataAfterRecovery = false
                    if (characteristicReadJob == null) {
                        startPeriodicCharacteristicReads()
                    }
                }
            }
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

        // Fallback: Handle characteristic reads (for when notifications fail)
        @Deprecated("Used on Android 12 and below")
        override fun onCharacteristicRead(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            Log.d(TAG, "onCharacteristicRead status=$status (old API)")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                handleData(characteristic.value)
            }
        }

        // Fallback: Handle characteristic reads (for Android 13+)
        override fun onCharacteristicRead(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            Log.d(TAG, "onCharacteristicRead status=$status (new API)")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                handleData(value)
            }
        }
    }

    // ───────────────────────── DATA ─────────────────────────

    private fun handleData(bytes: ByteArray) {
        if (bytes.isEmpty()) {
            Log.w(TAG, "Empty characteristic value received")
            return
        }

        // Track that data was received successfully
        lastDataReceivedMs = System.currentTimeMillis()
        notificationRecoveryAttempts = 0
        isRecoveringNotifications = false
        waitingForDataAfterRecovery = false
        starvationStrikeCount = 0
        nextRecoveryStrike = STARVATION_RECOVERY_STRIKES

        val raw = String(bytes, Charsets.UTF_8).trim()
        Log.d(TAG, "RAW: $raw")

        val parsed = Esp32SensorData.parse(raw)
        if (parsed == null) {
            Log.w(TAG, "Failed to parse sensor data: $raw")
            return
        }

        val emitted = _sensorData.tryEmit(parsed)
        if (emitted) {
            Log.d(TAG, "✓ EMITTED: Weight=${parsed.weightKg}kg, Temp=${parsed.temperatureC}°C")
        } else {
            Log.w(TAG, "✗ Failed to emit data (buffer full): $parsed")
        }
    }

    /**
     * Sends a command string to the connected device.
     */
    fun writeCommand(command: String) {
        val g = gatt ?: run {
            Log.w(TAG, "Cannot write command: GATT is null")
            return
        }
        if (_status.value !is BleStatus.Connected) {
            Log.w(TAG, "Cannot write command: Not connected")
            return
        }

        try {
            val service = g.getService(SERVICE_UUID)
            val char = service?.getCharacteristic(CMD_CHAR_UUID)
                ?: service?.getCharacteristic(CHAR_UUID)
            if (char == null) {
                Log.e(TAG, "Cannot write command: Characteristic not found")
                return
            }

            // Check if write is supported
            val canWrite = (char.properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0
            if (!canWrite) {
                Log.e(TAG, "Cannot write command: Characteristic does not support WRITE")
                return
            }

            val bytes = command.toByteArray(Charsets.UTF_8)
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                val writeResult = g.writeCharacteristic(char, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                Log.d(TAG, "Write command '$command' result: $writeResult (New API)")
            } else {
                @Suppress("DEPRECATION")
                char.value = bytes
                @Suppress("DEPRECATION")
                val writeQueued = g.writeCharacteristic(char)
                Log.d(TAG, "Write command '$command' queued: $writeQueued (Old API)")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException writing command", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error writing command: ${e.message}")
        }
    }
}
