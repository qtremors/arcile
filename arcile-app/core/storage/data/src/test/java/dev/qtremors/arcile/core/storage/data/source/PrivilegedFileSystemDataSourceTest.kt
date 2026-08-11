package dev.qtremors.arcile.core.storage.data.source

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
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PrivilegedFileSystemDataSourceTest {
    @Test
    fun `listing bounds remote pages and retains remote storage identity`() = runTest {
        val client = FakeClient(rootSession).apply {
            add(directory("/storage/emulated/0"))
            pages += PrivilegedDirectoryPage(
                entries = listOf(file("/storage/emulated/0/first.txt")),
                nextPageToken = "next"
            )
            pages += PrivilegedDirectoryPage(
                entries = listOf(directory("/storage/emulated/0/Folder")),
                nextPageToken = null
            )
        }
        val source = source(client)
        val root = rootRef("/storage/emulated/0")

        val pages = source.list(root, pageSize = 2_000).toList()

        assertEquals(listOf(250, 250), client.requestedPageSizes)
        assertEquals(listOf(null, "next"), client.requestedTokens)
        assertEquals(2, pages.size)
        assertFalse(pages.first().isComplete)
        assertTrue(pages.last().isComplete)
        val first = pages.first().files.single()
        assertEquals(StorageNodeRef.ROOT_BACKEND_ID, first.nodeRef.backendId)
        assertEquals("root:/storage/emulated/0/first.txt", first.nodeRef.canonicalIdentity.value)
        assertEquals("/storage/emulated/0/first.txt", first.nodeRef.backendIdentity)
    }

    @Test
    fun `system mutation is rejected before create reaches remote client`() = runTest {
        val client = FakeClient(rootSession).apply {
            add(directory("/system", writable = true))
        }
        val source = source(client)

        val result = source.createNodeFile(rootRef("/system"), "unsafe.txt")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("read-only"))
        assertTrue(client.createdPaths.isEmpty())
    }

    @Test
    fun `same backend copy publishes a partial output with rename`() = runTest {
        val client = FakeClient(rootSession).apply {
            add(directory("/storage/emulated/0/Source"))
            add(directory("/storage/emulated/0/Destination"))
            add(file("/storage/emulated/0/Source/report.txt"))
        }
        val source = source(client)
        val sourceRef = rootRef("/storage/emulated/0/Source/report.txt")
        val destinationRef = rootRef("/storage/emulated/0/Destination")

        val result = source.copyNodes(
            sources = listOf(sourceRef),
            destination = destinationRef,
            resolutions = emptyMap()
        )

        assertTrue(result.isSuccess)
        assertEquals(1, client.copyCalls.size)
        val staging = client.copyCalls.single().second
        assertTrue(staging.contains(".arcile-transfer-"))
        assertTrue(staging.endsWith(".partial"))
        assertEquals(
            staging to "/storage/emulated/0/Destination/report.txt",
            client.renameCalls.single()
        )
    }

    @Test
    fun `retained root ref does not redirect into active Shizuku session`() = runTest {
        val client = FakeClient(shellSession).apply {
            add(directory("/storage/emulated/0"))
        }
        val source = source(client)

        val result = source.listNodeFiles(rootRef("/storage/emulated/0"))

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("shizuku is active"))
        assertTrue(client.requestedPageSizes.isEmpty())
    }

    @Test
    fun `special files expose no content or mutation actions`() {
        val client = FakeClient(rootSession)
        val mapper = PrivilegedFileModelMapper(policy())
        val device = file("/dev/block/dm-0", type = PrivilegedFileType.BLOCK_DEVICE)

        val model = mapper.toFileModel(device, rootSession)

        assertFalse(model.nodeRef.capabilities.canRead)
        assertFalse(model.nodeRef.capabilities.canWrite)
        assertFalse(model.nodeRef.capabilities.canDelete)
        assertFalse(model.nodeRef.capabilities.canShare)
        assertFalse(model.nodeRef.capabilities.canOpenWith)
        client.close()
    }

    @Test
    fun `ordinary user storage files expose privileged trash capability`() {
        val mapper = PrivilegedFileModelMapper(policy())
        val document = file("/storage/emulated/0/Documents/report.txt")
        val directory = directory("/storage/emulated/0/Documents/Project")

        val documentCapabilities = mapper.toFileModel(document, rootSession).nodeRef.capabilities
        val directoryCapabilities = mapper.toFileModel(directory, rootSession).nodeRef.capabilities

        assertTrue(documentCapabilities.canMove)
        assertTrue(documentCapabilities.canDelete)
        assertTrue(documentCapabilities.canTrash)
        assertTrue(documentCapabilities.canArchive)
        assertTrue(directoryCapabilities.canMove)
        assertTrue(directoryCapabilities.canDelete)
        assertTrue(directoryCapabilities.canTrash)
        assertTrue(directoryCapabilities.canArchive)
    }

    @Test
    fun `Android application storage can move but never advertises trash`() {
        val mapper = PrivilegedFileModelMapper(policy())
        val otherAppFile = file(
            "/storage/emulated/0/Android/data/com.example/cache/private.bin"
        )

        val rootCapabilities = mapper.toFileModel(otherAppFile, rootSession).nodeRef.capabilities
        val shellCapabilities = mapper.toFileModel(otherAppFile, shellSession).nodeRef.capabilities

        assertTrue(rootCapabilities.canMove)
        assertTrue(rootCapabilities.canDelete)
        assertFalse(rootCapabilities.canTrash)
        assertTrue(shellCapabilities.canMove)
        assertTrue(shellCapabilities.canDelete)
        assertFalse(shellCapabilities.canTrash)
    }

    @Test
    fun `enabled protected writes do not turn private app data into trashable storage`() {
        val writablePrivatePolicy = PrivilegedPathPolicy(
            packageName = "dev.qtremors.arcile",
            protectedWritesEnabled = { true },
            storageRoots = { listOf("/storage/emulated/0") }
        )
        val mapper = PrivilegedFileModelMapper(writablePrivatePolicy)
        val anotherApp = file("/data/user/0/com.example/files/database.db")

        val capabilities = mapper.toFileModel(anotherApp, rootSession).nodeRef.capabilities

        assertTrue(capabilities.canMove)
        assertTrue(capabilities.canDelete)
        assertFalse(capabilities.canTrash)
    }

    @Test
    fun `Arcile private storage remains non-trashable even for Root`() {
        val mapper = PrivilegedFileModelMapper(policy())
        val ownMetadata = file(
            "/data/user/0/dev.qtremors.arcile/files/privileged-trash/item.json"
        )

        val capabilities = mapper.toFileModel(ownMetadata, rootSession).nodeRef.capabilities

        assertFalse(capabilities.canMove)
        assertFalse(capabilities.canDelete)
        assertFalse(capabilities.canTrash)
    }

    @Test
    fun `atomic replacement reads source writes synced sibling and renames only after close`() = runTest {
        val path = "/storage/emulated/0/Documents/note.txt"
        val client = FakeClient(rootSession).apply {
            add(file(path), "old text".toByteArray())
        }
        val source = source(client)
        val replacement = "complete replacement".toByteArray()

        val result = source.replaceNodeContentAtomically(rootRef(path), replacement)

        assertTrue(result.isSuccess)
        assertEquals(PrivilegedOpenMode.READ, client.openCalls.first().second)
        assertEquals(PrivilegedOpenMode.WRITE_TRUNCATE, client.openCalls[1].second)
        val rename = client.renameCalls.single()
        assertTrue(rename.first.startsWith("/storage/emulated/0/Documents/.note.txt.arcile-"))
        assertTrue(rename.first.endsWith(".tmp"))
        assertEquals(path, rename.second)
        assertArrayEquals(replacement, client.contents[path])
        assertFalse(client.entries.keys.any { it.endsWith(".tmp") })
        assertTrue(client.closedWritePaths.contains(rename.first))
    }

    @Test
    fun `atomic replacement preserves original when sibling write fails`() = runTest {
        val path = "/storage/emulated/0/Documents/note.txt"
        val original = "original remains".toByteArray()
        val client = FakeClient(rootSession).apply {
            add(file(path), original)
            failWrites = true
        }

        val result = source(client).replaceNodeContentAtomically(
            rootRef(path),
            "new content".toByteArray()
        )

        assertTrue(result.isFailure)
        assertArrayEquals(original, client.contents[path])
        assertTrue(client.renameCalls.isEmpty())
        assertFalse(client.entries.keys.any { it.endsWith(".tmp") })
        assertEquals(1, client.deletedPaths.size)
    }

    @Test
    fun `atomic replacement preserves original and removes sibling when rename fails`() = runTest {
        val path = "/storage/emulated/0/Documents/note.txt"
        val original = "before rename".toByteArray()
        val client = FakeClient(rootSession).apply {
            add(file(path), original)
            failRenames = true
        }

        val result = source(client).replaceNodeContentAtomically(
            rootRef(path),
            "after rename".toByteArray()
        )

        assertTrue(result.isFailure)
        assertArrayEquals(original, client.contents[path])
        assertEquals(1, client.renameCalls.size)
        assertEquals(1, client.deletedPaths.size)
        assertFalse(client.entries.keys.any { it.endsWith(".tmp") })
    }

    @Test
    fun `atomic replacement rejects changed canonical identity before creating sibling`() = runTest {
        val path = "/storage/emulated/0/Documents/note.txt"
        val client = FakeClient(rootSession).apply {
            add(file(path).copy(canonicalIdentity = "/different/inode"))
        }

        val result = source(client).replaceNodeContentAtomically(
            rootRef(path),
            "not written".toByteArray()
        )

        assertTrue(result.isFailure)
        assertTrue(client.createdPaths.isEmpty())
        assertTrue(client.openCalls.isEmpty())
        assertTrue(client.renameCalls.isEmpty())
    }

    @Test
    fun `atomic replacement preserves source when read descriptor cannot open`() = runTest {
        val path = "/storage/emulated/0/Documents/note.txt"
        val original = "readable before disconnect".toByteArray()
        val client = FakeClient(rootSession).apply {
            add(file(path), original)
            failReadOpen = true
        }

        val result = source(client).replaceNodeContentAtomically(
            rootRef(path),
            "never written".toByteArray()
        )

        assertTrue(result.isFailure)
        assertArrayEquals(original, client.contents[path])
        assertTrue(client.createdPaths.isEmpty())
        assertTrue(client.renameCalls.isEmpty())
    }

    private fun source(client: FakeClient): PrivilegedFileSystemDataSource {
        val dispatcher = UnconfinedTestDispatcher()
        return PrivilegedFileSystemDataSource(
            clientProvider = object : PrivilegedFileClientProvider {
                override fun activeClient(): Result<PrivilegedFileClient> = Result.success(client)
                override fun clientFor(session: PrivilegeSession): Result<PrivilegedFileClient> =
                    if (session == client.session) Result.success(client)
                    else Result.failure(IllegalStateException("Stale session"))
            },
            pathPolicy = policy(),
            dispatchers = ArcileDispatchers(
                io = dispatcher,
                default = dispatcher,
                main = dispatcher,
                storage = dispatcher
            )
        )
    }

    private fun policy() = PrivilegedPathPolicy(
        packageName = "dev.qtremors.arcile",
        protectedWritesEnabled = { false },
        storageRoots = { listOf("/storage/emulated/0") }
    )

    private fun rootRef(path: String) = StorageNodeRef.root(
        displayPath = path,
        remoteCanonicalIdentity = path
    )

    companion object {
        val rootSession = session(PrivilegeBackendId.ROOT, 0, PrivilegeTransport.ROOT_SERVICE)
        val shellSession = session(
            PrivilegeBackendId.SHIZUKU,
            2000,
            PrivilegeTransport.SHIZUKU_USER_SERVICE
        )

        fun session(
            backendId: PrivilegeBackendId,
            uid: Int,
            transport: PrivilegeTransport
        ) = PrivilegeSession(
            backendId = backendId,
            generation = 8,
            identity = PrivilegeServiceIdentity(uid, 42, transport),
            capabilities = PrivilegeCapability.entries.toSet()
        )

        fun directory(path: String, writable: Boolean = true) = file(
            path,
            type = PrivilegedFileType.DIRECTORY,
            writable = writable
        )

        fun file(
            path: String,
            type: PrivilegedFileType = PrivilegedFileType.REGULAR_FILE,
            writable: Boolean = true
        ) = PrivilegedFileEntry(
            path = path,
            canonicalIdentity = path,
            displayName = path.substringAfterLast('/').ifEmpty { "/" },
            type = type,
            size = if (type == PrivilegedFileType.REGULAR_FILE) 12 else 0,
            modifiedAtMillis = 123,
            mode = 0,
            readable = true,
            writable = writable
        )
    }
}

private class FakeClient(
    override val session: PrivilegeSession
) : PrivilegedFileClient {
    val entries = linkedMapOf<String, PrivilegedFileEntry>()
    val pages = ArrayDeque<PrivilegedDirectoryPage>()
    val requestedPageSizes = mutableListOf<Int>()
    val requestedTokens = mutableListOf<String?>()
    val createdPaths = mutableListOf<String>()
    val copyCalls = mutableListOf<Pair<String, String>>()
    val renameCalls = mutableListOf<Pair<String, String>>()
    val openCalls = mutableListOf<Pair<String, PrivilegedOpenMode>>()
    val closedWritePaths = mutableListOf<String>()
    val deletedPaths = mutableListOf<String>()
    val contents = linkedMapOf<String, ByteArray>()
    var failWrites = false
    var failRenames = false
    var failReadOpen = false

    fun add(entry: PrivilegedFileEntry, content: ByteArray? = null) {
        entries[entry.path] = entry
        content?.let { contents[entry.path] = it.copyOf() }
    }

    override suspend fun handshake() = PrivilegedHandshake(
        protocolVersion = 1,
        identity = session.identity,
        capabilities = session.capabilities,
        maximumDirectoryPageSize = 250
    )

    override suspend fun canonicalizeAndLstat(path: String): Result<PrivilegedFileEntry> =
        entries[path]?.let(Result.Companion::success)
            ?: Result.failure(PrivilegedFileFailure.PathMissing(path))

    override suspend fun listDirectory(
        path: String,
        pageToken: String?,
        pageSize: Int
    ): Result<PrivilegedDirectoryPage> {
        requestedPageSizes += pageSize
        requestedTokens += pageToken
        return pages.removeFirstOrNull()?.let(Result.Companion::success)
            ?: Result.success(PrivilegedDirectoryPage(emptyList()))
    }

    override suspend fun filesystemStats(path: String) = Result.success(
        PrivilegedFilesystemStats(100, 80, 70)
    )

    override suspend fun createFile(path: String): Result<PrivilegedFileEntry> {
        createdPaths += path
        return Result.success(PrivilegedFileSystemDataSourceTest.file(path).also(::add))
    }

    override suspend fun createDirectory(path: String): Result<PrivilegedFileEntry> {
        createdPaths += path
        return Result.success(PrivilegedFileSystemDataSourceTest.directory(path).also(::add))
    }

    override suspend fun open(path: String, mode: PrivilegedOpenMode): Result<PrivilegedFileHandle> {
        openCalls += path to mode
        if (mode == PrivilegedOpenMode.READ && failReadOpen) {
            return Result.failure(PrivilegedFileFailure.BackendDisconnected())
        }
        val writeBuffer = ByteArrayOutputStream()
        val writeOutput = object : OutputStream() {
            private var closed = false
            override fun write(value: Int) {
                if (failWrites) throw java.io.IOException("simulated write failure")
                writeBuffer.write(value)
            }

            override fun write(bytes: ByteArray, offset: Int, length: Int) {
                if (failWrites) throw java.io.IOException("simulated write failure")
                writeBuffer.write(bytes, offset, length)
            }

            override fun close() {
                if (closed) return
                closed = true
                if (!failWrites) {
                    contents[path] = writeBuffer.toByteArray()
                    entries[path]?.let { entries[path] = it.copy(size = writeBuffer.size().toLong()) }
                    closedWritePaths += path
                }
            }
        }
        val input = if (mode == PrivilegedOpenMode.READ) {
            ByteArrayInputStream(contents[path] ?: byteArrayOf())
        } else {
            null
        }
        return Result.success(object : PrivilegedFileHandle {
            override val mode = mode
            override val input = input
            override val output = if (mode != PrivilegedOpenMode.READ) writeOutput else null
            override fun close() {
                input?.close()
                output?.close()
            }
        })
    }

    override suspend fun rename(sourcePath: String, destinationPath: String): Result<Unit> {
        renameCalls += sourcePath to destinationPath
        if (failRenames) return Result.failure(PrivilegedFileFailure.IoFailure("simulated rename failure"))
        val source = entries.remove(sourcePath)
            ?: return Result.failure(PrivilegedFileFailure.PathMissing(sourcePath))
        entries[destinationPath] = source.copy(
            path = destinationPath,
            canonicalIdentity = destinationPath,
            displayName = destinationPath.substringAfterLast('/')
        )
        contents.remove(sourcePath)?.let { contents[destinationPath] = it }
        return Result.success(Unit)
    }

    override suspend fun copy(
        sourcePath: String,
        destinationPath: String,
        operationId: PrivilegedOperationId,
        onProgress: ((PrivilegedOperationProgress) -> Unit)?
    ): Result<Unit> {
        copyCalls += sourcePath to destinationPath
        val source = entries[sourcePath]
            ?: return Result.failure(PrivilegedFileFailure.PathMissing(sourcePath))
        entries[destinationPath] = source.copy(
            path = destinationPath,
            canonicalIdentity = destinationPath,
            displayName = destinationPath.substringAfterLast('/')
        )
        return Result.success(Unit)
    }

    override suspend fun move(
        sourcePath: String,
        destinationPath: String,
        operationId: PrivilegedOperationId,
        onProgress: ((PrivilegedOperationProgress) -> Unit)?
    ): Result<Unit> = rename(sourcePath, destinationPath)

    override suspend fun delete(path: String): Result<Unit> {
        deletedPaths += path
        contents.remove(path)
        return if (entries.remove(path) != null) Result.success(Unit)
        else Result.failure(PrivilegedFileFailure.PathMissing(path))
    }

    override suspend fun deleteRecursively(
        path: String,
        operationId: PrivilegedOperationId,
        onProgress: ((PrivilegedOperationProgress) -> Unit)?
    ): Result<Unit> {
        entries.keys.filter { it == path || it.startsWith("$path/") }.forEach(entries::remove)
        return Result.success(Unit)
    }

    override suspend fun secureOverwrite(
        path: String,
        operationId: PrivilegedOperationId,
        onProgress: ((PrivilegedOperationProgress) -> Unit)?
    ): Result<Unit> = if (path in entries) Result.success(Unit)
    else Result.failure(PrivilegedFileFailure.PathMissing(path))

    override suspend fun updateTimestamps(
        path: String,
        accessedAtMillis: Long,
        modifiedAtMillis: Long
    ): Result<Unit> = Result.success(Unit)

    override suspend fun cancel(operationId: PrivilegedOperationId): Result<Unit> = Result.success(Unit)
    override fun close() = Unit
}
