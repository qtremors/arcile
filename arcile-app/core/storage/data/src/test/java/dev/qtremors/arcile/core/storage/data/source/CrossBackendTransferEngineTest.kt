package dev.qtremors.arcile.core.storage.data.source

import dev.qtremors.arcile.core.operation.BulkFileOperationProgress
import dev.qtremors.arcile.core.privilege.PrivilegeBackendId
import dev.qtremors.arcile.core.privilege.PrivilegeCapability
import dev.qtremors.arcile.core.privilege.PrivilegeServiceIdentity
import dev.qtremors.arcile.core.privilege.PrivilegeSession
import dev.qtremors.arcile.core.privilege.PrivilegeTransport
import dev.qtremors.arcile.core.privilege.PrivilegedDirectoryPage
import dev.qtremors.arcile.core.privilege.PrivilegedFileClient
import dev.qtremors.arcile.core.privilege.PrivilegedFileClientProvider
import dev.qtremors.arcile.core.privilege.PrivilegedFileEntry
import dev.qtremors.arcile.core.privilege.PrivilegedFileFailure
import dev.qtremors.arcile.core.privilege.PrivilegedFileHandle
import dev.qtremors.arcile.core.privilege.PrivilegedFileType
import dev.qtremors.arcile.core.privilege.PrivilegedFilesystemStats
import dev.qtremors.arcile.core.privilege.PrivilegedHandshake
import dev.qtremors.arcile.core.privilege.PrivilegedOpenMode
import dev.qtremors.arcile.core.privilege.PrivilegedOperationId
import dev.qtremors.arcile.core.privilege.PrivilegedOperationProgress
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import dev.qtremors.arcile.core.storage.domain.BatchMutationFailure
import dev.qtremors.arcile.core.storage.domain.BatchMutationResult
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.FileConflict
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.ListingPage
import dev.qtremors.arcile.core.storage.domain.StorageNodeCapabilities
import dev.qtremors.arcile.core.storage.domain.StorageNodePath
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CrossBackendTransferEngineTest {
    private lateinit var localRoot: File
    private lateinit var local: TestLocalFileSystemDataSource
    private lateinit var remoteClient: MemoryPrivilegedFileClient
    private lateinit var remote: PrivilegedFileSystemDataSource

    @Before
    fun setUp() {
        localRoot = createTempDir(prefix = "arcile-cross-backend-")
        local = TestLocalFileSystemDataSource(localRoot)
        remoteClient = MemoryPrivilegedFileClient(ROOT_SESSION).apply {
            directory(REMOTE_ROOT)
            directory(REMOTE_SOURCE)
            directory(REMOTE_DESTINATION)
        }
    }

    @After
    fun tearDown() {
        localRoot.deleteRecursively()
        remoteClient.close()
    }

    @Test
    fun `copies local file into privileged destination through descriptor streams`() = runTest {
        val payload = patternedBytes(320_000)
        val source = localFile("source/payload.bin", payload)
        val progress = mutableListOf<BulkFileOperationProgress>()
        val engine = engine()

        val result = engine.copy(
            sources = listOf(StorageNodeRef.local(source.absolutePath)),
            destination = rootRef(REMOTE_DESTINATION),
            resolutions = emptyMap(),
            onProgress = progress::add
        )

        assertTrue(result.exceptionOrNull()?.stackTraceToString(), result.isSuccess)
        assertArrayEquals(payload, remoteClient.content("$REMOTE_DESTINATION/payload.bin"))
        assertTrue(source.exists())
        assertTrue(remoteClient.paths.none { it.endsWith(".partial") })
        assertEquals(payload.size.toLong(), progress.last().bytesCopied)
        assertEquals(1, progress.last().completedItems)
    }

    @Test
    fun `copies privileged file into local destination through descriptor streams`() = runTest {
        val payload = "privileged report".repeat(40_000).encodeToByteArray()
        remoteClient.file("$REMOTE_SOURCE/report.txt", payload)
        val destination = localDirectory("destination")

        val result = engine().copy(
            sources = listOf(rootRef("$REMOTE_SOURCE/report.txt")),
            destination = StorageNodeRef.local(destination.absolutePath),
            resolutions = emptyMap()
        )

        assertTrue(result.exceptionOrNull()?.stackTraceToString(), result.isSuccess)
        assertArrayEquals(payload, File(destination, "report.txt").readBytes())
        assertArrayEquals(payload, remoteClient.content("$REMOTE_SOURCE/report.txt"))
        assertTrue(destination.listFiles().orEmpty().none { it.name.endsWith(".partial") })
    }

    @Test
    fun `copies nested directory without recursive call stack`() = runTest {
        remoteClient.directory("$REMOTE_SOURCE/tree")
        remoteClient.directory("$REMOTE_SOURCE/tree/one")
        remoteClient.directory("$REMOTE_SOURCE/tree/one/two")
        remoteClient.directory("$REMOTE_SOURCE/tree/one/two/three")
        remoteClient.file("$REMOTE_SOURCE/tree/root.txt", "root".encodeToByteArray())
        remoteClient.file("$REMOTE_SOURCE/tree/one/first.txt", "first".encodeToByteArray())
        remoteClient.file("$REMOTE_SOURCE/tree/one/two/three/deep.txt", "deep".encodeToByteArray())
        val destination = localDirectory("nested-destination")

        val result = engine().copy(
            sources = listOf(rootRef("$REMOTE_SOURCE/tree")),
            destination = StorageNodeRef.local(destination.absolutePath),
            resolutions = emptyMap()
        )

        assertTrue(result.exceptionOrNull()?.stackTraceToString(), result.isSuccess)
        assertEquals("root", File(destination, "tree/root.txt").readText())
        assertEquals("first", File(destination, "tree/one/first.txt").readText())
        assertEquals("deep", File(destination, "tree/one/two/three/deep.txt").readText())
        assertTrue(destination.walkTopDown().none { it.name.endsWith(".partial") })
    }

    @Test
    fun `detects destination conflict without changing either backend`() = runTest {
        val source = localFile("conflict/report.txt", "new".encodeToByteArray())
        remoteClient.file("$REMOTE_DESTINATION/report.txt", "old".encodeToByteArray())

        val result = engine().detectConflicts(
            sources = listOf(StorageNodeRef.local(source.absolutePath)),
            destination = rootRef(REMOTE_DESTINATION)
        )

        assertTrue(result.exceptionOrNull()?.stackTraceToString(), result.isSuccess)
        val conflict = result.getOrThrow().single()
        assertEquals(source.absolutePath, conflict.sourcePath)
        assertEquals("report.txt", conflict.sourceFile.name)
        assertEquals("report.txt", conflict.existingFile.name)
        assertEquals("old", remoteClient.content("$REMOTE_DESTINATION/report.txt").decodeToString())
        assertEquals("new", source.readText())
    }

    @Test
    fun `keep both allocates a stable numbered name`() = runTest {
        val source = localFile("keep/report.txt", "incoming".encodeToByteArray())
        remoteClient.file("$REMOTE_DESTINATION/report.txt", "existing".encodeToByteArray())
        remoteClient.file("$REMOTE_DESTINATION/report (1).txt", "also existing".encodeToByteArray())

        val result = engine().copy(
            sources = listOf(StorageNodeRef.local(source.absolutePath)),
            destination = rootRef(REMOTE_DESTINATION),
            resolutions = mapOf(source.absolutePath to ConflictResolution.KEEP_BOTH)
        )

        assertTrue(result.exceptionOrNull()?.stackTraceToString(), result.isSuccess)
        assertEquals("existing", remoteClient.content("$REMOTE_DESTINATION/report.txt").decodeToString())
        assertEquals("also existing", remoteClient.content("$REMOTE_DESTINATION/report (1).txt").decodeToString())
        assertEquals("incoming", remoteClient.content("$REMOTE_DESTINATION/report (2).txt").decodeToString())
    }

    @Test
    fun `skip leaves existing destination and source untouched`() = runTest {
        val source = localFile("skip/photo.jpg", "incoming".encodeToByteArray())
        remoteClient.file("$REMOTE_DESTINATION/photo.jpg", "existing".encodeToByteArray())
        val progress = mutableListOf<BulkFileOperationProgress>()

        val result = engine().move(
            sources = listOf(StorageNodeRef.local(source.absolutePath)),
            destination = rootRef(REMOTE_DESTINATION),
            resolutions = mapOf(source.absolutePath to ConflictResolution.SKIP),
            onProgress = progress::add
        )

        assertTrue(result.exceptionOrNull()?.stackTraceToString(), result.isSuccess)
        assertTrue(source.exists())
        assertEquals("existing", remoteClient.content("$REMOTE_DESTINATION/photo.jpg").decodeToString())
        assertEquals(1, progress.single().completedItems)
    }

    @Test
    fun `replace publishes staged content under the original name`() = runTest {
        val payload = patternedBytes(200_000)
        val source = localFile("replace/archive.bin", payload)
        remoteClient.file("$REMOTE_DESTINATION/archive.bin", "old".encodeToByteArray())

        val result = engine().copy(
            sources = listOf(StorageNodeRef.local(source.absolutePath)),
            destination = rootRef(REMOTE_DESTINATION),
            resolutions = mapOf(source.absolutePath to ConflictResolution.REPLACE)
        )

        assertTrue(result.exceptionOrNull()?.stackTraceToString(), result.isSuccess)
        assertArrayEquals(payload, remoteClient.content("$REMOTE_DESTINATION/archive.bin"))
        assertTrue(remoteClient.paths.none { it.endsWith(".partial") })
    }

    @Test
    fun `failed replacement publish restores original destination`() = runTest {
        val source = localFile("replace-failure/archive.bin", patternedBytes(200_000))
        remoteClient.file("$REMOTE_DESTINATION/archive.bin", "original destination".encodeToByteArray())
        remoteClient.failRenameDestinationOnce = "$REMOTE_DESTINATION/archive.bin"

        val result = engine().copy(
            sources = listOf(StorageNodeRef.local(source.absolutePath)),
            destination = rootRef(REMOTE_DESTINATION),
            resolutions = mapOf(source.absolutePath to ConflictResolution.REPLACE)
        )

        assertTrue(result.isFailure)
        assertEquals(
            "original destination",
            remoteClient.content("$REMOTE_DESTINATION/archive.bin").decodeToString()
        )
        assertTrue(source.exists())
        assertTrue(remoteClient.paths.none { it.endsWith(".partial") || it.endsWith(".backup") })
    }

    @Test
    fun `stream failure removes recognizable partial output`() = runTest {
        val source = localFile("failure/large.bin", patternedBytes(400_000))
        remoteClient.failWritesAfterBytes = 16_384

        val result = engine().copy(
            sources = listOf(StorageNodeRef.local(source.absolutePath)),
            destination = rootRef(REMOTE_DESTINATION),
            resolutions = emptyMap()
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
        assertTrue(source.exists())
        assertFalse(remoteClient.exists("$REMOTE_DESTINATION/large.bin"))
        assertTrue(remoteClient.paths.none { it.contains("arcile-cross") || it.endsWith(".partial") })
    }

    @Test
    fun `move cleanup failure retains source and reports published destination`() = runTest {
        val payload = "retain me".repeat(2_000).encodeToByteArray()
        val remotePath = "$REMOTE_SOURCE/retained.txt"
        remoteClient.file(remotePath, payload)
        remoteClient.failDeletes += remotePath
        val destination = localDirectory("cleanup-failure")

        val result = engine().move(
            sources = listOf(rootRef(remotePath)),
            destination = StorageNodeRef.local(destination.absolutePath),
            resolutions = emptyMap()
        )

        assertTrue(result.isFailure)
        val failure = result.exceptionOrNull() as CrossBackendMoveCleanupFailure
        assertEquals(remotePath, failure.source.displayPath.absolutePath)
        assertEquals(File(destination, "retained.txt").absolutePath, failure.publishedDestination.displayPath.absolutePath)
        assertArrayEquals(payload, File(destination, "retained.txt").readBytes())
        assertArrayEquals(payload, remoteClient.content(remotePath))
    }

    @Test
    fun `successful move removes source only after destination is published`() = runTest {
        val source = localFile("move/movie.dat", patternedBytes(50_000))

        val result = engine().move(
            sources = listOf(StorageNodeRef.local(source.absolutePath)),
            destination = rootRef(REMOTE_DESTINATION),
            resolutions = emptyMap()
        )

        assertTrue(result.exceptionOrNull()?.stackTraceToString(), result.isSuccess)
        assertFalse(source.exists())
        assertEquals(50_000, remoteClient.content("$REMOTE_DESTINATION/movie.dat").size)
        assertTrue(remoteClient.events.contains("rename:$REMOTE_DESTINATION/movie.dat"))
    }

    @Test
    fun `source without copy capability is rejected before output creation`() = runTest {
        val source = localFile("blocked/device", byteArrayOf(1, 2, 3))
        val blocked = StorageNodeRef.local(
            path = source.absolutePath,
            capabilities = StorageNodeCapabilities(canRead = false, canCopy = false)
        )

        val result = engine().copy(
            sources = listOf(blocked),
            destination = rootRef(REMOTE_DESTINATION),
            resolutions = emptyMap()
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("cannot be streamed"))
        assertEquals(setOf(REMOTE_ROOT, REMOTE_SOURCE, REMOTE_DESTINATION), remoteClient.paths)
    }

    @Test
    fun `unsupported backend is rejected without touching destination`() = runTest {
        val unsupported = StorageNodeRef.mediaStore(
            id = 42,
            volumeName = "external",
            contentUri = "content://media/42",
            displayPath = "/Pictures/image.jpg"
        )

        val result = engine().copy(
            sources = listOf(unsupported),
            destination = rootRef(REMOTE_DESTINATION),
            resolutions = emptyMap()
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is UnsupportedCrossBackendNode)
        assertEquals(setOf(REMOTE_ROOT, REMOTE_SOURCE, REMOTE_DESTINATION), remoteClient.paths)
    }

    private fun engine(): CrossBackendTransferEngine {
        val dispatcher = Dispatchers.Unconfined
        remote = PrivilegedFileSystemDataSource(
            clientProvider = object : PrivilegedFileClientProvider {
                override fun activeClient(): Result<PrivilegedFileClient> = Result.success(remoteClient)
                override fun clientFor(session: PrivilegeSession): Result<PrivilegedFileClient> =
                    if (session == ROOT_SESSION) Result.success(remoteClient)
                    else Result.failure(IllegalStateException("Stale privileged session"))
            },
            pathPolicy = PrivilegedPathPolicy(
                packageName = "dev.qtremors.arcile",
                protectedWritesEnabled = { false },
                storageRoots = { listOf(REMOTE_ROOT) }
            ),
            dispatchers = ArcileDispatchers(dispatcher, dispatcher, dispatcher, dispatcher)
        )
        return CrossBackendTransferEngine(
            local = local,
            privileged = remote,
            dispatchers = ArcileDispatchers(dispatcher, dispatcher, dispatcher, dispatcher)
        )
    }

    private fun rootRef(path: String) = StorageNodeRef.root(path, path)

    private fun localFile(relativePath: String, content: ByteArray): File =
        File(localRoot, relativePath).apply {
            parentFile?.mkdirs()
            writeBytes(content)
        }

    private fun localDirectory(relativePath: String): File = File(localRoot, relativePath).apply { mkdirs() }

    private fun patternedBytes(size: Int) = ByteArray(size) { index -> (index % 251).toByte() }

    companion object {
        private const val REMOTE_ROOT = "/storage/emulated/0"
        private const val REMOTE_SOURCE = "$REMOTE_ROOT/source"
        private const val REMOTE_DESTINATION = "$REMOTE_ROOT/destination"
        private val ROOT_SESSION = PrivilegeSession(
            backendId = PrivilegeBackendId.ROOT,
            generation = 19,
            identity = PrivilegeServiceIdentity(0, 300, PrivilegeTransport.ROOT_SERVICE),
            capabilities = PrivilegeCapability.entries.toSet()
        )
    }
}

private class TestLocalFileSystemDataSource(
    private val root: File
) : FileSystemDataSource {
    private val mapper = LocalFileModelMapper()

    override fun getStandardFolders(): Map<String, String?> = emptyMap()

    override fun list(path: StorageNodePath, pageSize: Int): Flow<ListingPage> = flowOf(
        ListingPage(
            path = path,
            files = File(path.absolutePath).listFiles().orEmpty().map(mapper::toFileModel),
            pageIndex = 0,
            isComplete = true
        )
    )

    override suspend fun listFiles(path: String): Result<List<FileModel>> = runCatching {
        checked(path).listFiles().orEmpty().map(mapper::toFileModel)
    }

    override suspend fun createDirectory(parentPath: String, name: String): Result<FileModel> = runCatching {
        File(checked(parentPath), name).also { require(it.mkdir()) }.let(mapper::toFileModel)
    }

    override suspend fun createFile(parentPath: String, name: String): Result<FileModel> = runCatching {
        File(checked(parentPath), name).also { require(it.createNewFile()) }.let(mapper::toFileModel)
    }

    override suspend fun deletePermanently(paths: List<String>): Result<Unit> = runCatching {
        paths.forEach { path -> require(checked(path).deleteRecursively()) }
    }

    override suspend fun deletePermanentlyDetailed(paths: List<String>): Result<BatchMutationResult> =
        deletePermanently(paths).map {
            BatchMutationResult(succeededPaths = paths)
        }

    override suspend fun shred(paths: List<String>): Result<Unit> = deletePermanently(paths)

    override suspend fun shredDetailed(paths: List<String>): Result<BatchMutationResult> =
        deletePermanentlyDetailed(paths)

    override suspend fun renameFile(path: String, newName: String): Result<FileModel> = runCatching {
        val source = checked(path)
        val destination = File(source.parentFile, newName)
        require(source.renameTo(destination))
        mapper.toFileModel(destination)
    }

    override suspend fun detectCopyConflicts(
        sourcePaths: List<String>,
        destinationPath: String
    ): Result<List<FileConflict>> = Result.failure(UnsupportedOperationException())

    override suspend fun copyFiles(
        sourcePaths: List<String>,
        destinationPath: String,
        resolutions: Map<String, ConflictResolution>,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ): Result<Unit> = Result.failure(UnsupportedOperationException())

    override suspend fun moveFiles(
        sourcePaths: List<String>,
        destinationPath: String,
        resolutions: Map<String, ConflictResolution>,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ): Result<Unit> = Result.failure(UnsupportedOperationException())

    override suspend fun createFakeFile(
        parentPath: String,
        name: String,
        size: Long,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ): Result<FileModel> = Result.failure(UnsupportedOperationException())

    private fun checked(path: String): File {
        val file = File(path).canonicalFile
        require(file.path == root.canonicalPath || file.path.startsWith(root.canonicalPath + File.separator))
        return file
    }
}

private class MemoryPrivilegedFileClient(
    override val session: PrivilegeSession
) : PrivilegedFileClient {
    private data class Node(
        val type: PrivilegedFileType,
        var content: ByteArray = byteArrayOf(),
        val writable: Boolean = true
    )

    private val nodes = linkedMapOf<String, Node>()
    val events = mutableListOf<String>()
    val failDeletes = mutableSetOf<String>()
    var failWritesAfterBytes: Int? = null
    var failRenameDestinationOnce: String? = null

    val paths: Set<String> get() = nodes.keys.toSet()

    fun exists(path: String): Boolean = path in nodes

    fun directory(path: String) {
        nodes[path] = Node(PrivilegedFileType.DIRECTORY)
    }

    fun file(path: String, content: ByteArray, writable: Boolean = true) {
        nodes[path] = Node(PrivilegedFileType.REGULAR_FILE, content.copyOf(), writable)
    }

    fun content(path: String): ByteArray = requireNotNull(nodes[path]).content.copyOf()

    override suspend fun handshake() = PrivilegedHandshake(
        protocolVersion = 1,
        identity = session.identity,
        capabilities = session.capabilities,
        maximumDirectoryPageSize = 250
    )

    override suspend fun canonicalizeAndLstat(path: String): Result<PrivilegedFileEntry> =
        nodes[path]?.let { Result.success(entry(path, it)) }
            ?: Result.failure(PrivilegedFileFailure.PathMissing(path))

    override suspend fun listDirectory(
        path: String,
        pageToken: String?,
        pageSize: Int
    ): Result<PrivilegedDirectoryPage> {
        val parent = nodes[path] ?: return Result.failure(PrivilegedFileFailure.PathMissing(path))
        if (parent.type != PrivilegedFileType.DIRECTORY) {
            return Result.failure(IllegalArgumentException("Not a directory: $path"))
        }
        val prefix = path.trimEnd('/') + "/"
        val children = nodes.entries
            .filter { (candidate, _) ->
                candidate.startsWith(prefix) && !candidate.removePrefix(prefix).contains('/')
            }
            .map { (candidate, node) -> entry(candidate, node) }
            .sortedBy(PrivilegedFileEntry::displayName)
        return Result.success(PrivilegedDirectoryPage(children.take(pageSize), nextPageToken = null))
    }

    override suspend fun filesystemStats(path: String) = Result.success(
        PrivilegedFilesystemStats(totalBytes = 1_000_000, availableBytes = 800_000, freeBytes = 750_000)
    )

    override suspend fun createFile(path: String): Result<PrivilegedFileEntry> {
        if (path in nodes) return Result.failure(IllegalStateException("Path already exists: $path"))
        val parentFailure = validateParent(path)
        if (parentFailure != null) return Result.failure(parentFailure)
        val node = Node(PrivilegedFileType.REGULAR_FILE)
        nodes[path] = node
        events += "create:$path"
        return Result.success(entry(path, node))
    }

    override suspend fun createDirectory(path: String): Result<PrivilegedFileEntry> {
        if (path in nodes) return Result.failure(IllegalStateException("Path already exists: $path"))
        val parentFailure = validateParent(path)
        if (parentFailure != null) return Result.failure(parentFailure)
        val node = Node(PrivilegedFileType.DIRECTORY)
        nodes[path] = node
        events += "mkdir:$path"
        return Result.success(entry(path, node))
    }

    override suspend fun open(path: String, mode: PrivilegedOpenMode): Result<PrivilegedFileHandle> {
        val node = nodes[path] ?: return Result.failure(PrivilegedFileFailure.PathMissing(path))
        if (node.type != PrivilegedFileType.REGULAR_FILE) {
            return Result.failure(IllegalArgumentException("Not a regular file: $path"))
        }
        events += "open:${path.substringBeforeLast('/')}"
        return when (mode) {
            PrivilegedOpenMode.READ -> Result.success(object : PrivilegedFileHandle {
                override val mode = PrivilegedOpenMode.READ
                override val input = ByteArrayInputStream(node.content)
                override val output: OutputStream? = null
                override fun close() = input.close()
            })
            else -> {
                val sink = object : ByteArrayOutputStream() {
                    override fun write(buffer: ByteArray, offset: Int, length: Int) {
                        val limit = failWritesAfterBytes
                        if (limit != null && size() + length > limit) throw IOException("Injected remote write failure")
                        super.write(buffer, offset, length)
                    }
                }
                Result.success(object : PrivilegedFileHandle {
                    override val mode = mode
                    override val input = null
                    override val output: OutputStream = sink
                    override fun close() {
                        node.content = sink.toByteArray()
                        sink.close()
                    }
                })
            }
        }
    }

    override suspend fun rename(sourcePath: String, destinationPath: String): Result<Unit> {
        if (failRenameDestinationOnce == destinationPath) {
            failRenameDestinationOnce = null
            return Result.failure(IOException("Injected publish failure"))
        }
        val source = nodes[sourcePath]
            ?: return Result.failure(PrivilegedFileFailure.PathMissing(sourcePath))
        if (destinationPath in nodes) return Result.failure(IllegalStateException("Destination exists"))
        val descendants = nodes.entries
            .filter { (path, _) -> path.startsWith("$sourcePath/") }
            .map { it.key to it.value }
        nodes.remove(sourcePath)
        descendants.forEach { (path, _) -> nodes.remove(path) }
        nodes[destinationPath] = source
        descendants.forEach { (path, node) ->
            nodes[destinationPath + path.removePrefix(sourcePath)] = node
        }
        events += "rename:$destinationPath"
        return Result.success(Unit)
    }

    override suspend fun copy(
        sourcePath: String,
        destinationPath: String,
        operationId: PrivilegedOperationId,
        onProgress: ((PrivilegedOperationProgress) -> Unit)?
    ): Result<Unit> = Result.failure(UnsupportedOperationException("Cross-backend test uses descriptor streams"))

    override suspend fun move(
        sourcePath: String,
        destinationPath: String,
        operationId: PrivilegedOperationId,
        onProgress: ((PrivilegedOperationProgress) -> Unit)?
    ): Result<Unit> = rename(sourcePath, destinationPath)

    override suspend fun delete(path: String): Result<Unit> = deleteRecursively(
        path,
        PrivilegedOperationId.of("delete"),
        null
    )

    override suspend fun deleteRecursively(
        path: String,
        operationId: PrivilegedOperationId,
        onProgress: ((PrivilegedOperationProgress) -> Unit)?
    ): Result<Unit> {
        if (path in failDeletes) return Result.failure(IOException("Injected cleanup failure"))
        if (path !in nodes) return Result.failure(PrivilegedFileFailure.PathMissing(path))
        nodes.keys.filter { it == path || it.startsWith("$path/") }.toList().forEach(nodes::remove)
        events += "delete:$path"
        return Result.success(Unit)
    }

    override suspend fun secureOverwrite(
        path: String,
        operationId: PrivilegedOperationId,
        onProgress: ((PrivilegedOperationProgress) -> Unit)?
    ): Result<Unit> = if (path in nodes) Result.success(Unit)
    else Result.failure(PrivilegedFileFailure.PathMissing(path))

    override suspend fun updateTimestamps(
        path: String,
        accessedAtMillis: Long,
        modifiedAtMillis: Long
    ): Result<Unit> = if (path in nodes) Result.success(Unit)
    else Result.failure(PrivilegedFileFailure.PathMissing(path))

    override suspend fun cancel(operationId: PrivilegedOperationId): Result<Unit> = Result.success(Unit)

    override fun close() {
        nodes.clear()
    }

    private fun validateParent(path: String): Throwable? {
        val parent = nodes[path.substringBeforeLast('/')]
            ?: return PrivilegedFileFailure.PathMissing(path.substringBeforeLast('/'))
        return if (parent.type == PrivilegedFileType.DIRECTORY) null
        else IllegalArgumentException("Parent is not a directory")
    }

    private fun entry(path: String, node: Node) = PrivilegedFileEntry(
        path = path,
        canonicalIdentity = path,
        displayName = path.substringAfterLast('/').ifEmpty { "/" },
        type = node.type,
        size = node.content.size.toLong(),
        modifiedAtMillis = 123,
        mode = 0,
        readable = true,
        writable = node.writable
    )
}
