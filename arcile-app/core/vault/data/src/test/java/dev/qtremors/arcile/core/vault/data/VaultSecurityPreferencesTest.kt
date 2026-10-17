package dev.qtremors.arcile.core.vault.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.core.vault.domain.DirectoryId
import dev.qtremors.arcile.core.vault.domain.VaultFolderViewOptions
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.vaultFolderViewKey
import dev.qtremors.arcile.core.vault.domain.VaultSortDirection
import dev.qtremors.arcile.core.vault.domain.VaultSortField
import dev.qtremors.arcile.core.vault.domain.VaultViewOptions
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class VaultSecurityPreferencesTest {
    @Test
    fun `screenshot protection defaults on and persists an explicit change`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        File(context.filesDir, "datastore/onlyfiles_security.preferences_pb").delete()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val preferences = DefaultVaultSecurityPreferences(context, scope)

        runCurrent()
        assertTrue(preferences.settings.value.screenshotProtectionEnabled)
        preferences.setScreenshotProtectionEnabled(false)
        runCurrent()
        assertFalse(preferences.settings.value.screenshotProtectionEnabled)
        val viewOptions = VaultViewOptions(
            grid = true,
            sortField = VaultSortField.SIZE,
            sortDirection = VaultSortDirection.DESCENDING,
            recursiveSearch = true,
            showThumbnails = false,
            listZoom = 1.15f,
            gridMinCellSize = 160f,
            showHiddenFiles = false
        )
        preferences.setViewOptions(viewOptions)
        val vault = VaultId.of("vault")
        val directory = DirectoryId.of("folder")
        val folderOptions = VaultFolderViewOptions(
            VaultViewOptions(sortField = VaultSortField.FILE_COUNT, foldersFirst = false),
            applyToSubfolders = true
        )
        preferences.setFolderViewOptions(vault, directory, folderOptions)
        preferences.setLastDirectory(vault, directory)
        preferences.setDirectoryPinned(vault, directory, pinned = true)
        val reloaded = DefaultVaultSecurityPreferences(context, scope)
        val reloadedSettings = withContext(Dispatchers.IO) {
            withTimeout(5_000) { reloaded.settings.first { it.lastDirectories[vault] == directory && it.pinnedDirectories.isNotEmpty() } }
        }
        assertEquals(viewOptions, reloadedSettings.viewOptions)
        assertEquals(folderOptions, reloadedSettings.folderViewOptions[vaultFolderViewKey(vault, directory)])
        assertFalse(reloadedSettings.screenshotProtectionEnabled)
        assertEquals(setOf(directory), reloadedSettings.pinnedDirectories[vault])
        assertEquals(directory, reloadedSettings.lastDirectories[vault])
        reloaded.resetFolderViewOptions(vault, directory)
        reloaded.setDirectoryPinned(vault, directory, pinned = false)
        val reset = withContext(Dispatchers.IO) {
            withTimeout(5_000) { reloaded.settings.first { it.folderViewOptions.isEmpty() && it.pinnedDirectories[vault].isNullOrEmpty() } }
        }
        assertEquals(directory, reset.lastDirectories[vault])

        scope.cancel()
    }

    @Test
    fun `workspace settings store ordered opaque IDs independently for each vault`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        val preferences = DefaultVaultSecurityPreferences(context, scope)
        val first = VaultId.of("workspace-first")
        val second = VaultId.of("workspace-second")
        val location = DirectoryId.of("opaque,id")
        val ordered = listOf(location, DirectoryId.Root, location)
        try {
            preferences.setWorkspaceTabsEnabled(true)
            preferences.setPinnedWorkspaceTabs(first, ordered)
            preferences.setPinnedWorkspaceTabs(second, listOf(DirectoryId.Root))
            val reloaded = DefaultVaultSecurityPreferences(context, scope)
            val restored = withContext(Dispatchers.IO) {
                withTimeout(5_000) { reloaded.settings.first { first in it.pinnedWorkspaceTabs && second in it.pinnedWorkspaceTabs } }
            }
            assertTrue(restored.workspaceTabsEnabled)
            assertEquals(ordered, restored.pinnedWorkspaceTabs[first])
            assertEquals(listOf(DirectoryId.Root), restored.pinnedWorkspaceTabs[second])
            assertFalse(first in restored.pinnedDirectories)

            preferences.setWorkspaceTabsEnabled(false)
            preferences.setPinnedWorkspaceTabs(first, emptyList())
            val hidden = withContext(Dispatchers.IO) {
                withTimeout(5_000) { reloaded.settings.first { !it.workspaceTabsEnabled && second in it.pinnedWorkspaceTabs && first !in it.pinnedWorkspaceTabs } }
            }
            assertEquals(listOf(DirectoryId.Root), hidden.pinnedWorkspaceTabs[second])
        } finally {
            scope.cancel()
        }
    }
}
