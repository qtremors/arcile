package dev.qtremors.arcile.core.vault.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import dev.qtremors.arcile.core.storage.domain.StorageVolume
import dev.qtremors.arcile.core.storage.domain.VolumeRepository
import dev.qtremors.arcile.core.vault.domain.VaultFailure
import dev.qtremors.arcile.core.vault.domain.DirectoryId
import dev.qtremors.arcile.core.vault.domain.VaultFolderTotals
import dev.qtremors.arcile.core.vault.domain.VaultArchiveSource
import dev.qtremors.arcile.core.vault.domain.VaultArchiveProgress
import dev.qtremors.arcile.core.vault.domain.VaultPath
import dev.qtremors.arcile.core.vault.domain.VaultHealthMode
import dev.qtremors.arcile.core.vault.domain.VaultListOptions
import dev.qtremors.arcile.core.vault.domain.VaultSearchQuery
import dev.qtremors.arcile.core.vault.domain.VaultObjectId
import dev.qtremors.arcile.core.vault.domain.VaultCreationRequest
import dev.qtremors.arcile.core.vault.domain.VaultLocation
import dev.qtremors.arcile.core.vault.domain.VaultRenameItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultVaultRepositoryTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        File(context.noBackupFilesDir, ROOT_DIRECTORY).deleteRecursively()
    }

    @After
    fun tearDown() {
        File(context.noBackupFilesDir, ROOT_DIRECTORY).deleteRecursively()
    }

    @Test
    fun `encrypted backup restores as a separate verified vault and rejects corruption`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val repository = DefaultVaultRepository(
            context, ArcileDispatchers(dispatcher, dispatcher, dispatcher, dispatcher), scope, testPortableResolver()
        )
        val directory = java.nio.file.Files.createTempDirectory("onlyfiles-backup").toFile()
        val password = "Backup-Password-7"
        try {
            val source = repository.createAppPrivateVault("Original", "original password".toCharArray()).getOrThrow()
            val folder = repository.createDirectory(source, DirectoryId.Root, "Confidential").getOrThrow()
            val folderId = requireNotNull(folder.ref.directoryId)
            val note = repository.createEmptyFile(source, folderId, "private.md", "text/markdown").getOrThrow()
            val original = "This is a private note".encodeToByteArray()
            val saved = repository.replaceFileContents(note.ref, note.revision, original).getOrThrow()

            val creationSecret = password.toCharArray()
            val backup = File(repository.createBackup(source, directory.path, "Snapshot", creationSecret, false).getOrThrow())
            assertTrue(creationSecret.all { it == '\u0000' })
            assertEquals("Snapshot.onlyfiles-backup", backup.name)
            assertEquals(listOf(source), repository.list().map { it.id })
            assertFalse(backup.walkTopDown().any { it.name == note.name || it.name == folder.name })
            assertTrue(backup.walkTopDown().filter { it.isFile }.none { original.contentEquals(it.readBytes()) })
            assertTrue(repository.createBackup(source, directory.path, "Snapshot", password.toCharArray(), false).isFailure)

            repository.replaceFileContents(saved.ref, saved.revision, "later edit".encodeToByteArray()).getOrThrow()
            val wrongSecret = "wrong password".toCharArray()
            assertTrue(repository.restoreBackup(backup.path, "Wrong", wrongSecret).isFailure)
            assertTrue(wrongSecret.all { it == '\u0000' })
            assertEquals(listOf(source), repository.list().map { it.id })

            val restorationSecret = password.toCharArray()
            val restored = repository.restoreBackup(backup.path, "Restored", restorationSecret).getOrThrow()
            assertTrue(restorationSecret.all { it == '\u0000' })
            assertTrue(restored != source)
            assertFalse(restored in repository.unlockedVaultIds.value)
            repository.unlock(restored, password.toCharArray()).getOrThrow()
            val restoredFolder = repository.listDirectory(restored, DirectoryId.Root, VaultListOptions())
                .getOrThrow().items.single()
            assertEquals("Confidential", restoredFolder.name)
            val restoredNote = repository.listDirectory(
                restored, requireNotNull(restoredFolder.ref.directoryId), VaultListOptions()
            ).getOrThrow().items.single()
            assertEquals(note.name, restoredNote.name)
            assertEquals(note.mimeType, restoredNote.mimeType)
            repository.openReader(restoredNote.ref).getOrThrow().use { reader ->
                val contents = ByteArray(original.size)
                assertEquals(original.size, reader.readAt(0, contents, 0, contents.size))
                assertTrue(contents.contentEquals(original))
            }
            assertTrue(repository.verify(restored, VaultHealthMode.FULL).getOrThrow().isHealthy)
            File(backup, OBJECTS_DIRECTORY).walkTopDown().first { it.isFile }.writeBytes(byteArrayOf(0))
            assertTrue(repository.restoreBackup(backup.path, "Corrupt", password.toCharArray()).isFailure)
            assertEquals(setOf(source, restored), repository.list().map { it.id }.toSet())
            val appStorage = File(context.noBackupFilesDir, ROOT_DIRECTORY)
            assertTrue(appStorage.listFiles().orEmpty().none { it.name.startsWith(STAGING_PREFIX) })
        } finally {
            scope.cancel()
            directory.deleteRecursively()
        }
    }

    @Test
    fun `vault lifecycle remains encrypted independent and lock safe`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val repository = DefaultVaultRepository(
            context,
            ArcileDispatchers(dispatcher, dispatcher, dispatcher, dispatcher),
            scope,
            testPortableResolver()
        )

        val firstPassword = "first password".toCharArray()
        val first = repository.createAppPrivateVault("First", firstPassword).getOrThrow()
        assertTrue(firstPassword.all { it == '\u0000' })
        val second = repository.createAppPrivateVault("Second", "second password".toCharArray()).getOrThrow()
        assertEquals(2, repository.vaults.value.size)
        assertTrue(first in repository.unlockedVaultIds.value)
        assertTrue(second in repository.unlockedVaultIds.value)

        val folder = repository.createDirectory(first, VaultPath.Root, "Photos").getOrThrow()
        val renamed = repository.rename(first, folder.path, "Private photos").getOrThrow()
        assertEquals("Private photos", renamed.name)

        val lease = repository.holdSession(first).getOrThrow()
        repository.lock(first)
        assertFalse(first in repository.unlockedVaultIds.value)
        assertTrue(repository.list(first).exceptionOrNull() is VaultFailure.Locked)
        lease.close()

        val wrongPassword = "wrong".toCharArray()
        assertTrue(repository.unlock(first, wrongPassword).exceptionOrNull() is VaultFailure.AuthenticationFailed)
        assertTrue(wrongPassword.all { it == '\u0000' })
        repository.unlock(first, "first password".toCharArray()).getOrThrow()
        assertEquals(listOf("Private photos"), repository.list(first).getOrThrow().map { it.name })
        repository.delete(first, renamed.path).getOrThrow()
        assertTrue(repository.list(first).getOrThrow().isEmpty())

        repository.lockAll()
        assertTrue(repository.unlockedVaultIds.value.isEmpty())
        scope.cancel()
    }

    @Test
    fun `app private vaults with the same visible label remain independent`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val repository = DefaultVaultRepository(
            context,
            ArcileDispatchers(dispatcher, dispatcher, dispatcher, dispatcher),
            scope,
            testPortableResolver()
        )
        val first = repository.createAppPrivateVault("Private", "one".toCharArray()).getOrThrow()
        val second = repository.createAppPrivateVault("private", "two".toCharArray()).getOrThrow()
        assertEquals(2, repository.vaults.value.size)
        assertTrue(first != second)
        scope.cancel()
    }

    @Test
    fun `stable id file system and health checks cover encrypted objects`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val repository = DefaultVaultRepository(
            context,
            ArcileDispatchers(dispatcher, dispatcher, dispatcher, dispatcher),
            scope,
            testPortableResolver()
        )
        val vaultId = repository.createAppPrivateVault("Stable", "password".toCharArray()).getOrThrow()
        val folder = repository.createDirectory(vaultId, VaultSessionRecord.ROOT_DIRECTORY_ID, "Folder").getOrThrow()
        val folderId = requireNotNull(folder.ref.directoryId)
        val empty = repository.createEmptyFile(vaultId, folderId, "empty.txt", "text/plain").getOrThrow()

        assertEquals(listOf("Folder"), repository.listDirectory(vaultId, VaultSessionRecord.ROOT_DIRECTORY_ID, VaultListOptions()).getOrThrow().items.map { it.name })
        assertEquals(listOf("empty.txt"), repository.listDirectory(vaultId, folderId, VaultListOptions()).getOrThrow().items.map { it.name })
        assertEquals("empty.txt", repository.metadata(empty.ref).getOrThrow().name)
        repository.openReader(empty.ref).getOrThrow().use { assertEquals(0L, it.sizeBytes) }
        val search = repository.search(
            vaultId,
            VaultSessionRecord.ROOT_DIRECTORY_ID,
            VaultSearchQuery("empty", recursive = true, pageSize = 1)
        ).getOrThrow()
        assertEquals(listOf("empty.txt"), search.items.map { it.metadata.name })
        assertEquals(listOf("Folder"), search.items.single().parentNames)
        assertEquals(listOf(folderId), search.items.single().parentDirectoryIds)
        assertEquals(null, search.nextPageToken)
        assertEquals(VaultFolderTotals(1L, 0L), repository.folderTotals(folder.ref).getOrThrow())

        val contents = "# Updated".encodeToByteArray()
        val saved = repository.replaceFileContents(empty.ref, empty.revision, contents).getOrThrow()
        assertEquals(empty.ref, saved.ref)
        assertEquals(empty.revision + 1L, saved.revision)
        assertEquals(VaultFolderTotals(1L, contents.size.toLong()), repository.folderTotals(folder.ref).getOrThrow())
        repository.createDirectory(vaultId, folderId, "Nested").getOrThrow()
        assertEquals(
            VaultFolderTotals(1L, contents.size.toLong(), folderCount = 1),
            repository.folderTotals(folder.ref).getOrThrow()
        )
        assertTrue(repository.replaceFileContents(empty.ref, empty.revision, byteArrayOf(1)).isFailure)
        repository.openReader(saved.ref).getOrThrow().use { reader ->
            val loaded = ByteArray(contents.size)
            assertEquals(contents.size, reader.readAt(0, loaded, 0, loaded.size))
            assertTrue(contents.contentEquals(loaded))
        }

        assertTrue(repository.verify(vaultId, VaultHealthMode.QUICK).getOrThrow().isHealthy)
        assertTrue(repository.verify(vaultId, VaultHealthMode.FULL).getOrThrow().isHealthy)

        val root = VaultSessionRecord.ROOT_DIRECTORY_ID
        val hidden = repository.createDirectory(vaultId, root, ".private").getOrThrow()
        repository.createEmptyFile(vaultId, requireNotNull(hidden.ref.directoryId), "hidden.txt").getOrThrow()
        val visiblePage = repository.listDirectory(
            vaultId, root, VaultListOptions(pageSize = 1, showHiddenFiles = false)
        ).getOrThrow()
        assertEquals(listOf("Folder"), visiblePage.items.map { it.name })
        assertEquals(null, visiblePage.nextPageToken)
        val filtered = repository.search(
            vaultId, root,
            VaultSearchQuery("", recursive = true, pageSize = 1, includeHidden = false) { !it.isDirectory }
        ).getOrThrow()
        assertEquals(listOf("empty.txt"), filtered.items.map { it.metadata.name })
        assertEquals(null, filtered.nextPageToken)

        val later = repository.createEmptyFile(vaultId, root, "z.txt").getOrThrow()
        val sortedQuery = VaultSearchQuery(
            ".txt", recursive = true, pageSize = 1, includeHidden = false,
            direction = dev.qtremors.arcile.core.vault.domain.VaultSortDirection.DESCENDING
        )
        val firstPage = repository.search(vaultId, root, sortedQuery).getOrThrow()
        assertEquals(later.ref, firstPage.items.single().metadata.ref)
        val nextPage = repository.search(vaultId, root, sortedQuery.copy(pageToken = requireNotNull(firstPage.nextPageToken))).getOrThrow()
        assertEquals(empty.ref, nextPage.items.single().metadata.ref)
        assertEquals(null, nextPage.nextPageToken)
        repository.replaceFileContents(empty.ref, saved.revision, "Changed between pages".encodeToByteArray()).getOrThrow()
        val changedPage = repository.search(vaultId, root, sortedQuery.copy(pageToken = firstPage.nextPageToken)).getOrThrow()
        assertTrue(firstPage.generation != changedPage.generation)
        val countSort = repository.listDirectory(
            vaultId, root,
            VaultListOptions(
                sortField = dev.qtremors.arcile.core.vault.domain.VaultSortField.FILE_COUNT,
                showHiddenFiles = false, foldersFirst = false
            )
        ).getOrThrow()
        assertEquals(listOf("Folder", "z.txt"), countSort.items.map { it.name })

        val hiddenReveal = repository.listDirectory(
            vaultId, root, VaultListOptions(showHiddenFiles = false, revealHiddenNodeId = hidden.ref.nodeId.value)
        ).getOrThrow()
        assertTrue(hiddenReveal.items.any { it.ref == hidden.ref })
        val folderPicker = repository.listDirectory(vaultId, root, VaultListOptions(foldersOnly = true)).getOrThrow()
        assertEquals(setOf("Folder", ".private"), folderPicker.items.map { it.name }.toSet())

        val nested = repository.listDirectory(vaultId, folderId, VaultListOptions(foldersOnly = true)).getOrThrow().items.single()
        val nestedId = requireNotNull(nested.ref.directoryId)
        repository.rename(folder.ref, "Renamed").getOrThrow()
        val trail = repository.directoryTrail(vaultId, nestedId).getOrThrow()
        assertEquals(listOf(root, folderId, nestedId), trail.map { it.id })
        assertEquals(listOf("", "Renamed", "Nested"), trail.map { it.name })

        val synthetic = repository.createSyntheticFile(vaultId, nestedId, "sample.bin", 4097L).getOrThrow()
        assertEquals(4097L, synthetic.sizeBytes)
        repository.openReader(synthetic.ref).getOrThrow().use { reader ->
            val tail = ByteArray(16)
            assertEquals(1, reader.readAt(4096L, tail, 0, tail.size))
            assertEquals(-1, reader.readAt(4097L, tail, 0, tail.size))
        }

        val orphan = VaultObjectId.of("f".repeat(64))
        val orphanFile = File(context.noBackupFilesDir, "$ROOT_DIRECTORY/${vaultId.value}/${orphan.shardedPath()}")
        orphanFile.parentFile?.mkdirs()
        orphanFile.writeBytes(byteArrayOf(1, 2, 3))
        val damaged = repository.verify(vaultId, VaultHealthMode.QUICK).getOrThrow()
        assertTrue(orphan in damaged.orphanObjectIds)
        assertEquals(1, repository.cleanupOrphans(vaultId, setOf(orphan)).getOrThrow())
        assertFalse(orphanFile.exists())
        repository.lock(vaultId)
        assertTrue(repository.directoryTrail(vaultId, nestedId).exceptionOrNull() is VaultFailure.Locked)
        scope.cancel()
    }

    @Test
    fun `vault batch rename publishes swaps across folders and preserves opaque references`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val repository = DefaultVaultRepository(
            context, ArcileDispatchers(dispatcher, dispatcher, dispatcher, dispatcher), scope, testPortableResolver()
        )
        try {
            val vault = repository.createAppPrivateVault("Rename", "password".toCharArray()).getOrThrow()
            val root = VaultSessionRecord.ROOT_DIRECTORY_ID
            val folder = repository.createDirectory(vault, root, "Folder").getOrThrow()
            val child = requireNotNull(folder.ref.directoryId)
            val first = repository.createEmptyFile(vault, root, "alpha.txt").getOrThrow()
            val second = repository.createEmptyFile(vault, root, "beta.txt").getOrThrow()
            val other = repository.createEmptyFile(vault, child, "ALPHA.txt").getOrThrow()
            val occupied = repository.createEmptyFile(vault, child, "Résumé.txt").getOrThrow()
            val changes = listOf(
                VaultRenameItem(first.ref, first.name, second.name),
                VaultRenameItem(second.ref, second.name, first.name),
                VaultRenameItem(other.ref, other.name, "alpha.txt")
            )

            val siblings = repository.batchRenameSiblingNames(changes).getOrThrow()
            assertEquals(setOf(folder.name), siblings[root])
            assertEquals(setOf(occupied.name), siblings[child])

            val invalid = changes.dropLast(1) + changes.last().copy(newName = "re\u0301sume\u0301.txt")
            assertTrue(repository.batchRename(invalid).exceptionOrNull() is VaultFailure.NameConflict)
            assertEquals(first.name, repository.metadata(first.ref).getOrThrow().name)
            assertEquals(second.name, repository.metadata(second.ref).getOrThrow().name)
            assertEquals(other.name, repository.metadata(other.ref).getOrThrow().name)

            val renamed = repository.batchRename(changes).getOrThrow()
            assertEquals(changes.map(VaultRenameItem::ref), renamed.map { it.ref })
            assertEquals(changes.map(VaultRenameItem::newName), renamed.map { it.name })
            assertEquals(listOf(first.revision, second.revision, other.revision), renamed.map { it.revision })
            assertTrue(repository.batchRename(changes).isFailure)
            assertTrue(repository.batchRenameSiblingNames(changes).isFailure)

            repository.lock(vault)
            assertTrue(repository.batchRename(changes).exceptionOrNull() is VaultFailure.Locked)
            repository.unlock(vault, "password".toCharArray()).getOrThrow()
            assertEquals(listOf("Folder", "alpha.txt", "beta.txt"), repository.listDirectory(vault, root)
                .getOrThrow().items.map { it.name })
            assertEquals(listOf("alpha.txt", "Résumé.txt"), repository.listDirectory(vault, child)
                .getOrThrow().items.map { it.name })
            repository.openReader(first.ref).getOrThrow().use { assertEquals(0L, it.sizeBytes) }

            val renamedFolder = repository.batchRename(listOf(VaultRenameItem(folder.ref, folder.name, "Cafe\u0301")))
                .getOrThrow().single()
            assertEquals(folder.ref, renamedFolder.ref)
            assertEquals("Café", renamedFolder.name)
            assertEquals("Café", repository.directoryTrail(vault, child).getOrThrow().last().name)
            assertEquals("alpha.txt", repository.metadata(other.ref).getOrThrow().name)

            val invalidPath = VaultRenameItem(renamedFolder.ref, renamedFolder.name, "../elsewhere")
            assertTrue(repository.batchRename(listOf(invalidPath)).exceptionOrNull() is VaultFailure.InvalidName)
            assertEquals("Café", repository.metadata(folder.ref).getOrThrow().name)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `vault complete selection matches filters and counts overlapping folders and files once`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val repository = DefaultVaultRepository(
            context, ArcileDispatchers(dispatcher, dispatcher, dispatcher, dispatcher), scope, testPortableResolver()
        )
        try {
            val vault = repository.createAppPrivateVault("Selection", "password".toCharArray()).getOrThrow()
            val root = VaultSessionRecord.ROOT_DIRECTORY_ID
            val folder = repository.createDirectory(vault, root, "Folder").getOrThrow()
            val folderId = requireNotNull(folder.ref.directoryId)
            val child = repository.createDirectory(vault, folderId, "Child").getOrThrow()
            val childId = requireNotNull(child.ref.directoryId)
            val first = repository.createSyntheticFile(vault, childId, "one.txt", 5).getOrThrow()
            val second = repository.createSyntheticFile(vault, folderId, "two.txt", 7).getOrThrow()
            repository.createSyntheticFile(vault, folderId, ".hidden.txt", 9).getOrThrow()
            val direct = repository.createSyntheticFile(vault, root, "root.txt", 2).getOrThrow()
            repository.createEmptyFile(vault, folderId, "other.bin").getOrThrow()

            val listOptions = VaultListOptions(pageSize = 1, showHiddenFiles = false)
            assertEquals(1, repository.listDirectory(vault, folderId, listOptions).getOrThrow().items.size)
            val listing = repository.selectDirectoryRows(vault, folderId, listOptions.copy(pageToken = "1")).getOrThrow()
            assertEquals(listOf("Child", "other.bin", "two.txt"), listing.items.map { it.name })
            assertEquals(null, listing.nextPageToken)

            val query = VaultSearchQuery(".txt", recursive = true, pageSize = 1, includeHidden = false) {
                it.ref.nodeId != direct.ref.nodeId
            }
            assertEquals("1", repository.search(vault, root, query).getOrThrow().nextPageToken)
            val results = repository.selectSearchRows(vault, root, query.copy(pageToken = "1")).getOrThrow()
            assertEquals(listOf(first.ref, second.ref), results.items.map { it.metadata.ref })
            assertEquals(null, results.nextPageToken)
            assertEquals(listOf("Folder", "Child"), results.items.first().parentNames)

            val overlapping = listOf(first.ref, child.ref, folder.ref, direct.ref, folder.ref)
            val totals = VaultFolderTotals(fileCount = 5, totalBytes = 23, folderCount = 2)
            assertEquals(totals, repository.selectionTotals(overlapping).getOrThrow())
            assertEquals(totals, repository.selectionTotals(overlapping.reversed()).getOrThrow())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `vault management moves a verified local file and leaves unsupported sources in place`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val repository = DefaultVaultRepository(
            context, ArcileDispatchers(dispatcher, dispatcher, dispatcher, dispatcher), scope, testPortableResolver()
        )
        val temporary = java.nio.file.Files.createTempDirectory("vault-move").toFile()
        try {
            val vault = repository.createAppPrivateVault("Moves", "password".toCharArray()).getOrThrow()
            val original = temporary.resolve("note.txt").also { it.writeText("verify this content") }
            val directory = temporary.resolve("Folder").also { assertTrue(it.mkdir()) }
            val directoryChild = directory.resolve("keep.txt").also { it.writeText("keep this") }
            val token = requireNotNull(repository.reserveImport(vault))

            val result = repository.importUris(token, VaultPath.Root, listOf(directory.path, original.path), move = true) {
                _, _, _, _, _ -> Unit
            }.getOrThrow()

            assertEquals(1, result.completed.size)
            assertEquals(1, result.rolledBack.size)
            assertFalse(original.exists())
            assertEquals("keep this", directoryChild.readText())
            val node = repository.listDirectory(vault, VaultSessionRecord.ROOT_DIRECTORY_ID).getOrThrow().items.single()
            assertEquals("note.txt", node.name)
            val contents = ByteArray(node.sizeBytes.toInt())
            repository.openReader(node.ref).getOrThrow().use {
                assertEquals(contents.size, it.readAt(0, contents, 0, contents.size))
            }
            assertEquals("verify this content", contents.decodeToString())

            val changed = temporary.resolve("changed.txt").also { it.writeText("keep the local original") }
            val oldModifiedAt = java.nio.file.Files.getLastModifiedTime(changed.toPath())
            var touched = false
            val secondToken = requireNotNull(repository.reserveImport(vault))
            val partiallyMoved = repository.importUris(
                secondToken, VaultPath.Root, listOf(changed.path), move = true
            ) { completed, _, bytes, _, _ ->
                if (completed == 0 && bytes > 0L && !touched) {
                    java.nio.file.Files.setLastModifiedTime(
                        changed.toPath(),
                        java.nio.file.attribute.FileTime.fromMillis(oldModifiedAt.toMillis() - 60_000L)
                    )
                    touched = true
                }
            }.getOrThrow()
            assertTrue(touched)
            assertEquals(1, partiallyMoved.partial.size)
            assertTrue(partiallyMoved.partial.single().failure is VaultFailure.SourceChanged)
            assertEquals("keep the local original", changed.readText())
            assertEquals(setOf("note.txt", "changed.txt"), repository.listDirectory(vault, VaultSessionRecord.ROOT_DIRECTORY_ID)
                .getOrThrow().items.map { it.name }.toSet())
        } finally {
            temporary.deleteRecursively()
            scope.cancel()
        }
    }

    @Test
    fun `vault management reports stored bytes and only returns folder IDs after deletion`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val repository = DefaultVaultRepository(
            context, ArcileDispatchers(dispatcher, dispatcher, dispatcher, dispatcher), scope, testPortableResolver()
        )
        try {
            val vault = repository.createAppPrivateVault("Usage", "password".toCharArray()).getOrThrow()
            val root = VaultSessionRecord.ROOT_DIRECTORY_ID
            val large = repository.createDirectory(vault, root, "Larger").getOrThrow()
            val small = repository.createDirectory(vault, root, "Smaller").getOrThrow()
            val largeId = requireNotNull(large.ref.directoryId)
            val child = repository.createDirectory(vault, largeId, "Nested").getOrThrow()
            val childId = requireNotNull(child.ref.directoryId)
            repository.createSyntheticFile(vault, childId, "child.bin", 13).getOrThrow()
            repository.createSyntheticFile(vault, largeId, "file.bin", 7).getOrThrow()
            repository.createSyntheticFile(vault, requireNotNull(small.ref.directoryId), "small.bin", 3).getOrThrow()

            val usage = repository.storageUsage(vault).getOrThrow()
            assertTrue(requireNotNull(usage.storedBytes) > 23L)
            assertFalse(usage.storedBytesPartial)
            assertEquals(2, usage.foldersMeasured)
            assertEquals(listOf("Larger" to 20L, "Smaller" to 3L), usage.largestFolders.map { it.name to it.totalBytes })
            assertEquals(setOf(largeId, childId), repository.deleteAndReportFolders(large.ref).getOrThrow())
            assertTrue(repository.deleteAndReportFolders(large.ref).isFailure)
            assertEquals(listOf("Smaller"), repository.storageUsage(vault).getOrThrow().largestFolders.map { it.name })
            repository.lock(vault)
            assertTrue(repository.storageUsage(vault).exceptionOrNull() is VaultFailure.Locked)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `vault ZIP output and extracted folders publish only after their data is complete`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val repository = DefaultVaultRepository(
            context, ArcileDispatchers(dispatcher, dispatcher, dispatcher, dispatcher), scope, testPortableResolver()
        )
        try {
            val vault = repository.createAppPrivateVault("Archives", "password".toCharArray()).getOrThrow()
            val root = VaultSessionRecord.ROOT_DIRECTORY_ID
            val contents = "private archive payload".encodeToByteArray()
            val crc = java.util.zip.CRC32().apply { update(contents) }.value
            val archive = repository.createZip(vault, root, "packed.zip") { output ->
                java.util.zip.ZipOutputStream(output).use { zip ->
                    zip.putNextEntry(java.util.zip.ZipEntry("docs/note.txt"))
                    zip.write(contents)
                    zip.closeEntry()
                }
            }.getOrThrow()
            assertEquals("application/zip", archive.mimeType)
            val zipBytes = ByteArray(archive.sizeBytes.toInt())
            repository.openReader(archive.ref).getOrThrow().use { reader ->
                assertEquals(zipBytes.size, reader.readAt(0, zipBytes, 0, zipBytes.size))
            }
            java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(zipBytes)).use { zip ->
                assertEquals("docs/note.txt", zip.nextEntry.name)
                assertTrue(contents.contentEquals(zip.readBytes()))
                assertEquals(null, zip.nextEntry)
            }

            val progress = mutableListOf<VaultArchiveProgress>()
            val file = VaultArchiveSource.File(listOf("docs", "note.txt"), contents.size.toLong(), crc) {
                java.io.ByteArrayInputStream(contents)
            }
            val directory = repository.extractArchive(
                vault, root, "Extracted", listOf(file, VaultArchiveSource.Folder(listOf("empty"))),
                onProgress = progress::add
            ).getOrThrow()
            val extractionId = requireNotNull(directory.ref.directoryId)
            val children = repository.listDirectory(vault, extractionId).getOrThrow().items
            assertEquals(listOf("docs", "empty"), children.map { it.name })
            val extracted = repository.listDirectory(vault, requireNotNull(children.first().ref.directoryId))
                .getOrThrow().items.single()
            repository.openReader(extracted.ref).getOrThrow().use { reader ->
                val actual = ByteArray(contents.size)
                assertEquals(actual.size, reader.readAt(0, actual, 0, actual.size))
                assertTrue(contents.contentEquals(actual))
            }
            assertEquals(1, progress.last().completed)

            val corrupt = file.copy(path = listOf("bad.txt"), crc32 = crc xor 1L)
            assertTrue(repository.extractArchive(vault, root, "Broken", listOf(file, corrupt)) {}.isFailure)
            assertTrue(repository.extractArchive(vault, root, "Invalid", listOf(file.copy(path = listOf("..", "bad")))) {}.isFailure)
            assertTrue(repository.extractArchive(vault, root, "Duplicate", listOf(file, file)) {}.isFailure)
            assertTrue(repository.extractArchive(vault, root, "Extracted", listOf(file)) {}.isFailure)
            assertTrue(repository.createZip(vault, root, "broken.zip") { output ->
                output.write(contents)
                error("Interrupted ZIP")
            }.isFailure)
            assertEquals(listOf("Extracted", "packed.zip"), repository.listDirectory(vault, root).getOrThrow().items.map { it.name })
            val kept = repository.extractArchive(vault, root, "Extracted", listOf(file), keepBoth = true) {}.getOrThrow()
            assertEquals("Extracted (1)", kept.name)
            val unused = repository.extractArchive(vault, root, "Another", listOf(file), keepBoth = true) {}.getOrThrow()
            assertEquals("Another", unused.name)
            val short = file.copy(path = listOf("short.txt"), sizeBytes = contents.size.toLong() + 1L)
            val overlong = file.copy(path = listOf("overlong.txt"), sizeBytes = contents.size.toLong() - 1L)
            assertTrue(repository.extractArchive(vault, root, "Short", listOf(file, short)) {}.isFailure)
            assertTrue(repository.extractArchive(vault, root, "Overlong", listOf(file, overlong)) {}.isFailure)
            val tooLarge = file.copy(
                path = listOf("too-large.txt"),
                sizeBytes = dev.qtremors.arcile.core.vault.domain.VAULT_ARCHIVE_MAX_BYTES + 1L,
                open = { error("An oversized entry must be rejected before it is opened") }
            )
            assertTrue(repository.extractArchive(vault, root, "Too large", listOf(tooLarge)) {}.isFailure)
            val conflict = listOf(file.copy(path = listOf("item")), file.copy(path = listOf("item", "child.txt")))
            assertTrue(repository.extractArchive(vault, root, "Conflict", conflict) {}.isFailure)
            val cancelled = file.copy(path = listOf("cancelled.txt"), open = { throw kotlinx.coroutines.CancellationException() })
            val cancellation = runCatching {
                repository.extractArchive(vault, root, "Cancelled", listOf(file, cancelled)) {}.getOrThrow()
            }
            assertTrue(cancellation.exceptionOrNull() is kotlinx.coroutines.CancellationException)
            assertEquals(
                listOf("Another", "Extracted", "Extracted (1)", "packed.zip"),
                repository.listDirectory(vault, root).getOrThrow().items.map { it.name }
            )
            assertTrue(repository.verify(vault, VaultHealthMode.FULL).getOrThrow().isHealthy)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `weak passwords require confirmation and password changes are atomic`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val repository = DefaultVaultRepository(
            context,
            ArcileDispatchers(dispatcher, dispatcher, dispatcher, dispatcher),
            scope,
            testPortableResolver()
        )

        val rejected = "short".toCharArray()
        val createFailure = repository.create(
            VaultCreationRequest("Rejected", rejected, VaultLocation.AppPrivate("new"), false)
        ).exceptionOrNull()
        assertTrue(createFailure is VaultFailure.WeakPasswordConfirmationRequired)
        assertTrue(rejected.all { it == '\u0000' })

        val original = "Correct-Horse-7".toCharArray()
        val vaultId = repository.create(
            VaultCreationRequest("Rotating", original, VaultLocation.AppPrivate("new"), false)
        ).getOrThrow()
        val current = "Correct-Horse-7".toCharArray()
        val weakReplacement = "tiny".toCharArray()
        assertTrue(
            repository.changePassword(vaultId, current, weakReplacement, false).exceptionOrNull()
                is VaultFailure.WeakPasswordConfirmationRequired
        )
        assertTrue(current.all { it == '\u0000' })
        assertTrue(weakReplacement.all { it == '\u0000' })

        repository.changePassword(
            vaultId,
            "Correct-Horse-7".toCharArray(),
            "Even-Better-Password-8".toCharArray(),
            false
        ).getOrThrow()
        repository.lock(vaultId)
        assertTrue(repository.unlock(vaultId, "Correct-Horse-7".toCharArray()).isFailure)
        repository.unlock(vaultId, "Even-Better-Password-8".toCharArray()).getOrThrow()
        scope.cancel()
    }

    @Test
    fun `destructive deletion requires the exact public label`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val repository = DefaultVaultRepository(
            context,
            ArcileDispatchers(dispatcher, dispatcher, dispatcher, dispatcher),
            scope,
            testPortableResolver()
        )
        val id = repository.createAppPrivateVault("Delete me", "password".toCharArray()).getOrThrow()
        assertTrue(
            repository.deletePermanently(id, "delete me").exceptionOrNull()
                is VaultFailure.DestructiveConfirmationRequired
        )
        assertEquals(1, repository.list().size)
        repository.deletePermanently(id, "Delete me").getOrThrow()
        assertTrue(repository.list().isEmpty())
        scope.cancel()
    }
}

private fun testPortableResolver(): VaultPortableLocationResolver = VaultPortableLocationResolver(
    object : VolumeRepository {
        private val root = File(requireNotNull(System.getProperty("java.io.tmpdir")))
        private val volume = StorageVolume(
            id = "test",
            storageKey = "test",
            name = "Test",
            path = root.path,
            totalBytes = root.totalSpace,
            freeBytes = root.freeSpace,
            isPrimary = true,
            isRemovable = false
        )

        override fun observeStorageVolumes() = MutableStateFlow(listOf(volume))
        override suspend fun getStorageVolumes() = Result.success(listOf(volume))
        override suspend fun getVolumeForPath(path: String) = Result.success(volume)
        override fun getStandardFolders(): Map<String, String?> = emptyMap()
    }
)
