package dev.qtremors.arcile

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import org.robolectric.shadows.ShadowSQLiteConnection

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@Suppress("DEPRECATION")
class RobolectricRuntimeConfigurationTest {
    @Test
    fun `app tests avoid the native SQLite loader`() {
        assertEquals(SQLiteMode.Mode.LEGACY, ShadowSQLiteConnection.sqliteMode())
        assertEquals(
            ArcileRobolectricTestApp::class.java,
            ApplicationProvider.getApplicationContext<ArcileRobolectricTestApp>()::class.java
        )
    }
}
