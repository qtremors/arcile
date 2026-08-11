package dev.qtremors.arcile.feature.browser.delegate

import androidx.lifecycle.SavedStateHandle
import dev.qtremors.arcile.core.storage.domain.StorageBrowserLocation
import dev.qtremors.arcile.core.storage.domain.StorageNodeCapabilities
import dev.qtremors.arcile.core.storage.domain.StorageNodePath
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import dev.qtremors.arcile.feature.browser.BrowserNavigationState
import dev.qtremors.arcile.feature.browser.BrowserArchiveContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserNavigationPersistenceTest {
    @Test
    fun `restores direct filesystem location without volume id`() {
        val savedStateHandle = SavedStateHandle(
            mapOf(
                "currentPath" to "/system",
                "currentVolumeId" to null,
                "isVolumeRootScreen" to false,
                "isCategoryScreen" to false
            )
        )

        val restored = BrowserNavigationPersistence(savedStateHandle).restoreLocation()

        assertEquals(
            StorageBrowserLocation.DirectDirectory(StorageNodePath.of("/system")),
            restored
        )
    }

    @Test
    fun `saves and restores privileged current directory identity`() {
        val handle = SavedStateHandle()
        val persistence = BrowserNavigationPersistence(handle)
        val directory = StorageNodeRef.root(
            displayPath = "/data/user/0/com.example/files",
            remoteCanonicalIdentity = "/data/user/0/com.example/files",
            volumeId = "private",
            capabilities = StorageNodeCapabilities(canWrite = false, canDelete = false)
        )
        val state = BrowserNavigationState().withValues(
            currentPath = directory.displayPath.absolutePath,
            currentNodeRef = directory,
            currentVolumeId = null,
            isVolumeRootScreen = false,
            isCategoryScreen = false,
            activeCategoryName = ""
        )

        persistence.save(state)
        val restored = persistence.restoredCurrentNodeRef(directory.displayPath.absolutePath)

        assertEquals(StorageNodeRef.ROOT_BACKEND_ID, restored?.backendId)
        assertEquals(directory.displayPath, restored?.displayPath)
        assertEquals(directory.canonicalIdentity, restored?.canonicalIdentity)
        assertEquals(directory.backendIdentity, restored?.backendIdentity)
        assertEquals(directory.volumeId, restored?.volumeId)
    }

    @Test
    fun `privileged history codec preserves separators unicode and backend identity`() {
        val reference = StorageNodeRef.shizuku(
            displayPath = "/storage/emulated/0/Documents/報告:2026",
            remoteCanonicalIdentity = "/storage/emulated/0/Documents/報告:2026",
            volumeId = "primary:visible"
        )
        val entry = BrowserHistoryEntry.Directory(reference.displayPath.absolutePath, reference)

        val encoded = entry.toSavedValue()
        val decoded = BrowserHistoryEntry.fromSavedValue(encoded)

        assertTrue(encoded.startsWith("dir2:"))
        assertEquals(entry, decoded)
    }

    @Test
    fun `saves and restores protected archive identity independently from parent directory`() {
        val handle = SavedStateHandle()
        val persistence = BrowserNavigationPersistence(handle)
        val parent = StorageNodeRef.root(
            displayPath = "/data/local/tmp",
            remoteCanonicalIdentity = "/data/local/tmp"
        )
        val archive = StorageNodeRef.root(
            displayPath = "/data/local/tmp/資料.zip",
            remoteCanonicalIdentity = "/data/local/tmp/資料.zip",
            volumeId = "protected"
        )
        val state = BrowserNavigationState().withValues(
            currentPath = archive.displayPath.absolutePath,
            currentNodeRef = parent,
            archiveContext = BrowserArchiveContext(
                archivePath = archive.displayPath.absolutePath,
                archiveNodeRef = archive,
                entryPrefix = "folder"
            )
        )

        persistence.save(state)
        val restored = persistence.restoredArchiveNodeRef(archive.displayPath.absolutePath)

        assertEquals(archive.backendId, restored?.backendId)
        assertEquals(archive.displayPath, restored?.displayPath)
        assertEquals(archive.canonicalIdentity, restored?.canonicalIdentity)
        assertEquals(archive.backendIdentity, restored?.backendIdentity)
        assertEquals(archive.volumeId, restored?.volumeId)
        assertEquals(parent.backendId, persistence.restoredCurrentNodeRef(parent.displayPath.absolutePath)?.backendId)
    }

    @Test
    fun `protected archive history codec preserves entry prefix and backend identity`() {
        val archive = StorageNodeRef.shizuku(
            displayPath = "/storage/emulated/0/Android/data/報告:2026.zip",
            remoteCanonicalIdentity = "/storage/emulated/0/Android/data/報告:2026.zip",
            volumeId = "primary:visible"
        )
        val entry = BrowserHistoryEntry.Archive(
            archivePath = archive.displayPath.absolutePath,
            entryPrefix = "資料/選択",
            archiveNodeRef = archive
        )

        val encoded = entry.toSavedValue()
        val restored = BrowserHistoryEntry.fromSavedValue(encoded)

        assertTrue(encoded.startsWith("archive2:"))
        assertEquals(entry, restored)
    }

    @Test
    fun `legacy archive history remains readable`() {
        assertEquals(
            BrowserHistoryEntry.Archive("/storage/emulated/0/old.zip", "folder"),
            BrowserHistoryEntry.fromSavedValue("archive:/storage/emulated/0/old.zip|folder")
        )
    }

    @Test
    fun `privileged history codec preserves absent optional identity fields`() {
        val reference = StorageNodeRef(
            backendId = StorageNodeRef.ROOT_BACKEND_ID,
            displayPath = StorageNodePath.of("/storage/emulated/0/EmptyOptionalFields"),
            canonicalIdentity = dev.qtremors.arcile.core.storage.domain.CanonicalStorageIdentity.of(
                "root:/storage/emulated/0/EmptyOptionalFields"
            )
        )

        val restored = BrowserHistoryEntry.fromSavedValue(
            BrowserHistoryEntry.Directory(reference.displayPath.absolutePath, reference).toSavedValue()
        ) as BrowserHistoryEntry.Directory

        assertEquals(reference.backendId, restored.nodeRef?.backendId)
        assertEquals(reference.canonicalIdentity, restored.nodeRef?.canonicalIdentity)
        assertNull(restored.nodeRef?.backendIdentity)
        assertNull(restored.nodeRef?.volumeId)
    }

    @Test
    fun `legacy directory history remains readable`() {
        assertEquals(
            BrowserHistoryEntry.Directory("/storage/emulated/0/Download"),
            BrowserHistoryEntry.fromSavedValue("dir:/storage/emulated/0/Download")
        )
        assertEquals(
            BrowserHistoryEntry.Directory("/storage/emulated/0/DCIM"),
            BrowserHistoryEntry.fromSavedValue("/storage/emulated/0/DCIM")
        )
    }

    @Test
    fun `malformed encoded history is ignored`() {
        assertNull(BrowserHistoryEntry.fromSavedValue("dir2:not-valid-base64"))
        assertNull(BrowserHistoryEntry.fromSavedValue("dir2:a:b:c:d"))
        assertNull(BrowserHistoryEntry.fromSavedValue("arcile-archive-entry://stale"))
    }

    @Test
    fun `history entry retains active node reference`() {
        val reference = StorageNodeRef.root("/system/etc", "/system/etc")
        val state = BrowserNavigationState().withValues(
            currentPath = "/system/etc",
            currentNodeRef = reference,
            currentVolumeId = null,
            isVolumeRootScreen = false,
            isCategoryScreen = false,
            activeCategoryName = ""
        )

        assertEquals(BrowserHistoryEntry.Directory("/system/etc", reference), state.historyEntry())
    }
}
