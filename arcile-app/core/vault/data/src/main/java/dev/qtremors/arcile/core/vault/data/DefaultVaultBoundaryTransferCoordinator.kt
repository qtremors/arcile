package dev.qtremors.arcile.core.vault.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.qtremors.arcile.core.vault.domain.VaultBatchResult
import dev.qtremors.arcile.core.vault.domain.VaultBoundaryTransferCoordinator
import dev.qtremors.arcile.core.vault.domain.VaultCancellationSignal
import dev.qtremors.arcile.core.vault.domain.VaultConflict
import dev.qtremors.arcile.core.vault.domain.VaultConflictDecision
import dev.qtremors.arcile.core.vault.domain.VaultConflictResolver
import dev.qtremors.arcile.core.vault.domain.VaultFailure
import dev.qtremors.arcile.core.vault.domain.VaultFileSystem
import dev.qtremors.arcile.core.vault.domain.VaultItemOutcome
import dev.qtremors.arcile.core.vault.domain.VaultItemResult
import dev.qtremors.arcile.core.vault.domain.VaultLeasePurpose
import dev.qtremors.arcile.core.vault.domain.VaultListOptions
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import dev.qtremors.arcile.core.vault.domain.VaultNodeRef
import dev.qtremors.arcile.core.vault.domain.VaultSessionManager
import dev.qtremors.arcile.core.vault.domain.VaultTransferAction
import dev.qtremors.arcile.core.vault.domain.VaultTransferProgress
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class DefaultVaultBoundaryTransferCoordinator @Inject constructor(
    @ApplicationContext context: Context,
    private val fileSystem: VaultFileSystem,
    private val sessions: VaultSessionManager
) : VaultBoundaryTransferCoordinator {
    private val resolver = context.contentResolver
    private val mutex = Mutex()
    private val updates = MutableSharedFlow<VaultTransferProgress>(extraBufferCapacity = 32)
    override val progress: Flow<VaultTransferProgress> = updates

    override suspend fun exportToDocumentTree(
        sources: List<VaultNodeRef>,
        destinationTreeUri: String,
        move: Boolean,
        conflicts: VaultConflictResolver,
        cancellation: VaultCancellationSignal
    ): VaultBatchResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            val unique = sources.distinctBy { "${it.vaultId.value}:${it.nodeId.value}" }
            val tree = Uri.parse(destinationTreeUri)
            require(tree.scheme == ContentResolver.SCHEME_CONTENT && DocumentsContract.isTreeUri(tree)) {
                "A document-tree destination is required"
            }
            val root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val results = mutableListOf<VaultItemResult>()
            unique.forEachIndexed { index, ref ->
                val metadata = fileSystem.metadata(ref).getOrElse { error ->
                    results += failed(ref, ref.nodeId.value, error)
                    return@forEachIndexed
                }
                updates.tryEmit(VaultTransferProgress(
                    if (move) VaultTransferAction.MOVE else VaultTransferAction.EXPORT,
                    metadata.name, index, unique.size, 0L, metadata.sizeBytes.takeIf { !metadata.isDirectory }
                ))
                try {
                    cancellation.throwIfCancelled()
                    sessions.acquireLease(ref.vaultId, VaultLeasePurpose.TRANSFER).getOrThrow().use {
                        val outcome = exportTopLevel(metadata, root, conflicts, cancellation)
                        if (outcome == VaultItemOutcome.SKIPPED) {
                            results += item(ref, metadata.name, outcome)
                            return@forEachIndexed
                        }
                    }
                    if (move) fileSystem.deletePermanently(ref).getOrThrow()
                    results += item(ref, metadata.name, VaultItemOutcome.COMPLETED)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: VaultFailure.Cancelled) {
                    results += item(ref, metadata.name, VaultItemOutcome.ROLLED_BACK, error)
                    unique.drop(index + 1).forEach { remaining ->
                        results += item(
                            remaining,
                            remaining.nodeId.value,
                            VaultItemOutcome.SKIPPED,
                            VaultFailure.Cancelled()
                        )
                    }
                    return@withLock VaultBatchResult(results)
                } catch (error: Throwable) {
                    results += failed(ref, metadata.name, error)
                }
            }
            VaultBatchResult(results)
        }
    }

    private suspend fun exportTopLevel(
        source: VaultNodeMetadata,
        destination: Uri,
        conflicts: VaultConflictResolver,
        cancellation: VaultCancellationSignal
    ): VaultItemOutcome {
        val existing = findChild(destination, source.name)
        val decision = existing?.let {
            conflicts.decide(VaultConflict(source.name, it.name, source.isDirectory, it.isDirectory))
        }
        if (decision == VaultConflictDecision.SKIP) return VaultItemOutcome.SKIPPED
        if (decision == VaultConflictDecision.MERGE_DIRECTORIES &&
            (!source.isDirectory || existing?.isDirectory != true)
        ) {
            throw VaultFailure.NameConflict(source.name)
        }
        val name = if (decision == VaultConflictDecision.KEEP_BOTH) uniqueName(destination, source.name) else source.name
        val temporaryName = ".arcile-${UUID.randomUUID()}.tmp"
        val created = create(destination, temporaryName, source.isDirectory, source.mimeType)
        try {
            if (decision == VaultConflictDecision.MERGE_DIRECTORIES && existing != null) {
                copyDocumentDirectory(existing.uri, created, cancellation)
            }
            if (source.isDirectory) exportDirectoryContents(source, created, conflicts, cancellation)
            else exportFile(source, created, cancellation)
            publish(
                temporary = created,
                finalName = name,
                replacing = existing?.takeIf {
                    decision == VaultConflictDecision.REPLACE || decision == VaultConflictDecision.MERGE_DIRECTORIES
                }
            )
            return VaultItemOutcome.COMPLETED
        } catch (error: Throwable) {
            runCatching { DocumentsContract.deleteDocument(resolver, created) }
            throw error
        }
    }

    private suspend fun exportDirectoryContents(
        directory: VaultNodeMetadata,
        destination: Uri,
        conflicts: VaultConflictResolver,
        cancellation: VaultCancellationSignal
    ) {
        val directoryId = directory.ref.directoryId ?: throw VaultFailure.InvalidPath("Folder identity is missing")
        var token: String? = null
        do {
            cancellation.throwIfCancelled()
            val page = fileSystem.listDirectory(directory.ref.vaultId, directoryId, VaultListOptions(pageToken = token)).getOrThrow()
            for (child in page.items) exportTopLevel(child, destination, conflicts, cancellation)
            token = page.nextPageToken
        } while (token != null)
    }

    private fun exportFile(source: VaultNodeMetadata, destination: Uri, cancellation: VaultCancellationSignal) {
        val digest = MessageDigest.getInstance("SHA-256")
        fileSystem.openReader(source.ref).getOrThrow().use { reader ->
            resolver.openOutputStream(destination, "wt")?.use { output ->
                val buffer = ByteArray(BUFFER_SIZE)
                var position = 0L
                while (position < reader.sizeBytes) {
                    cancellation.throwIfCancelled()
                    val count = reader.readAt(position, buffer, 0, minOf(buffer.size.toLong(), reader.sizeBytes - position).toInt())
                    if (count <= 0) throw VaultFailure.SourceChanged()
                    output.write(buffer, 0, count)
                    digest.update(buffer, 0, count)
                    position += count
                }
                buffer.fill(0)
                output.flush()
            } ?: throw VaultFailure.Unavailable("The destination refused the file")
        }
        val expected = digest.digest()
        val actual = MessageDigest.getInstance("SHA-256")
        resolver.openInputStream(destination)?.use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            var verifiedBytes = 0L
            while (true) {
                cancellation.throwIfCancelled()
                val count = input.read(buffer)
                if (count < 0) break
                actual.update(buffer, 0, count)
                verifiedBytes += count
            }
            buffer.fill(0)
            if (verifiedBytes != source.sizeBytes) throw VaultFailure.IntegrityFailed("Destination length changed")
        } ?: throw VaultFailure.Unavailable("The exported file could not be verified")
        if (!MessageDigest.isEqual(expected, actual.digest())) throw VaultFailure.IntegrityFailed("Destination verification failed")
        expected.fill(0)
    }

    private fun copyDocumentDirectory(source: Uri, destination: Uri, cancellation: VaultCancellationSignal) {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(source, DocumentsContract.getDocumentId(source))
        resolver.query(
            children,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            ),
            null,
            null,
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                cancellation.throwIfCancelled()
                val child = DocumentsContract.buildDocumentUriUsingTree(source, cursor.getString(0))
                val name = cursor.getString(1) ?: throw VaultFailure.InvalidPath("Destination item has no name")
                val mime = cursor.getString(2) ?: "application/octet-stream"
                val directory = mime == DocumentsContract.Document.MIME_TYPE_DIR
                val copy = create(destination, name, directory, mime)
                if (directory) copyDocumentDirectory(child, copy, cancellation)
                else copyDocumentFile(child, copy, cancellation)
            }
        } ?: throw VaultFailure.Unavailable("The existing destination could not be read")
    }

    private fun copyDocumentFile(source: Uri, destination: Uri, cancellation: VaultCancellationSignal) {
        val expected = MessageDigest.getInstance("SHA-256")
        var expectedBytes = 0L
        resolver.openInputStream(source)?.use { input ->
            resolver.openOutputStream(destination, "wt")?.use { output ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    cancellation.throwIfCancelled()
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    expected.update(buffer, 0, count)
                    expectedBytes += count
                }
                buffer.fill(0)
                output.flush()
            } ?: throw VaultFailure.Unavailable("The destination refused a merged file")
        } ?: throw VaultFailure.Unavailable("The existing destination file could not be read")
        val actual = MessageDigest.getInstance("SHA-256")
        var actualBytes = 0L
        resolver.openInputStream(destination)?.use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                cancellation.throwIfCancelled()
                val count = input.read(buffer)
                if (count < 0) break
                actual.update(buffer, 0, count)
                actualBytes += count
            }
            buffer.fill(0)
        } ?: throw VaultFailure.Unavailable("The merged destination file could not be verified")
        val expectedHash = expected.digest()
        if (expectedBytes != actualBytes || !MessageDigest.isEqual(expectedHash, actual.digest())) {
            throw VaultFailure.IntegrityFailed("Merged destination verification failed")
        }
        expectedHash.fill(0)
    }

    private fun publish(temporary: Uri, finalName: String, replacing: DocumentChild?) {
        if (replacing == null) {
            val published = DocumentsContract.renameDocument(resolver, temporary, finalName)
                ?: throw VaultFailure.Unavailable("The destination could not publish the exported item")
            requireDocument(published)
            return
        }
        val backupName = ".arcile-backup-${UUID.randomUUID()}.tmp"
        val backup = DocumentsContract.renameDocument(resolver, replacing.uri, backupName)
            ?: throw VaultFailure.Unavailable("The existing destination could not be staged for replacement")
        val published = DocumentsContract.renameDocument(resolver, temporary, finalName)
        if (published == null) {
            DocumentsContract.renameDocument(resolver, backup, replacing.name)
            throw VaultFailure.Unavailable("The destination could not publish the replacement")
        }
        try {
            requireDocument(published)
            if (!DocumentsContract.deleteDocument(resolver, backup)) {
                throw VaultFailure.Unavailable("The replaced destination could not be removed")
            }
        } catch (error: Throwable) {
            runCatching { DocumentsContract.deleteDocument(resolver, published) }
            runCatching { DocumentsContract.renameDocument(resolver, backup, replacing.name) }
            throw error
        }
    }

    private fun create(parent: Uri, name: String, directory: Boolean, mimeType: String?): Uri =
        DocumentsContract.createDocument(
            resolver, parent, if (directory) DocumentsContract.Document.MIME_TYPE_DIR else mimeType ?: "application/octet-stream", name
        ) ?: throw VaultFailure.Unavailable("The destination refused a new item")

    private fun findChild(parent: Uri, name: String): DocumentChild? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(parent, DocumentsContract.getDocumentId(parent))
        return resolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE),
            null, null, null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val candidate = cursor.getString(1)
                if (candidate.equals(name, ignoreCase = true)) return@use DocumentChild(
                    DocumentsContract.buildDocumentUriUsingTree(parent, cursor.getString(0)), candidate,
                    cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR
                )
            }
            null
        }
    }

    private fun uniqueName(parent: Uri, name: String): String {
        val dot = name.lastIndexOf('.').takeIf { it > 0 }
        val stem = dot?.let { name.substring(0, it) } ?: name
        val suffix = dot?.let(name::substring).orEmpty()
        for (index in 1..10_000) {
            val candidate = "$stem ($index)$suffix"
            if (findChild(parent, candidate) == null) return candidate
        }
        throw VaultFailure.NameConflict(name)
    }

    private fun requireDocument(uri: Uri) {
        resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)?.use {
            if (!it.moveToFirst()) throw VaultFailure.Unavailable("The exported item disappeared")
        } ?: throw VaultFailure.Unavailable("The exported item could not be verified")
    }

    private data class DocumentChild(val uri: Uri, val name: String, val isDirectory: Boolean)
    private fun item(ref: VaultNodeRef, name: String, outcome: VaultItemOutcome, failure: VaultFailure? = null) =
        VaultItemResult("${ref.vaultId.value}:${ref.nodeId.value}", name, outcome, failure)
    private fun failed(ref: VaultNodeRef, name: String, error: Throwable) = item(
        ref, name, VaultItemOutcome.FAILED, error as? VaultFailure ?: VaultFailure.Unavailable("Boundary transfer failed", error)
    )

    private companion object { const val BUFFER_SIZE = 256 * 1024 }
}
