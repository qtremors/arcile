package dev.qtremors.arcile.core.storage.domain

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveRepositoryCapabilityTest {
    private val unsupported = object : ArchiveRepository {}

    @Test
    fun `unsupported archive operations return typed capability failures`() = runTest {
        assertCapability(
            unsupported.listArchiveEntries("/file.zip"),
            StorageCapability.ARCHIVE_LIST
        )
        assertCapability(
            unsupported.getArchiveMetadata("/file.zip"),
            StorageCapability.ARCHIVE_METADATA
        )
        assertCapability(
            unsupported.extractArchive("/file.zip", "/destination"),
            StorageCapability.ARCHIVE_EXTRACT
        )
        assertCapability(
            unsupported.detectArchiveConflicts("/file.zip", "/destination"),
            StorageCapability.ARCHIVE_CONFLICT_DETECTION
        )
        assertCapability(
            unsupported.createArchive(listOf("/file.txt"), "/file.zip"),
            StorageCapability.ARCHIVE_CREATE
        )
    }

    private fun assertCapability(result: Result<*>, expected: StorageCapability) {
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as UnsupportedStorageCapabilityException
        assertEquals(expected, error.capability)
    }
}
