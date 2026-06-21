package dev.qtremors.arcile.core.storage.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
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
class ArcileDatabaseMigrationTest {
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        resetSingleton()
        context.deleteDatabase(DATABASE_NAME)
        restoredCacheMarker().delete()
    }

    @After
    fun teardown() {
        resetSingleton()
        context.deleteDatabase(DATABASE_NAME)
        restoredCacheMarker().delete()
    }

    @Test
    fun `version 1 cache database is explicitly reset when opened as version 2`() = runTest {
        restoredCacheMarker().apply {
            parentFile?.mkdirs()
            writeText("1")
        }
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(DATABASE_NAME), null).use { db ->
            db.version = 1
            db.execSQL(
                """
                CREATE TABLE folder_stats (
                    path TEXT NOT NULL PRIMARY KEY,
                    file_count INTEGER NOT NULL,
                    total_bytes INTEGER NOT NULL,
                    cached_at INTEGER NOT NULL,
                    status TEXT NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                "INSERT INTO folder_stats(path, file_count, total_bytes, cached_at, status) VALUES('/old', 1, 2, 3, 'Ready')"
            )
        }

        val database = ArcileDatabase.getInstance(context)

        assertEquals(2, database.openHelper.readableDatabase.version)
        assertEquals(0, database.folderStatsDao().count())
    }

    @Test
    fun `version 2 room schema is checked in`() {
        val moduleSchema = File(
            "schemas/dev.qtremors.arcile.core.storage.data.db.ArcileDatabase/2.json"
        )
        val rootSchema = File(
            "core/storage/data/schemas/dev.qtremors.arcile.core.storage.data.db.ArcileDatabase/2.json"
        )

        assertTrue(
            "Room schema JSON for ArcileDatabase version 2 must be committed",
            moduleSchema.exists() || rootSchema.exists()
        )
    }

    private fun restoredCacheMarker(): File =
        File(context.noBackupFilesDir, "arcile-cache-restored-state-invalidated")

    private fun resetSingleton() {
        val field = ArcileDatabase::class.java.getDeclaredField("instance")
        field.isAccessible = true
        (field.get(null) as? ArcileDatabase)?.close()
        field.set(null, null)
    }

    private companion object {
        const val DATABASE_NAME = "arcile-cache.db"
    }
}
