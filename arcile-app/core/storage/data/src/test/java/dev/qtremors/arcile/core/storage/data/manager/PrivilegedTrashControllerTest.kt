package dev.qtremors.arcile.core.storage.data.manager

import dev.qtremors.arcile.core.operation.BulkFileOperationProgress
import dev.qtremors.arcile.core.privilege.PrivilegedFileFailure
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import dev.qtremors.arcile.core.storage.data.MutationFinalizer
import dev.qtremors.arcile.core.storage.data.provider.VolumeProvider
import dev.qtremors.arcile.core.storage.data.source.FileSystemDataSource
import dev.qtremors.arcile.core.storage.domain.BatchMutationResult
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.FileConflict
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.ListingPage
import dev.qtremors.arcile.core.storage.domain.StorageKind
import dev.qtremors.arcile.core.storage.domain.StorageNodeCapabilities
import dev.qtremors.arcile.core.storage.domain.StorageNodePath
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import dev.qtremors.arcile.core.storage.domain.StorageVolume
import dev.qtremors.arcile.core.storage.domain.TrashRestoreStatus
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class PrivilegedTrashControllerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()
    private val dispatchers = ArcileDispatchers(dispatcher, dispatcher, dispatcher, dispatcher)
    private val finalizer = mockk<MutationFinalizer>()

    @Test
    fun `Root file moves to backend trash and lists with original identity`() = runTest {
        val harness = harness()
        val source = harness.source.addFile("/storage/emulated/0/Documents/report.txt", 42L)
        val progress = mutableListOf<BulkFileOperationProgress>()

        val result = harness.controller.moveToTrash(
            listOf(TrashTarget(source.absolutePath, source.nodeRef)),
            progress::add
        )

        assertTrue(result.isSuccess)
        assertFalse(harness.source.exists(source.nodeRef))
        val payload = harness.source.model("/storage/emulated/0/.arcile/.trash/fixed-id/report.txt")
        assertNotNull(payload)
        assertEquals(StorageNodeRef.ROOT_BACKEND_ID, payload?.nodeRef?.backendId)
        assertEquals(listOf(0, 1), progress.map(BulkFileOperationProgress::completedItems))

        val listed = harness.controller.list().getOrThrow().single()
        assertEquals("privileged:fixed-id", listed.id)
        assertEquals(source.absolutePath, listed.originalPath)
        assertEquals("report.txt", listed.fileModel.name)
        assertEquals(payload?.nodeRef, listed.fileModel.nodeRef)
        assertEquals(TrashRestoreStatus.ORIGINAL_AVAILABLE, listed.restoreStatus)
    }

    @Test
    fun `Shizuku trash never substitutes a Root reference`() = runTest {
        val harness = harness(backend = StorageNodeRef.SHIZUKU_BACKEND_ID)
        val source = harness.source.addFile("/storage/emulated/0/Download/shell.log", 8L)

        harness.controller.moveToTrash(listOf(TrashTarget(source.absolutePath, source.nodeRef)), null)
            .getOrThrow()

        val item = harness.controller.list().getOrThrow().single()
        assertEquals(StorageNodeRef.SHIZUKU_BACKEND_ID, item.fileModel.nodeRef.backendId)
        assertEquals(StorageNodeRef.SHIZUKU_BACKEND_ID, harness.store.read("fixed-id")?.originalNode?.backendId)
    }

    @Test
    fun `protected node without trash capability is rejected before creating metadata`() = runTest {
        val harness = harness()
        val protected = harness.source.addFile(
            "/storage/emulated/0/Documents/protected.txt",
            1L,
            canTrash = false
        )

        val result = harness.controller.moveToTrash(
            listOf(TrashTarget(protected.absolutePath, protected.nodeRef)),
            null
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("permanent delete"))
        assertTrue(harness.store.list().isEmpty())
        assertTrue(harness.source.exists(protected.nodeRef))
    }

    @Test
    fun `unclassified volume cannot receive trash payloads`() = runTest {
        val harness = harness(kind = StorageKind.EXTERNAL_UNCLASSIFIED)
        val source = harness.source.addFile("/storage/emulated/0/Documents/report.txt", 1L)

        val result = harness.controller.moveToTrash(
            listOf(TrashTarget(source.absolutePath, source.nodeRef)),
            null
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("not supported"))
        assertTrue(harness.store.list().isEmpty())
    }

    @Test
    fun `failure before moving removes metadata and empty container`() = runTest {
        val harness = harness()
        val source = harness.source.addFile("/storage/emulated/0/Documents/report.txt", 1L)
        harness.source.moveBehavior = MoveBehavior.FAIL_BEFORE

        val result = harness.controller.moveToTrash(
            listOf(TrashTarget(source.absolutePath, source.nodeRef)),
            null
        )

        assertTrue(result.isFailure)
        assertTrue(harness.source.exists(source.nodeRef))
        assertNull(harness.store.read("fixed-id"))
        assertNull(harness.source.model("/storage/emulated/0/.arcile/.trash/fixed-id"))
    }

    @Test
    fun `failure reported after committed move is reconciled as success`() = runTest {
        val harness = harness()
        val source = harness.source.addFile("/storage/emulated/0/Documents/report.txt", 1L)
        harness.source.moveBehavior = MoveBehavior.FAIL_AFTER

        val result = harness.controller.moveToTrash(
            listOf(TrashTarget(source.absolutePath, source.nodeRef)),
            null
        )

        assertTrue(result.isSuccess)
        assertFalse(harness.source.exists(source.nodeRef))
        assertNotNull(harness.store.read("fixed-id"))
        assertNotNull(harness.source.model("/storage/emulated/0/.arcile/.trash/fixed-id/report.txt"))
    }

    @Test
    fun `ambiguous disconnect retains metadata for later reconciliation`() = runTest {
        val harness = harness()
        val source = harness.source.addFile("/storage/emulated/0/Documents/report.txt", 1L)
        harness.source.moveBehavior = MoveBehavior.DISCONNECT_BEFORE

        val result = harness.controller.moveToTrash(
            listOf(TrashTarget(source.absolutePath, source.nodeRef)),
            null
        )

        assertTrue(result.isFailure)
        assertNotNull(harness.store.read("fixed-id"))
        harness.source.available = true
        assertTrue(harness.source.exists(source.nodeRef))
        assertTrue(harness.controller.list().getOrThrow().isEmpty())
        assertNull(harness.store.read("fixed-id"))
    }

    @Test
    fun `backend loss keeps cached trash item visible and unavailable`() = runTest {
        val harness = harness()
        val source = harness.source.addFile("/storage/emulated/0/Documents/report.txt", 5L)
        harness.controller.moveToTrash(listOf(TrashTarget(source.absolutePath, source.nodeRef)), null)
            .getOrThrow()
        harness.source.available = false

        val item = harness.controller.list().getOrThrow().single()

        assertEquals(TrashRestoreStatus.BACKEND_UNAVAILABLE, item.restoreStatus)
        assertEquals(5L, item.fileModel.size)
        assertEquals(StorageNodeRef.ROOT_BACKEND_ID, item.fileModel.nodeRef.backendId)
    }

    @Test
    fun `restore returns payload to original parent and removes metadata`() = runTest {
        val harness = harness()
        val source = harness.source.addFile("/storage/emulated/0/Documents/report.txt", 42L)
        harness.controller.moveToTrash(listOf(TrashTarget(source.absolutePath, source.nodeRef)), null)
            .getOrThrow()

        val result = harness.controller.restore(listOf("privileged:fixed-id"), null)

        assertTrue(result.isSuccess)
        assertNotNull(harness.source.model(source.absolutePath))
        assertNull(harness.source.model("/storage/emulated/0/.arcile/.trash/fixed-id"))
        assertNull(harness.store.read("fixed-id"))
    }

    @Test
    fun `restore conflict renames payload without replacing existing file`() = runTest {
        val harness = harness(clock = 999L)
        val source = harness.source.addFile("/storage/emulated/0/Documents/report.txt", 42L)
        harness.controller.moveToTrash(listOf(TrashTarget(source.absolutePath, source.nodeRef)), null)
            .getOrThrow()
        harness.source.addFile(source.absolutePath, 100L)

        harness.controller.restore(listOf("privileged:fixed-id"), null).getOrThrow()

        assertEquals(100L, harness.source.model(source.absolutePath)?.size)
        assertEquals(
            42L,
            harness.source.model("/storage/emulated/0/Documents/report.restore-conflict-999.txt")?.size
        )
    }

    @Test
    fun `missing original parent requests a destination without moving payload`() = runTest {
        val harness = harness()
        val source = harness.source.addFile("/storage/emulated/0/Documents/report.txt", 42L)
        harness.controller.moveToTrash(listOf(TrashTarget(source.absolutePath, source.nodeRef)), null)
            .getOrThrow()
        harness.source.remove("/storage/emulated/0/Documents")

        val result = harness.controller.restore(listOf("privileged:fixed-id"), null)

        val error = result.exceptionOrNull()
        assertTrue(error is dev.qtremors.arcile.core.storage.domain.DestinationRequiredException)
        assertEquals(listOf("privileged:fixed-id"), (error as dev.qtremors.arcile.core.storage.domain.DestinationRequiredException).trashIds)
        assertNotNull(harness.store.read("fixed-id"))
    }

    @Test
    fun `explicit privileged destination restores when original parent is gone`() = runTest {
        val harness = harness()
        val source = harness.source.addFile("/storage/emulated/0/Documents/report.txt", 42L)
        harness.source.addDirectory("/storage/emulated/0/Recovered")
        harness.controller.moveToTrash(listOf(TrashTarget(source.absolutePath, source.nodeRef)), null)
            .getOrThrow()
        harness.source.remove("/storage/emulated/0/Documents")

        harness.controller.restore(
            listOf("privileged:fixed-id"),
            "/storage/emulated/0/Recovered"
        ).getOrThrow()

        assertEquals(42L, harness.source.model("/storage/emulated/0/Recovered/report.txt")?.size)
        assertNull(harness.store.read("fixed-id"))
    }

    @Test
    fun `permanent delete removes payload container before metadata`() = runTest {
        val harness = harness()
        val source = harness.source.addFile("/storage/emulated/0/Documents/report.txt", 42L)
        harness.controller.moveToTrash(listOf(TrashTarget(source.absolutePath, source.nodeRef)), null)
            .getOrThrow()

        harness.controller.delete(listOf("privileged:fixed-id")).getOrThrow()

        assertNull(harness.source.model("/storage/emulated/0/.arcile/.trash/fixed-id"))
        assertNull(harness.store.read("fixed-id"))
    }

    @Test
    fun `delete failure retains metadata and payload`() = runTest {
        val harness = harness()
        val source = harness.source.addFile("/storage/emulated/0/Documents/report.txt", 42L)
        harness.controller.moveToTrash(listOf(TrashTarget(source.absolutePath, source.nodeRef)), null)
            .getOrThrow()
        harness.source.deleteFailure = PrivilegedFileFailure.ReadOnlyFilesystem("trash")

        val result = harness.controller.delete(listOf("privileged:fixed-id"))

        assertTrue(result.isFailure)
        assertNotNull(harness.store.read("fixed-id"))
        assertNotNull(harness.source.model("/storage/emulated/0/.arcile/.trash/fixed-id/report.txt"))
    }

    @Test
    fun `empty deletes every privileged record`() = runTest {
        val harness = harness(ids = ArrayDeque(listOf("one", "two")))
        val first = harness.source.addFile("/storage/emulated/0/Documents/one.txt", 1L)
        val second = harness.source.addFile("/storage/emulated/0/Documents/two.txt", 2L)
        harness.controller.moveToTrash(listOf(TrashTarget(first.absolutePath, first.nodeRef)), null).getOrThrow()
        harness.controller.moveToTrash(listOf(TrashTarget(second.absolutePath, second.nodeRef)), null).getOrThrow()

        harness.controller.empty().getOrThrow()

        assertTrue(harness.store.list().isEmpty())
        assertTrue(harness.controller.list().getOrThrow().isEmpty())
    }

    @Test
    fun `storage usage combines file and directory payload sizes by volume`() = runTest {
        val harness = harness(ids = ArrayDeque(listOf("file", "folder")))
        val file = harness.source.addFile("/storage/emulated/0/Documents/file.bin", 10L)
        val folder = harness.source.addDirectory("/storage/emulated/0/Documents/folder")
        harness.source.addFile("/storage/emulated/0/Documents/folder/child.bin", 30L)
        harness.controller.moveToTrash(listOf(TrashTarget(file.absolutePath, file.nodeRef)), null).getOrThrow()
        harness.controller.moveToTrash(listOf(TrashTarget(folder.absolutePath, folder.nodeRef)), null).getOrThrow()

        val usage = harness.controller.storageUsage().getOrThrow()

        assertEquals(40L, usage.totalBytes)
        assertEquals(mapOf("primary" to 40L), usage.byVolumeId)
    }

    @Test(expected = CancellationException::class)
    fun `cancellation is never converted to a failed trash result`() = runTest {
        val harness = harness()
        val source = harness.source.addFile("/storage/emulated/0/Documents/report.txt", 1L)
        harness.source.moveBehavior = MoveBehavior.CANCEL

        harness.controller.moveToTrash(listOf(TrashTarget(source.absolutePath, source.nodeRef)), null)
    }

    @Test
    fun `invalid external ids cannot address another metadata file`() = runTest {
        val harness = harness()

        val restore = harness.controller.restore(listOf("privileged:../escape"), null)
        val delete = harness.controller.delete(listOf("privileged:../escape"))

        assertTrue(restore.isFailure)
        assertTrue(delete.isFailure)
    }

    @Test
    fun `multiple targets publish monotonic item progress`() = runTest {
        val harness = harness(ids = ArrayDeque(listOf("one", "two")))
        val first = harness.source.addFile("/storage/emulated/0/Documents/one.txt", 1L)
        val second = harness.source.addFile("/storage/emulated/0/Documents/two.txt", 2L)
        val progress = mutableListOf<BulkFileOperationProgress>()

        harness.controller.moveToTrash(
            listOf(
                TrashTarget(first.absolutePath, first.nodeRef),
                TrashTarget(second.absolutePath, second.nodeRef)
            ),
            progress::add
        ).getOrThrow()

        assertEquals(listOf(0, 1, 1, 2), progress.map(BulkFileOperationProgress::completedItems))
        assertTrue(progress.all { it.totalItems == 2 })
    }

    @Test
    fun `generated id collision retries without overwriting existing trash metadata`() = runTest {
        val harness = harness(ids = ArrayDeque(listOf("same", "same", "unique")))
        val first = harness.source.addFile(
            "/storage/emulated/0/Documents/first.txt",
            1L
        )
        val second = harness.source.addFile(
            "/storage/emulated/0/Documents/second.txt",
            2L
        )

        harness.controller.moveToTrash(
            listOf(TrashTarget(first.absolutePath, first.nodeRef)),
            null
        ).getOrThrow()
        harness.controller.moveToTrash(
            listOf(TrashTarget(second.absolutePath, second.nodeRef)),
            null
        ).getOrThrow()

        assertEquals(setOf("unique", "same"), harness.store.list().map(PrivilegedTrashRecord::id).toSet())
        assertEquals("first.txt", harness.store.read("same")?.originalFile?.name)
        assertEquals("second.txt", harness.store.read("unique")?.originalFile?.name)
        assertNotNull(harness.source.model("/storage/emulated/0/.arcile/.trash/same/first.txt"))
        assertNotNull(harness.source.model("/storage/emulated/0/.arcile/.trash/unique/second.txt"))
    }

    @Test
    fun `id collision exhaustion leaves the new source untouched`() = runTest {
        val repeatedIds = MutableList(33) { "same" }
        val harness = harness(ids = ArrayDeque(repeatedIds))
        val first = harness.source.addFile(
            "/storage/emulated/0/Documents/first.txt",
            1L
        )
        val second = harness.source.addFile(
            "/storage/emulated/0/Documents/second.txt",
            2L
        )
        harness.controller.moveToTrash(
            listOf(TrashTarget(first.absolutePath, first.nodeRef)),
            null
        ).getOrThrow()

        val result = harness.controller.moveToTrash(
            listOf(TrashTarget(second.absolutePath, second.nodeRef)),
            null
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("unique privileged trash id"))
        assertTrue(harness.source.exists(second.nodeRef))
        assertEquals(listOf("same"), harness.store.list().map(PrivilegedTrashRecord::id))
        assertNull(harness.source.model("/storage/emulated/0/.arcile/.trash/same/second.txt"))
    }

    private fun harness(
        backend: String = StorageNodeRef.ROOT_BACKEND_ID,
        kind: StorageKind = StorageKind.INTERNAL,
        clock: Long = 100L,
        ids: ArrayDeque<String> = ArrayDeque(listOf("fixed-id"))
    ): Harness {
        coEvery { finalizer.finalize(*anyVararg()) } just Runs
        val source = InMemoryNodeDataSource(backend)
        source.addDirectory("/storage/emulated/0")
        source.addDirectory("/storage/emulated/0/Documents")
        source.addDirectory("/storage/emulated/0/Download")
        val volume = StorageVolume(
            id = "primary",
            storageKey = "primary",
            name = "Internal",
            path = "/storage/emulated/0",
            totalBytes = 1_000L,
            freeBytes = 500L,
            isPrimary = true,
            isRemovable = false,
            kind = kind
        )
        val volumeProvider = TestVolumeProvider(listOf(volume))
        val store = PrivilegedTrashMetadataStore(temporaryFolder.newFolder())
        val controller = PrivilegedTrashController(
            fileSystemDataSource = source,
            volumeProvider = volumeProvider,
            mutationFinalizer = finalizer,
            metadataStore = store,
            dispatchers = dispatchers,
            clock = { clock },
            idFactory = { ids.removeFirst() }
        )
        return Harness(controller, source, store)
    }

    private data class Harness(
        val controller: PrivilegedTrashController,
        val source: InMemoryNodeDataSource,
        val store: PrivilegedTrashMetadataStore
    )
}

private enum class MoveBehavior { SUCCESS, FAIL_BEFORE, FAIL_AFTER, DISCONNECT_BEFORE, CANCEL }

private class InMemoryNodeDataSource(private val backendId: String) : FileSystemDataSource {
    private val entries = linkedMapOf<String, FileModel>()
    var available: Boolean = true
    var moveBehavior: MoveBehavior = MoveBehavior.SUCCESS
    var deleteFailure: Throwable? = null

    fun addDirectory(path: String): FileModel = add(path, directory = true, size = 0L, canTrash = true)
    fun addFile(path: String, size: Long, canTrash: Boolean = true): FileModel =
        add(path, directory = false, size = size, canTrash = canTrash)

    fun model(path: String): FileModel? = entries[key(path)]
    fun exists(node: StorageNodeRef): Boolean = model(node.displayPath.absolutePath) != null
    fun remove(path: String) {
        entries.keys.filter { it == key(path) || it.startsWith("${key(path)}/") }.forEach(entries::remove)
    }

    private fun add(path: String, directory: Boolean, size: Long, canTrash: Boolean): FileModel {
        val node = ref(path, canTrash)
        return FileModel(
            name = path.substringAfterLast('/').ifBlank { path },
            absolutePath = path,
            size = if (directory) 0L else size,
            isDirectory = directory,
            extension = if (directory) "" else path.substringAfterLast('.', ""),
            nodeRef = node
        ).also { entries[key(path)] = it }
    }

    override fun list(path: StorageNodePath, pageSize: Int): Flow<ListingPage> = flowOf(
        ListingPage(path = path, files = emptyList(), pageIndex = 0, isComplete = true)
    )

    override fun getStandardFolders(): Map<String, String?> = emptyMap()
    override suspend fun listFiles(path: String): Result<List<FileModel>> = unsupported()
    override suspend fun createDirectory(parentPath: String, name: String): Result<FileModel> = unsupported()
    override suspend fun createFile(parentPath: String, name: String): Result<FileModel> = unsupported()
    override suspend fun deletePermanently(paths: List<String>): Result<Unit> = unsupported()
    override suspend fun deletePermanentlyDetailed(paths: List<String>): Result<BatchMutationResult> = unsupported()
    override suspend fun shred(paths: List<String>): Result<Unit> = unsupported()
    override suspend fun shredDetailed(paths: List<String>): Result<BatchMutationResult> = unsupported()
    override suspend fun renameFile(path: String, newName: String): Result<FileModel> = unsupported()
    override suspend fun detectCopyConflicts(sourcePaths: List<String>, destinationPath: String): Result<List<FileConflict>> = unsupported()
    override suspend fun copyFiles(
        sourcePaths: List<String>,
        destinationPath: String,
        resolutions: Map<String, ConflictResolution>,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ): Result<Unit> = unsupported()
    override suspend fun moveFiles(
        sourcePaths: List<String>,
        destinationPath: String,
        resolutions: Map<String, ConflictResolution>,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ): Result<Unit> = unsupported()
    override suspend fun createFakeFile(
        parentPath: String,
        name: String,
        size: Long,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ): Result<FileModel> = unsupported()

    override suspend fun inspectNode(node: StorageNodeRef): Result<FileModel> {
        if (!available) return Result.failure(PrivilegedFileFailure.BackendDisconnected())
        return model(node.displayPath.absolutePath)?.let(Result.Companion::success)
            ?: Result.failure(PrivilegedFileFailure.PathMissing(node.displayPath.absolutePath))
    }

    override suspend fun listNodeFiles(directory: StorageNodeRef): Result<List<FileModel>> {
        if (!available) return Result.failure(PrivilegedFileFailure.BackendDisconnected())
        val parent = directory.displayPath.absolutePath.trimEnd('/')
        return Result.success(entries.values.filter { file ->
            file.absolutePath.substringBeforeLast('/', "") == parent
        })
    }

    override suspend fun createNodeDirectory(parent: StorageNodeRef, name: String): Result<FileModel> {
        if (!available) return Result.failure(PrivilegedFileFailure.BackendDisconnected())
        val path = parent.displayPath.absolutePath.child(name)
        if (model(path) != null) return Result.failure(PrivilegedFileFailure.PathAlreadyExists(path))
        return Result.success(addDirectory(path))
    }

    override suspend fun renameNode(node: StorageNodeRef, newName: String): Result<FileModel> {
        val source = inspectNode(node).getOrElse { return Result.failure(it) }
        val destination = source.absolutePath.substringBeforeLast('/', "/").child(newName)
        if (model(destination) != null) return Result.failure(PrivilegedFileFailure.PathAlreadyExists(destination))
        moveTree(source.absolutePath, destination)
        return Result.success(requireNotNull(model(destination)))
    }

    override suspend fun moveNodes(
        sources: Collection<StorageNodeRef>,
        destination: StorageNodeRef,
        resolutions: Map<String, ConflictResolution>,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ): Result<Unit> {
        if (moveBehavior == MoveBehavior.CANCEL) return Result.failure(CancellationException("cancelled"))
        if (moveBehavior == MoveBehavior.DISCONNECT_BEFORE) {
            available = false
            return Result.failure(PrivilegedFileFailure.BackendDisconnected())
        }
        if (moveBehavior == MoveBehavior.FAIL_BEFORE) {
            return Result.failure(PrivilegedFileFailure.IoFailure("move failed"))
        }
        for (sourceRef in sources) {
            val source = inspectNode(sourceRef).getOrElse { return Result.failure(it) }
            moveTree(source.absolutePath, destination.displayPath.absolutePath.child(source.name))
        }
        return if (moveBehavior == MoveBehavior.FAIL_AFTER) {
            Result.failure(PrivilegedFileFailure.OperationInterrupted())
        } else {
            Result.success(Unit)
        }
    }

    override suspend fun deleteNodesPermanently(nodes: Collection<StorageNodeRef>): Result<Unit> {
        deleteFailure?.let { return Result.failure(it) }
        if (!available) return Result.failure(PrivilegedFileFailure.BackendDisconnected())
        nodes.forEach { remove(it.displayPath.absolutePath) }
        return Result.success(Unit)
    }

    private fun moveTree(sourcePath: String, destinationPath: String) {
        val moving = entries.values.filter {
            it.absolutePath == sourcePath || it.absolutePath.startsWith("$sourcePath/")
        }.sortedBy { it.absolutePath.length }
        moving.forEach { entries.remove(key(it.absolutePath)) }
        moving.forEach { file ->
            val newPath = destinationPath + file.absolutePath.removePrefix(sourcePath)
            val newNode = ref(newPath, file.nodeRef.capabilities.canTrash)
            entries[key(newPath)] = file.copy(
                name = newPath.substringAfterLast('/'),
                absolutePath = newPath,
                nodeRef = newNode
            )
        }
    }

    private fun ref(path: String, canTrash: Boolean): StorageNodeRef {
        val capabilities = StorageNodeCapabilities(canTrash = canTrash)
        return when (backendId) {
            StorageNodeRef.ROOT_BACKEND_ID -> StorageNodeRef.root(path, path, capabilities = capabilities)
            else -> StorageNodeRef.shizuku(path, path, capabilities = capabilities)
        }
    }

    private fun key(path: String): String = "$backendId|$path"
    private fun String.child(name: String): String = if (this == "/") "/$name" else "${trimEnd('/')}/$name"
    private fun <T> unsupported(): Result<T> = Result.failure(UnsupportedOperationException())
}

private class TestVolumeProvider(private val volumes: List<StorageVolume>) : VolumeProvider {
    override val activeStorageRoots: List<String> = volumes.map(StorageVolume::path)
    override fun observeStorageVolumes() = flowOf(volumes)
    override suspend fun getStorageVolumes(): Result<List<StorageVolume>> = Result.success(volumes)
    override suspend fun currentVolumes(): List<StorageVolume> = volumes
    override fun invalidateCache() = Unit
}
