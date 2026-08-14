package dev.qtremors.arcile.core.storage.data

import dev.qtremors.arcile.core.storage.data.source.PrivilegedFileSystemDataSource
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.PrivilegedTextFileEditor
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef

class DefaultPrivilegedTextFileEditor(
    private val dataSource: PrivilegedFileSystemDataSource
) : PrivilegedTextFileEditor {
    override suspend fun saveAtomically(
        node: StorageNodeRef,
        content: ByteArray
    ): Result<FileModel> = dataSource.replaceNodeContentAtomically(node, content)
}
