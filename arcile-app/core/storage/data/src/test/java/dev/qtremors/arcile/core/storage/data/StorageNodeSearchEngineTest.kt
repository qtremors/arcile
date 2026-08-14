package dev.qtremors.arcile.core.storage.data

import dev.qtremors.arcile.core.storage.data.source.FileSystemDataSource
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.SearchFilters
import dev.qtremors.arcile.core.storage.domain.StorageNodeCapabilities
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import dev.qtremors.arcile.core.storage.domain.StorageNodeSearchLimits
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageNodeSearchEngineTest {
    private val source = mockk<FileSystemDataSource>()

    @Test
    fun `search walks the selected privileged directory and preserves node identity`() = runTest {
        val root = rootNode("/data", "root-data")
        val apps = directory("apps", "/data/apps", rootNode("/data/apps", "root-apps"))
        val direct = file("settings.xml", "/data/settings.xml", rootNode("/data/settings.xml", "root-settings"))
        val nested = file("user-settings.xml", "/data/apps/user-settings.xml", rootNode("/data/apps/user-settings.xml", "root-user-settings"))
        givenTree(root, listOf(apps, direct), apps.nodeRef to listOf(nested))

        val result = engine().search("settings", root, null, limits()).getOrThrow()

        assertEquals(listOf("settings.xml", "user-settings.xml"), result.map(FileModel::name))
        assertEquals(listOf("root-settings", "root-user-settings"), result.map { it.nodeRef.backendIdentity })
        assertTrue(result.all { it.nodeRef.backendId == StorageNodeRef.ROOT_BACKEND_ID })
    }

    @Test
    fun `search applies type size date extension and mime filters`() = runTest {
        val root = rootNode("/data", "data")
        val matching = file(
            name = "report.PDF",
            path = "/data/report.PDF",
            node = rootNode("/data/report.PDF", "report"),
            size = 4_096L,
            modified = 50L,
            extension = "pdf",
            mimeType = "application/pdf"
        )
        val tooSmall = matching.copy(
            name = "small.pdf",
            absolutePath = "/data/small.pdf",
            size = 5L,
            nodeRef = rootNode("/data/small.pdf", "small")
        )
        val wrongMime = matching.copy(
            name = "image.pdf",
            absolutePath = "/data/image.pdf",
            mimeType = "image/jpeg",
            nodeRef = rootNode("/data/image.pdf", "image")
        )
        givenTree(root, listOf(matching, tooSmall, wrongMime))

        val result = engine().search(
            query = "",
            root = root,
            filters = SearchFilters(
                itemType = "Files",
                minSize = 1_000L,
                maxSize = 10_000L,
                minDateMillis = 40L,
                maxDateMillis = 60L,
                extensions = setOf(".PDF"),
                mimeType = "application/*"
            ),
            limits = limits()
        ).getOrThrow()

        assertEquals(listOf("report.PDF"), result.map(FileModel::name))
    }

    @Test
    fun `hidden folders are not traversed unless requested`() = runTest {
        val root = rootNode("/data", "data")
        val hidden = directory(".secret", "/data/.secret", rootNode("/data/.secret", "secret"))
        val child = file("password.txt", "/data/.secret/password.txt", rootNode("/data/.secret/password.txt", "password"))
        givenTree(root, listOf(hidden), hidden.nodeRef to listOf(child))

        val excluded = engine().search("password", root, SearchFilters(), limits()).getOrThrow()
        val included = engine().search(
            "password",
            root,
            SearchFilters(includeHidden = true),
            limits()
        ).getOrThrow()

        assertTrue(excluded.isEmpty())
        assertEquals(listOf(child), included)
        coVerify(exactly = 1) { source.listNodeFiles(hidden.nodeRef) }
    }

    @Test
    fun `canonical directory cycles are visited once`() = runTest {
        val root = rootNode("/data", "same-directory")
        val alias = directory("loop", "/data/loop", rootNode("/data/loop", "same-directory"))
        val match = file("match.txt", "/data/match.txt", rootNode("/data/match.txt", "match"))
        givenTree(root, listOf(alias, match))

        val result = engine().search("match", root, null, limits()).getOrThrow()

        assertEquals(listOf(match), result)
        coVerify(exactly = 0) { source.listNodeFiles(alias.nodeRef) }
    }

    @Test
    fun `an inaccessible descendant is skipped without losing accessible results`() = runTest {
        val root = rootNode("/data", "data")
        val denied = directory("denied", "/data/denied", rootNode("/data/denied", "denied"))
        val accessible = file("match.txt", "/data/match.txt", rootNode("/data/match.txt", "match"))
        givenTree(root, listOf(denied, accessible))
        coEvery { source.listNodeFiles(denied.nodeRef) } returns Result.failure(SecurityException("denied"))

        val result = engine().search("match", root, null, limits()).getOrThrow()

        assertEquals(listOf(accessible), result)
    }

    @Test
    fun `root listing failure is returned to the caller`() = runTest {
        val root = rootNode("/data", "data")
        coEvery { source.inspectNode(root) } returns Result.success(directory("data", "/data", root))
        val denied = SecurityException("Root listing denied")
        coEvery { source.listNodeFiles(root) } returns Result.failure(denied)

        val result = engine().search("anything", root, null, limits())

        assertTrue(result.isFailure)
        assertSame(denied, result.exceptionOrNull())
    }

    @Test
    fun `inspection failure is returned without attempting a listing`() = runTest {
        val root = rootNode("/data", "data")
        val disconnected = IllegalStateException("Backend disconnected")
        coEvery { source.inspectNode(root) } returns Result.failure(disconnected)

        val result = engine().search("anything", root, null, limits())

        assertSame(disconnected, result.exceptionOrNull())
        coVerify(exactly = 0) { source.listNodeFiles(any()) }
    }

    @Test
    fun `a non-directory root is rejected`() = runTest {
        val root = rootNode("/data/file.bin", "file")
        coEvery { source.inspectNode(root) } returns Result.success(file("file.bin", "/data/file.bin", root))

        val result = engine().search("file", root, null, limits())

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("folder"))
    }

    @Test
    fun `unreadable root is rejected before listing`() = runTest {
        val root = rootNode(
            "/data",
            "data",
            StorageNodeCapabilities(canRead = false, canWrite = false, canDelete = false)
        )
        coEvery { source.inspectNode(root) } returns Result.success(directory("data", "/data", root))

        val result = engine().search("file", root, null, limits())

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("not readable"))
        coVerify(exactly = 0) { source.listNodeFiles(any()) }
    }

    @Test
    fun `depth zero searches immediate children without traversing folders`() = runTest {
        val root = rootNode("/data", "data")
        val folder = directory("nested", "/data/nested", rootNode("/data/nested", "nested"))
        val direct = file("match-direct.txt", "/data/match-direct.txt", rootNode("/data/match-direct.txt", "direct"))
        val nested = file("match-nested.txt", "/data/nested/match-nested.txt", rootNode("/data/nested/match-nested.txt", "nested-match"))
        givenTree(root, listOf(folder, direct), folder.nodeRef to listOf(nested))

        val result = engine().search("match", root, null, limits(maxDepth = 0)).getOrThrow()

        assertEquals(listOf(direct), result)
        coVerify(exactly = 0) { source.listNodeFiles(folder.nodeRef) }
    }

    @Test
    fun `visited entry budget stops traversal deterministically`() = runTest {
        val root = rootNode("/data", "data")
        val files = (1..10).map { index ->
            file("match-$index.txt", "/data/match-$index.txt", rootNode("/data/match-$index.txt", "match-$index"))
        }
        givenTree(root, files)

        val result = engine().search(
            "match",
            root,
            null,
            limits(maxVisitedEntries = 3)
        ).getOrThrow()

        assertEquals(3, result.size)
        assertEquals(setOf("match-1.txt", "match-2.txt", "match-3.txt"), result.mapTo(mutableSetOf(), FileModel::name))
    }

    @Test
    fun `result budget returns only the configured number of matches`() = runTest {
        val root = rootNode("/data", "data")
        val files = (1..10).map { index ->
            file("result-$index.txt", "/data/result-$index.txt", rootNode("/data/result-$index.txt", "result-$index"))
        }
        givenTree(root, files)

        val result = engine().search("result", root, null, limits(maxResults = 2)).getOrThrow()

        assertEquals(2, result.size)
    }

    @Test
    fun `duration budget can stop before reading a directory`() = runTest {
        val root = rootNode("/data", "data")
        coEvery { source.inspectNode(root) } returns Result.success(directory("data", "/data", root))
        val times = ArrayDeque(listOf(0L, 2_000_000L))
        val engine = StorageNodeSearchEngine(source) { times.removeFirst() }

        val result = engine.search(
            "result",
            root,
            null,
            limits(maxDurationMillis = 1L)
        ).getOrThrow()

        assertTrue(result.isEmpty())
        coVerify(exactly = 0) { source.listNodeFiles(root) }
    }

    @Test(expected = CancellationException::class)
    fun `cancellation from inspection is never converted to a failed result`() = runTest {
        val root = rootNode("/data", "data")
        coEvery { source.inspectNode(root) } throws CancellationException("cancelled")

        engine().search("result", root, null, limits())
    }

    @Test(expected = CancellationException::class)
    fun `cancellation returned by a descendant listing is never skipped`() = runTest {
        val root = rootNode("/data", "data")
        val child = directory("child", "/data/child", rootNode("/data/child", "child"))
        givenTree(root, listOf(child))
        coEvery { source.listNodeFiles(child.nodeRef) } returns
            Result.failure(CancellationException("cancelled"))

        engine().search("result", root, null, limits())
    }

    @Test
    fun `results are ordered by exact prefix and contained matches`() = runTest {
        val root = rootNode("/data", "data")
        val contained = file("my-report.txt", "/data/my-report.txt", rootNode("/data/my-report.txt", "contained"))
        val prefixFile = file("report-old.txt", "/data/report-old.txt", rootNode("/data/report-old.txt", "prefix-file"))
        val prefixFolder = directory("report-folder", "/data/report-folder", rootNode("/data/report-folder", "prefix-folder"))
        val exact = file("report", "/data/report", rootNode("/data/report", "exact"))
        givenTree(root, listOf(contained, prefixFile, prefixFolder, exact), prefixFolder.nodeRef to emptyList())

        val result = engine().search("REPORT", root, null, limits()).getOrThrow()

        assertEquals(
            listOf("report", "report-folder", "report-old.txt", "my-report.txt"),
            result.map(FileModel::name)
        )
    }

    @Test
    fun `same display path on another backend is not substituted`() = runTest {
        val root = shizukuNode("/data", "shizuku-data")
        val match = file("match.txt", "/data/match.txt", shizukuNode("/data/match.txt", "shizuku-match"))
        givenTree(root, listOf(match))

        val result = engine().search("match", root, null, limits()).getOrThrow()

        assertEquals(StorageNodeRef.SHIZUKU_BACKEND_ID, result.single().nodeRef.backendId)
        assertEquals("shizuku-match", result.single().nodeRef.backendIdentity)
        assertFalse(result.single().nodeRef == rootNode("/data/match.txt", "shizuku-match"))
    }

    private fun engine() = StorageNodeSearchEngine(source)

    private fun limits(
        maxDepth: Int = 8,
        maxVisitedEntries: Int = 1_000,
        maxResults: Int = 100,
        maxDurationMillis: Long = 30_000L
    ) = StorageNodeSearchLimits(maxDepth, maxVisitedEntries, maxResults, maxDurationMillis)

    private fun givenTree(
        root: StorageNodeRef,
        rootChildren: List<FileModel>,
        vararg descendants: Pair<StorageNodeRef, List<FileModel>>
    ) {
        coEvery { source.inspectNode(root) } returns Result.success(
            directory(root.displayPath.absolutePath.substringAfterLast('/'), root.displayPath.absolutePath, root)
        )
        coEvery { source.listNodeFiles(root) } returns Result.success(rootChildren)
        descendants.forEach { (node, children) ->
            coEvery { source.listNodeFiles(node) } returns Result.success(children)
        }
    }

    private fun directory(name: String, path: String, node: StorageNodeRef) = FileModel(
        name = name,
        absolutePath = path,
        isDirectory = true,
        isHidden = name.startsWith('.'),
        nodeRef = node
    )

    private fun file(
        name: String,
        path: String,
        node: StorageNodeRef,
        size: Long = 1L,
        modified: Long = 1L,
        extension: String = name.substringAfterLast('.', "").lowercase(),
        mimeType: String? = null
    ) = FileModel(
        name = name,
        absolutePath = path,
        size = size,
        lastModified = modified,
        extension = extension,
        isHidden = name.startsWith('.'),
        mimeType = mimeType,
        nodeRef = node
    )

    private fun rootNode(
        path: String,
        identity: String,
        capabilities: StorageNodeCapabilities = StorageNodeCapabilities()
    ) = StorageNodeRef.root(path, identity, capabilities = capabilities)

    private fun shizukuNode(path: String, identity: String) = StorageNodeRef.shizuku(path, identity)
}
