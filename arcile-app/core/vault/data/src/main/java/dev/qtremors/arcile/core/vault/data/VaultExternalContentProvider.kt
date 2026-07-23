package dev.qtremors.arcile.core.vault.data

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.Binder
import android.os.Handler
import android.os.HandlerThread
import android.os.ProxyFileDescriptorCallback
import android.os.storage.StorageManager
import android.provider.OpenableColumns
import android.system.ErrnoException
import android.system.OsConstants
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.qtremors.arcile.core.vault.domain.VaultExternalAccessManager
import java.io.FileNotFoundException

class VaultExternalContentProvider : ContentProvider() {
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
            val row: Array<Any?> = columns.map<String, Any?> {
                when (it) {
                    OpenableColumns.DISPLAY_NAME -> grant.displayName
                    OpenableColumns.SIZE -> grant.sizeBytes
                    else -> null
                }
            }.toTypedArray()
            addRow(row)
        }
    }

    override fun getType(uri: Uri): String = manager().describe(token(uri)).fold(
        onSuccess = { it.mimeType },
        onFailure = { throw FileNotFoundException() }
    )

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw SecurityException("OnlyFiles grants are read-only")
        val content = manager().openGrantedContent(token(uri), Binder.getCallingUid())
            .getOrElse { throw FileNotFoundException() }
        val callback = object : ProxyFileDescriptorCallback() {
            override fun onGetSize(): Long = content.reader.sizeBytes

            override fun onRead(offset: Long, size: Int, data: ByteArray): Int = try {
                if (offset >= content.reader.sizeBytes) 0 else {
                    val requested = minOf(size.toLong(), content.reader.sizeBytes - offset).toInt()
                    content.reader.readAt(offset, data, 0, requested).also {
                        if (it <= 0 && requested > 0) throw ErrnoException("read", OsConstants.EIO)
                    }
                }
            } catch (error: ErrnoException) {
                throw error
            } catch (_: Exception) {
                throw ErrnoException("read", OsConstants.EIO)
            }

            override fun onRelease() {
                content.reader.close()
            }
        }
        return try {
            requireNotNull(context).getSystemService(StorageManager::class.java).openProxyFileDescriptor(
                ParcelFileDescriptor.MODE_READ_ONLY,
                callback,
                Handler(proxyThread.looper)
            )
        } catch (error: Exception) {
            content.reader.close()
            throw FileNotFoundException(error.message)
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    private fun token(uri: Uri): String {
        if (uri.authority != DefaultVaultExternalAccessManager.authority(requireNotNull(context))) {
            throw FileNotFoundException()
        }
        return uri.pathSegments.singleOrNull()?.takeIf { it.length == 64 && it.all { char -> char in '0'..'9' || char in 'a'..'f' } }
            ?: throw FileNotFoundException()
    }

    private fun manager(): VaultExternalAccessManager = EntryPointAccessors.fromApplication(
        requireNotNull(context).applicationContext,
        ProviderEntryPoint::class.java
    ).manager()

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ProviderEntryPoint {
        fun manager(): VaultExternalAccessManager
    }

    private companion object {
        val proxyThread = HandlerThread("onlyfiles-external-read").apply { start() }
    }
}
