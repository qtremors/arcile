package dev.qtremors.arcile.core.storage.data

import dev.qtremors.arcile.core.privilege.PrivilegedFileFailure
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import dev.qtremors.arcile.core.storage.data.source.FileSystemDataSource
import dev.qtremors.arcile.core.storage.data.source.StorageNodeInput
import dev.qtremors.arcile.core.storage.domain.CleanerGroupType
import dev.qtremors.arcile.core.storage.domain.CleanerRiskLevel
import dev.qtremors.arcile.core.storage.domain.CleanerRiskReason
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.StorageCleanerRules
import dev.qtremors.arcile.core.storage.domain.StorageCleanerScanLimits
import dev.qtremors.arcile.core.storage.domain.StorageCleanerScanPhase
import dev.qtremors.arcile.core.storage.domain.StorageNodeCapabilities
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import io.mockk.mockk
import java.io.ByteArrayInputStream
import java.io.IOException
import javax.inject.Provider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultStorageCleanerScannerNodeTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val dispatchers = ArcileDispatchers(dispatcher, dispatcher, dispatcher, dispatcher)
    private val source = CleanerNodeFileSystem()
    private val scanner = DefaultStorageCleanerScanner(
        dispatchers = dispatchers,
        snapshotStore = null,
        fileSystemDataSource = Provider { source }
    )

    @Test
    fun `protected scan recursively classifies files and retains node identity`() = runTest {
        val root = source.root("/data/local/tmp", "tmp")
        source.directory(root)
        val cache = source.folder(root, "cache")
        val large = source.file(cache, "payload.tmp", ByteArray(24) { 7 })

        val result = scanner.scanNodes(
            roots = listOf(root),
            limits = limits(largeFileThresholdBytes = 20L)
        )

        val largeCandidate = result.candidate(CleanerGroupType.LargeFiles, large.displayPath.absolutePath)
        val junkCandidate = result.candidate(CleanerGroupType.Junk, large.displayPath.absolutePath)
        assertEquals(large, largeCandidate.nodeRef)
        assertEquals(large, junkCandidate.nodeRef)
        assertEquals(1, result.scannedFiles)
        assertFalse(result.isPartial)
        assertEquals(listOf(root), source.inspected)
        assertTrue(source.listed.containsAll(listOf(root, cache)))
    }

    @Test
    fun `duplicate scan hashes protected descriptors instead of local paths`() = runTest {
        val root = source.root("/data/media", "media")
        source.directory(root)
        val first = source.file(root, "first.bin", "same payload".toByteArray())
        val second = source.file(root, "second.bin", "same payload".toByteArray())

        val result = scanner.scanNodes(listOf(root), limits = limits())

        val duplicates = result.group(CleanerGroupType.Duplicates)
        assertEquals(setOf(first, second), duplicates.mapNotNull { it.nodeRef }.toSet())
        assertEquals(1, duplicates.mapNotNull { it.duplicateGroupKey }.distinct().size)
        assertEquals(2, source.openCount(first))
        assertEquals(2, source.openCount(second))
    }

    @Test
    fun `same size protected files with different content are not duplicates`() = runTest {
        val root = source.root("/data/media", "media")
        source.directory(root)
        source.file(root, "first.bin", "alpha".toByteArray())
        source.file(root, "second.bin", "bravo".toByteArray())

        val result = scanner.scanNodes(listOf(root), limits = limits())

        assertTrue(result.group(CleanerGroupType.Duplicates).isEmpty())
    }

    @Test
    fun `large protected files use bounded descriptor samples before full hashing`() = runTest {
        val root = source.root("/data/media", "media")
        source.directory(root)
        val bytes = ByteArray(20_000) { index -> (index % 251).toByte() }
        val first = source.file(root, "first.bin", bytes)
        val second = source.file(root, "second.bin", bytes.copyOf())

        val result = scanner.scanNodes(listOf(root), limits = limits())

        assertEquals(2, result.group(CleanerGroupType.Duplicates).size)
        assertEquals(2, source.openCount(first))
        assertEquals(2, source.openCount(second))
    }

    @Test
    fun `descriptor failure excludes uncertain duplicate without failing other groups`() = runTest {
        val root = source.root("/data/media", "media")
        source.directory(root)
        val first = source.file(root, "first.bin", "same".toByteArray())
        source.file(root, "second.bin", "same".toByteArray())
        source.openFailures[first.canonicalIdentity.value] = IOException("descriptor closed")

        val result = scanner.scanNodes(
            listOf(root),
            limits = limits(largeFileThresholdBytes = 1L)
        )

        assertTrue(result.group(CleanerGroupType.Duplicates).isEmpty())
        assertEquals(2, result.group(CleanerGroupType.LargeFiles).size)
    }

    @Test
    fun `scan safety limit returns partial protected results`() = runTest {
        val root = source.root("/data/files", "files")
        source.directory(root)
        repeat(5) { source.file(root, "item-$it.bin", byteArrayOf(it.toByte())) }

        val result = scanner.scanNodes(
            listOf(root),
            limits = limits(maxFiles = 2, largeFileThresholdBytes = 1L)
        )

        assertEquals(2, result.scannedFiles)
        assertTrue(result.isPartial)
        assertEquals(2, result.group(CleanerGroupType.LargeFiles).size)
    }

    @Test
    fun `depth limit marks scan partial and does not enter deeper folder`() = runTest {
        val root = source.root("/data/tree", "tree")
        source.directory(root)
        val first = source.folder(root, "first")
        val second = source.folder(first, "second")
        source.file(second, "deep.tmp", byteArrayOf(1))

        val result = scanner.scanNodes(listOf(root), limits = limits(maxDepth = 1))

        assertTrue(result.isPartial)
        assertFalse(source.listed.contains(second))
        assertTrue(result.group(CleanerGroupType.Junk).isEmpty())
    }

    @Test
    fun `directory identity cycle is bounded and marked partial`() = runTest {
        val root = source.root("/data/tree", "tree")
        source.directory(root)
        source.children.getValue(root.canonicalIdentity.value) += source.model(root)

        val result = scanner.scanNodes(listOf(root), limits = limits())

        assertTrue(result.isPartial)
        assertEquals(1, source.listed.count { it == root })
    }

    @Test
    fun `Arcile Trash and thumbnail work folders are excluded`() = runTest {
        val root = source.root("/data/files", "files")
        source.directory(root)
        listOf(".arcile", ".trash", ".thumbnails").forEach { name ->
            val internal = source.folder(root, name)
            source.file(internal, "hidden.tmp", byteArrayOf(1))
        }
        val visible = source.file(root, "visible.tmp", byteArrayOf(2))

        val result = scanner.scanNodes(listOf(root), limits = limits())

        assertEquals(listOf(visible), result.group(CleanerGroupType.Junk).mapNotNull { it.nodeRef })
    }

    @Test
    fun `special protected file is neither classified nor opened`() = runTest {
        val root = source.root("/proc", "proc")
        source.directory(root)
        val special = source.file(
            root,
            "kmsg",
            byteArrayOf(1),
            capabilities = StorageNodeCapabilities(
                canRead = true,
                canWrite = false,
                canDelete = false,
                canArchive = false
            )
        )

        val result = scanner.scanNodes(listOf(root), limits = limits(largeFileThresholdBytes = 1L))

        assertEquals(0, result.scannedFiles)
        assertTrue(result.groups.all { it.candidates.isEmpty() })
        assertEquals(0, source.openCount(special))
    }

    @Test
    fun `unreadable protected file is skipped`() = runTest {
        val root = source.root("/data/files", "files")
        source.directory(root)
        source.file(
            root,
            "secret.tmp",
            byteArrayOf(1),
            capabilities = StorageNodeCapabilities(canRead = false, canArchive = true)
        )

        val result = scanner.scanNodes(listOf(root), limits = limits())

        assertEquals(0, result.scannedFiles)
    }

    @Test
    fun `empty deletable protected directory becomes a cleaner candidate`() = runTest {
        val root = source.root("/data/files", "files")
        source.directory(root)
        val empty = source.folder(root, "empty")

        val result = scanner.scanNodes(listOf(root), limits = limits())

        val candidate = result.candidate(CleanerGroupType.EmptyFolders, empty.displayPath.absolutePath)
        assertTrue(candidate.isDirectory)
        assertEquals(empty, candidate.nodeRef)
    }

    @Test
    fun `empty nondeletable protected directory is not offered for cleanup`() = runTest {
        val root = source.root("/data/files", "files")
        source.directory(root)
        source.folder(
            root,
            "read-only",
            capabilities = StorageNodeCapabilities(canRead = true, canDelete = false)
        )

        val result = scanner.scanNodes(listOf(root), limits = limits())

        assertTrue(result.group(CleanerGroupType.EmptyFolders).isEmpty())
    }

    @Test
    fun `old download classification recognizes remote slash paths`() = runTest {
        val root = source.root("/data/media", "media")
        source.directory(root)
        val download = source.folder(root, "Download")
        val old = source.file(download, "report.pdf", byteArrayOf(1), lastModified = 1L)

        val result = scanner.scanNodes(
            listOf(root),
            now = 10_000L,
            limits = limits(oldDownloadAgeMs = 100L)
        )

        assertEquals(old, result.candidate(CleanerGroupType.OldDownloads, old.displayPath.absolutePath).nodeRef)
    }

    @Test
    fun `protected Android data candidate is high risk`() = runTest {
        val root = source.root("/data/media/0", "media")
        source.directory(root)
        val android = source.folder(root, "Android")
        val data = source.folder(android, "data")
        val app = source.folder(data, "com.example.app")
        val candidateNode = source.file(app, "cache.tmp", byteArrayOf(1))

        val result = scanner.scanNodes(listOf(root), limits = limits())

        val candidate = result.candidate(CleanerGroupType.Junk, candidateNode.displayPath.absolutePath)
        assertEquals(CleanerRiskLevel.High, candidate.riskLevel)
        assertTrue(CleanerRiskReason.SystemOwnedPath in candidate.riskReasons)
        assertTrue(CleanerRiskReason.AppLikeFolder in candidate.riskReasons)
    }

    @Test
    fun `inaccessible descendant marks results partial while preserving accessible siblings`() = runTest {
        val root = source.root("/data/files", "files")
        source.directory(root)
        val denied = source.folder(root, "denied")
        val visible = source.file(root, "visible.tmp", byteArrayOf(1))
        source.listFailures[denied.canonicalIdentity.value] = PrivilegedFileFailure.AccessDenied("denied")

        val result = scanner.scanNodes(listOf(root), limits = limits())

        assertTrue(result.isPartial)
        assertEquals(visible, result.candidate(CleanerGroupType.Junk, visible.displayPath.absolutePath).nodeRef)
    }

    @Test
    fun `root listing failure remains actionable`() = runTest {
        val root = source.root("/data/denied", "denied")
        source.directory(root)
        val failure = PrivilegedFileFailure.AccessDenied("denied")
        source.listFailures[root.canonicalIdentity.value] = failure

        val result = runCatching { scanner.scanNodes(listOf(root), limits = limits()) }

        assertSame(failure, result.exceptionOrNull())
    }

    @Test
    fun `backend disconnect during root inspection remains actionable`() = runTest {
        val root = source.root("/data/files", "files")
        val failure = PrivilegedFileFailure.BackendDisconnected()
        source.inspectFailures[root.canonicalIdentity.value] = failure

        val result = runCatching { scanner.scanNodes(listOf(root), limits = limits()) }

        assertSame(failure, result.exceptionOrNull())
    }

    @Test(expected = CancellationException::class)
    fun `cancellation during protected listing escapes scanner`() = runTest {
        val root = source.root("/data/files", "files")
        source.directory(root)
        source.listFailures[root.canonicalIdentity.value] = CancellationException("cancelled")

        scanner.scanNodes(listOf(root), limits = limits())
    }

    @Test
    fun `protected scan emits a complete terminal update`() = runTest {
        val root = source.root("/data/files", "files")
        source.directory(root)
        source.file(root, "one.tmp", byteArrayOf(1))

        val updates = scanner.scanNodeGroupUpdates(
            roots = listOf(root),
            groupTypes = setOf(CleanerGroupType.Junk),
            limits = limits()
        ).toList()

        val terminal = updates.last()
        assertEquals(StorageCleanerScanPhase.Complete, terminal.progress.phase)
        assertEquals(1f, terminal.progress.progressFraction)
        assertEquals(setOf(CleanerGroupType.Junk), terminal.progress.completedGroups)
        assertNotNull(terminal.result)
    }

    @Test
    fun `disabled protected cleaner group remains empty`() = runTest {
        val root = source.root("/data/files", "files")
        source.directory(root)
        source.file(root, "one.tmp", byteArrayOf(1))
        val rules = StorageCleanerRules(
            sections = StorageCleanerRules.defaultSections() +
                (CleanerGroupType.Junk to dev.qtremors.arcile.core.storage.domain.CleanerSectionRule(enabled = false))
        )

        val result = scanner.scanNodes(listOf(root), limits = limits(), rules = rules)

        assertTrue(result.group(CleanerGroupType.Junk).isEmpty())
    }

    @Test
    fun `ignored protected path is excluded exactly`() = runTest {
        val root = source.root("/data/files", "files")
        source.directory(root)
        val ignored = source.file(root, "ignored.tmp", byteArrayOf(1))
        val visible = source.file(root, "visible.tmp", byteArrayOf(2))

        val result = scanner.scanNodes(
            listOf(root),
            limits = limits(),
            rules = StorageCleanerRules(ignoredPaths = setOf(ignored.displayPath.absolutePath))
        )

        assertEquals(listOf(visible), result.group(CleanerGroupType.Junk).mapNotNull { it.nodeRef })
    }

    @Test
    fun `Unicode and newline names retain exact protected identity`() = runTest {
        val root = source.root("/data/files", "files")
        source.directory(root)
        val unusual = source.file(root, "résumé\n.tmp", byteArrayOf(1))

        val result = scanner.scanNodes(listOf(root), limits = limits())

        val candidate = result.group(CleanerGroupType.Junk).single()
        assertEquals("résumé\n.tmp", candidate.name)
        assertEquals(unusual.canonicalIdentity, candidate.nodeRef?.canonicalIdentity)
    }

    @Test
    fun `empty protected root list is rejected before backend access`() = runTest {
        val result = runCatching { scanner.scanNodes(emptyList(), limits = limits()) }

        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("Choose a folder"))
        assertTrue(source.inspected.isEmpty())
    }

    @Test
    fun `mixed local and protected scopes are rejected`() = runTest {
        val root = source.root("/data/files", "files")
        val local = StorageNodeRef.local("/storage/emulated/0")

        val result = runCatching { scanner.scanNodes(listOf(root, local), limits = limits()) }

        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("cannot be mixed"))
    }

    @Test
    fun `mixed Root and Shizuku scopes are rejected`() = runTest {
        val root = source.root("/data/files", "root-files")
        val shizuku = source.shizuku("/data/files", "shell-files")

        val result = runCatching { scanner.scanNodes(listOf(root, shizuku), limits = limits()) }

        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("one connected backend"))
    }

    @Test
    fun `missing protected backend identity is rejected`() = runTest {
        val root = source.root("/data/files", "files").copy(backendIdentity = null)

        val result = runCatching { scanner.scanNodes(listOf(root), limits = limits()) }

        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("identity is missing"))
    }

    @Test
    fun `unreadable root is rejected before inspection`() = runTest {
        val root = source.root("/data/files", "files").copy(
            capabilities = StorageNodeCapabilities(canRead = false)
        )

        val result = runCatching { scanner.scanNodes(listOf(root), limits = limits()) }

        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("not readable"))
        assertTrue(source.inspected.isEmpty())
    }

    @Test
    fun `file cannot be selected as protected cleaner scope`() = runTest {
        val root = source.root("/data/files", "files")
        source.fileModel(root, byteArrayOf(1))

        val result = runCatching { scanner.scanNodes(listOf(root), limits = limits()) }

        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("must be a folder"))
    }

    @Test
    fun `same visible path on different backends retains distinct candidate identity`() = runTest {
        val root = source.root("/data/files", "root-files")
        source.directory(root)
        val rootFile = source.file(root, "one.tmp", byteArrayOf(1), identity = "root-one")
        val rootResult = scanner.scanNodes(listOf(root), limits = limits())

        source.resetInventory()
        val shizuku = source.shizuku("/data/files", "shell-files")
        source.directory(shizuku)
        val shellFile = source.file(shizuku, "one.tmp", byteArrayOf(1), identity = "shell-one")
        val shellResult = scanner.scanNodes(listOf(shizuku), limits = limits())

        assertEquals(rootFile.canonicalIdentity, rootResult.group(CleanerGroupType.Junk).single().nodeRef?.canonicalIdentity)
        assertEquals(shellFile.canonicalIdentity, shellResult.group(CleanerGroupType.Junk).single().nodeRef?.canonicalIdentity)
        assertTrue(rootFile.canonicalIdentity != shellFile.canonicalIdentity)
    }

    private fun limits(
        maxFiles: Int = 1_000,
        maxDepth: Int = 32,
        largeFileThresholdBytes: Long = 100L,
        oldDownloadAgeMs: Long = 1_000L
    ) = StorageCleanerScanLimits(
        maxFiles = maxFiles,
        maxDepth = maxDepth,
        maxCandidatesPerGroup = 200,
        largeFileThresholdBytes = largeFileThresholdBytes,
        oldDownloadAgeMs = oldDownloadAgeMs
    )
}

private class CleanerNodeFileSystem : FileSystemDataSource by mockk(relaxed = true) {
    val inspected = mutableListOf<StorageNodeRef>()
    val listed = mutableListOf<StorageNodeRef>()
    val children = linkedMapOf<String, MutableList<FileModel>>()
    val models = linkedMapOf<String, FileModel>()
    val content = linkedMapOf<String, ByteArray>()
    val opens = linkedMapOf<String, Int>()
    val inspectFailures = mutableMapOf<String, Throwable>()
    val listFailures = mutableMapOf<String, Throwable>()
    val openFailures = mutableMapOf<String, Throwable>()

    override suspend fun inspectNode(node: StorageNodeRef): Result<FileModel> {
        inspected += node
        inspectFailures[node.canonicalIdentity.value]?.let { return Result.failure(it) }
        return models[node.canonicalIdentity.value]
            ?.let(Result.Companion::success)
            ?: Result.failure(IllegalStateException("Missing ${node.canonicalIdentity.value}"))
    }

    override suspend fun listNodeFiles(directory: StorageNodeRef): Result<List<FileModel>> {
        listed += directory
        listFailures[directory.canonicalIdentity.value]?.let { return Result.failure(it) }
        return Result.success(children[directory.canonicalIdentity.value].orEmpty().toList())
    }

    override suspend fun openNodeInput(node: StorageNodeRef): Result<StorageNodeInput> {
        opens[node.canonicalIdentity.value] = openCount(node) + 1
        openFailures[node.canonicalIdentity.value]?.let { return Result.failure(it) }
        val bytes = content[node.canonicalIdentity.value]
            ?: return Result.failure(IOException("Missing content"))
        return Result.success(StorageNodeInput(ByteArrayInputStream(bytes)))
    }

    fun root(path: String, identity: String): StorageNodeRef = StorageNodeRef.root(
        displayPath = path,
        remoteCanonicalIdentity = identity,
        capabilities = cleanerCapabilities()
    )

    fun shizuku(path: String, identity: String): StorageNodeRef = StorageNodeRef.shizuku(
        displayPath = path,
        remoteCanonicalIdentity = identity,
        capabilities = cleanerCapabilities()
    )

    fun directory(node: StorageNodeRef) {
        models[node.canonicalIdentity.value] = model(node, isDirectory = true)
        children.getOrPut(node.canonicalIdentity.value) { mutableListOf() }
    }

    fun folder(
        parent: StorageNodeRef,
        name: String,
        identity: String = "${parent.backendIdentity}/$name",
        capabilities: StorageNodeCapabilities = cleanerCapabilities()
    ): StorageNodeRef {
        val node = child(parent, name, identity, capabilities)
        directory(node)
        children.getOrPut(parent.canonicalIdentity.value) { mutableListOf() } += model(node)
        return node
    }

    fun file(
        parent: StorageNodeRef,
        name: String,
        bytes: ByteArray,
        identity: String = "${parent.backendIdentity}/$name",
        capabilities: StorageNodeCapabilities = cleanerCapabilities(),
        lastModified: Long = 5_000L
    ): StorageNodeRef {
        val node = child(parent, name, identity, capabilities)
        val model = model(node, size = bytes.size.toLong(), lastModified = lastModified)
        models[node.canonicalIdentity.value] = model
        content[node.canonicalIdentity.value] = bytes
        children.getOrPut(parent.canonicalIdentity.value) { mutableListOf() } += model
        return node
    }

    fun fileModel(node: StorageNodeRef, bytes: ByteArray) {
        models[node.canonicalIdentity.value] = model(node, size = bytes.size.toLong())
        content[node.canonicalIdentity.value] = bytes
    }

    fun model(node: StorageNodeRef): FileModel = requireNotNull(models[node.canonicalIdentity.value])

    fun openCount(node: StorageNodeRef): Int = opens[node.canonicalIdentity.value] ?: 0

    fun resetInventory() {
        inspected.clear()
        listed.clear()
        children.clear()
        models.clear()
        content.clear()
        opens.clear()
        inspectFailures.clear()
        listFailures.clear()
        openFailures.clear()
    }

    private fun child(
        parent: StorageNodeRef,
        name: String,
        identity: String,
        capabilities: StorageNodeCapabilities
    ): StorageNodeRef = StorageNodeRef.privileged(
        backendId = parent.backendId,
        displayPath = "${parent.displayPath.absolutePath.trimEnd('/')}/$name",
        remoteCanonicalIdentity = identity,
        capabilities = capabilities
    )

    private fun model(
        node: StorageNodeRef,
        isDirectory: Boolean = false,
        size: Long = 0L,
        lastModified: Long = 5_000L
    ) = FileModel(
        name = node.displayPath.absolutePath.substringAfterLast('/'),
        absolutePath = node.displayPath.absolutePath,
        size = size,
        lastModified = lastModified,
        isDirectory = isDirectory,
        extension = node.displayPath.absolutePath.substringAfterLast('.').takeIf {
            '.' in node.displayPath.absolutePath.substringAfterLast('/')
        }.orEmpty(),
        isHidden = node.displayPath.absolutePath.substringAfterLast('/').startsWith('.'),
        nodeRef = node
    )

    private fun cleanerCapabilities() = StorageNodeCapabilities(
        canRead = true,
        canWrite = true,
        canDelete = true,
        canTrash = true,
        canArchive = true
    )
}

private fun dev.qtremors.arcile.core.storage.domain.StorageCleanerResult.group(
    type: CleanerGroupType
) = groups.first { it.type == type }.candidates

private fun dev.qtremors.arcile.core.storage.domain.StorageCleanerResult.candidate(
    type: CleanerGroupType,
    path: String
) = group(type).first { it.absolutePath == path }
