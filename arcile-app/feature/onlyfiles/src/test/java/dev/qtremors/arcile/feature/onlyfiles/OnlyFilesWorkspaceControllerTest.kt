package dev.qtremors.arcile.feature.onlyfiles

import dev.qtremors.arcile.core.storage.domain.SearchFilters
import dev.qtremors.arcile.core.vault.domain.DirectoryId
import dev.qtremors.arcile.core.vault.domain.VAULT_MAX_WORKSPACE_TABS
import dev.qtremors.arcile.core.vault.domain.VaultDirectoryLocation
import dev.qtremors.arcile.core.vault.domain.VaultFailure
import dev.qtremors.arcile.core.vault.domain.VaultFileSystem
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultPath
import dev.qtremors.arcile.core.vault.domain.VaultSecurityPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnlyFilesWorkspaceControllerTest {
    @Test
    fun `switching tabs restores independent searches and current authenticated folder names`() = runTest {
        val fixture = Fixture(this)
        val photos = DirectoryId.of("photos")
        val reports = DirectoryId.of("reports")
        val scope = DirectoryId.of("scope")
        fixture.trail(photos, "Renamed Photos")
        fixture.trail(reports, "Reports")
        fixture.trail(scope, "Renamed scope")
        val filters = SearchFilters(extensions = setOf("jpg"))
        fixture.state.value = fixture.state.value.copy(
            directoryStack = crumbs(photos, "Old Photos"),
            searchQuery = "private query",
            searchScope = crumbs(scope, "Old scope"),
            searchFilters = filters,
            selectedNodeIds = setOf("selected")
        )

        fixture.controller.addTab()
        advanceUntilIdle()
        val second = fixture.workspace.activeTabId
        assertEquals(DirectoryId.Root, fixture.state.value.currentDirectory?.id)
        assertEquals("", fixture.state.value.searchQuery)
        assertTrue(fixture.state.value.selectedNodeIds.isEmpty())
        fixture.state.value = fixture.state.value.copy(directoryStack = crumbs(reports, "Reports"), searchQuery = "PDF")

        fixture.controller.selectTab(PRIMARY_VAULT_TAB_ID)
        advanceUntilIdle()
        assertEquals("Renamed Photos", fixture.state.value.currentDirectory?.name)
        assertEquals("private query", fixture.state.value.searchQuery)
        assertEquals(filters, fixture.state.value.searchFilters)
        assertEquals("Renamed scope", fixture.state.value.searchScope?.last()?.name)

        fixture.controller.selectTab(second)
        advanceUntilIdle()
        assertEquals(reports, fixture.state.value.currentDirectory?.id)
        assertEquals("PDF", fixture.state.value.searchQuery)
        assertNull(fixture.state.value.searchScope)
        assertEquals(3, fixture.reloads)
        coVerify { fixture.preferences.setLastDirectory(fixture.vault, reports) }
    }

    @Test
    fun `missing folder falls back to a surviving parent and stale search scope is cleared`() = runTest {
        val fixture = Fixture(this)
        val parent = DirectoryId.of("parent")
        val child = DirectoryId.of("child")
        val gone = DirectoryId.of("gone")
        fixture.trail(parent, "Parent")
        fixture.missing(child)
        fixture.missing(gone)
        fixture.state.value = fixture.state.value.copy(
            directoryStack = crumbs(parent, "Parent") + VaultDirectoryCrumb(child, "Child", VaultPath.of("Parent/Child")),
            searchScope = crumbs(gone, "Gone"),
            searchQuery = "query"
        )
        fixture.controller.addTab()
        advanceUntilIdle()

        fixture.controller.selectTab(PRIMARY_VAULT_TAB_ID)
        advanceUntilIdle()

        assertEquals(parent, fixture.state.value.currentDirectory?.id)
        assertEquals("query", fixture.state.value.searchQuery)
        assertNull(fixture.state.value.searchScope)
        assertTrue(fixture.errors.isEmpty())
    }

    @Test
    fun `pins restore in order with duplicate locations and prune confirmed missing folders`() = runTest {
        val fixture = Fixture(this, restoreEmpty = false)
        val first = DirectoryId.of("first")
        val missing = DirectoryId.of("missing")
        val second = DirectoryId.of("second")
        fixture.trail(first, "First")
        fixture.trail(second, "Second")
        fixture.missing(missing)

        fixture.controller.restorePins(listOf(first, missing, second, first))
        advanceUntilIdle()

        assertTrue(fixture.workspace.pinsLoaded)
        assertEquals(listOf(first, second, first), fixture.workspace.tabs.filter { it.pinned }.map { it.location.directory.id })
        assertEquals(4, fixture.workspace.tabs.map { it.id }.distinct().size)
        coVerify(exactly = 1) { fixture.preferences.setPinnedWorkspaceTabs(fixture.vault, listOf(first, second, first)) }

        val middlePin = fixture.workspace.tabs[2].id
        fixture.controller.moveTab(middlePin, -1)
        advanceUntilIdle()
        coVerify(exactly = 1) { fixture.preferences.setPinnedWorkspaceTabs(fixture.vault, listOf(second, first, first)) }
        fixture.controller.closeTab(middlePin)
        assertTrue(fixture.workspace.tabs.any { it.id == middlePin })
        fixture.controller.setPinned(middlePin, pinned = false)
        fixture.controller.closeTab(middlePin)
        assertFalse(fixture.workspace.tabs.any { it.id == middlePin })
    }

    @Test
    fun `duplicate opens the same folder with a fresh search and closing it returns to its neighbor`() = runTest {
        val fixture = Fixture(this)
        val folder = DirectoryId.of("folder")
        fixture.trail(folder, "Folder")
        fixture.state.value = fixture.state.value.copy(directoryStack = crumbs(folder, "Folder"), searchQuery = "private")
        fixture.controller.duplicateTab(PRIMARY_VAULT_TAB_ID)
        advanceUntilIdle()
        val duplicate = fixture.workspace.activeTabId
        assertEquals(folder, fixture.state.value.currentDirectory?.id)
        assertEquals("", fixture.state.value.searchQuery)

        fixture.controller.closeTab(duplicate)
        advanceUntilIdle()
        assertEquals(listOf(PRIMARY_VAULT_TAB_ID), fixture.workspace.tabs.map { it.id })
        assertEquals("private", fixture.state.value.searchQuery)
    }

    @Test
    fun `locking clears pending tab activation and late authenticated paths cannot return`() = runTest {
        val fixture = Fixture(this, restoreEmpty = false)
        val privateFolder = DirectoryId.of("private")
        fixture.trail(privateFolder, "Private")
        fixture.controller.restorePins(listOf(privateFolder))
        advanceUntilIdle()
        val pinned = fixture.workspace.tabs.last().id
        val pending = CompletableDeferred<Result<List<VaultDirectoryLocation>>>()
        coEvery { fixture.fs.directoryTrail(fixture.vault, privateFolder) } coAnswers { pending.await() }
        fixture.controller.selectTab(pinned)
        runCurrent()
        assertTrue(fixture.workspace.switching)

        fixture.controller.clear()
        fixture.state.value = OnlyFilesUiState(mountGeneration = 2L)
        pending.complete(Result.success(locations(privateFolder, "Late private name")))
        advanceUntilIdle()

        assertNull(fixture.state.value.selectedVaultId)
        assertTrue(fixture.workspace.tabs.isEmpty())
        assertTrue(fixture.state.value.directoryStack.isEmpty())
        assertEquals(0, fixture.reloads)
    }

    @Test
    fun `cancelled tab switch leaves the previous location active`() = runTest {
        val fixture = Fixture(this, restoreEmpty = false)
        val target = DirectoryId.of("target")
        fixture.trail(target, "Target")
        fixture.controller.restorePins(listOf(target))
        advanceUntilIdle()
        val pending = CompletableDeferred<Result<List<VaultDirectoryLocation>>>()
        coEvery { fixture.fs.directoryTrail(fixture.vault, target) } coAnswers { pending.await() }
        fixture.controller.selectTab(fixture.workspace.tabs.last().id)
        runCurrent()
        fixture.controller.cancelSwitch()
        pending.complete(Result.success(locations(target, "Late target")))
        advanceUntilIdle()

        assertEquals(PRIMARY_VAULT_TAB_ID, fixture.workspace.activeTabId)
        assertEquals(DirectoryId.Root, fixture.state.value.currentDirectory?.id)
        assertFalse(fixture.workspace.switching)
        assertEquals(1, fixture.reloads)
    }

    @Test
    fun `a transient search scope failure keeps the current tab active`() = runTest {
        val fixture = Fixture(this)
        val scope = DirectoryId.of("scope")
        fixture.state.value = fixture.state.value.copy(searchScope = crumbs(scope, "Scope"), searchQuery = "query")
        fixture.controller.addTab()
        advanceUntilIdle()
        val current = fixture.workspace.activeTabId
        val failure = VaultFailure.Unavailable("Storage unavailable")
        coEvery { fixture.fs.directoryTrail(fixture.vault, scope) } returns Result.failure(failure)

        fixture.controller.selectTab(PRIMARY_VAULT_TAB_ID)
        advanceUntilIdle()

        assertEquals(current, fixture.workspace.activeTabId)
        assertFalse(fixture.workspace.switching)
        assertEquals("", fixture.state.value.searchQuery)
        assertEquals(listOf(failure), fixture.errors)
    }

    @Test
    fun `the tab limit and close others preserve the primary and pinned tabs`() = runTest {
        val fixture = Fixture(this)
        repeat(VAULT_MAX_WORKSPACE_TABS + 2) {
            fixture.controller.addTab()
            advanceUntilIdle()
        }
        assertEquals(VAULT_MAX_WORKSPACE_TABS, fixture.workspace.tabs.size)
        assertEquals(VAULT_MAX_WORKSPACE_TABS - 1, fixture.reloads)
        val pinned = fixture.workspace.tabs[1].id
        fixture.controller.setPinned(pinned, true)
        fixture.controller.closeOtherTabs(PRIMARY_VAULT_TAB_ID)
        advanceUntilIdle()
        assertEquals(listOf(PRIMARY_VAULT_TAB_ID, pinned), fixture.workspace.tabs.map { it.id })
        assertEquals(PRIMARY_VAULT_TAB_ID, fixture.workspace.activeTabId)
    }

    private class Fixture(scope: CoroutineScope, restoreEmpty: Boolean = true) {
        val vault = VaultId.of("vault")
        val fs = mockk<VaultFileSystem>()
        val preferences = mockk<VaultSecurityPreferences>(relaxed = true)
        val state = MutableStateFlow(OnlyFilesUiState(
            selectedVaultId = vault, mountGeneration = 1L, directoryStack = VaultWorkspaceLocation().stack,
            workspaceTabsEnabled = true, workspace = VaultWorkspace.mounted()
        ))
        val workspace get() = state.value.workspace
        val errors = mutableListOf<Throwable>()
        var reloads = 0
        val controller = OnlyFilesWorkspaceController(fs, preferences, state, scope, {}, { reloads++ }, { errors += it })
        init { if (restoreEmpty) controller.restorePins(emptyList()) }

        fun trail(id: DirectoryId, name: String) {
            coEvery { fs.directoryTrail(vault, id) } returns Result.success(locations(id, name))
        }

        fun missing(id: DirectoryId) {
            coEvery { fs.directoryTrail(vault, id) } returns Result.failure(VaultFailure.InvalidPath("Folder unavailable"))
        }
    }

    private companion object {
        fun locations(id: DirectoryId, name: String) = listOf(
            VaultDirectoryLocation(DirectoryId.Root, ""), VaultDirectoryLocation(id, name)
        )
        fun crumbs(id: DirectoryId, name: String) = locations(id, name).toCrumbs()
    }
}
