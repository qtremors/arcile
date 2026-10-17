package dev.qtremors.arcile.feature.onlyfiles

import dev.qtremors.arcile.core.storage.domain.SearchFilters
import dev.qtremors.arcile.core.ui.WorkspaceScrollPosition
import dev.qtremors.arcile.core.vault.domain.DirectoryId
import dev.qtremors.arcile.core.vault.domain.NodeId
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultNodeCapabilities
import dev.qtremors.arcile.core.vault.domain.VaultNodeKind
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import dev.qtremors.arcile.core.vault.domain.VaultNodeRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OnlyFilesWorkspaceScrollTest {
    @Test
    fun `each tab has separate directory and search scroll objects`() {
        val workspace = VaultWorkspaceScroll()
        val primary = workspace.forTab(1)
        val other = workspace.forTab(2)
        val root = DirectoryId.Root
        val first = primary.forDirectory(root)
        val second = other.forDirectory(root)
        assertNotSame(first.list, second.list)
        assertNotSame(first.grid, second.grid)
        assertSame(first, primary.forDirectory(root))

        val filters = SearchFilters(extensions = setOf("jpg"))
        val firstSearch = primary.forSearch(root, "query", filters, true)
        val secondSearch = other.forSearch(root, "query", filters, true)
        assertNotSame(firstSearch, secondSearch)
        assertSame(firstSearch, primary.forSearch(root, "query", filters, true))
        assertNotSame(firstSearch, primary.forSearch(root, "different", filters, true))
        assertSame(first, primary.forDirectory(root))
    }

    @Test
    fun `closing a tab releases its scroll state while retaining open tabs`() {
        val workspace = VaultWorkspaceScroll()
        val closed = workspace.forTab(2)
        val retained = workspace.forTab(1)
        workspace.retain(listOf(VaultWorkspaceTab(1)))
        assertSame(retained, workspace.forTab(1))
        assertNotSame(closed, workspace.forTab(2))
    }

    @Test
    fun `a viewport prefers the stable file identity over its old index and can request another page`() {
        val viewport = VaultViewport(index = 5, offset = 42, nodeId = "anchor", grid = false)
        val firstPage = listOf(node("first"), node("second"))
        assertNull(viewport.restoreIndex(firstPage, canLoadMore = true))
        val changed = firstPage + node("anchor")
        assertEquals(2, viewport.restoreIndex(changed, canLoadMore = true))
        assertEquals(1, viewport.restoreIndex(firstPage, canLoadMore = false))
        assertEquals(0, viewport.restoreIndex(emptyList(), canLoadMore = false))
    }

    @Test
    fun `the shared strip collapses on downward scroll and returns when scrolling back`() {
        val top = WorkspaceScrollPosition(false, 0, 0)
        val down = WorkspaceScrollPosition(true, 3, 12)
        val farther = down.copy(offset = 25)
        assertFalse(down.tabsVisible(top, visible = true))
        assertFalse(farther.tabsVisible(down, visible = true))
        assertTrue(down.tabsVisible(farther, visible = false))
        assertTrue(top.tabsVisible(down, visible = false))
        assertTrue(down.copy(isScrolling = false).tabsVisible(top, visible = true))
    }

    private fun node(id: String) = VaultNodeMetadata(
        VaultNodeRef(VaultId.of("vault"), NodeId.of(id), DirectoryId.Root, VaultNodeCapabilities()),
        id, VaultNodeKind.FILE, 0L, 0L, 1L
    )
}
