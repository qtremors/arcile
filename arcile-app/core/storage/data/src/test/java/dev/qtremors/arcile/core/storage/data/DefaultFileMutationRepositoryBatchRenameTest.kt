package dev.qtremors.arcile.core.storage.data

import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import dev.qtremors.arcile.core.storage.data.source.FileSystemDataSource
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.TrashRepository
import dev.qtremors.arcile.core.storage.domain.VolumeRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DefaultFileMutationRepositoryBatchRenameTest {

    @Test
    fun `final rename failure rolls every staged file back to its original name`() = runTest {
        val dataSource = mockk<FileSystemDataSource>()
        val calls = mutableListOf<Pair<String, String>>()
        val temporaryPaths = mutableMapOf<String, String>()

        coEvery { dataSource.renameFile(any(), any()) } answers {
            val path = firstArg<String>()
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

        coEvery { dataSource.renameFile(any(), any()) } answers {
            val path = firstArg<String>()
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
}
