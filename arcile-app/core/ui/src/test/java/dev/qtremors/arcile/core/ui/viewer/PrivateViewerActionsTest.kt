package dev.qtremors.arcile.core.ui.viewer

import dev.qtremors.arcile.core.storage.domain.StorageNodeCapabilities
import org.junit.Assert.*
import org.junit.Test

class PrivateViewerActionsTest {
    @Test fun `every authorized private action reaches its vault owner exactly once`() {
        val calls = mutableListOf<ViewerFileAction>()
        val actions = PrivateViewerActions(StorageNodeCapabilities(canDelete = true), calls::add)
        assertEquals(ViewerFileAction.entries.toSet(), actions.allowedActions)
        for (action in ViewerFileAction.entries) assertTrue(actions.dispatch(action))
        assertEquals(ViewerFileAction.entries, calls)
    }

    @Test fun `read-only sources expose only their permitted actions and reject stale mutation events`() {
        val calls = mutableListOf<ViewerFileAction>()
        val actions = PrivateViewerActions(StorageNodeCapabilities(
            canWrite = false, canDelete = false, canTrash = false, canRename = false,
            canCopy = false, canMove = false, canArchive = false, canShare = false, canOpenWith = false
        ), calls::add)
        assertEquals(setOf(ViewerFileAction.Properties), actions.allowedActions)
        for (action in ViewerFileAction.entries) assertEquals(action == ViewerFileAction.Properties, actions.dispatch(action))
        assertEquals(listOf(ViewerFileAction.Properties), calls)
    }

    @Test fun `unreadable private sources cannot archive or expose properties`() {
        val calls = mutableListOf<ViewerFileAction>()
        val actions = PrivateViewerActions(StorageNodeCapabilities(canRead = false, canArchive = true), calls::add)
        assertFalse(actions.dispatch(ViewerFileAction.CreateArchive))
        assertFalse(actions.dispatch(ViewerFileAction.Properties))
        assertTrue(calls.isEmpty())
    }

    @Test fun `native Trash permission does not authorize private permanent deletion`() {
        val calls = mutableListOf<ViewerFileAction>()
        val actions = PrivateViewerActions(StorageNodeCapabilities(canDelete = false, canTrash = true), calls::add)
        assertFalse(actions.dispatch(ViewerFileAction.Delete))
        assertTrue(calls.isEmpty())
    }

    @Test fun `a replacement source binding applies its own capabilities`() {
        val originalCalls = mutableListOf<ViewerFileAction>()
        val nextCalls = mutableListOf<ViewerFileAction>()
        val original = PrivateViewerActions(StorageNodeCapabilities(canArchive = true), originalCalls::add)
        val replacement = PrivateViewerActions(StorageNodeCapabilities(canArchive = false), nextCalls::add)
        assertTrue(original.dispatch(ViewerFileAction.CreateArchive))
        assertFalse(replacement.dispatch(ViewerFileAction.CreateArchive))
        assertEquals(listOf(ViewerFileAction.CreateArchive), originalCalls)
        assertTrue(nextCalls.isEmpty())
    }
}
