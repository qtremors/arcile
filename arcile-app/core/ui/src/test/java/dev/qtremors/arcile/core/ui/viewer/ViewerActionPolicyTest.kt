package dev.qtremors.arcile.core.ui.viewer

import dev.qtremors.arcile.core.storage.domain.StorageNodeCapabilities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewerActionPolicyTest {

    @Test
    fun `normal scope with full capabilities enables all actions`() {
        val caps = StorageNodeCapabilities(
            canRead = true,
            canWrite = true,
            canDelete = true,
            canTrash = true,
            canArchive = true
        )
        val actions = ViewerActionPolicy.resolveAllowedActions(ViewerSourceScope.Normal, caps)

        assertTrue(ViewerFileAction.Rename in actions)
        assertTrue(ViewerFileAction.Copy in actions)
        assertTrue(ViewerFileAction.Cut in actions)
        assertTrue(ViewerFileAction.Delete in actions)
        assertTrue(ViewerFileAction.Share in actions)
        assertTrue(ViewerFileAction.OpenWith in actions)
        assertTrue(ViewerFileAction.CreateArchive in actions)
        assertTrue(ViewerFileAction.Properties in actions)
    }

    @Test
    fun `archive entry scope only enables read-only actions`() {
        val caps = StorageNodeCapabilities(
            canRead = true,
            canWrite = true,
            canDelete = true,
            canTrash = true,
            canArchive = true
        )
        val actions = ViewerActionPolicy.resolveAllowedActions(ViewerSourceScope.ArchiveEntry, caps)

        assertFalse(ViewerFileAction.Rename in actions)
        assertFalse(ViewerFileAction.Copy in actions)
        assertFalse(ViewerFileAction.Cut in actions)
        assertFalse(ViewerFileAction.Delete in actions)
        assertFalse(ViewerFileAction.CreateArchive in actions)
        assertTrue(ViewerFileAction.Share in actions)
        assertTrue(ViewerFileAction.OpenWith in actions)
        assertTrue(ViewerFileAction.Properties in actions)
    }

    @Test
    fun `external standalone scope only enables non-mutating actions`() {
        val caps = StorageNodeCapabilities(
            canRead = true,
            canWrite = true,
            canDelete = true,
            canTrash = true,
            canArchive = true
        )
        val actions = ViewerActionPolicy.resolveAllowedActions(ViewerSourceScope.External, caps)

        assertFalse(ViewerFileAction.Rename in actions)
        assertFalse(ViewerFileAction.Copy in actions)
        assertFalse(ViewerFileAction.Cut in actions)
        assertFalse(ViewerFileAction.Delete in actions)
        assertFalse(ViewerFileAction.CreateArchive in actions)
        assertTrue(ViewerFileAction.Share in actions)
        assertTrue(ViewerFileAction.OpenWith in actions)
        assertTrue(ViewerFileAction.Properties in actions)
    }

    @Test
    fun `managed trash scope blocks mutation but retains safe external actions`() {
        val caps = StorageNodeCapabilities(
            canRead = true,
            canWrite = false,
            canDelete = true,
            canTrash = false,
            canArchive = false
        )
        val actions = ViewerActionPolicy.resolveAllowedActions(ViewerSourceScope.ManagedTrash, caps)

        assertFalse(ViewerFileAction.Rename in actions)
        assertFalse(ViewerFileAction.Copy in actions)
        assertFalse(ViewerFileAction.Cut in actions)
        assertTrue(ViewerFileAction.Delete in actions)
        assertTrue(ViewerFileAction.Share in actions)
        assertTrue(ViewerFileAction.OpenWith in actions)
        assertFalse(ViewerFileAction.CreateArchive in actions)
        assertTrue(ViewerFileAction.Properties in actions)
    }

    @Test
    fun `vault scope exposes only capability backed vault actions`() {
        val actions = ViewerActionPolicy.resolveAllowedActions(
            ViewerSourceScope.Vault,
            StorageNodeCapabilities(
                canRead = true,
                canWrite = true,
                canDelete = true,
                canTrash = false,
                canArchive = false
            )
        )

        assertTrue(ViewerFileAction.Rename in actions)
        assertTrue(ViewerFileAction.Copy in actions)
        assertTrue(ViewerFileAction.Cut in actions)
        assertTrue(ViewerFileAction.Delete in actions)
        assertFalse(ViewerFileAction.CreateArchive in actions)
    }

    @Test
    fun `normal scope never grants actions absent from node capabilities`() {
        val actions = ViewerActionPolicy.resolveAllowedActions(
            ViewerSourceScope.Normal,
            StorageNodeCapabilities(
                canRead = false,
                canWrite = false,
                canDelete = false,
                canTrash = false,
                canArchive = false,
                canRename = false,
                canCopy = false,
                canMove = false,
                canShare = false,
                canOpenWith = false
            )
        )

        assertTrue(actions.isEmpty())
    }
}
