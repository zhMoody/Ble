package com.coder.ble.model
import android.bluetooth.BluetoothDevice

enum class ConnectionState(val displayName: String) {
    DISCONNECTED("未连接"),
    CONNECTING("连接中"),
    RECONNECTING("重新连接中"),
    CONNECTED("已连接"),
    FAILED("连接失败"),
    TIMEOUT("连接超时")
}

/**
 * 封装了所有可能的扫描失败原因。
 */
sealed class ScanFailure {
    /**
     * 权限不足。
     * @property missingPermissions 缺失的权限列表。
     */
    data class InsufficientPermissions(val missingPermissions: List<String>) : ScanFailure()

    /**
     * 设备不支持蓝牙低功耗（BLE）。
     */
    object BleNotSupported : ScanFailure()

    /**
     * 扫描已经正在进行中。
     */
    object AlreadyScanning : ScanFailure()

    /**
     * 封装了来自 Android 操作系统的原生扫描错误。
     * @property errorCode 系统的错误码，对应于 `android.bluetooth.le.ScanCallback` 中的常量。
     */
    data class OsScanError(val errorCode: Int) : ScanFailure() {
        /**
         * 将错误码转换为可读的字符串。
         */
        fun getErrorName(): String {
            return when (errorCode) {
                1 -> "SCAN_FAILED_ALREADY_STARTED"
                2 -> "SCAN_FAILED_APPLICATION_REGISTRATION_FAILED"
                3 -> "SCAN_FAILED_INTERNAL_ERROR"
                4 -> "SCAN_FAILED_FEATURE_UNSUPPORTED"
                5 -> "SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES"
                6 -> "SCAN_FAILED_SCANNING_TOO_FREQUENTLY"
                else -> "UNKNOWN_ERROR"
            }
        }
    }
}
/**
 * 封装了扫描发现的蓝牙设备及其所有相关信息。
 *
 * @property device 底层的 Android BluetoothDevice 对象。
 * @property rssi 信号强度。
 * @property manufacturerData 解析后的厂商特定数据。
 *                          Key 是 16 位的公司标识符 (Company ID)。
 *                          Value 是厂商自定义的数据包 (ByteArray)。
 */
data class BleDevice(
    val device: BluetoothDevice,
    val rssi: Int,
    val manufacturerData: Map<Int, ByteArray>
) {
    val name: String?
        get() = device.name
    val address: String
        get() = device.address
}