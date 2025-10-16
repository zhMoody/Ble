// MainViewModel.kt (升级版)

package com.coder.exble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.util.SparseArray
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coder.ble.BluetoothManager
import com.coder.ble.models.ConnectionCallback
import com.coder.ble.configs.ConnectionConfig
import com.coder.ble.models.ConnectionState
import com.coder.ble.models.DeviceConnection
import com.coder.ble.configs.ScanConfig
import com.coder.ble.models.ScanFailure
import com.coder.ble.models.ScanCallback
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

data class ScannedDevice(
    val device: BluetoothDevice,
    val rssi: Int,
    val manufacturerData: SparseArray<ByteArray>?
)

class MainViewModel : ViewModel() {

    private val scanner = BluetoothManager.getScanner()
    private var deviceConnection: DeviceConnection? = null

    // --- 状态 ---
    val isBluetoothEnabled = BluetoothManager.bluetoothState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BluetoothManager.isBluetoothEnabled())
    val interactingDevice = mutableStateOf<ScannedDevice?>(null)
    val isScanning = mutableStateOf(false)
    val devices = mutableStateListOf<ScannedDevice>()
    val connectionState = mutableStateOf(ConnectionState.DISCONNECTED)
    val receivedData = mutableStateOf("")

    // --- 用于服务和特征选择 ---
    val services = mutableStateListOf<BluetoothGattService>()
    val characteristics = mutableStateListOf<BluetoothGattCharacteristic>()
    val selectedService = mutableStateOf<BluetoothGattService?>(null)

    // 标记是否已经完成了特征的选择，用于控制UI显示
    val isCharacteristicSelected = mutableStateOf(false)

    // --- 扫描逻辑 ---
    fun startScan() {
        if (isScanning.value) return
        devices.clear()
        val scanConfig = ScanConfig(
            scanDuration = 15000L, // 扫描15秒
            deviceNameFilter = listOf("JL", "MFR:21", "MFR:23")
        )

        scanner.startScan(scanConfig, object : ScanCallback {
            override fun onScanStarted() {
                isScanning.value = true
            }

            override fun onScanStopped() {
                isScanning.value = false
            }

            override fun onScanFailed(failure: ScanFailure) {
                isScanning.value = false
            }

            override fun onDeviceFound(
                device: BluetoothDevice,
                rssi: Int,
                scanRecord: ByteArray,
                manufacturerData: SparseArray<ByteArray>?
            ) {
                if (!devices.any { it.device.address == device.address }) {
                    devices.add(ScannedDevice(device, rssi, manufacturerData))
                }
            }
        })
    }

    fun stopScan() {
        scanner.stopScan()
    }

    fun connectToDevice(scannedDevice: ScannedDevice) {
//        clearConnectionStates()
        if (deviceConnection != null) {
            deviceConnection?.close()
        }
        this.interactingDevice.value = scannedDevice
        val device = scannedDevice.device
        // 确保清理旧的连接实例，以防万一
        deviceConnection?.close()
        deviceConnection = BluetoothManager.createDeviceConnection(device.address)
        val connectionConfig = ConnectionConfig()


        deviceConnection?.connect(connectionConfig, object : ConnectionCallback {
            override fun onConnectionStateChanged(state: ConnectionState) {
                connectionState.value = state
                if (state == ConnectionState.DISCONNECTED || state == ConnectionState.FAILED || state == ConnectionState.TIMEOUT) {
                    clearConnectionStates()
                }
            }

            override fun onServicesDiscovered(discoveredServices: List<BluetoothGattService>) {
                services.clear()
                services.addAll(discoveredServices)
            }

            override fun onMtuChanged(mtu: Int) {
            }

            override fun onCharacteristicRead(
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray
            ) {
            }

            override fun onCharacteristicWritten(characteristic: BluetoothGattCharacteristic) {
            }

            override fun onCharacteristicChanged(characteristic: BluetoothGattCharacteristic, value: ByteArray) {
                receivedData.value = value.toHexString()
            }
        })
    }

    /** 用户从UI上选择了一个服务 */
    fun selectService(service: BluetoothGattService) {
        selectedService.value = service
        characteristics.clear()
        characteristics.addAll(service.characteristics)
    }

    fun selectCharacteristic(characteristic: BluetoothGattCharacteristic) {
        // 根据特征的属性，自动设置它
        // 一个特征可能同时支持多种操作
        val properties = characteristic.properties
        if ((properties and BluetoothGattCharacteristic.PROPERTY_WRITE) > 0 ||
            (properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) > 0
        ) {
            deviceConnection?.setWriteCharacteristic(characteristic.service.uuid, characteristic.uuid)
        }
        if ((properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0 ||
            (properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) > 0
        ) {
            deviceConnection?.setNotificationCharacteristic(characteristic.service.uuid, characteristic.uuid)
            deviceConnection?.startNotifications()
        }

        isCharacteristicSelected.value = true // 标记选择完成
    }

    fun disconnect() {
        deviceConnection?.disconnect()
        clearConnectionStates()
    }

    fun sendData(text: String) {
        if (text.isNotBlank()) {
            // 使用新的转换函数
            val dataToSend = text.hexToByteArray()

            // 增加一个检查，如果转换失败（例如输入了"G"或奇数长度的字符串），则不发送
            if (dataToSend.isNotEmpty()) {
                deviceConnection?.send(dataToSend)
                print("[发送] HEX: $text")
            } else {
                // 可以给用户一个提示，说明输入的十六进制格式不正确
                print("[错误] 无效的十六进制字符串: $text")
            }
        }
    }

    /** 清理所有与连接相关的状态 */
    private fun clearConnectionStates() {
        interactingDevice.value = null
        connectionState.value = ConnectionState.DISCONNECTED
        services.clear()
        characteristics.clear()
        selectedService.value = null
        isCharacteristicSelected.value = false
    }

    override fun onCleared() {
        super.onCleared()
        deviceConnection?.close()
    }
}

fun String.hexToByteArray(): ByteArray {
    // 确保字符串长度为偶数，且不含非法字符
    if (this.length % 2 != 0 || !this.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
        return byteArrayOf()
    }

    return ByteArray(this.length / 2) { i ->
        this.substring(i * 2, i * 2 + 2).toInt(16).toByte()
    }
}

/**
 * 将 ByteArray 转换为十六进制字符串。
 * @param separator 字节之间的分隔符，默认为无分隔。可以传入 " " 让字节之间有空格，更易读。
 * @return 转换后的十六进制字符串。例如: "0A1BFF"
 */
fun ByteArray.toHexString(separator: String = ""): String {
    return this.joinToString(separator) {
        "%02X".format(it) // %02X 表示输出两位大写的十六进制数，不足两位前面补0
    }
}