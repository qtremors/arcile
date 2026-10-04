package dev.qtremors.arcile.backup

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.core.storage.data.filePreferencesDataStore
import dev.qtremors.arcile.core.storage.data.utilityDataStore
import dev.qtremors.arcile.core.ui.theme.AccentColor
import dev.qtremors.arcile.core.ui.theme.ThemeMode
import dev.qtremors.arcile.core.ui.theme.UiPreferencesStore
import dev.qtremors.arcile.core.ui.theme.UiPreferences
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PreferencesBackupManagerTest {
    private lateinit var context: Context
    private lateinit var manager: PreferencesBackupManager
    private lateinit var uiPreferencesStore: UiPreferencesStore
    private val browserValue = stringPreferencesKey("backup_test_browser_value")
    private val utilityValue = stringPreferencesKey("backup_test_utility_value")

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        uiPreferencesStore = UiPreferencesStore(context)
        manager = PreferencesBackupManager(context, uiPreferencesStore)
        runBlocking {
            context.filePreferencesDataStore.updateData { emptyPreferences() }
            context.utilityDataStore.updateData { emptyPreferences() }
            uiPreferencesStore.saveUiPreferences(UiPreferences())
        }
    }

    @Test
    fun `export and restore updates live preference datastores`() = runTest {
        context.filePreferencesDataStore.edit { it[browserValue] = "browser-backup" }
        context.utilityDataStore.edit { it[utilityValue] = "utility-backup" }
        uiPreferencesStore.saveUiPreferences(
            UiPreferences(
                themeMode = ThemeMode.DARK,
                accentColor = AccentColor.GREEN,
                harmonizeColors = false,
                landscapeDualPaneEnabled = true,
                folderIconsEnabled = false
            )
        )
        val backupFile = backupFile("settings-backup.json")

        val exportResult = manager.exportTo(Uri.fromFile(backupFile)).getOrThrow()
        context.filePreferencesDataStore.edit { it[browserValue] = "browser-current" }
        context.utilityDataStore.edit { it[utilityValue] = "utility-current" }
        uiPreferencesStore.saveUiPreferences(
            UiPreferences(themeMode = ThemeMode.LIGHT, accentColor = AccentColor.RED)
        )

        val preview = manager.preview(Uri.fromFile(backupFile)).getOrThrow()
        val restoreResult = manager.restoreFrom(Uri.fromFile(backupFile)).getOrThrow()

        assertTrue(exportResult.successCount >= 3)
        assertEquals(10, preview.items.size)
        assertEquals(10, restoreResult.successCount)
        assertTrue(backupFile.readText().contains("\"decodedSizeBytes\""))
        assertTrue(backupFile.readText().contains("\"sha256\""))
        assertEquals("browser-backup", context.filePreferencesDataStore.data.first()[browserValue])
        assertEquals("utility-backup", context.utilityDataStore.data.first()[utilityValue])
        assertEquals(ThemeMode.DARK, uiPreferencesStore.uiPreferences.first().themeMode)
        assertEquals(AccentColor.GREEN, uiPreferencesStore.uiPreferences.first().accentColor)
        assertEquals(false, uiPreferencesStore.uiPreferences.first().harmonizeColors)
        assertTrue(uiPreferencesStore.uiPreferences.first().landscapeDualPaneEnabled)
        assertFalse(uiPreferencesStore.uiPreferences.first().folderIconsEnabled)
    }

    @Test
    fun `restore rejects backups for another package`() = runTest {
        val backupFile = backupFile("wrong-package-backup.json").apply {
            writeText(
                """
                {
                  "schemaVersion": 1,
                  "createdAtMillis": 1,
                  "packageName": "dev.qtremors.other",
                  "stores": []
                }
                """.trimIndent()
            )
        }

        val result = manager.restoreFrom(Uri.fromFile(backupFile))

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("different app"))
    }

    @Test
    fun `duplicate stores are rejected before settings change`() = runTest {
        context.filePreferencesDataStore.edit { it[browserValue] = "unchanged" }
        val backupFile = backupFile("duplicate-backup.json").apply {
            writeText(
                """
                {
                  "schemaVersion": 1,
                  "createdAtMillis": 1,
                  "packageName": "${context.packageName}",
                  "stores": [
                    {"name":"browser_prefs","encodedBytes":""},
                    {"name":"browser_prefs","encodedBytes":""}
                  ]
                }
                """.trimIndent()
            )
        }

        val result = manager.restoreFrom(Uri.fromFile(backupFile))

        assertTrue(result.isFailure)
        assertEquals("unchanged", context.filePreferencesDataStore.data.first()[browserValue])
    }

    @Test
    fun `unknown stores are rejected before settings change`() = runTest {
        context.filePreferencesDataStore.edit { it[browserValue] = "unchanged" }
        val backupFile = backupFile("unknown-backup.json").apply {
            writeText(
                """
                {
                  "schemaVersion": 1,
                  "createdAtMillis": 1,
                  "packageName": "${context.packageName}",
                  "stores": [
                    {"name":"unknown_store","encodedBytes":""}
                  ]
                }
                """.trimIndent()
            )
        }

        val result = manager.restoreFrom(Uri.fromFile(backupFile))

        assertTrue(result.isFailure)
        assertEquals("unchanged", context.filePreferencesDataStore.data.first()[browserValue])
    }

    @Test
    fun `declared oversized stores are rejected before decoding`() = runTest {
        context.filePreferencesDataStore.edit { it[browserValue] = "unchanged" }
        val backupFile = backupFile("oversized-backup.json").apply {
            writeText(
                """
                {
                  "schemaVersion": 2,
                  "createdAtMillis": 1,
                  "packageName": "${context.packageName}",
                  "stores": [
                    {
                      "name":"browser_prefs",
                      "encodedBytes":"",
                      "decodedSizeBytes":4194305,
                      "sha256":"${"0".repeat(64)}"
                    }
                  ]
                }
                """.trimIndent()
            )
        }

        val result = manager.restoreFrom(Uri.fromFile(backupFile))

        assertTrue(result.isFailure)
        assertEquals("unchanged", context.filePreferencesDataStore.data.first()[browserValue])
    }

    @Test
    fun `integrity failure preserves every current setting`() = runTest {
        context.filePreferencesDataStore.edit { it[browserValue] = "backup" }
        val backupFile = backupFile("corrupt-backup.json")
        manager.exportTo(Uri.fromFile(backupFile)).getOrThrow()
        context.filePreferencesDataStore.edit { it[browserValue] = "current" }
        backupFile.writeText(
            backupFile.readText().replaceFirst(
                Regex("\\\"sha256\\\": \\\"[0-9a-f]+\\\""),
                "\"sha256\": \"${"0".repeat(64)}\""
            )
        )

        val result = manager.restoreFrom(Uri.fromFile(backupFile))

        assertTrue(result.isFailure)
        assertEquals("current", context.filePreferencesDataStore.data.first()[browserValue])
    }

    @Test
    fun `commit failure at every step rolls back stores already restored`() = runTest {
        context.filePreferencesDataStore.edit { it[browserValue] = "backup" }
        context.utilityDataStore.edit { it[utilityValue] = "utility-backup" }
        uiPreferencesStore.saveUiPreferences(UiPreferences(themeMode = ThemeMode.DARK))
        val backupFile = backupFile("rollback-backup.json")
        manager.exportTo(Uri.fromFile(backupFile)).getOrThrow()
        val storeNames = listOf(
            "browser_prefs",
            "browser_tabs",
            "quick_access_prefs",
            "storage_classifications_prefs",
            "onboarding_prefs",
            "theme_prefs",
            "activity_log",
            "storage_cleaner_prefs",
            "utility_prefs",
            "onlyfiles_security"
        )

        storeNames.forEach { failingStore ->
            context.filePreferencesDataStore.edit { it[browserValue] = "current-$failingStore" }
            context.utilityDataStore.edit { it[utilityValue] = "utility-current-$failingStore" }
            uiPreferencesStore.saveUiPreferences(UiPreferences(themeMode = ThemeMode.LIGHT))
            manager.beforeRestoreCommit = { storeName ->
                if (storeName == failingStore) error("Injected failure at $storeName")
            }

            val result = manager.restoreFrom(Uri.fromFile(backupFile))

            assertTrue("Expected failure at $failingStore", result.isFailure)
            assertEquals(
                "current-$failingStore",
                context.filePreferencesDataStore.data.first()[browserValue]
            )
            assertEquals(
                "utility-current-$failingStore",
                context.utilityDataStore.data.first()[utilityValue]
            )
            assertEquals(ThemeMode.LIGHT, uiPreferencesStore.uiPreferences.first().themeMode)
        }
    }

    @Test
    fun `missing stores reset through their live datastore`() = runTest {
        context.filePreferencesDataStore.edit { it[browserValue] = "remove-me" }
        val backupFile = backupFile("reset-backup.json").apply {
            writeText(
                """
                {
                  "schemaVersion": 1,
                  "createdAtMillis": 1,
                  "packageName": "${context.packageName}",
                  "stores": []
                }
                """.trimIndent()
            )
        }

        manager.restoreFrom(Uri.fromFile(backupFile)).getOrThrow()

        assertNull(context.filePreferencesDataStore.data.first()[browserValue])
    }

    private fun backupFile(name: String) = File(context.cacheDir, name).apply { delete() }
}
