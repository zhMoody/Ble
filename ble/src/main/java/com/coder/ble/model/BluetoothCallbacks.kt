package com.coder.ble.model

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.util.SparseArray


/**
 * 蓝牙扫描回调接口
 */
interface ScanCallback {
    fun onScanStarted()
    fun onScanStopped()
    fun onDeviceFound(device: BluetoothDevice, rssi: Int, scanRecord: ByteArray, manufacturerData: SparseArray<ByteArray>?)
    fun onScanFailed(failure: ScanFailure)
}

/**
 * 设备连接与通信的回调接口
 */
interface ConnectionCallback {
    fun onConnectionStateChanged(state: ConnectionState)
    fun onServicesDiscovered(services: List<BluetoothGattService>)
    fun onMtuChanged(mtu: Int)
    fun onCharacteristicRead(characteristic: BluetoothGattCharacteristic, value: ByteArray)
    fun onCharacteristicWritten(characteristic: BluetoothGattCharacteristic)
    fun onCharacteristicChanged(characteristic: BluetoothGattCharacteristic, value: ByteArray)
}