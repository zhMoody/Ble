package com.coder.ble.configs

data class ScanConfig(
    val deviceNameFilter: List<String>? = null,
    val scanDuration: Long = 10000L
)

data class ReconnectionConfig(
    val enabled: Boolean = false,
    val attempts: Int = 3,
    val initialDelayMillis: Long = 1000L,
    val backoffMultiplier: Double = 1.5,
    val maxDelayMillis: Long = 30000L
)

data class ConnectionConfig(
    val connectTimeoutMillis: Long = 15000L,
    val desiredMtu: Int? = 517,
    val reconnectionConfig: ReconnectionConfig = ReconnectionConfig()
)