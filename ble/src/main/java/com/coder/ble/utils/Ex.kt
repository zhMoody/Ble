package com.coder.ble.utils

fun String.hexToByteArray(): ByteArray {
    if (this.length % 2 != 0 || !this.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return byteArrayOf()
    return ByteArray(this.length / 2) { i -> this.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
}

fun ByteArray.endsWith(suffix: ByteArray): Boolean {
    if (suffix.isEmpty()) return true
    if (suffix.size > this.size) return false
    return this.sliceArray(this.size - suffix.size..this.lastIndex).contentEquals(suffix)
}

fun ByteArray.toHexString() = joinToString(separator = " ") { "%02x".format(it) }