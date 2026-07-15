package dev.qtremors.arcile.core.vault.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.qtremors.arcile.core.vault.crypto.VaultFileCodec
import dev.qtremors.arcile.core.vault.crypto.VaultIndexCodec
import dev.qtremors.arcile.core.vault.crypto.VaultIndexEntry
import dev.qtremors.arcile.core.vault.domain.VaultFailure
import dev.qtremors.arcile.core.vault.domain.VaultPath
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject

internal class VaultImportEngine @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val fileCodec = VaultFileCodec()
    private val indexCodec = VaultIndexCodec()

    suspend fun import(
        session: VaultSessionRecord,
        destination: VaultPath,
        sourceUris: List<String>,
        onProgress: (Int, Int, Long, Long?, String?) -> Unit
    ) {
        session.mutationMutex.withLock {
            requireDirectory(session.index, destination)
            val sources = VaultUriTreeReader(context.contentResolver).collect(sourceUris)
            val files = sources.filterNot(VaultImportSource::isDirectory)
            val totalBytes = files.mapNotNull { it.sizeBytes }.takeIf { it.size == files.size }?.sum()
            var completed = 0
            var copied = 0L
            sources.forEach { source ->
                val desiredParent = ensureParentDirectories(session, destination, source)
                if (source.isDirectory) return@forEach
                val target = uniqueImportPath(session.index, desiredParent, source.name)
                val objectName = "${UUID.randomUUID()}.off"
                val part = "$OBJECTS_DIRECTORY/.$objectName.part"
                val finalObject = "$OBJECTS_DIRECTORY/$objectName"
                val input = context.contentResolver.openInputStream(source.uri)
                    ?: throw VaultFailure.ImportUnavailable("Unable to read ${source.name}")
                val startCopied = copied
                var indexed = false
                try {
                    val result = input.use { stream ->
                        fileCodec.write(session.directory, part, session.id, session.masterKey, stream) { itemBytes ->
                            onProgress(completed, files.size, startCopied + itemBytes, totalBytes, source.name)
                        }
                    }
                    check(session.directory.rename(part, objectName)) { "Unable to commit encrypted file" }
                    val entry = VaultIndexEntry(
                        id = result.fileId,
                        path = target.value,
                        objectName = objectName,
                        sizeBytes = result.sizeBytes,
                        modifiedAtMillis = source.modifiedAtMillis ?: System.currentTimeMillis(),
                        isDirectory = false,
                        mimeType = source.mimeType
                    )
                    persist(session, session.index.entries + entry)
                    indexed = true
                    completed++
                    copied += result.sizeBytes
                    onProgress(completed, files.size, copied, totalBytes, source.name)
                } finally {
                    session.directory.delete(part)
                    if (!indexed) session.directory.delete(finalObject)
                }
            }
        }
    }

    private fun ensureParentDirectories(
        session: VaultSessionRecord,
        destination: VaultPath,
        source: VaultImportSource
    ): VaultPath = source.relativeParent.fold(destination) { path, segment ->
        val desired = path.resolve(segment)
        val existing = session.index.entries.firstOrNull { it.path.equals(desired.value, ignoreCase = true) }
        if (existing == null) {
            persist(
                session,
                session.index.entries + VaultIndexEntry(
                    id = UUID.randomUUID().toString(),
                    path = desired.value,
                    objectName = null,
                    sizeBytes = 0L,
                    modifiedAtMillis = source.modifiedAtMillis ?: System.currentTimeMillis(),
                    isDirectory = true,
                    mimeType = null
                )
            )
        } else if (!existing.isDirectory) {
            throw VaultFailure.PathConflict(desired)
        }
        desired
    }

    private fun persist(session: VaultSessionRecord, entries: List<VaultIndexEntry>) {
        val next = session.index.copy(generation = session.index.generation + 1L, entries = entries)
        indexCodec.write(session.directory, session.id, session.masterKey, next)
        session.index = next
    }
}
