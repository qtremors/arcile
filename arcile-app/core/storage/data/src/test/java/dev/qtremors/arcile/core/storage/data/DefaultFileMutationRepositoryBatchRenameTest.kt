package dev.qtremors.arcile.core.storage.data

import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import dev.qtremors.arcile.core.storage.data.source.FileSystemDataSource
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import dev.qtremors.arcile.core.storage.domain.TrashRepository
import dev.qtremors.arcile.core.storage.domain.VolumeRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DefaultFileMutationRepositoryBatchRenameTest {

    @Test
    fun `privileged batch keeps backend identity through staging and final rename`() = runTest {
        val dataSource = mockk<FileSystemDataSource>()
        val calls = mutableListOf<Pair<StorageNodeRef, String>>()
        val first = rootNode("/system/a.conf")
        val second = rootNode("/system/b.conf")
        coEvery { dataSource.renameNode(any(), any()) } answers {
            val node = firstArg<StorageNodeRef>()
            val newName = secondArg<String>()
            calls += node to newName
            renamedLike(node, newName)
        }

        val result = repository(dataSource).batchRenameNodes(
            listOf(first to "one.conf", second to "two.conf")
        ).getOrThrow()

        assertEquals(2, result.size)
        assertEquals(listOf(first, second), result.map { it.first })
        assertEquals(listOf("/system/one.conf", "/system/two.conf"), result.map {
            it.second.displayPath.absolutePath
        })
        assertTrue(result.all { it.second.backendId == StorageNodeRef.ROOT_BACKEND_ID })
        assertTrue(calls.all { it.first.backendId == StorageNodeRef.ROOT_BACKEND_ID })
        assertEquals(4, calls.size)
        assertTrue(calls.take(2).all { it.second.startsWith(".arcile_tmp_") })
        assertEquals(listOf("one.conf", "two.conf"), calls.takeLast(2).map { it.second })
    }

    @Test
    fun `same visible path on local and privileged backends remains two transactions`() = runTest {
        val dataSource = mockk<FileSystemDataSource>()
        val calls = mutableListOf<Pair<String, String>>()
        val local = StorageNodeRef.local("/shared/item.txt")
        val root = rootNode("/shared/item.txt")
        coEvery { dataSource.renameNode(any(), any()) } answers {
            val node = firstArg<StorageNodeRef>()
            val newName = secondArg<String>()
            calls += node.backendId to newName
            renamedLike(node, newName)
        }

        val completed = repository(dataSource).batchRenameNodes(
            listOf(local to "local.txt", root to "root.txt")
        ).getOrThrow()

        assertEquals(2, completed.size)
        assertEquals(
            listOf(StorageNodeRef.LOCAL_BACKEND_ID, StorageNodeRef.ROOT_BACKEND_ID),
            completed.map { it.first.backendId }
        )
        assertEquals(
            listOf(StorageNodeRef.LOCAL_BACKEND_ID, StorageNodeRef.ROOT_BACKEND_ID),
            completed.map { it.second.backendId }
        )
        assertEquals(2, calls.count { it.first == StorageNodeRef.LOCAL_BACKEND_ID })
        assertEquals(2, calls.count { it.first == StorageNodeRef.ROOT_BACKEND_ID })
    }

    @Test
    fun `privileged final failure rolls completed and staged entries back on their backend`() = runTest {
        val dataSource = mockk<FileSystemDataSource>()
        val first = rootNode("/system/a.conf")
        val second = rootNode("/system/b.conf")
        val temporaryPaths = mutableMapOf<String, String>()
        val calls = mutableListOf<Pair<StorageNodeRef, String>>()
        coEvery { dataSource.renameNode(any(), any()) } answers {
            val node = firstArg<StorageNodeRef>()
            val path = node.displayPath.absolutePath
            val newName = secondArg<String>()
            calls += node to newName
            when {
                path == "/system/a.conf" && newName.startsWith(".arcile_tmp_") -> {
                    temporaryPaths["a"] = "/system/$newName"
                    renamedLike(node, newName)
                }
                path == "/system/b.conf" && newName.startsWith(".arcile_tmp_") -> {
                    temporaryPaths["b"] = "/system/$newName"
                    renamedLike(node, newName)
                }
                path == temporaryPaths["a"] && newName == "one.conf" -> renamedLike(node, newName)
                path == temporaryPaths["b"] && newName == "two.conf" ->
                    Result.failure(IllegalStateException("remote final rename failed"))
                path == "/system/one.conf" && newName.startsWith(".arcile_tmp_") ->
                    renamedLike(node, newName)
                path == temporaryPaths["b"] && newName == "b.conf" -> renamedLike(node, newName)
                path == temporaryPaths["a"] && newName == "a.conf" -> renamedLike(node, newName)
                else -> Result.failure(IllegalStateException("Unexpected rename: $path -> $newName"))
            }
        }

        val result = repository(dataSource).batchRenameNodes(
            listOf(first to "one.conf", second to "two.conf")
        )

        assertTrue(result.isFailure)
        assertTrue(calls.all { it.first.backendId == StorageNodeRef.ROOT_BACKEND_ID })
        assertTrue(calls.any { it.first.displayPath.absolutePath == temporaryPaths["b"] && it.second == "b.conf" })
        assertTrue(calls.any { it.first.displayPath.absolutePath == temporaryPaths["a"] && it.second == "a.conf" })
    }

    @Test
    fun `final rename failure rolls every staged file back to its original name`() = runTest {
        val dataSource = mockk<FileSystemDataSource>()
        val calls = mutableListOf<Pair<String, String>>()
        val temporaryPaths = mutableMapOf<String, String>()

        coEvery { dataSource.renameNode(any(), any()) } answers {
            val path = firstArg<StorageNodeRef>().displayPath.absolutePath
            val newName = secondArg<String>()
            calls += path to newName
            when {
                path == "/folder/a.txt" && newName.startsWith(".arcile_tmp_") -> {
                    temporaryPaths["a"] = "/folder/$newName"
                    renamed("/folder/$newName")
                }
                path == "/folder/b.txt" && newName.startsWith(".arcile_tmp_") -> {
                    temporaryPaths["b"] = "/folder/$newName"
                    renamed("/folder/$newName")
                }
                path == temporaryPaths["a"] && newName == "one.txt" -> renamed("/folder/one.txt")
                path == temporaryPaths["b"] && newName == "two.txt" ->
                    Result.failure(IllegalStateException("final rename failed"))
                path == "/folder/one.txt" && newName == temporaryPaths["a"]?.substringAfterLast('/') ->
                    renamed(temporaryPaths.getValue("a"))
                path == temporaryPaths["b"] && newName == "b.txt" -> renamed("/folder/b.txt")
                path == temporaryPaths["a"] && newName == "a.txt" -> renamed("/folder/a.txt")
                else -> Result.failure(IllegalStateException("Unexpected rename: $path -> $newName"))
            }
        }

        val result = repository(dataSource).batchRenameFiles(
            listOf("/folder/a.txt" to "one.txt", "/folder/b.txt" to "two.txt")
        )

        assertTrue(result.isFailure)
        assertTrue(calls.contains(temporaryPaths.getValue("b") to "b.txt"))
        assertTrue(calls.contains(temporaryPaths.getValue("a") to "a.txt"))
    }

    @Test
    fun `cancellation rolls back staged files and remains cancellation`() = runTest {
        val dataSource = mockk<FileSystemDataSource>()
        val temporaryPaths = mutableMapOf<String, String>()

        coEvery { dataSource.renameNode(any(), any()) } answers {
            val path = firstArg<StorageNodeRef>().displayPath.absolutePath
            val newName = secondArg<String>()
            when {
                path == "/folder/a.txt" && newName.startsWith(".arcile_tmp_") -> {
                    temporaryPaths["a"] = "/folder/$newName"
                    renamed("/folder/$newName")
                }
                path == "/folder/b.txt" && newName.startsWith(".arcile_tmp_") -> {
                    temporaryPaths["b"] = "/folder/$newName"
                    throw CancellationException("cancel batch")
                }
                path == temporaryPaths["b"] && newName == "b.txt" -> renamed("/folder/b.txt")
                path == temporaryPaths["a"] && newName == "a.txt" -> renamed("/folder/a.txt")
                else -> Result.failure(IllegalStateException("Unexpected rename: $path -> $newName"))
            }
        }

        try {
            repository(dataSource).batchRenameFiles(
                listOf("/folder/a.txt" to "one.txt", "/folder/b.txt" to "two.txt")
            )
            fail("Expected CancellationException")
        } catch (_: CancellationException) {
            assertTrue(temporaryPaths.keys.containsAll(listOf("a", "b")))
        }
    }

    private fun repository(dataSource: FileSystemDataSource) =
        DefaultFileMutationRepository(
            fileSystemDataSource = dataSource,
            volumeRepository = mockk<VolumeRepository>(relaxed = true),
            trashRepository = mockk<TrashRepository>(relaxed = true),
            dispatchers = ArcileDispatchers(
                main = Dispatchers.Unconfined,
                io = Dispatchers.Unconfined,
                default = Dispatchers.Unconfined,
                storage = Dispatchers.Unconfined
            )
        )

    private fun renamed(path: String): Result<FileModel> =
        Result.success(
            FileModel(
                absolutePath = path,
                name = path.substringAfterLast('/'),
                isDirectory = false
            )
        )

    private fun rootNode(path: String): StorageNodeRef = StorageNodeRef.root(
        displayPath = path,
        remoteCanonicalIdentity = path
    )

    private fun renamedLike(node: StorageNodeRef, newName: String): Result<FileModel> {
        val path = node.displayPath.absolutePath
        val parent = path.substringBeforeLast('/', "")
        val renamedPath = if (parent.isEmpty()) "/$newName" else "$parent/$newName"
        val renamedNode = when (node.backendId) {
            StorageNodeRef.ROOT_BACKEND_ID -> rootNode(renamedPath)
            StorageNodeRef.SHIZUKU_BACKEND_ID -> StorageNodeRef.shizuku(
                displayPath = renamedPath,
                remoteCanonicalIdentity = renamedPath
            )
            else -> StorageNodeRef.local(renamedPath)
        }
        return Result.success(
            FileModel(
                absolutePath = renamedPath,
                name = newName,
                isDirectory = false,
                nodeRef = renamedNode
            )
        )
    }
}
