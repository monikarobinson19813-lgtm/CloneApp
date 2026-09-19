package com.cloneapp.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkImportSupportTest {
    @Test
    fun apkNameValidationIsCaseInsensitive() {
        assertTrue(ApkImportSupport.isApkFileName("CA-Test-App-debug.apk"))
        assertTrue(ApkImportSupport.isApkFileName("sample.APK"))
        assertFalse(ApkImportSupport.isApkFileName("sample.zip"))
        assertFalse(ApkImportSupport.isApkFileName("sample.apk.txt"))
    }

    @Test
    fun zipMagicValidationRequiresLocalFileHeader() {
        assertTrue(ApkImportSupport.hasZipMagic(byteArrayOf(0x50, 0x4b, 0x03, 0x04)))
        assertFalse(ApkImportSupport.hasZipMagic(byteArrayOf(0x50, 0x4b, 0x05, 0x06)))
        assertFalse(ApkImportSupport.hasZipMagic(byteArrayOf(0x50, 0x4b)))
    }

    @Test
    fun hexEncodingIsDeterministic() {
        assertEquals(
            "000f10ff",
            ApkImportSupport.toHex(byteArrayOf(0x00, 0x0f, 0x10, 0xff.toByte()))
        )
    }
}
