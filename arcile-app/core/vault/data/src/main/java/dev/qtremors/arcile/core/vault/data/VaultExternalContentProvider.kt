package dev.qtremors.arcile.core.vault.data

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.qtremors.arcile.core.vault.domain.VaultExternalAccessManager
import java.io.FileNotFoundException
import java.util.concurrent.Executors

class VaultExternalContentProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val content = manager().openGrantedContent(token(uri)).getOrElse { throw FileNotFoundException() }
        content.reader.close()
        val columns = projection?.takeIf(Array<out String>::isNotEmpty)
            ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(columns).apply {
            val row: Array<Any?> = columns.map<String, Any?> {
                when (it) {
                    OpenableColumns.DISPLAY_NAME -> content.displayName
                    OpenableColumns.SIZE -> content.sizeBytes
                    else -> null
                }
            }.toTypedArray()
            addRow(row)
        }
    }

    override fun getType(uri: Uri): String = manager().openGrantedContent(token(uri)).fold(
        onSuccess = { it.reader.close(); it.mimeType },
        onFailure = { throw FileNotFoundException() }
    )

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw SecurityException("OnlyFiles grants are read-only")
        val content = manager().openGrantedContent(token(uri)).getOrElse { throw FileNotFoundException() }
        val pipe = ParcelFileDescriptor.createPipe()
        executor.execute {
            content.reader.use { reader ->
                ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { output ->
                    val buffer = ByteArray(256 * 1024)
                    var position = 0L
                    while (position < reader.sizeBytes) {
                        val count = reader.readAt(position, buffer, 0, minOf(buffer.size.toLong(), reader.sizeBytes - position).toInt())
                        if (count <= 0) throw FileNotFoundException("Encrypted file ended unexpectedly")
                        output.write(buffer, 0, count)
                        position += count
                    }
                }
            }
        }
        return pipe[0]
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
        val executor = Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "onlyfiles-external-read").apply { isDaemon = true }
        }
    }
}
