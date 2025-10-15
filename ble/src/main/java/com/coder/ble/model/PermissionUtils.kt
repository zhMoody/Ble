package com.coder.ble.model

import android.Manifest
import android.os.Build

/**
 * 蓝牙权限相关的工具类
 */
object PermissionUtils {

    /**
     * 根据当前 Android 系统版本，获取进行蓝牙扫描所必需的权限列表。
     * @return 一个包含所需权限字符串的数组，可以直接用于 `ActivityCompat.requestPermissions`。
     */
    fun getRequiredScanPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12 (API 31) 及以上版本
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT // 连接也需要，通常扫描和连接是一起请求的
            )
        } else {
            // Android 11 (API 30) 及以下版本
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION // 低版本扫描必须有位置权限
            )
        }
    }
}