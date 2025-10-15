package com.coder.ble.models

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import com.coder.ble.configs.ConnectionConfig
import java.util.UUID

/**
 * 定义了与单个蓝牙设备进行交互的所有可用操作。
 */
interface DeviceConnection {
    fun getConnectionState(): ConnectionState

    // 修正了这里的回调类型，使用我们自己的 ConnectionCallback
    fun connect(config: ConnectionConfig = ConnectionConfig(), callback: ConnectionCallback)

    fun disconnect()
    fun close()
    fun getDiscoveredServices(): List<BluetoothGattService>?

    // --- 便捷调用 ---
    fun setWriteCharacteristic(serviceUUID: UUID, characteristicUUID: UUID): Boolean
    fun setReadCharacteristic(serviceUUID: UUID, characteristicUUID: UUID): Boolean
    fun setNotificationCharacteristic(serviceUUID: UUID, characteristicUUID: UUID): Boolean
    fun send(data: ByteArray, writeType: Int = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT): Boolean
    fun read(): Boolean
    fun startNotifications(): Boolean
    fun stopNotifications(): Boolean

    // --- 显式调用 ---
    fun readCharacteristic(serviceUUID: UUID, characteristicUUID: UUID): Boolean
    fun writeCharacteristic(
        serviceUUID: UUID,
        characteristicUUID: UUID,
        data: ByteArray,
        writeType: Int = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
    ): Boolean

    fun enableNotifications(serviceUUID: UUID, characteristicUUID: UUID): Boolean
    fun disableNotifications(serviceUUID: UUID, characteristicUUID: UUID): Boolean
}