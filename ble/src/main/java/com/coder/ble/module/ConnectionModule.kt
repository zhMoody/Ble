package com.coder.ble.module

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.coder.ble.models.ConnectionCallback
import com.coder.ble.configs.ConnectionConfig
import com.coder.ble.models.ConnectionState
import com.coder.ble.models.DeviceConnection
import java.lang.ref.WeakReference
import java.util.UUID
import android.util.Log
import com.coder.ble.BluetoothManager

/**
 * 实现了 DeviceConnection 接口的核心类。
 *
 * 管理单个蓝牙设备的连接、数据交换、重连等所有操作。
 * 这个类的实例应该是短暂的，每个连接会话都通过 `BluetoothManager.createDeviceConnection()` 创建一个新的实例。
 *
 * @SuppressLint("MissingPermission") 用于告知编译器，已在调用前手动检查了权限。
 */
@SuppressLint("MissingPermission")
class ConnectionModule internal constructor(
    private val context: Context,
    private val macAddress: String
) : DeviceConnection {
    private val TAG = "BLE: ConnectionModule"

    // --- 核心属性 ---
    private val mainHandler = Handler(Looper.getMainLooper())
    private var bluetoothGatt: BluetoothGatt? = null
    private var callbackRef: WeakReference<ConnectionCallback>? = null
    private var connectionConfig: ConnectionConfig = ConnectionConfig()

    // --- 状态管理 ---
    private var isUserDisconnect = false
    private var reconnectionAttemptsLeft = 0
    private var currentReconnectDelay = 0L
    private var currentState: ConnectionState = ConnectionState.DISCONNECTED
        set(value) {
            field = value
            mainHandler.post { callbackRef?.get()?.onConnectionStateChanged(value) }
        }

    // --- 便捷调用属性 ---
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var readCharacteristic: BluetoothGattCharacteristic? = null
    private var notificationCharacteristic: BluetoothGattCharacteristic? = null

    private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")


    // --- Android 蓝牙回调 ---
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val deviceAddress = gatt.device.address
            Log.d(TAG, "[${deviceAddress}] 连接状态改变: status=$status, newState=$newState")
            mainHandler.removeCallbacks(connectionTimeoutRunnable) // 移除连接超时

            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "[$deviceAddress] --> GATT 连接成功。")
                handleConnectionSuccess(gatt)
            } else {
                // 【所有】其他情况 (断开, 失败, 蓝牙关闭等) 都被视为连接终止
                Log.w(TAG, "[$deviceAddress] --> GATT 连接终止。 status=$status, newState=$newState")
                handleConnectionTermination()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "[${gatt.device.address}] 发现服务成功，共找到 ${gatt.services.size} 个服务。")
                mainHandler.post { callbackRef?.get()?.onServicesDiscovered(gatt.services) }
            } else {
                Log.w(TAG, "[${gatt.device.address}] 发现服务失败, status: $status")
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "[${gatt.device.address}] MTU 设置成功，新值为 $mtu。即将开始发现服务...")
                mainHandler.post { callbackRef?.get()?.onMtuChanged(mtu) }
                // MTU 设置成功后，立即开始发现服务
                gatt.discoverServices()
            } else {
                Log.w(TAG, "[${gatt.device.address}] MTU 设置失败, status $status。仍然尝试发现服务。")
                gatt.discoverServices()
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "[${gatt.device.address}] 特征读取成功: ${characteristic.uuid}")
                mainHandler.post { callbackRef?.get()?.onCharacteristicRead(characteristic, characteristic.value) }
            } else {
                Log.w(TAG, "[${gatt.device.address}] 特征读取失败: ${characteristic.uuid}, status: $status")
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "[${gatt.device.address}] 特征写入成功: ${characteristic.uuid}")
                mainHandler.post { callbackRef?.get()?.onCharacteristicWritten(characteristic) }
            } else {
                Log.w(TAG, "[${gatt.device.address}] 特征写入失败: ${characteristic.uuid}, status: $status")
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            Log.d(
                TAG,
                "[${gatt.device.address}] 收到特征通知: ${characteristic.uuid}, 数据: ${characteristic.value.toHexString()}"
            )
            mainHandler.post { callbackRef?.get()?.onCharacteristicChanged(characteristic, characteristic.value) }
        }
    }

    // --- `DeviceConnection` 接口实现 ---

    override fun getConnectionState(): ConnectionState = currentState
    private fun ByteArray.toHexString() = joinToString(separator = " ") { "%02x".format(it) }
    override fun connect(config: ConnectionConfig, callback: ConnectionCallback) {
        Log.i(TAG, "[$macAddress] 调用 connect() 方法。")
        if (currentState == ConnectionState.CONNECTING || currentState == ConnectionState.CONNECTED) return

        if (!hasConnectPermission()) {
            // 注意：没有专门的回调来通知权限问题，因为这应该在调用前由 App 解决。
            // 可以通过 PermissionUtils 自行检查。
            Log.w(TAG, "[$macAddress] 调用 connect() 时设备已处于连接中或已连接状态，本次操作忽略。")
            return
        }
        Log.d(TAG, "[$macAddress] 开始连接流程...")
        this.connectionConfig = config
        this.callbackRef = WeakReference(callback)
        isUserDisconnect = false
        val bluetoothAdapter = BluetoothManager.getAdapter()
        if (bluetoothAdapter == null) {
            currentState = ConnectionState.FAILED
            return
        }

        val device = bluetoothAdapter.getRemoteDevice(macAddress)
        currentState = ConnectionState.CONNECTING

        mainHandler.postDelayed(connectionTimeoutRunnable, config.connectTimeoutMillis)
        bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, gattCallback)
        }
    }

    override fun disconnect() {
        Log.i(TAG, "[$macAddress] 用户主动调用 disconnect()。")
        isUserDisconnect = true
        stopReconnectionAttempts()
        bluetoothGatt?.disconnect()
    }

    override fun close() {
        Log.i(TAG, "[$macAddress] 调用 close()。正在释放所有资源。")
        disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        callbackRef?.clear()
        mainHandler.removeCallbacksAndMessages(null)
    }

    override fun getDiscoveredServices(): List<BluetoothGattService>? = bluetoothGatt?.services

    // --- 便捷调用实现 ---

    override fun setWriteCharacteristic(serviceUUID: UUID, characteristicUUID: UUID): Boolean {
        writeCharacteristic = getCharacteristic(serviceUUID, characteristicUUID)
        return writeCharacteristic != null
    }

    override fun setReadCharacteristic(serviceUUID: UUID, characteristicUUID: UUID): Boolean {
        readCharacteristic = getCharacteristic(serviceUUID, characteristicUUID)
        return readCharacteristic != null
    }

    override fun setNotificationCharacteristic(serviceUUID: UUID, characteristicUUID: UUID): Boolean {
        notificationCharacteristic = getCharacteristic(serviceUUID, characteristicUUID)
        return notificationCharacteristic != null
    }

    override fun send(data: ByteArray, writeType: Int): Boolean {
        val characteristic = writeCharacteristic ?: run {
            Log.w(TAG, "[$macAddress] 发送失败：未设置写入特征。")
            return false
        }
        Log.d(TAG, "[$macAddress] 准备发送数据 (大小: ${data.size}字节) 到特征: ${characteristic.uuid}")
        return writeCharacteristic(characteristic, data, writeType)
    }

    override fun read(): Boolean {
        val characteristic = readCharacteristic ?: return false
        return readCharacteristic(characteristic)
    }

    override fun startNotifications(): Boolean {
        val characteristic = notificationCharacteristic ?: return false
        return setNotification(characteristic, true)
    }

    override fun stopNotifications(): Boolean {
        val characteristic = notificationCharacteristic ?: return false
        return setNotification(characteristic, false)
    }

    // --- 显式调用实现 ---

    override fun readCharacteristic(serviceUUID: UUID, characteristicUUID: UUID): Boolean {
        val characteristic = getCharacteristic(serviceUUID, characteristicUUID) ?: return false
        return readCharacteristic(characteristic)
    }

    override fun writeCharacteristic(
        serviceUUID: UUID,
        characteristicUUID: UUID,
        data: ByteArray,
        writeType: Int
    ): Boolean {
        val characteristic = getCharacteristic(serviceUUID, characteristicUUID) ?: return false
        return writeCharacteristic(characteristic, data, writeType)
    }

    override fun enableNotifications(serviceUUID: UUID, characteristicUUID: UUID): Boolean {
        val characteristic = getCharacteristic(serviceUUID, characteristicUUID) ?: return false
        return setNotification(characteristic, true)
    }

    override fun disableNotifications(serviceUUID: UUID, characteristicUUID: UUID): Boolean {
        val characteristic = getCharacteristic(serviceUUID, characteristicUUID) ?: return false
        return setNotification(characteristic, false)
    }

    // --- 私有辅助方法 ---
    private fun handleConnectionTermination() {
        // 检查是否是【意外】断开并且【需要】重连
        if (!isUserDisconnect && connectionConfig.reconnectionConfig.enabled) {
            Log.i(TAG, "[$macAddress] 连接意外终止。即将启动重连程序。")
            startReconnection()
        } else {
            // 否则 (用户主动断开, 无需重连, 或连接失败)，彻底关闭
            Log.i(TAG, "[$macAddress] 连接彻底终止。即将关闭GATT。")
            // 在这里设置最终状态，并触发回调
            if (currentState == ConnectionState.CONNECTING) {
                // 如果是从连接中状态过来的失败，我们认为是连接失败
                currentState = ConnectionState.FAILED
            } else {
                currentState = ConnectionState.DISCONNECTED
            }
            // 最终调用 close 来清理资源
            close()
        }
    }

    private fun handleConnectionSuccess(gatt: BluetoothGatt) {
        bluetoothGatt = gatt
        currentState = ConnectionState.CONNECTED
        stopReconnectionAttempts() // 成功连接后重置重连计数器

        // 开始连接后的流程：请求 MTU -> 发现服务
        connectionConfig.desiredMtu?.let { mtu ->
            Log.d(TAG, "[$macAddress] 连接成功。正在请求 MTU: $mtu")
            gatt.requestMtu(mtu)
        } ?: run {
            Log.d(TAG, "[$macAddress] 连接成功，无需请求 MTU。正在发现服务。")
            gatt.discoverServices() // 如果不请求 MTU，直接发现服务
        }
    }


    private val connectionTimeoutRunnable = Runnable {
        if (currentState == ConnectionState.CONNECTING) {
            Log.w(TAG, "[$macAddress] 连接超时 (>${connectionConfig.connectTimeoutMillis}毫秒)。")
            currentState = ConnectionState.TIMEOUT
            close()
        }
    }

    private fun startReconnection() {
        if (reconnectionAttemptsLeft <= 0) { // 如果已经没有重试次数
            Log.w(TAG, "[$macAddress] 无剩余重连次数，连接彻底断开。")
            currentState = ConnectionState.DISCONNECTED
            close()
            return
        }
        Log.i(
            TAG,
            "[$macAddress] 计划在 ${currentReconnectDelay}毫秒 后进行重连。剩余尝试次数: $reconnectionAttemptsLeft"
        )
        currentState = ConnectionState.RECONNECTING
        mainHandler.postDelayed({
            if (currentState == ConnectionState.RECONNECTING) {
                // 再次尝试连接
                connect(connectionConfig, callbackRef?.get() ?: return@postDelayed)
            }
        }, currentReconnectDelay)

        // 更新下一次的延迟
        currentReconnectDelay =
            (currentReconnectDelay.toDouble() * connectionConfig.reconnectionConfig.backoffMultiplier).toLong()
                .coerceAtMost(connectionConfig.reconnectionConfig.maxDelayMillis)
        reconnectionAttemptsLeft--
    }

    private fun stopReconnectionAttempts() {
        mainHandler.removeCallbacks(reconnectionRunnable)
        reconnectionAttemptsLeft = connectionConfig.reconnectionConfig.attempts
        currentReconnectDelay = connectionConfig.reconnectionConfig.initialDelayMillis
    }

    private val reconnectionRunnable = Runnable {
        if (currentState == ConnectionState.RECONNECTING) {
            Log.i(TAG, "[$macAddress] 正在执行重连尝试...")
        }
        startReconnection()
    }

    private fun getCharacteristic(serviceUUID: UUID, characteristicUUID: UUID): BluetoothGattCharacteristic? {
        return bluetoothGatt?.getService(serviceUUID)?.getCharacteristic(characteristicUUID)
    }

    private fun readCharacteristic(characteristic: BluetoothGattCharacteristic): Boolean {
        return bluetoothGatt?.readCharacteristic(characteristic) ?: false
    }

    private fun writeCharacteristic(
        characteristic: BluetoothGattCharacteristic,
        data: ByteArray,
        writeType: Int
    ): Boolean {
        characteristic.writeType = writeType
        characteristic.value = data
        return bluetoothGatt?.writeCharacteristic(characteristic) ?: false
    }

    private fun setNotification(characteristic: BluetoothGattCharacteristic, enable: Boolean): Boolean {
        if (bluetoothGatt?.setCharacteristicNotification(characteristic, enable) == false) {
            return false
        }
        val descriptor = characteristic.getDescriptor(CCCD_UUID) ?: return false
        val value =
            if (enable) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
        descriptor.value = value
        return bluetoothGatt?.writeDescriptor(descriptor) ?: false
    }

    private fun hasConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true // 低版本不需要独立的连接权限
        }
    }
}