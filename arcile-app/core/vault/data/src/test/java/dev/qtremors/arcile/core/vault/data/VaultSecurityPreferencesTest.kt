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
        val reloaded = DefaultVaultSecurityPreferences(context, scope)
        val reloadedSettings = withContext(Dispatchers.IO) {
            withTimeout(5_000) { reloaded.settings.first { it.folderViewOptions.isNotEmpty() } }
        }
        assertEquals(viewOptions, reloadedSettings.viewOptions)
        assertEquals(folderOptions, reloadedSettings.folderViewOptions[vaultFolderViewKey(vault, directory)])
        assertFalse(reloadedSettings.screenshotProtectionEnabled)

        scope.cancel()
    }
}
