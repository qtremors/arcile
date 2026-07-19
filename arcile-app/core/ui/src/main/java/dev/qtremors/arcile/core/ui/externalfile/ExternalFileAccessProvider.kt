package dev.qtremors.arcile.core.ui.externalfile

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.ConcurrentHashMap

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

    override fun getType(uri: Uri): String {
        val extension = fileFor(uri).extension.lowercase()
        return when (extension) {
            "glb" -> "model/gltf-binary"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                ?: "application/octet-stream"
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw SecurityException("External handoffs are read-only")
        val file = fileFor(uri)
        if (!file.isFile) throw FileNotFoundException(uri.toString())
        val key = observerKey(requireNotNull(context), file)
        val observer = accessObservers[key]
        if (observer != null && !observer.onOpen(Binder.getCallingUid())) {
            throw SecurityException("This private handoff belongs to another application")
        }
        return try {
            if (observer == null) {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            } else {
                ParcelFileDescriptor.open(
                    file,
                    ParcelFileDescriptor.MODE_READ_ONLY,
                    Handler(Looper.getMainLooper())
                ) { observer.onClose() }
            }
        } catch (error: Throwable) {
            observer?.onClose()
            throw error
        }
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
        private val accessObservers = ConcurrentHashMap<String, AccessObserver>()

        data class AccessObserver(
            val onOpen: (consumerUid: Int) -> Boolean,
            val onClose: () -> Unit
        )

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

        fun registerAccessObserver(context: Context, uri: Uri, observer: AccessObserver) {
            val file = fileFromUri(context, uri)
            accessObservers[observerKey(context, file)] = observer
        }

        fun unregisterAccessObserver(context: Context, uri: Uri) {
            val file = fileFromUri(context, uri)
            accessObservers.remove(observerKey(context, file))
        }

        private fun fileFromUri(context: Context, uri: Uri): File {
            require(uri.authority == authority(context)) { "Unexpected handoff authority" }
            val root = stagingRoot(context)
            val file = File(root, uri.pathSegments.joinToString(File.separator)).canonicalFile
            require(file == root || file.path.startsWith(root.path + File.separator)) { "Handoff path escapes cache" }
            return file
        }

        private fun observerKey(context: Context, file: File): String =
            file.canonicalFile.relativeTo(stagingRoot(context)).invariantSeparatorsPath
    }
}
