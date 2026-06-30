package dev.qtremors.arcile.core.ui.externalfile

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

class ExternalFileAccessProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val file = fileFor(uri)
        val columns = projection?.takeIf { it.isNotEmpty() }
            ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(columns).apply {
            val row: Array<Any?> = columns.map { column ->
                when (column) {
                    OpenableColumns.DISPLAY_NAME -> file.name
                    OpenableColumns.SIZE -> file.length()
                    else -> null
                }
            }.toTypedArray()
            addRow(row)
        }
    }

    override fun getType(uri: Uri): String? = null

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw SecurityException("External handoffs are read-only")
        val file = fileFor(uri)
        if (!file.isFile) throw FileNotFoundException(uri.toString())
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    private fun fileFor(uri: Uri): File {
        val context = requireNotNull(context)
        val relativePath = uri.pathSegments.joinToString(File.separator)
        val root = stagingRoot(context)
        val file = File(root, relativePath).canonicalFile
        if (file != root && !file.path.startsWith(root.path + File.separator)) {
            throw SecurityException("Path escapes external handoff staging")
        }
        return file
    }

    companion object {
        private const val STAGING_ROOT = "external_access"

        fun authority(context: Context): String =
            "${context.packageName}.externalfileaccess"

        fun stagingRoot(context: Context): File =
            File(context.cacheDir, STAGING_ROOT).canonicalFile

        fun uriFor(context: Context, file: File): Uri {
            val root = stagingRoot(context)
            val canonicalFile = file.canonicalFile
            if (canonicalFile != root && !canonicalFile.path.startsWith(root.path + File.separator)) {
                throw SecurityException("Path escapes external handoff staging")
            }
            val relativePath = canonicalFile.relativeTo(root).invariantSeparatorsPath
            return Uri.Builder()
                .scheme("content")
                .authority(authority(context))
                .encodedPath(relativePath.split('/').joinToString("/") { Uri.encode(it) })
                .build()
        }
    }
}
