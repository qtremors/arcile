package dev.qtremors.arcile.backup

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.ui.theme.AccentColor
import dev.qtremors.arcile.ui.theme.ThemeMode
import dev.qtremors.arcile.ui.theme.ThemePreferences
import dev.qtremors.arcile.ui.theme.ThemeState
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PreferencesBackupManagerTest {
    private lateinit var context: Context
    private lateinit var manager: PreferencesBackupManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        manager = PreferencesBackupManager(context)
        File(context.filesDir, "datastore").deleteRecursively()
    }

    @Test
    fun `export and restore copies preference datastore files`() = runTest {
        val dataStoreDir = File(context.filesDir, "datastore").apply { mkdirs() }
        val browserPrefs = File(dataStoreDir, "browser_prefs.preferences_pb").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }
        val themePreferences = ThemePreferences(context)
        themePreferences.saveThemeState(
            ThemeState(
                themeMode = ThemeMode.DARK,
                accentColor = AccentColor.GREEN,
                harmonizeColors = false
            )
        )
        val utilityPrefs = File(dataStoreDir, "utility_prefs.preferences_pb")
        val backupFile = File(context.cacheDir, "settings-backup.json").apply { delete() }

        val exportResult = manager.exportTo(Uri.fromFile(backupFile)).getOrThrow()
        browserPrefs.writeBytes(byteArrayOf(9, 9, 9))
        themePreferences.saveThemeState(ThemeState(themeMode = ThemeMode.LIGHT, accentColor = AccentColor.RED))
        utilityPrefs.writeBytes(byteArrayOf(10, 11, 12))

        val preview = manager.preview(Uri.fromFile(backupFile)).getOrThrow()
        val restoreResult = manager.restoreFrom(Uri.fromFile(backupFile)).getOrThrow()

        assertEquals(2, exportResult.successCount)
        assertEquals(8, preview.items.size)
        assertEquals(8, restoreResult.successCount)
        assertTrue(backupFile.readText().contains("\"browser_prefs\""))
        assertEquals(listOf(1, 2, 3, 4), browserPrefs.readBytes().map { it.toInt() })
        assertEquals(ThemeMode.DARK, themePreferences.themeState.first().themeMode)
        assertEquals(AccentColor.GREEN, themePreferences.themeState.first().accentColor)
        assertEquals(false, themePreferences.themeState.first().harmonizeColors)
        assertTrue("stores omitted from the backup should reset to defaults", !utilityPrefs.exists())
    }

    @Test
    fun `restore rejects backups for another package`() = runTest {
        val backupFile = File(context.cacheDir, "wrong-package-backup.json").apply {
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
}
