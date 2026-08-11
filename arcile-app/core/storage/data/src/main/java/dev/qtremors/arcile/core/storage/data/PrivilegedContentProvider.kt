package dev.qtremors.arcile.core.storage.data

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.os.ProxyFileDescriptorCallback
import android.os.storage.StorageManager
import android.provider.OpenableColumns
import android.system.ErrnoException
import android.system.OsConstants
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.qtremors.arcile.core.storage.domain.PrivilegedContentAccessManager
import java.io.FileNotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/** Read-only Android bridge for opaque protected-content capabilities. */
class PrivilegedContentProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val grant = manager().describe(token(uri)).getOrElse { throw FileNotFoundException() }
        val columns = projection?.takeIf(Array<out String>::isNotEmpty)
            ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(columns).apply {
            addRow(
                columns.map<String, Any?> { column ->
                    when (column) {
                        OpenableColumns.DISPLAY_NAME -> grant.displayName
                        OpenableColumns.SIZE -> grant.sizeBytes
                        else -> null
                    }
                }.toTypedArray()
            )
        }
    }

    override fun getType(uri: Uri): String = manager().describe(token(uri)).fold(
        onSuccess = { it.mimeType },
        onFailure = { throw FileNotFoundException() }
    )

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw SecurityException("Protected-content grants are read-only")
        val granted = runBlocking(Dispatchers.IO) {
            manager().openGrantedContent(token(uri), Binder.getCallingUid())
        }.getOrElse { error ->
            throw FileNotFoundException(error.message)
        }
        val callback = object : ProxyFileDescriptorCallback() {
            override fun onGetSize(): Long = granted.reader.sizeBytes

            override fun onRead(offset: Long, size: Int, data: ByteArray): Int = try {
                if (offset >= granted.reader.sizeBytes) {
                    0
                } else {
                    val requested = minOf(
                        size.toLong(),
                        granted.reader.sizeBytes - offset
                    ).toInt()
                    granted.reader.readAt(offset, data, 0, requested).also { count ->
                        if (count <= 0 && requested > 0) {
                            throw ErrnoException("read", OsConstants.EIO)
                        }
                    }
                }
            } catch (error: ErrnoException) {
                throw error
            } catch (_: Exception) {
                throw ErrnoException("read", OsConstants.EIO)
            }

            override fun onRelease() {
                granted.reader.close()
            }
        }
        return try {
            requireNotNull(context)
                .getSystemService(StorageManager::class.java)
                .openProxyFileDescriptor(
                    ParcelFileDescriptor.MODE_READ_ONLY,
                    callback,
                    Handler(proxyThread.looper)
                )
        } catch (error: Exception) {
            granted.reader.close()
            throw FileNotFoundException(error.message)
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("Protected-content grants are read-only")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    private fun token(uri: Uri): String {
        val providerContext = requireNotNull(context)
        if (uri.authority != DefaultPrivilegedContentAccessManager.authority(providerContext)) {
            throw FileNotFoundException()
        }
        return uri.pathSegments.singleOrNull()
            ?.takeIf(DefaultPrivilegedContentAccessManager::isValidToken)
            ?: throw FileNotFoundException()
    }

    private fun manager(): PrivilegedContentAccessManager = EntryPointAccessors.fromApplication(
        requireNotNull(context).applicationContext,
        ProviderEntryPoint::class.java
    ).privilegedContentManager()

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ProviderEntryPoint {
        fun privilegedContentManager(): PrivilegedContentAccessManager
    }

    private companion object {
        val proxyThread = HandlerThread("protected-content-read").apply { start() }
    }
}
