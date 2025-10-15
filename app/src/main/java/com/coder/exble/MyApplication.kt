package com.coder.exble
import android.app.Application
import com.coder.ble.BluetoothManager

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // 在这里全局初始化我们的蓝牙库
        BluetoothManager.init(this)
    }
}