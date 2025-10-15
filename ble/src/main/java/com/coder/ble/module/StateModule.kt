// StateModule.kt (V2 - 现代化版本)

package com.coder.ble.module

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * 负责监听系统蓝牙适配器的状态（开启/关闭），并以响应式数据流 (Flow) 的形式提供。
 */
class StateModule internal constructor(
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter
) {
    /**
     * 一个可监听的蓝牙状态数据流。
     * - 当 Flow 开始被收集 (collect) 时，会自动注册 BroadcastReceiver。
     * - 当 Flow 停止被收集时，会自动注销 BroadcastReceiver，以节省资源。
     *
     * @return 返回一个 Flow<Boolean>，true 表示蓝牙已开启，false 表示已关闭。
     */
    val bluetoothState: Flow<Boolean> = callbackFlow {
        // 发送初始状态
        trySend(bluetoothAdapter.isEnabled)

        // 创建并注册 BroadcastReceiver
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    if (state == BluetoothAdapter.STATE_ON) {
                        trySend(true) // 发送新状态：开启
                    } else if (state == BluetoothAdapter.STATE_OFF) {
                        trySend(false) // 发送新状态：关闭
                    }
                }
            }
        }
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        // 当 Flow 被取消或关闭时，执行此代码块进行清理
        awaitClose {
            context.unregisterReceiver(receiver)
        }
    }
}