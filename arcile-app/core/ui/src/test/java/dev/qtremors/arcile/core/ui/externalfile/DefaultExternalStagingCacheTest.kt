package dev.qtremors.arcile.core.ui.externalfile

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DefaultExternalStagingCacheTest {
    @Test
    fun `stats and clear execute outside feature presentation`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val staging = File(context.cacheDir, "external_access/test").apply {
            deleteRecursively()
            mkdirs()
        }
        File(staging, "one.bin").writeBytes(byteArrayOf(1, 2, 3))
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val cache = DefaultExternalStagingCache(
            context,
            ArcileDispatchers(dispatcher, dispatcher, dispatcher, dispatcher)
        )

        assertEquals(ExternalStagingCacheStats(fileCount = 1, sizeBytes = 3L), cache.stats().getOrThrow())
        assertEquals(ExternalStagingCacheStats(), cache.clear().getOrThrow())
    }
}
