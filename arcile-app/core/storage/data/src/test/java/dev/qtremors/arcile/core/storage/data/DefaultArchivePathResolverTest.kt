package dev.qtremors.arcile.core.storage.data

import dev.qtremors.arcile.core.storage.domain.ArchiveCollisionStyle
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.core.storage.domain.ArchivePathRequest
import dev.qtremors.arcile.core.storage.domain.ArchiveExtractionDestinationStyle
import dev.qtremors.arcile.core.storage.domain.ArchiveExtractionPathRequest
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultArchivePathResolverTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `extraction destinations are resolved and normalized in storage data`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val resolver = DefaultArchivePathResolver(
            ArcileDispatchers(dispatcher, dispatcher, dispatcher, dispatcher)
        )
        val archive = File(temporaryFolder.root, "backup.tar.gz")

        val named = resolver.resolveExtraction(
            ArchiveExtractionPathRequest(archive.path)
        ).getOrThrow()
        val same = resolver.resolveExtraction(
            ArchiveExtractionPathRequest(
                archivePath = archive.path,
                style = ArchiveExtractionDestinationStyle.SAME_FOLDER
            )
        ).getOrThrow()
        val custom = resolver.resolveExtraction(
            ArchiveExtractionPathRequest(
                archivePath = archive.path,
                style = ArchiveExtractionDestinationStyle.CUSTOM_FOLDER,
                customDestination = "C:\\Exports"
            )
        ).getOrThrow()

        assertEquals(File(temporaryFolder.root, "backup").path.replace('\\', '/'), named)
        assertEquals(temporaryFolder.root.path.replace('\\', '/'), same)
        assertEquals("C:/Exports", custom)
    }

    @Test
    fun `single source uses source name and underscore collision style`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val resolver = DefaultArchivePathResolver(
            ArcileDispatchers(dispatcher, dispatcher, dispatcher, dispatcher)
        )
        val parent = temporaryFolder.root
        val source = File(parent, "photo.jpg")
        File(parent, "photo.zip").createNewFile()

        val result = resolver.resolve(
            ArchivePathRequest(
                sourcePaths = listOf(source.path),
                format = ArchiveFormat.ZIP,
                collisionStyle = ArchiveCollisionStyle.UNDERSCORE
            )
        ).getOrThrow()

        assertEquals(
            File(parent, "photo_1.zip").absolutePath.replace('\\', '/'),
            result
        )
    }

    @Test
    fun `requested name is sanitized with parenthesized collisions`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val resolver = DefaultArchivePathResolver(
            ArcileDispatchers(dispatcher, dispatcher, dispatcher, dispatcher)
        )
        val parent = temporaryFolder.root
        File(parent, "safe_name.zip").createNewFile()

        val result = resolver.resolve(
            ArchivePathRequest(
                sourcePaths = listOf(File(parent, "source.txt").path),
                parentPath = parent.path,
                requestedName = "safe/name.zip",
                collisionStyle = ArchiveCollisionStyle.PARENTHESIZED
            )
        ).getOrThrow()

        assertEquals(
            File(parent, "safe_name (1).zip").absolutePath.replace('\\', '/'),
            result
        )
    }
}
