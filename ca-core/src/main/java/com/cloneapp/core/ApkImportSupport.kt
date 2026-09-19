package com.cloneapp.core

object ApkImportSupport {
    fun isApkFileName(name: String): Boolean =
        name.trim().lowercase().endsWith(".apk")

    fun hasZipMagic(prefix: ByteArray): Boolean =
        prefix.size >= 4 &&
            prefix[0] == 0x50.toByte() &&
            prefix[1] == 0x4b.toByte() &&
            prefix[2] == 0x03.toByte() &&
            prefix[3] == 0x04.toByte()

    fun toHex(bytes: ByteArray): String =
        bytes.joinToString(separator = "") { "%02x".format(it) }
}
