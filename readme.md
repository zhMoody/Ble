# Ble - 一款现代化的Android BLE蓝牙库

## 🚀 快速上手指南

### 1. 监听蓝牙状态

在你的 `ViewModel` 中，可以非常简单地将蓝牙状态转换为一个UI可观察的`StateFlow`。

```kotlin
class MyViewModel : ViewModel() {
    val isBluetoothEnabled: StateFlow<Boolean> = BluetoothManager.bluetoothState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = BluetoothManager.isBluetoothEnabled()
        )
}
```

在你的Compose UI中：

```kotlin
val isEnabled by viewModel.isBluetoothEnabled.collectAsStateWithLifecycle()
Text(if (isEnabled) "蓝牙已开启" else "蓝牙已关闭")
```

### 2. 扫描设备

```kotlin
// 在 ViewModel 中
private val scanner = BluetoothManager.getScanner()
val scannedDevices = mutableStateListOf<ScannedDevice>()

fun startScan() {
    val scanConfig = ScanConfig(
        deviceNameFilter = listOf("MyDevice", "TestDevice"), // 可选，过滤设备名
        scanDuration = 10000L // 扫描10秒
    )

    scanner.startScan(scanConfig, object : ScanCallback {
        override fun onScanStarted() { /* ... */
        }
        override fun onScanStopped() { /* ... */
        }

        override fun onDeviceFound(
            device: BluetoothDevice,
            rssi: Int,
            scanRecord: ByteArray,
            manufacturerData: SparseArray<ByteArray>?
        ) {
            // 将扫描到的设备添加到列表中
        }

        override fun onScanFailed(failure: ScanFailure) {
            when (failure) {
                is ScanFailure.InsufficientPermissions -> Log.e("Scan", "权限不足: ${failure.missingPermissions}")
                is ScanFailure.BleNotSupported -> Log.e("Scan", "设备不支持BLE")
                // ... 处理其他错误
            }
        }
    })
}

fun stopScan() {
    scanner.stopScan()
}
```

### 3. 连接与数据交互

```kotlin
// 在 ViewModel 中
private var deviceConnection: DeviceConnection? = null

fun connect(device: BluetoothDevice) {
    deviceConnection = BluetoothManager.createDeviceConnection(device.address)

    val connectionConfig = ConnectionConfig(
        connectTimeoutMillis = 15000L, // 15秒连接超时
        reconnectionConfig = ReconnectionConfig(
            enabled = true,      // 开启自动重连
            attempts = 5,        // 最多重连5次
            initialDelayMillis = 500L // 初始延迟500ms
        )
    )

    deviceConnection?.connect(connectionConfig, object : ConnectionCallback {
        override fun onConnectionStateChanged(state: ConnectionState) {
            // UI可以根据 state.displayName 显示中文状态
        }

        override fun onServicesDiscovered(services: List<BluetoothGattService>) {
            // 在这里让用户选择一个Service和Characteristic
        }

        override fun onCharacteristicChanged(characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            // 自动收到的通知数据
            val hexData = value.toHexString(" ") // 使用辅助函数
            Log.d("Data", "收到数据: $hexData")
        }

        // ... 其他回调
    })
}

fun sendCommand(hexCommand: String) {
    val data = hexCommand.hexToByteArray() // 使用辅助函数
    deviceConnection?.send(data)
}

fun disconnect() {
    deviceConnection?.disconnect()
}

// 在ViewModel销毁时，确保释放资源
override fun onCleared() {
    super.onCleared()
    deviceConnection?.close()
}
```

## 📝 API 详解

### `BluetoothManager`

- `init(context: Context)`: **必须调用**。初始化库。
- `bluetoothState: Flow<Boolean>`: 响应式地获取蓝牙开关状态。
- `isBluetoothEnabled(): Boolean`: 获取当前的即时状态。
- `getScanner(): ScanModule`: 获取扫描模块实例。
- `createDeviceConnection(macAddress: String): DeviceConnection`: 为指定设备创建连接实例。

### `DeviceConnection` 接口

- `connect(config, callback)`: 发起连接。
- `disconnect()`: 主动断开连接。
- `close()`: 断开并释放所有底层资源。
- `setWriteCharacteristic(...)` / `send(data)`: 便捷写入。
- `setNotificationCharacteristic(...)` / `startNotifications()`: 便捷开启通知。
- `writeCharacteristic(...)` / `enableNotifications(...)`: 显式写入和开启通知。

---