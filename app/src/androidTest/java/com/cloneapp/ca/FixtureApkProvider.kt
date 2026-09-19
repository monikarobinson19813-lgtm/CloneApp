package com.cloneapp.ca

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File

class FixtureApkProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val columns = projection?.map { it }?.toTypedArray()
            ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val cursor = MatrixCursor(columns)
        val file = fileFor(uri)
        val displayName = when (uri.lastPathSegment) {
            INVALID_PATH -> INVALID_APK_NAME
            else -> SOURCE_APK_NAME
        }
        val row = cursor.newRow()
        columns.forEach { column ->
            when (column) {
                OpenableColumns.DISPLAY_NAME -> row.add(displayName)
                OpenableColumns.SIZE -> row.add(file.length())
                else -> row.add(null)
            }
        }
        return cursor
    }

    override fun getType(uri: Uri): String = APK_MIME_TYPE

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
        ParcelFileDescriptor.open(fileFor(uri), ParcelFileDescriptor.MODE_READ_ONLY)

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("Fixture provider is read-only")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("Fixture provider is read-only")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException("Fixture provider is read-only")

    private fun fileFor(uri: Uri): File {
        val providerContext = requireNotNull(context)
        return if (uri.lastPathSegment == INVALID_PATH) {
            File(providerContext.cacheDir, INVALID_APK_NAME).apply {
                if (!exists()) {
                    writeText("this is deliberately not an APK archive")
                }
            }
        } else {
            val applicationInfo = providerContext.packageManager.getApplicationInfo(
                TEST_APP_PACKAGE,
                0
            )
            File(applicationInfo.sourceDir)
        }
    }

    companion object {
        private const val AUTHORITY = "com.cloneapp.ca.test.fixture"
        private const val TEST_APP_PACKAGE = "com.cloneapp.testapp"
        private const val VALID_PATH = "valid"
        private const val INVALID_PATH = "invalid"
        private const val SOURCE_APK_NAME = "CA-Test-App-debug.apk"
        private const val INVALID_APK_NAME = "Invalid-Guest.apk"
        private const val APK_MIME_TYPE = "application/vnd.android.package-archive"

        fun validApkUri(): Uri = Uri.parse("content://$AUTHORITY/$VALID_PATH")
        fun invalidApkUri(): Uri = Uri.parse("content://$AUTHORITY/$INVALID_PATH")
    }
}
