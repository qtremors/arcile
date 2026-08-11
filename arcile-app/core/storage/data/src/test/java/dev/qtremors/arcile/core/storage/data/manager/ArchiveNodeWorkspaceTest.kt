package dev.qtremors.arcile.core.storage.data.manager

import dev.qtremors.arcile.core.privilege.PrivilegedFileFailure
import dev.qtremors.arcile.core.storage.data.MutationFinalizer
import dev.qtremors.arcile.core.storage.data.provider.VolumeProvider
import dev.qtremors.arcile.core.storage.domain.ArchiveEntryModel
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.StorageNodeCapabilities
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import dev.qtremors.arcile.core.storage.domain.StorageVolume
import io.mockk.coEvery
import io.mockk.mockk
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ArchiveNodeWorkspaceTest {
    private lateinit var testRoot: File
    private lateinit var workspaceRoot: File
    private lateinit var remoteRoot: File
    private lateinit var io: FakeArchiveNodeIo
    private lateinit var bridge: ArchiveNodeWorkspaceBridge

    @Before
    fun setUp() {
        testRoot = createTempDir(prefix = "archive-node-workspace").canonicalFile
        workspaceRoot = File(testRoot, "workspaces")
        remoteRoot = File(testRoot, "remote").apply { mkdirs() }
        io = FakeArchiveNodeIo(remoteRoot)
        bridge = ArchiveNodeWorkspaceBridge(io, workspaceRoot)
    }

    @After
    fun tearDown() {
        testRoot.deleteRecursively()
    }

    @Test
    fun `workspace is removed after successful block`() = runTest {
        val result = bridge.withWorkspace { workspace ->
            File(workspace.directory, "private").writeText("secret")
            workspace.directory.absolutePath
        }

        assertFalse(File(result).exists())
        assertTrue(workspaceRoot.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `workspace is removed after failed block`() = runTest {
        var directory: File? = null

        val failure = runCatching {
            bridge.withWorkspace { workspace ->
                directory = workspace.directory
                File(workspace.directory, "partial").writeText("secret")
                throw IOException("codec failed")
            }
        }.exceptionOrNull()

        assertEquals("codec failed", failure?.message)
        assertFalse(requireNotNull(directory).exists())
    }

    @Test
    fun `initialization removes only abandoned workspaces`() {
        val old = File(workspaceRoot, "workspace-old").apply {
            mkdirs()
            setLastModified(System.currentTimeMillis() - ArchiveNodeWorkspaceBridge.ABANDONED_WORKSPACE_AGE_MILLIS - 1)
        }
        val recent = File(workspaceRoot, "workspace-recent").apply {
            mkdirs()
            setLastModified(System.currentTimeMillis())
        }
        val unrelated = File(workspaceRoot, "thumbnail-cache").apply { mkdirs() }

        ArchiveNodeWorkspaceBridge(io, workspaceRoot)

        assertFalse(old.exists())
        assertTrue(recent.exists())
        assertTrue(unrelated.exists())
    }

    @Test
    fun `stage archive reads descriptor and retains compound extension`() = runTest {
        val archive = io.writeFile("/protected/bundle.tar.gz", "archive bytes")

        bridge.withWorkspace { workspace ->
            val staged = bridge.stageArchive(archive, workspace)

            assertTrue(staged.name.endsWith(".tar.gz"))
            assertEquals("archive bytes", staged.readText())
            assertEquals(listOf("/protected/bundle.tar.gz"), io.openedInputs)
        }
    }

    @Test
    fun `stage archive rejects directory`() = runTest {
        val directory = io.createDirectoryPath("/protected/folder")

        val result = runCatching {
            bridge.withWorkspace { bridge.stageArchive(directory, it) }
        }

        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("not available"))
        assertTrue(io.openedInputs.isEmpty())
    }

    @Test
    fun `stage archive rejects unreadable capability before opening`() = runTest {
        val archive = io.writeFile("/protected/private.zip", "bytes").copy(
            capabilities = StorageNodeCapabilities(canRead = false, canArchive = true)
        )

        val result = runCatching {
            bridge.withWorkspace { bridge.stageArchive(archive, it) }
        }

        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("not readable"))
        assertTrue(io.openedInputs.isEmpty())
    }

    @Test
    fun `stage archive detects source changing during descriptor read`() = runTest {
        val archive = io.writeFile("/protected/changing.zip", "complete")
        io.inputOverrides[archive.displayPath.absolutePath] = "short".toByteArray()

        val result = runCatching {
            bridge.withWorkspace { bridge.stageArchive(archive, it) }
        }

        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("changed"))
    }

    @Test
    fun `stage sources recursively preserves source tree`() = runTest {
        val folder = io.createDirectoryPath("/protected/project")
        io.createDirectoryPath("/protected/project/nested")
        io.writeFile("/protected/project/readme.txt", "readme")
        io.writeFile("/protected/project/nested/data.bin", "data")

        bridge.withWorkspace { workspace ->
            val staged = bridge.stageSources(listOf(folder), workspace)

            assertEquals(1, staged.size)
            assertEquals("readme", File(staged.single(), "readme.txt").readText())
            assertEquals("data", File(staged.single(), "nested/data.bin").readText())
        }
    }

    @Test
    fun `stage sources assigns stable keep-both names to duplicate roots`() = runTest {
        io.createDirectoryPath("/protected/one")
        io.createDirectoryPath("/protected/two")
        val first = io.writeFile("/protected/one/note.txt", "one")
        val second = io.writeFile("/protected/two/note.txt", "two")

        bridge.withWorkspace { workspace ->
            val staged = bridge.stageSources(listOf(first, second), workspace)

            assertEquals(listOf("note.txt", "note (1).txt"), staged.map(File::getName))
            assertEquals(listOf("one", "two"), staged.map(File::readText))
        }
    }

    @Test
    fun `stage sources reports descriptor progress`() = runTest {
        val source = io.writeFile("/protected/video.bin", ByteArray(300_000) { 7 })
        val bytes = mutableListOf<Long>()

        bridge.withWorkspace { workspace ->
            bridge.stageSources(listOf(source), workspace) { bytes += requireNotNull(it.bytesCopied) }
        }

        assertTrue(bytes.size >= 2)
        assertEquals(300_000L, bytes.last())
        assertTrue(bytes.zipWithNext().all { (left, right) -> right >= left })
    }

    @Test
    fun `stage sources applies workspace limit across every selected source`() = runTest {
        val first = io.writeFile("/protected/first.bin", byteArrayOf(1, 2, 3))
        val second = io.writeFile("/protected/second.bin", byteArrayOf(4, 5, 6))
        val limitedBridge = ArchiveNodeWorkspaceBridge(
            io = io,
            workspaceRoot = workspaceRoot,
            safetyPolicy = ArchiveSafetyPolicy(maxUncompressedBytes = 5L)
        )

        val result = runCatching {
            limitedBridge.withWorkspace { workspace ->
                limitedBridge.stageSources(listOf(first, second), workspace)
            }
        }

        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("safety limit"))
        assertTrue(workspaceRoot.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `publish archive writes partial then atomically renames`() = runTest {
        val destination = io.reference("/protected/bundle.zip")

        bridge.withWorkspace { workspace ->
            val staged = workspace.archiveOutputWithExtension(destination.displayPath.absolutePath)
            staged.writeText("zip content")
            bridge.publishArchive(staged, destination)
        }

        assertEquals("zip content", io.readFile("/protected/bundle.zip"))
        assertTrue(io.listNames("/protected").none { it.endsWith(".partial") })
        assertEquals(listOf("/protected/bundle.zip"), io.renamedDestinations)
    }

    @Test
    fun `publish archive rejects an existing final target without mutation`() = runTest {
        io.writeFile("/protected/existing.zip", "old")
        val destination = io.reference("/protected/existing.zip")

        val result = runCatching {
            bridge.withWorkspace { workspace ->
                workspace.archiveOutputWithExtension("existing.zip").writeText("new")
                bridge.publishArchive(workspace.archiveOutputWithExtension("existing.zip"), destination)
            }
        }

        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("already exists"))
        assertEquals("old", io.readFile("/protected/existing.zip"))
        assertTrue(io.listNames("/protected").none { it.endsWith(".partial") })
    }

    @Test
    fun `publish archive removes partial output after descriptor failure`() = runTest {
        val destination = io.reference("/protected/fail.zip")
        io.failOutputWhenPathContains = ".partial"

        val result = runCatching {
            bridge.withWorkspace { workspace ->
                val staged = workspace.archiveOutputWithExtension("fail.zip")
                staged.writeText("new")
                bridge.publishArchive(staged, destination)
            }
        }

        assertEquals("output failed", result.exceptionOrNull()?.message)
        assertFalse(io.exists("/protected/fail.zip"))
        assertTrue(io.listNames("/protected").none { it.endsWith(".partial") })
    }

    @Test
    fun `publish archive reports final monotonic progress`() = runTest {
        val destination = io.reference("/protected/progress.zip")
        val progress = mutableListOf<Long>()

        bridge.withWorkspace { workspace ->
            val staged = workspace.archiveOutputWithExtension("progress.zip")
            staged.writeBytes(ByteArray(400_000))
            bridge.publishArchive(staged, destination) { progress += requireNotNull(it.bytesCopied) }
        }

        assertEquals(400_000L, progress.last())
        assertTrue(progress.zipWithNext().all { (left, right) -> right >= left })
    }

    @Test
    fun `conflict detection finds nested protected target`() = runTest {
        io.createDirectoryPath("/protected/out")
        io.createDirectoryPath("/protected/out/folder")
        io.writeFile("/protected/out/folder/note.txt", "existing")
        val entries = listOf(archiveEntry("folder/note.txt", size = 3))

        val conflicts = bridge.detectConflicts(entries, io.reference("/protected/out"), null)

        assertEquals(1, conflicts.size)
        assertEquals("folder/note.txt", conflicts.single().sourcePath)
        assertEquals("/protected/out/folder/note.txt", conflicts.single().existingFile.absolutePath)
    }

    @Test
    fun `conflict detection respects selected entry prefix`() = runTest {
        io.createDirectoryPath("/protected/out")
        io.writeFile("/protected/out/one.txt", "one")
        io.writeFile("/protected/out/two.txt", "two")
        val entries = listOf(archiveEntry("one.txt"), archiveEntry("two.txt"))

        val conflicts = bridge.detectConflicts(entries, io.reference("/protected/out"), "two.txt")

        assertEquals(listOf("two.txt"), conflicts.map { it.sourcePath })
    }

    @Test
    fun `conflict detection returns empty for destination not created yet`() = runTest {
        val conflicts = bridge.detectConflicts(
            listOf(archiveEntry("note.txt")),
            io.reference("/protected/new-folder"),
            null
        )

        assertTrue(conflicts.isEmpty())
    }

    @Test
    fun `conflict detection propagates backend disconnection`() = runTest {
        io.disconnected = true

        val result = runCatching {
            bridge.detectConflicts(
                listOf(archiveEntry("note.txt")),
                io.reference("/protected/out"),
                null
            )
        }

        assertTrue(result.exceptionOrNull() is PrivilegedFileFailure.BackendDisconnected)
    }

    @Test
    fun `publish extraction creates missing destination and nested folders`() = runTest {
        val extracted = File(testRoot, "extracted").apply { mkdirs() }
        File(extracted, "folder").mkdirs()
        File(extracted, "folder/note.txt").writeText("new")

        bridge.publishExtraction(
            extracted,
            io.reference("/protected/new-output"),
            emptyMap()
        )

        assertEquals("new", io.readFile("/protected/new-output/folder/note.txt"))
    }

    @Test
    fun `publish extraction skip directory omits its descendants`() = runTest {
        io.createDirectoryPath("/protected/out")
        io.createDirectoryPath("/protected/out/folder")
        io.writeFile("/protected/out/folder/old.txt", "old")
        val extracted = File(testRoot, "extracted-skip").apply { mkdirs() }
        File(extracted, "folder").mkdirs()
        File(extracted, "folder/new.txt").writeText("new")

        bridge.publishExtraction(
            extracted,
            io.reference("/protected/out"),
            mapOf("folder" to ConflictResolution.SKIP)
        )

        assertEquals(listOf("old.txt"), io.listNames("/protected/out/folder"))
    }

    @Test
    fun `publish extraction keep both aliases directory descendants`() = runTest {
        io.createDirectoryPath("/protected/out")
        io.createDirectoryPath("/protected/out/folder")
        io.writeFile("/protected/out/folder/old.txt", "old")
        val extracted = File(testRoot, "extracted-keep").apply { mkdirs() }
        File(extracted, "folder").mkdirs()
        File(extracted, "folder/new.txt").writeText("new")

        bridge.publishExtraction(extracted, io.reference("/protected/out"), emptyMap())

        assertEquals("old", io.readFile("/protected/out/folder/old.txt"))
        assertEquals("new", io.readFile("/protected/out/folder (1)/new.txt"))
    }

    @Test
    fun `publish extraction replace commits and removes backup`() = runTest {
        io.createDirectoryPath("/protected/out")
        io.writeFile("/protected/out/note.txt", "old")
        val extracted = File(testRoot, "extracted-replace").apply { mkdirs() }
        File(extracted, "note.txt").writeText("new")

        bridge.publishExtraction(
            extracted,
            io.reference("/protected/out"),
            mapOf("note.txt" to ConflictResolution.REPLACE)
        )

        assertEquals("new", io.readFile("/protected/out/note.txt"))
        assertTrue(io.listNames("/protected/out").none { it.contains("arcile-replace") })
    }

    @Test
    fun `publish extraction rolls back created outputs and restores replacement`() = runTest {
        io.createDirectoryPath("/protected/out")
        io.writeFile("/protected/out/first.txt", "old")
        val extracted = File(testRoot, "extracted-rollback").apply { mkdirs() }
        File(extracted, "first.txt").writeText("new")
        File(extracted, "second.txt").writeText("fail")
        io.failOutputWhenPathContains = "second.txt"

        val result = runCatching {
            bridge.publishExtraction(
                extracted,
                io.reference("/protected/out"),
                mapOf("first.txt" to ConflictResolution.REPLACE)
            )
        }

        assertEquals("output failed", result.exceptionOrNull()?.message)
        assertEquals("old", io.readFile("/protected/out/first.txt"))
        assertFalse(io.exists("/protected/out/second.txt"))
        assertTrue(io.listNames("/protected/out").none { it.contains("arcile-replace") })
    }

    @Test
    fun `publish extraction handles same base names with and without extensions`() = runTest {
        io.createDirectoryPath("/protected/out")
        io.writeFile("/protected/out/name", "old")
        io.writeFile("/protected/out/name.txt", "old text")
        val extracted = File(testRoot, "extracted-names").apply { mkdirs() }
        File(extracted, "name").writeText("new")
        File(extracted, "name.txt").writeText("new text")

        bridge.publishExtraction(extracted, io.reference("/protected/out"), emptyMap())

        assertEquals("new", io.readFile("/protected/out/name (1)"))
        assertEquals("new text", io.readFile("/protected/out/name (1).txt"))
    }

    @Test
    fun `publish extraction reports aggregate file progress`() = runTest {
        io.createDirectoryPath("/protected/out")
        val extracted = File(testRoot, "extracted-progress").apply { mkdirs() }
        File(extracted, "one.bin").writeBytes(ByteArray(150_000))
        File(extracted, "two.bin").writeBytes(ByteArray(170_000))
        val progress = mutableListOf<Pair<Int, Long>>()

        bridge.publishExtraction(extracted, io.reference("/protected/out"), emptyMap()) {
            progress += it.completedItems to requireNotNull(it.bytesCopied)
        }

        assertEquals(2, progress.last().first)
        assertEquals(320_000L, progress.last().second)
        assertTrue(progress.map(Pair<Int, Long>::second).zipWithNext().all { (left, right) -> right >= left })
    }

    private fun archiveEntry(path: String, size: Long = 1L) = ArchiveEntryModel(
        name = path.substringAfterLast('/'),
        path = path,
        size = size,
        compressedSize = null,
        lastModified = null,
        isDirectory = false,
        canRead = true
    )
}

class BackendAwareArchiveManagerTest {
    private lateinit var testRoot: File
    private lateinit var remoteRoot: File
    private lateinit var workspaceRoot: File
    private lateinit var io: FakeArchiveNodeIo
    private lateinit var manager: BackendAwareArchiveManager

    @Before
    fun setUp() {
        testRoot = createTempDir(prefix = "backend-aware-archive").canonicalFile
        remoteRoot = File(testRoot, "remote").apply { mkdirs() }
        workspaceRoot = File(testRoot, "workspaces")
        io = FakeArchiveNodeIo(remoteRoot)
        val finalizer = mockk<MutationFinalizer>(relaxed = true)
        coEvery { finalizer.finalize(*anyVararg()) } returns Unit
        val volumeProvider = object : VolumeProvider {
            override val activeStorageRoots = listOf(testRoot.absolutePath)
            override fun observeStorageVolumes(): Flow<List<StorageVolume>> = flowOf(emptyList())
            override suspend fun getStorageVolumes(): Result<List<StorageVolume>> = Result.success(emptyList())
            override suspend fun currentVolumes(): List<StorageVolume> = emptyList()
            override fun invalidateCache() = Unit
        }
        val codec = DefaultArchiveManager(
            volumeProvider = volumeProvider,
            mutationFinalizer = finalizer,
            additionalSafeRoots = listOf(workspaceRoot.absolutePath)
        )
        manager = BackendAwareArchiveManager(
            codec = codec,
            bridge = ArchiveNodeWorkspaceBridge(io, workspaceRoot)
        )
    }

    @After
    fun tearDown() {
        testRoot.deleteRecursively()
    }

    @Test
    fun `remote zip creation listing metadata and extraction are end to end`() = runTest {
        val source = io.writeFile("/protected/note.txt", "protected body")
        val archive = io.reference("/protected/bundle.zip")

        val created = manager.createArchive(
            sources = listOf(source),
            destinationArchive = archive,
            format = ArchiveFormat.ZIP
        )
        val entries = manager.listArchiveEntries(archive).getOrThrow()
        val summary = manager.getArchiveMetadata(archive).getOrThrow()
        val extracted = manager.extractArchive(archive, io.reference("/protected/out"))

        assertTrue(created.isSuccess)
        assertEquals(listOf("note.txt"), entries.filterNot { it.isDirectory }.map { it.path })
        assertEquals("/protected/bundle.zip", summary.archivePath)
        assertEquals(io.fileLength("/protected/bundle.zip"), summary.archiveSize)
        assertTrue(extracted.isSuccess)
        assertEquals("protected body", io.readFile("/protected/out/note.txt"))
        assertNoWorkspaceFiles()
    }

    @Test
    fun `remote tar gzip creation and extraction use descriptor boundary`() = runTest {
        val source = io.writeFile("/protected/report.txt", "tar body")
        val archive = io.reference("/protected/reports.tar.gz")

        assertTrue(
            manager.createArchive(
                sources = listOf(source),
                destinationArchive = archive,
                format = ArchiveFormat.TAR_GZIP
            ).isSuccess
        )
        assertTrue(manager.listArchiveEntries(archive).getOrThrow().any { it.path == "report.txt" })
        assertTrue(manager.extractArchive(archive, io.reference("/protected/tar-out")).isSuccess)

        assertEquals("tar body", io.readFile("/protected/tar-out/report.txt"))
        assertNoWorkspaceFiles()
    }

    @Test
    fun `remote seven zip creation and extraction use seekable private workspace`() = runTest {
        val source = io.writeFile("/protected/seven.txt", "seven body")
        val archive = io.reference("/protected/seven.7z")

        assertTrue(
            manager.createArchive(
                sources = listOf(source),
                destinationArchive = archive,
                format = ArchiveFormat.SEVEN_Z
            ).isSuccess
        )
        assertTrue(manager.listArchiveEntries(archive).getOrThrow().any { it.path == "seven.txt" })
        assertTrue(manager.extractArchive(archive, io.reference("/protected/seven-out")).isSuccess)

        assertEquals("seven body", io.readFile("/protected/seven-out/seven.txt"))
        assertNoWorkspaceFiles()
    }

    @Test
    fun `encrypted remote zip accepts correct password and rejects missing password`() = runTest {
        val source = io.writeFile("/protected/secret.txt", "classified")
        val archive = io.reference("/protected/secret.zip")
        assertTrue(
            manager.createArchive(
                sources = listOf(source),
                destinationArchive = archive,
                format = ArchiveFormat.ZIP,
                password = "correct horse"
            ).isSuccess
        )

        val missing = manager.listArchiveEntries(archive)
        val listed = manager.listArchiveEntries(archive, "correct horse")
        val extracted = manager.extractArchive(
            archive,
            io.reference("/protected/secret-out"),
            password = "correct horse"
        )

        assertTrue(missing.isFailure)
        assertTrue(missing.exceptionOrNull()?.message.orEmpty().contains("password"))
        assertEquals(listOf("secret.txt"), listed.getOrThrow().map { it.path })
        assertTrue(extracted.isSuccess)
        assertEquals("classified", io.readFile("/protected/secret-out/secret.txt"))
        assertNoWorkspaceFiles()
    }

    @Test
    fun `remote directory source is archived recursively`() = runTest {
        val folder = io.createDirectoryPath("/protected/project")
        io.createDirectoryPath("/protected/project/nested")
        io.writeFile("/protected/project/nested/config.json", "{}")
        val archive = io.reference("/protected/project.zip")

        val result = manager.createArchive(
            sources = listOf(folder),
            destinationArchive = archive,
            format = ArchiveFormat.ZIP
        )

        assertTrue(result.isSuccess)
        assertTrue(
            manager.listArchiveEntries(archive).getOrThrow()
                .any { it.path == "project/nested/config.json" }
        )
    }

    @Test
    fun `remote conflict detection uses destination backend identity`() = runTest {
        val source = io.writeFile("/protected/note.txt", "new")
        val archive = io.reference("/protected/conflict.zip")
        manager.createArchive(listOf(source), archive, ArchiveFormat.ZIP).getOrThrow()
        io.createDirectoryPath("/protected/out")
        io.writeFile("/protected/out/note.txt", "old")

        val conflicts = manager.detectArchiveConflicts(
            archive = archive,
            destination = io.reference("/protected/out")
        ).getOrThrow()

        assertEquals(1, conflicts.size)
        assertEquals("/protected/out/note.txt", conflicts.single().existingFile.absolutePath)
    }

    @Test
    fun `remote extraction applies replace conflict and leaves no hidden backup`() = runTest {
        val source = io.writeFile("/protected/source/note.txt", "new")
        val archive = io.reference("/protected/replace.zip")
        manager.createArchive(listOf(source), archive, ArchiveFormat.ZIP).getOrThrow()
        io.createDirectoryPath("/protected/out")
        io.writeFile("/protected/out/note.txt", "old")

        val result = manager.extractArchive(
            archive = archive,
            destination = io.reference("/protected/out"),
            resolutions = mapOf("note.txt" to ConflictResolution.REPLACE)
        )

        assertTrue(result.isSuccess)
        assertEquals("new", io.readFile("/protected/out/note.txt"))
        assertTrue(io.listNames("/protected/out").none { it.contains("arcile-replace") })
    }

    @Test
    fun `remote extraction selected prefix publishes only selected entry`() = runTest {
        io.createDirectoryPath("/protected/sources")
        val one = io.writeFile("/protected/sources/one.txt", "one")
        val two = io.writeFile("/protected/sources/two.txt", "two")
        val archive = io.reference("/protected/selected.zip")
        manager.createArchive(listOf(one, two), archive, ArchiveFormat.ZIP).getOrThrow()

        val result = manager.extractArchive(
            archive = archive,
            destination = io.reference("/protected/selected-out"),
            entryPrefix = "two.txt"
        )

        assertTrue(result.isSuccess)
        assertFalse(io.exists("/protected/selected-out/one.txt"))
        assertEquals("two", io.readFile("/protected/selected-out/two.txt"))
    }

    @Test
    fun `backend loss is returned instead of falling back to display path`() = runTest {
        val archive = io.writeFile("/protected/lost.zip", "not important")
        io.disconnected = true

        val result = manager.listArchiveEntries(archive)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is PrivilegedFileFailure.BackendDisconnected)
        assertTrue(io.openedInputs.isEmpty())
        assertNoWorkspaceFiles()
    }

    @Test
    fun `remote publish failure leaves no final archive or private workspace`() = runTest {
        val source = io.writeFile("/protected/failure.txt", "body")
        val archive = io.reference("/protected/failure.zip")
        io.failOutputWhenPathContains = ".partial"

        val result = manager.createArchive(listOf(source), archive, ArchiveFormat.ZIP)

        assertTrue(result.isFailure)
        assertFalse(io.exists("/protected/failure.zip"))
        assertTrue(io.listNames("/protected").none { it.endsWith(".partial") })
        assertNoWorkspaceFiles()
    }

    @Test
    fun `local node overload remains on direct codec path`() = runTest {
        val source = File(testRoot, "local.txt").apply { writeText("local") }
        val archive = File(testRoot, "local.zip")

        val result = manager.createArchive(
            sources = listOf(StorageNodeRef.local(source.absolutePath)),
            destinationArchive = StorageNodeRef.local(archive.absolutePath),
            format = ArchiveFormat.ZIP
        )

        assertTrue(result.isSuccess)
        assertTrue(manager.listArchiveEntries(StorageNodeRef.local(archive.absolutePath)).isSuccess)
        assertTrue(io.openedInputs.isEmpty())
        assertTrue(io.openedOutputs.isEmpty())
    }

    private fun assertNoWorkspaceFiles() {
        assertTrue(workspaceRoot.listFiles().orEmpty().isEmpty())
    }
}

private class FakeArchiveNodeIo(
    private val root: File
) : ArchiveNodeIo {
    val openedInputs = mutableListOf<String>()
    val openedOutputs = mutableListOf<String>()
    val renamedDestinations = mutableListOf<String>()
    val inputOverrides = mutableMapOf<String, ByteArray>()
    var failOutputWhenPathContains: String? = null
    var disconnected = false

    init {
        root.mkdirs()
    }

    override suspend fun inspect(node: StorageNodeRef): FileModel {
        checkConnected()
        val file = file(node.displayPath.absolutePath)
        if (!file.exists()) throw PrivilegedFileFailure.PathMissing(node.displayPath.absolutePath)
        return model(node.displayPath.absolutePath, file)
    }

    override suspend fun children(directory: StorageNodeRef): List<FileModel> {
        checkConnected()
        val parent = file(directory.displayPath.absolutePath)
        if (!parent.exists()) throw PrivilegedFileFailure.PathMissing(directory.displayPath.absolutePath)
        require(parent.isDirectory) { "Path is not a directory" }
        return parent.listFiles().orEmpty().sortedBy(File::getName).map { child ->
            model(childPath(directory.displayPath.absolutePath, child.name), child)
        }
    }

    override suspend fun createDirectory(parent: StorageNodeRef, name: String): FileModel {
        checkConnected()
        validateName(name)
        val targetPath = childPath(parent.displayPath.absolutePath, name)
        val target = file(targetPath)
        require(!target.exists()) { "Path already exists" }
        require(target.mkdir()) { "Could not create directory" }
        return model(targetPath, target)
    }

    override suspend fun createFile(parent: StorageNodeRef, name: String): FileModel {
        checkConnected()
        validateName(name)
        val targetPath = childPath(parent.displayPath.absolutePath, name)
        val target = file(targetPath)
        require(!target.exists()) { "Path already exists" }
        require(target.createNewFile()) { "Could not create file" }
        return model(targetPath, target)
    }

    override suspend fun rename(node: StorageNodeRef, newName: String): FileModel {
        checkConnected()
        validateName(newName)
        val source = file(node.displayPath.absolutePath)
        if (!source.exists()) throw PrivilegedFileFailure.PathMissing(node.displayPath.absolutePath)
        val targetPath = childPath(node.displayPath.absolutePath.substringBeforeLast('/').ifBlank { "/" }, newName)
        val target = file(targetPath)
        require(!target.exists()) { "Path already exists" }
        require(source.renameTo(target)) { "Rename failed" }
        renamedDestinations += targetPath
        return model(targetPath, target)
    }

    override suspend fun delete(node: StorageNodeRef) {
        checkConnected()
        val target = file(node.displayPath.absolutePath)
        if (!target.exists()) return
        require(if (target.isDirectory) target.deleteRecursively() else target.delete()) { "Delete failed" }
    }

    override suspend fun openInput(node: StorageNodeRef): ArchiveInput {
        checkConnected()
        val path = node.displayPath.absolutePath
        openedInputs += path
        val override = inputOverrides[path]
        return if (override != null) ArchiveInput(ByteArrayInputStream(override)) else ArchiveInput(FileInputStream(file(path)))
    }

    override suspend fun openOutput(node: StorageNodeRef): ArchiveOutput {
        checkConnected()
        val path = node.displayPath.absolutePath
        openedOutputs += path
        if (failOutputWhenPathContains?.let(path::contains) == true) throw IOException("output failed")
        return ArchiveOutput(FileOutputStream(file(path), false))
    }

    fun reference(path: String, capabilities: StorageNodeCapabilities = StorageNodeCapabilities()): StorageNodeRef =
        StorageNodeRef.root(
            displayPath = path,
            remoteCanonicalIdentity = path,
            capabilities = capabilities
        )

    fun createDirectoryPath(path: String): StorageNodeRef {
        val directory = file(path)
        require(directory.mkdirs() || directory.isDirectory)
        return reference(path)
    }

    fun writeFile(path: String, text: String): StorageNodeRef = writeFile(path, text.toByteArray())

    fun writeFile(path: String, bytes: ByteArray): StorageNodeRef {
        val target = file(path)
        target.parentFile?.mkdirs()
        target.writeBytes(bytes)
        return reference(path)
    }

    fun readFile(path: String): String = file(path).readText()

    fun exists(path: String): Boolean = file(path).exists()

    fun fileLength(path: String): Long = file(path).length()

    fun listNames(path: String): List<String> = file(path).listFiles().orEmpty().map(File::getName).sorted()

    private fun model(path: String, file: File): FileModel = FileModel(
        name = file.name.ifBlank { "protected" },
        absolutePath = path,
        size = if (file.isFile) file.length() else 0L,
        lastModified = file.lastModified(),
        isDirectory = file.isDirectory,
        extension = file.extension.lowercase(),
        isHidden = file.name.startsWith('.'),
        nodeRef = reference(path)
    )

    private fun file(path: String): File {
        require(path == "/protected" || path.startsWith("/protected/")) { "Unexpected path $path" }
        val relative = path.removePrefix("/protected").trimStart('/')
        return if (relative.isBlank()) root else File(root, relative)
    }

    private fun childPath(parent: String, name: String): String =
        if (parent == "/protected") "$parent/$name" else "${parent.trimEnd('/')}/$name"

    private fun validateName(name: String) {
        require(name.isNotBlank() && '/' !in name && '\\' !in name && name != "." && name != "..")
    }

    private fun checkConnected() {
        if (disconnected) throw PrivilegedFileFailure.BackendDisconnected()
    }
}
