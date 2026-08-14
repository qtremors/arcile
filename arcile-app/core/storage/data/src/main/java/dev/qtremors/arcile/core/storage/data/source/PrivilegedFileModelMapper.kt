package dev.qtremors.arcile.core.storage.data.source

import android.webkit.MimeTypeMap
import dev.qtremors.arcile.core.privilege.PrivilegeBackendId
import dev.qtremors.arcile.core.privilege.PrivilegeSession
import dev.qtremors.arcile.core.privilege.PrivilegedFileEntry
import dev.qtremors.arcile.core.privilege.PrivilegedFileType
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.StorageNodeCapabilities
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef

class PrivilegedFileModelMapper(
    private val pathPolicy: PrivilegedPathPolicy
) {
    fun toFileModel(entry: PrivilegedFileEntry, session: PrivilegeSession): FileModel {
        val isDirectory = entry.type == PrivilegedFileType.DIRECTORY
        val extension = entry.displayName.substringAfterLast('.', missingDelimiterValue = "")
            .takeUnless { it == entry.displayName }
            .orEmpty()
            .lowercase()
        val mimeType = extension.takeIf(String::isNotEmpty)
            ?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) }
        val capabilities = capabilities(entry, session)
        val backendId = session.backendId.storageBackendId()
        val nodeRef = StorageNodeRef.privileged(
            backendId = backendId,
            displayPath = entry.path,
            remoteCanonicalIdentity = entry.canonicalIdentity,
            capabilities = capabilities
        )
        return FileModel(
            name = entry.displayName,
            absolutePath = entry.path,
            size = if (entry.type == PrivilegedFileType.REGULAR_FILE) entry.size.coerceAtLeast(0) else 0,
            lastModified = entry.modifiedAtMillis.coerceAtLeast(0),
            isDirectory = isDirectory,
            extension = extension,
            isHidden = entry.displayName.startsWith('.'),
            mimeType = mimeType,
            nodeRef = nodeRef
        )
    }

    fun capabilities(
        entry: PrivilegedFileEntry,
        session: PrivilegeSession
    ): StorageNodeCapabilities {
        val ordinaryFile = entry.type == PrivilegedFileType.REGULAR_FILE
        val directory = entry.type == PrivilegedFileType.DIRECTORY
        val link = entry.type == PrivilegedFileType.SYMBOLIC_LINK
        val readableType = ordinaryFile || directory || link
        val readable = readableType && entry.readable && pathPolicy.evaluate(
            entry.path,
            if (directory) PrivilegedPathOperation.LIST else PrivilegedPathOperation.READ,
            session,
            entry
        ).allowed
        val writable = readableType && entry.writable && pathPolicy.evaluate(
            entry.path,
            PrivilegedPathOperation.WRITE,
            session,
            entry
        ).allowed
        val deletable = readableType && pathPolicy.evaluate(
            entry.path,
            if (directory) PrivilegedPathOperation.RECURSIVE_DELETE else PrivilegedPathOperation.DELETE,
            session,
            entry
        ).allowed
        val renameable = readableType && pathPolicy.evaluate(
            entry.path,
            PrivilegedPathOperation.RENAME_SOURCE,
            session,
            entry
        ).allowed
        val copyable = (ordinaryFile || directory) && readable && pathPolicy.evaluate(
            entry.path,
            PrivilegedPathOperation.COPY_SOURCE,
            session,
            entry
        ).allowed
        val moveable = (ordinaryFile || directory) && deletable && pathPolicy.evaluate(
            entry.path,
            PrivilegedPathOperation.MOVE_SOURCE,
            session,
            entry
        ).allowed
        return StorageNodeCapabilities(
            canRead = readable,
            canWrite = writable,
            canDelete = deletable,
            canTrash = moveable &&
                pathPolicy.classify(entry.path).getOrNull() == PrivilegedPathScope.USER_STORAGE,
            canArchive = (ordinaryFile || directory) && readable,
            canRename = renameable,
            canCopy = copyable,
            canMove = moveable,
            canExport = ordinaryFile && readable,
            canShare = ordinaryFile && readable,
            canOpenWith = ordinaryFile && readable
        )
    }

    private fun PrivilegeBackendId.storageBackendId(): String = when (this) {
        PrivilegeBackendId.ROOT -> StorageNodeRef.ROOT_BACKEND_ID
        PrivilegeBackendId.SHIZUKU -> StorageNodeRef.SHIZUKU_BACKEND_ID
        else -> error("Normal Android sessions cannot create privileged storage nodes")
    }
}
