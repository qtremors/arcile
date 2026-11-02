package dev.qtremors.arcile.core.vault.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import dev.qtremors.arcile.core.storage.domain.StorageVolume
import dev.qtremors.arcile.core.storage.domain.VolumeRepository
import dev.qtremors.arcile.core.vault.domain.*
import dev.qtremors.arcile.core.vault.crypto.*
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.CRC32
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VaultArchiveDestinationTest {
    @get:Rule val temporary = TemporaryFolder()
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun `direct extraction adds files and empty folders without wrapping or changing existing entries`() = runTest {
        fixture { f ->
            val old = f.file("Existing.txt", "original")
            val progress = mutableListOf<Int>()
            val result = f.extract(listOf(source("New.txt", "new"), VaultArchiveSource.Folder(listOf("Empty")))) { progress += it.completed }
            assertEquals(2, result.completed.size)
            assertEquals(setOf("Existing.txt", "New.txt", "Empty"), f.rows().map { it.name }.toSet())
            assertEquals(old, f.rows().single { it.name == old.name })
            assertEquals("original", f.text(old))
            assertEquals("new", f.text(f.rows().single { it.name == "New.txt" }))
            assertTrue(f.rows(f.rows().single { it.name == "Empty" }.ref.directoryId!!).isEmpty())
            assertEquals(listOf(0, 1), progress)
            f.repository.lock(f.vault)
            f.repository.unlock(f.vault, "password".toCharArray()).getOrThrow()
            assertEquals("new", f.text(f.rows().single { it.name == "New.txt" }))
        }
    }

    @Test fun `keep both reserves incoming names as well as existing names`() = runTest {
        fixture { f ->
            f.file("File.txt", "old")
            val result = f.extract(listOf(source("File.txt", "new"), source("File (1).txt", "independent")), keepBoth)
            assertEquals(2, result.completed.size)
            val actual = f.rows().associate { it.name to f.text(it) }
            assertEquals(mapOf("File.txt" to "old", "File (1).txt" to "independent", "File (2).txt" to "new"), actual)
        }
    }

    @Test fun `an existing nested destination receives contents directly without changing its siblings`() = runTest {
        fixture { f ->
            val destination = f.folder("Destination")
            val sibling = f.file("Sibling.txt", "keep")
            val id = destination.ref.directoryId!!
            f.repository.extractArchiveInto(f.vault, id, listOf(source("New.txt", "nested")), replace) {}.getOrThrow()
            assertEquals(setOf(destination.name, sibling.name), f.rows().map { it.name }.toSet())
            val item = f.rows(id).single()
            assertEquals(id, item.ref.parentId)
            assertEquals("nested", f.text(item))
            assertEquals("keep", f.text(sibling))
        }
    }

    @Test fun `named extraction creates its folder once then reuses it with ordinary entry conflicts`() = runTest {
        fixture { f ->
            val first = f.repository.extractArchiveToNamedFolder(f.vault, DirectoryId.Root, "Output",
                listOf(source("Docs/Keep.txt", "original")), replace) {}.getOrThrow()
            assertEquals(first.directory, f.rows().single().ref.directoryId)
            val skipFiles = VaultConflictResolver { if (it.canMerge) VaultConflictDecision.MERGE_DIRECTORIES else VaultConflictDecision.SKIP }
            val second = f.repository.extractArchiveToNamedFolder(f.vault, DirectoryId.Root, "Output",
                listOf(source("Docs/Keep.txt", "discard"), source("Docs/New.txt", "new")), skipFiles) {}.getOrThrow()
            assertEquals(first.directory, second.directory)
            assertEquals(1, second.result.partial.size)
            assertEquals(listOf("Output"), f.rows().map { it.name })
            val docs = f.rows(second.directory).single()
            assertEquals(mapOf("Keep.txt" to "original", "New.txt" to "new"),
                f.rows(docs.ref.directoryId!!).associate { it.name to f.text(it) })
        }
    }

    @Test fun `named output cannot overwrite a file occupying its destination folder name`() = runTest {
        fixture { f ->
            val original = f.file("Output", "original")
            var opened = false
            val entry = VaultArchiveSource.File(listOf("New.txt"), 1, null) { opened = true; "x".byteInputStream() }
            val before = f.objects()
            assertTrue(f.repository.extractArchiveToNamedFolder(f.vault, DirectoryId.Root, "Output", listOf(entry), replace) {}.isFailure)
            assertFalse(opened)
            assertEquals(before, f.objects())
            assertEquals(listOf(original), f.rows())
        }
    }

    @Test fun `portable vault extraction stays encrypted and remains readable after locking`() = runTest {
        fixture(portable = true) { f ->
            f.extract(listOf(source("Private.txt", "private portable payload")))
            assertEquals("private portable payload", f.text(f.rows().single()))
            val physical = File(f.volumeRoot, "portable")
            physical.walkTopDown().filter(File::isFile).forEach { file ->
                val stored = file.readBytes().decodeToString()
                assertFalse(stored.contains("private portable payload"))
                assertFalse(stored.contains("Private.txt"))
            }
            f.repository.lock(f.vault)
            f.repository.unlock(f.vault, "password".toCharArray()).getOrThrow()
            assertEquals("private portable payload", f.text(f.rows().single()))
        }
    }

    @Test fun `entry byte folder and normalized-name limits are rejected before opening streams`() = runTest {
        fixture { f ->
            var opened = false
            fun input(path: List<String>, size: Long = 0) = VaultArchiveSource.File(path, size, null) {
                opened = true; "".byteInputStream()
            }
            val cases = listOf(
                List(VAULT_ARCHIVE_MAX_ENTRIES + 1) { input(listOf("Item-$it")) },
                listOf(input(listOf("Large"), VAULT_ARCHIVE_MAX_BYTES + 1)),
                listOf(input(List(VAULT_ARCHIVE_MAX_FOLDERS + 1) { "Folder-$it" } + "File.txt")),
                listOf(input(listOf("Résumé.txt")), input(listOf("Re\u0301sume\u0301.txt")))
            )
            val before = f.objects()
            for (entries in cases) {
                assertTrue(f.repository.extractArchiveInto(f.vault, DirectoryId.Root, entries, replace) {}.isFailure)
                assertTrue(f.rows().isEmpty())
                assertEquals(before, f.objects())
            }
            assertFalse(opened)
        }
    }

    @Test fun `skipping all conflicts leaves the destination and physical objects unchanged`() = runTest {
        fixture { f ->
            val old = f.file("File.txt", "old")
            val before = f.objects()
            val result = f.extract(listOf(source("File.txt", "discarded private text")), skip)
            assertEquals(1, result.skipped.size)
            assertEquals(listOf(old), f.rows())
            assertEquals(before, f.objects())
            assertEquals("old", f.text(old))
        }
    }

    @Test fun `replacement changes the selected destination while unrelated encrypted content survives`() = runTest {
        fixture { f ->
            f.file("File.txt", "old")
            val preserved = f.file("Keep.txt", "preserved")
            val result = f.extract(listOf(source("File.txt", "replacement")), replace)
            assertEquals(1, result.completed.size)
            assertEquals("replacement", f.text(f.rows().single { it.name == "File.txt" }))
            assertEquals(preserved, f.rows().single { it.name == "Keep.txt" })
            assertEquals("preserved", f.text(preserved))
        }
    }

    @Test fun `folder merge preserves existing identities and adopts verified incoming subtrees`() = runTest {
        fixture { f ->
            val docs = f.folder("Docs")
            val id = docs.ref.directoryId!!
            val old = f.file("Keep.txt", "kept", id)
            val sub = f.folder("Old folder", id)
            val deep = f.file("Deep.txt", "deep kept", sub.ref.directoryId!!)
            f.file("Replace.txt", "old", id)
            val result = f.extract(listOf(source("Docs/New tree/New.txt", "incoming"), source("Docs/Replace.txt", "new")), merge)
            assertEquals(1, result.completed.size)
            val current = f.rows().single()
            assertEquals("Docs", current.name)
            val children = f.rows(current.ref.directoryId!!)
            assertEquals(old, children.single { it.name == old.name }.copy(ref = old.ref))
            assertEquals(sub.ref.directoryId, children.single { it.name == sub.name }.ref.directoryId)
            assertEquals("kept", f.text(children.single { it.name == old.name }))
            assertEquals("deep kept", f.text(deep))
            val incoming = children.single { it.name == "New tree" }
            assertEquals("incoming", f.text(f.rows(incoming.ref.directoryId!!).single()))
            assertEquals("new", f.text(children.single { it.name == "Replace.txt" }))
            assertTrue(id in result.items.flatMap { it.removedFolders })
        }
    }

    @Test fun `nested skip reports partial extraction and retains the original bytes`() = runTest {
        fixture { f ->
            val id = f.folder("Docs").ref.directoryId!!
            f.file("Skip.txt", "original", id)
            val decisions = VaultConflictResolver { if (it.canMerge) VaultConflictDecision.MERGE_DIRECTORIES else VaultConflictDecision.SKIP }
            val result = f.extract(listOf(source("Docs/Skip.txt", "discard"), source("Docs/New.txt", "new")), decisions)
            assertEquals(1, result.partial.size)
            val children = f.rows(f.rows().single().ref.directoryId!!)
            assertEquals(mapOf("Skip.txt" to "original", "New.txt" to "new"), children.associate { it.name to f.text(it) })
        }
    }

    @Test fun `replace handles file-folder conflicts in both directions`() = runTest {
        fixture { f ->
            f.file("Folder", "old file")
            val dir = f.folder("File.txt")
            f.file("Old.txt", "removed", dir.ref.directoryId!!)
            val result = f.extract(listOf(source("Folder/Child.txt", "child"), source("File.txt", "file")), replace)
            assertEquals(2, result.completed.size)
            val rows = f.rows()
            assertEquals("file", f.text(rows.single { it.name == "File.txt" }))
            assertEquals("child", f.text(f.rows(rows.single { it.name == "Folder" }.ref.directoryId!!).single()))
            assertTrue(dir.ref.directoryId in result.items.flatMap { it.removedFolders })
        }
    }

    @Test fun `invalid merge choices fail before publishing and preserve the destination`() = runTest {
        fixture { f ->
            val old = f.file("File.txt", "old")
            val before = f.objects()
            assertTrue(f.repository.extractArchiveInto(f.vault, DirectoryId.Root, listOf(source("File.txt", "new")),
                VaultConflictResolver { VaultConflictDecision.MERGE_DIRECTORIES }) {}.isFailure)
            assertEquals(listOf(old), f.rows())
            assertEquals(before, f.objects())
        }
    }

    @Test fun `archive streams are opened in original order even when directories are interleaved`() = runTest {
        fixture { f ->
            val opened = mutableListOf<String>()
            val entries = listOf("A/One.txt", "B/Two.txt", "A/Three.txt").map { path ->
                VaultArchiveSource.File(path.split('/'), 1, null) { opened += path; "x".byteInputStream() }
            }
            f.extract(entries)
            assertEquals(listOf("A/One.txt", "B/Two.txt", "A/Three.txt"), opened)
        }
    }

    @Test fun `failure after a verified first entry rolls back the entire extraction`() = runTest {
        fixture { f ->
            val old = f.file("Existing.txt", "original")
            val before = f.objects()
            val broken = VaultArchiveSource.File(listOf("Broken.txt"), 1, null) { error("Failed archive reader") }
            assertTrue(f.repository.extractArchiveInto(f.vault, DirectoryId.Root,
                listOf(source("First.txt", "first"), broken), replace) {}.isFailure)
            assertEquals(listOf(old), f.rows())
            assertEquals(before, f.objects())
        }
    }

    @Test fun `size checksum and path failures never publish partial output`() = runTest {
        fixture { f ->
            val old = f.file("Existing.txt", "original")
            val invalid = listOf(
                VaultArchiveSource.File(listOf("Bad.txt"), 1, null) { "long".byteInputStream() },
                VaultArchiveSource.File(listOf("Bad.txt"), 20, null) { "short".byteInputStream() },
                VaultArchiveSource.File(listOf("Bad.txt"), 4, 123L) { "text".byteInputStream() },
                source("../Outside.txt", "outside")
            )
            val before = f.objects()
            for (source in invalid) {
                assertTrue(f.repository.extractArchiveInto(f.vault, DirectoryId.Root, listOf(source), replace) {}.isFailure)
                assertEquals(listOf(old), f.rows())
                assertEquals(before, f.objects())
            }
        }
    }

    @Test fun `cancellation while conflict review is pending removes unpublished objects`() = runTest {
        fixture { f ->
            val old = f.file("File.txt", "original")
            val before = f.objects()
            val operation = async {
                val ownJob = currentCoroutineContext().job
                val resolver = VaultConflictResolver { ownJob.cancel(); VaultConflictDecision.REPLACE }
                f.extract(listOf(source("File.txt", "new")), resolver)
            }
            assertTrue(runCatching { operation.await() }.exceptionOrNull() is CancellationException)
            assertEquals(listOf(old), f.rows())
            assertEquals(before, f.objects())
        }
    }

    @Test fun `missing destinations and locked sessions do not open archive entries`() = runTest {
        fixture { f ->
            var opened = false
            val source = VaultArchiveSource.File(listOf("File.txt"), 1, null) { opened = true; "x".byteInputStream() }
            assertTrue(f.repository.extractArchiveInto(f.vault, DirectoryId.of("missing"), listOf(source), replace) {}.isFailure)
            f.repository.lock(f.vault)
            assertTrue(f.repository.extractArchiveInto(f.vault, DirectoryId.Root, listOf(source), replace) {}.exceptionOrNull() is VaultFailure.Locked)
            assertFalse(opened)
        }
    }

    @Test fun `unrelated encrypted content is adopted without rereading or rewriting its bytes`() = runTest {
        val f = DirectFixture(temporary.newFolder())
        try {
            f.extract(listOf(source("Keep.txt", "existing contents")))
            val old = f.rootEntry("Keep.txt")
            try {
                val objectPath = old.objectId!!.shardedPath()
                f.forbiddenReads += objectPath
                f.extract(listOf(source("New.txt", "new contents")))
                assertEquals(0, f.forbiddenReadAttempts)
                val preserved = f.rootEntry(old.name)
                try {
                    assertEquals(old.nodeId, preserved.nodeId)
                    assertEquals(old.objectId, preserved.objectId)
                    assertEquals(old.revision, preserved.revision)
                } finally { preserved.protectedKey.fill(0) }
                f.forbiddenReads.clear()
                assertEquals("existing contents", f.text("Keep.txt"))
            } finally { old.protectedKey.fill(0) }
        } finally { f.session.destroy() }
    }

    @Test fun `a failure before the commit marker removes all staged content and preserves originals`() = runTest {
        val f = DirectFixture(temporary.newFolder())
        try {
            f.extract(listOf(source("Keep.txt", "existing contents")))
            val before = f.objects()
            f.fault = VaultTransactionStage.STAGED_RESULT_AUTHENTICATED
            assertTrue(runCatching { f.extract(listOf(source("Keep.txt", "replacement"), source("New.txt", "new"))) }.isFailure)
            assertFalse(f.transactions.hasPendingCommit(f.storage))
            assertEquals(before, f.objects())
            assertEquals("existing contents", f.text("Keep.txt"))
        } finally { f.session.destroy() }
    }

    @Test fun `an interrupted committed extraction retains its objects and recovers all publications`() = runTest {
        val f = DirectFixture(temporary.newFolder())
        try {
            f.extract(listOf(source("Keep.txt", "existing contents")))
            f.fault = VaultTransactionStage.MANIFESTS_PUBLISHED
            assertTrue(runCatching { f.extract(listOf(source("Keep.txt", "replacement"), source("Docs/New.txt", "new"))) }.isFailure)
            assertTrue(f.transactions.hasPendingCommit(f.storage))
            f.fault = null
            assertTrue(f.transactions.recover(f.storage, f.session.id, f.session.masterSecret))
            assertFalse(f.transactions.hasPendingCommit(f.storage))
            assertEquals("replacement", f.text("Keep.txt"))
            val docs = f.rootEntry("Docs")
            try {
                val snapshot = f.session.readDirectory(docs.childDirectoryId!!, docs.protectedKey)
                try { assertEquals("new", f.text(snapshot.entries.single())) } finally { snapshot.clearProtectedKeys() }
            } finally { docs.protectedKey.fill(0) }
        } finally { f.session.destroy() }
    }

    @Test fun `merging with every child skipped removes all unreferenced incoming content`() = runTest {
        val f = DirectFixture(temporary.newFolder())
        try {
            f.extract(listOf(source("Docs/Keep.txt", "original")))
            f.writtenContentPaths.clear()
            val skipChildren = VaultConflictResolver { if (it.canMerge) VaultConflictDecision.MERGE_DIRECTORIES else VaultConflictDecision.SKIP }
            val result = f.extract(listOf(source("Docs/Keep.txt", "discarded")), skipChildren)
            assertEquals(1, result.partial.size)
            assertEquals(1, f.writtenContentPaths.size)
            assertTrue(f.writtenContentPaths.none { f.storage.exists(it) })
            val entries = f.rootEntries()
            try {
                val docs = entries.single()
                val snapshot = f.session.readDirectory(docs.childDirectoryId!!, docs.protectedKey)
                try { assertEquals("original", f.text(snapshot.entries.single())) } finally { snapshot.clearProtectedKeys() }
            } finally { entries.forEach { it.protectedKey.fill(0) } }
        } finally { f.session.destroy() }
    }

    @Test fun `replaced folder references expire even when a provider cannot remove obsolete manifests`() = runTest {
        val f = DirectFixture(temporary.newFolder())
        try {
            f.extract(listOf(source("Docs/Keep.txt", "original")))
            val old = f.rootEntry("Docs")
            try {
                val oldId = old.childDirectoryId!!
                f.session.cacheDirectoryKey(oldId, old.protectedKey)
                val snapshot = f.session.readDirectory(oldId, old.protectedKey)
                try { f.failedDeletions += snapshot.pageObjectIds.map(VaultObjectId::shardedPath) }
                finally { snapshot.clearProtectedKeys() }
                f.failedDeletions += VaultDirectoryManifestCodec.rootSlot(oldId, 0)
                f.failedDeletions += VaultDirectoryManifestCodec.rootSlot(oldId, 1)
                f.extract(listOf(source("Docs/New.txt", "new")), merge)
                assertTrue(runCatching { f.session.resolveDirectory(oldId) }.exceptionOrNull() is VaultFailure.InvalidPath)
                assertTrue(f.storage.exists(VaultDirectoryManifestCodec.rootSlot(oldId, 0)))
            } finally { old.protectedKey.fill(0) }
        } finally { f.session.destroy() }
    }

    private class DirectFixture(directory: File) {
        val forbiddenReads = mutableSetOf<String>()
        val writtenContentPaths = mutableSetOf<String>()
        val failedDeletions = mutableSetOf<String>()
        var forbiddenReadAttempts = 0
        var fault: VaultTransactionStage? = null
        private val disk = FileVaultDirectory(directory)
        val storage = object : VaultDirectoryAccess by disk {
            override fun delete(relativePath: String): Boolean = if (relativePath in failedDeletions) false else disk.delete(relativePath)
            override fun openRandom(relativePath: String, writable: Boolean): VaultRandomAccess {
                if (writable) writtenContentPaths += relativePath
                if (!writable && relativePath in forbiddenReads) {
                    forbiddenReadAttempts++
                    error("An unrelated encrypted file was opened")
                }
                return disk.openRandom(relativePath, writable)
            }
        }
        private val codec = VaultDirectoryManifestCodec()
        private val files = VaultFileCodec()
        val transactions = VaultTransactionManager(codec, stageObserver = { if (it == fault) error("Injected publication failure") })
        val session = VaultSessionRecord(VaultId.random(), storage, VaultCryptography.randomBytes(32), codec)
        private val transfer = VaultTransferEngine(codec, files, transactions)
        private val writer = VaultArchiveWriter(files, codec, transactions)
        init {
            createVaultDirectories(storage)
            val root = session.root()
            try { codec.createRoot(storage, session.id, root.id, root.key) } finally { root.key.fill(0) }
        }
        suspend fun extract(entries: List<VaultArchiveSource>, resolver: VaultConflictResolver = replace) =
            writer.extractInto(session, DirectoryId.Root, entries, resolver, transfer, {}, {})
        fun rootEntries(): List<VaultManifestEntry> {
            val root = session.root()
            try {
                val snapshot = session.readDirectory(root)
                try { return snapshot.entries.map { it.copyDefensively() } } finally { snapshot.clearProtectedKeys() }
            } finally { root.key.fill(0) }
        }
        fun rootEntry(name: String): VaultManifestEntry {
            val entries = rootEntries()
            try { return entries.single { it.name == name }.copyDefensively() }
            finally { entries.forEach { it.protectedKey.fill(0) } }
        }
        fun text(name: String): String {
            val entries = rootEntries()
            try { return text(entries.single { it.name == name }) } finally { entries.forEach { it.protectedKey.fill(0) } }
        }
        fun text(entry: VaultManifestEntry): String = files.openObject(storage, entry.objectId!!.shardedPath(), session.id,
            requireNotNull(entry.objectId), entry.revision, entry.protectedKey).use { reader ->
            val bytes = ByteArray(reader.sizeBytes.toInt())
            check(reader.readAt(0, bytes, 0, bytes.size) == bytes.size)
            bytes.decodeToString()
        }
        fun objects() = directoryFiles()
        private fun directoryFiles() = disk.directory.walkTopDown().filter { it.isFile && it.extension == "obj" }.map { it.path }.toSet()
    }

    private inner class Fixture(val volumeRoot: File = context.cacheDir) {
        private val dispatcher = UnconfinedTestDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        private val volumes = object : VolumeRepository {
            val root = volumeRoot
            val volume = StorageVolume("test", "test", "Test", root.path, root.totalSpace, root.freeSpace, true, false)
            override fun observeStorageVolumes() = MutableStateFlow(listOf(volume))
            override suspend fun getStorageVolumes() = Result.success(listOf(volume))
            override suspend fun getVolumeForPath(path: String) = Result.success(volume)
            override fun getStandardFolders(): Map<String, String?> = emptyMap()
        }
        val repository = DefaultVaultRepository(context, ArcileDispatchers(dispatcher, dispatcher, dispatcher, dispatcher),
            scope, VaultPortableLocationResolver(volumes))
        var vault = VaultId.of("pending")
        suspend fun file(name: String, text: String, parent: DirectoryId = DirectoryId.Root): VaultNodeMetadata {
            val empty = repository.createEmptyFile(vault, parent, name).getOrThrow()
            return repository.replaceFileContents(empty.ref, empty.revision, text.encodeToByteArray()).getOrThrow()
        }
        suspend fun folder(name: String, parent: DirectoryId = DirectoryId.Root) = repository.createDirectory(vault, parent, name).getOrThrow()
        suspend fun rows(parent: DirectoryId = DirectoryId.Root) = repository.listDirectory(vault, parent).getOrThrow().items
        suspend fun extract(entries: List<VaultArchiveSource>, resolver: VaultConflictResolver = replace,
            progress: (VaultArchiveProgress) -> Unit = {}) = repository.extractArchiveInto(vault, DirectoryId.Root, entries, resolver, progress).getOrThrow()
        fun text(node: VaultNodeMetadata): String = repository.openReader(node.ref).getOrThrow().use { reader ->
            val bytes = ByteArray(reader.sizeBytes.toInt())
            var offset = 0
            while (offset < bytes.size) {
                val count = reader.readAt(offset.toLong(), bytes, offset, bytes.size - offset)
                check(count > 0)
                offset += count
            }
            bytes.decodeToString()
        }
        fun objects() = File(context.noBackupFilesDir, ROOT_DIRECTORY).walkTopDown()
            .filter { it.isFile && it.extension == "obj" }.map { it.path }.toSet()
    }

    private suspend fun fixture(portable: Boolean = false, test: suspend (Fixture) -> Unit) {
        val f = Fixture(if (portable) temporary.newFolder() else context.cacheDir)
        try {
            f.vault = if (portable) {
                check(File(f.volumeRoot, "portable").mkdir())
                f.repository.create(VaultCreationRequest("Archives", "password".toCharArray(),
                    VaultLocation.Portable("test", "portable"), weakPasswordConfirmed = true)).getOrThrow()
            } else f.repository.createAppPrivateVault("Archives", "password".toCharArray()).getOrThrow()
            test(f)
        } finally { f.repository.lockAll(); f.scope.cancel() }
    }

    private fun source(path: String, text: String): VaultArchiveSource.File {
        val bytes = text.encodeToByteArray()
        return VaultArchiveSource.File(path.split('/'), bytes.size.toLong(), CRC32().apply { update(bytes) }.value) { ByteArrayInputStream(bytes) }
    }

    private companion object {
        val replace = VaultConflictResolver { VaultConflictDecision.REPLACE }
        val skip = VaultConflictResolver { VaultConflictDecision.SKIP }
        val keepBoth = VaultConflictResolver { VaultConflictDecision.KEEP_BOTH }
        val merge = VaultConflictResolver { if (it.canMerge) VaultConflictDecision.MERGE_DIRECTORIES else VaultConflictDecision.REPLACE }
    }
}
