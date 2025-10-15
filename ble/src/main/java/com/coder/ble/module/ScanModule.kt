package com.coder.ble.module

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.coder.ble.utils.PermissionUtils
import com.coder.ble.models.ScanCallback
import com.coder.ble.configs.ScanConfig
import com.coder.ble.models.ScanFailure
import java.lang.ref.WeakReference

/**
 * 负责蓝牙低功耗（BLE）设备的扫描。
 *
 * 通过 `BluetoothManager.getScanner()` 获取实例。
 * @SuppressLint("MissingPermission") 用于告知编译器，我们已在调用前通过 `getMissingPermissions()` 进行了严谨的权限检查。
 */
@SuppressLint("MissingPermission")
class ScanModule internal constructor(
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter
) {
    private val TAG = "BLE: ScanModule"
    private val bluetoothLeScanner: BluetoothLeScanner? = bluetoothAdapter.bluetoothLeScanner
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isScanning = false
    private var scanCallbackRef: WeakReference<ScanCallback>? = null

    /**
     * Android 系统底层的扫描回调实现。
     */
    private val leScanCallback = object : android.bluetooth.le.ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            result?.device?.let { device ->
                // 过滤掉没有名字的设备，这在很多场景下是必要的
                if (device.name != null) {
                    // --- 核心修改点 ---
                    // 1. 从 ScanRecord 中提取厂商数据
                    val manufacturerData = result.scanRecord?.manufacturerSpecificData
                    Log.d(
                        TAG,
                        "发现设备: ${device.name} [${device.address}] RSSI: ${result.rssi}, 厂商数据: ${manufacturerData?.size() ?: 0} 条"
                    )

                    // 2. 通过更新后的回调方法传递出去
                    scanCallbackRef?.get()?.onDeviceFound(
                        device,
                        result.rssi,
                        result.scanRecord?.bytes ?: byteArrayOf(),
                        manufacturerData
                    )
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            isScanning = false
            // 使用定义的 ScanFailure.OsScanError 来封装系统错误，提供更丰富的信息
            Log.e(TAG, "系统扫描失败，错误码: $errorCode")
            scanCallbackRef?.get()?.onScanFailed(ScanFailure.OsScanError(errorCode))
            scanCallbackRef?.get()?.onScanStopped()
        }
    }

    /**
     * 开始扫描蓝牙设备。
     * @param config 扫描配置，包括过滤名称和扫描时长。
     * @param callback 用于接收扫描事件的回调。
     */
    fun startScan(config: ScanConfig, callback: ScanCallback) {
        Log.i(TAG, "请求开始扫描...")
        if (isScanning) {
            Log.w(TAG, "扫描失败：上一次扫描仍在进行中。")
            callback.onScanFailed(ScanFailure.AlreadyScanning)
            return
        }

        val missingPermissions = getMissingPermissions()
        if (missingPermissions.isNotEmpty()) {
            Log.e(TAG, "扫描失败：缺少必要权限: $missingPermissions")
            callback.onScanFailed(ScanFailure.InsufficientPermissions(missingPermissions))
            return
        }

        if (bluetoothLeScanner == null) {
            Log.e(TAG, "扫描失败：此设备不支持 BLE。")
            callback.onScanFailed(ScanFailure.BleNotSupported)
            return
        }

        this.scanCallbackRef = WeakReference(callback)
        val filters = buildScanFilters(config.deviceNameFilter)
        val settings = buildScanSettings()

        isScanning = true
        bluetoothLeScanner.startScan(filters, settings, leScanCallback)
        Log.i(TAG, "扫描已正式开始，持续时间: ${config.scanDuration} 毫秒。")
        callback.onScanStarted()

        // 使用 Handler 实现扫描超时自动停止
        mainHandler.postDelayed({
            if (isScanning) {
                Log.i(TAG, "扫描时间到达，自动停止扫描。")
                stopScan()
            }
        }, config.scanDuration)
    }

    /**
     * 主动停止正在进行的扫描。
     */
    fun stopScan() {
        if (!isScanning || bluetoothLeScanner == null) {
            return
        }
        Log.i(TAG, "用户主动停止扫描。")
        // 停止扫描也需要权限
        if (getMissingPermissions().isEmpty()) {
            bluetoothLeScanner.stopScan(leScanCallback)
        }
        isScanning = false
        scanCallbackRef?.get()?.onScanStopped()
        // 清理资源，防止内存泄漏
        scanCallbackRef?.clear()
        mainHandler.removeCallbacksAndMessages(null) // 移除超时停止的回调
    }

    /**
     * 检查并返回当前缺失的扫描权限列表。
     */
    private fun getMissingPermissions(): List<String> {
        return PermissionUtils.getRequiredScanPermissions().filter { permission ->
            context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * 根据配置构建用于系统 API 的 ScanFilter 列表。
     */
    private fun buildScanFilters(deviceNames: List<String>?): List<ScanFilter> {
        val filters = mutableListOf<ScanFilter>()
        deviceNames?.forEach { name ->
            val filter = ScanFilter.Builder()
                .setDeviceName(name)
                .build()
            filters.add(filter)
        }
        return filters
    }

    /**
     * 构建用于系统 API 的 ScanSettings。
     */
    private fun buildScanSettings(): ScanSettings {
        return ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) // 使用低延迟模式，更快发现设备
            .build()
    }
}