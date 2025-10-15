package com.coder.ble

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager as AndroidBluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.coder.ble.models.DeviceConnection
import com.coder.ble.module.ConnectionModule
import com.coder.ble.module.ScanModule
import com.coder.ble.module.StateModule
import kotlinx.coroutines.flow.Flow

/**
 * 蓝牙库的统一入口和核心管理器。
 *
 * 这是一个单例对象 (Singleton)，在整个应用程序中应只存在一个实例。
 *
 * **使用步骤:**
 * 1. 在你的 Application 类的 `onCreate` 方法中调用 `BluetoothManager.init(this)`。
 * 2. 在需要的地方，通过 `BluetoothManager.getScanner()` 或 `BluetoothManager.createDeviceConnection()` 获取功能模块。
 * 3. 通过 `BluetoothManager.bluetoothState` 以响应式的方式监听蓝牙的开关状态。
 */
object BluetoothManager {
    private const val TAG = "BLE: BluetoothManager"
    // --- 私有核心属性 ---

    private var applicationContext: Context? = null

    /**
     * 懒加载的系统蓝牙适配器实例。
     * 只有在第一次被访问时才会初始化。
     */
    private val bluetoothAdapter: BluetoothAdapter? by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val bluetoothManager =
            applicationContext?.getSystemService(Context.BLUETOOTH_SERVICE) as? AndroidBluetoothManager
        bluetoothManager?.adapter
    }

    /**
     * 懒加载的蓝牙状态模块实例。
     */
    private val stateModule: StateModule by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        checkInitialized()
        if (!isBluetoothSupported()) {
            throw IllegalStateException("Bluetooth is not supported on this device.")
        }
        // 此时 applicationContext 和 bluetoothAdapter 必定不为 null
        StateModule(applicationContext!!, bluetoothAdapter!!)
    }


    // --- 公开状态和功能 ---

    /**
     * 全局可监听的蓝牙状态。
     * 这是一个响应式数据流 (Flow)，它会发布最新的蓝牙状态（true=开启, false=关闭）。
     * 推荐在 CoroutineScope (如 viewModelScope 或 lifecycleScope) 中使用 `.collect{}` 来监听。
     *
     * @see StateModule.bluetoothState
     */
    val bluetoothState: Flow<Boolean> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        stateModule.bluetoothState
    }

    /**
     * 初始化蓝牙管理器。**必须在 Application 的 `onCreate` 中调用一次。**
     * @param context 应用程序上下文。内部会自动获取 ApplicationContext 以防止内存泄漏。
     */
    fun init(context: Context) {
        if (this.applicationContext == null) {
            this.applicationContext = context.applicationContext
            Log.i(TAG, "蓝牙管理器初始化成功。")
        }
    }

    /**
     * 检查设备是否支持蓝牙。
     * @return true 如果设备有蓝牙模块，否则 false。
     */
    fun isBluetoothSupported(): Boolean {
        checkInitialized()
        return bluetoothAdapter != null
    }

    /**
     * 检查蓝牙当前是否已开启。
     * 这是一个即时快照，如需持续监听，请使用 `bluetoothState` Flow。
     * @return true 如果蓝牙已开启，否则 false。
     */
    fun isBluetoothEnabled(): Boolean {
        checkInitialized()
        return bluetoothAdapter?.isEnabled == true
    }

    /**
     * 请求用户开启蓝牙（如果当前已关闭）。
     * 这会弹出一个系统对话框。你需要在调用此方法的 Activity 的 onActivityResult 中处理结果。
     *
     * **注意：** 从 Android 12 (API 31) 开始，此操作需要 `BLUETOOTH_CONNECT` 权限。
     * 如果没有该权限，此方法将不会执行任何操作。
     *
     * @param activity 用于启动系统意图 (Intent) 的 Activity。
     * @param requestCode 用于在 `onActivityResult` 中识别此请求的请求码。
     */
    fun requestEnableBluetooth(activity: Activity, requestCode: Int) {
        checkInitialized()
        // 对于 Android 12+，必须先有 BLUETOOTH_CONNECT 权限才能弹窗
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (applicationContext?.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                // 如果没有权限，则静默失败，因为库不应该自己去请求权限。
                // 开发者应该在调用此方法前确保权限已授予。
                Log.w(TAG, "请求开启蓝牙失败：缺少 BLUETOOTH_CONNECT 权限。")
                return
            }
        }

        if (bluetoothAdapter?.isEnabled == false) {
            Log.d(TAG, "蓝牙未开启，正在向用户请求开启蓝牙。")
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            activity.startActivityForResult(enableBtIntent, requestCode)
        } else {
            Log.d(TAG, "请求开启蓝牙：蓝牙已经处于开启状态。")
        }
    }

    // --- 模块工厂方法 ---

    /**
     * 获取扫描模块的实例。
     * @return 一个 `ScanModule` 实例，可用于发现 BLE 设备。
     */
    fun getScanner(): ScanModule {
        checkInitialized()
        if (!isBluetoothSupported()) {
            throw IllegalStateException("Bluetooth is not supported on this device.")
        }
        Log.d(TAG, "获取扫描模块实例。")
        return ScanModule(applicationContext!!, bluetoothAdapter!!)
    }

    /**
     * 为指定的蓝牙设备创建一个连接和通信的实例。
     * @param macAddress 目标设备的 MAC 地址。
     * @return 一个实现了 `DeviceConnection` 接口的实例，可用于后续的连接和数据操作。
     */
    fun createDeviceConnection(macAddress: String): DeviceConnection {
        checkInitialized()
        if (!isBluetoothSupported()) {
            throw IllegalStateException("Bluetooth is not supported on this device.")
        }
        if (!BluetoothAdapter.checkBluetoothAddress(macAddress)) {
            throw IllegalArgumentException("Invalid Bluetooth MAC address provided: $macAddress")
        }

        // 我们将在下一步实现 ConnectionModule
        Log.d(TAG, "为设备 [$macAddress] 创建连接模块实例。")
        return ConnectionModule(applicationContext!!, bluetoothAdapter!!, macAddress)
    }

    // --- 私有辅助方法 ---

    /**
     * 检查 `init()` 方法是否已被调用。
     * 如果未初始化，则抛出异常。
     */
    private fun checkInitialized() {
        if (applicationContext == null) {
            Log.e(TAG, "严重错误：蓝牙管理器必须先初始化！请在 Application 的 onCreate 中调用 init()。")
            throw IllegalStateException("BluetoothManager must be initialized first. Call BluetoothManager.init(context) in your Application's onCreate method.")
        }
    }
}