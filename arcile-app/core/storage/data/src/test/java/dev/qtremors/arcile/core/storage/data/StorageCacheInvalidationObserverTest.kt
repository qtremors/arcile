package dev.qtremors.arcile.core.storage.data

import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import dev.qtremors.arcile.core.storage.data.db.StorageNodeDao
import dev.qtremors.arcile.core.storage.data.provider.VolumeProvider
import dev.qtremors.arcile.core.storage.data.source.StorageQueryClient
import dev.qtremors.arcile.core.storage.domain.StorageMutationNotifier
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StorageCacheInvalidationObserverTest {
    @Test
    fun `registration preserves snapshots and real broad changes mark stats stale`() = runTest {
        val context = mockk<Context>()
        val resolver = mockk<ContentResolver>(relaxed = true)
        every { context.contentResolver } returns resolver
        val callback = slot<ContentObserver>()
        every { resolver.registerContentObserver(any(), any(), capture(callback)) } just Runs
        val media = mockk<StorageQueryClient>(relaxed = true)
        val folders = mockk<FolderStatsStore>(relaxed = true)
        val nodes = mockk<StorageNodeDao>(relaxed = true)
        val observer = StorageCacheInvalidationObserver(
            context, this, media, mockk<VolumeProvider>(relaxed = true), nodes, folders,
            mockk<StorageUsageSnapshotStore>(relaxed = true),
            mockk<StorageCleanerSnapshotStore>(relaxed = true),
            mockk<StorageMutationNotifier>(relaxed = true)
        )

        observer.register()
        advanceUntilIdle()
        coVerify(exactly = 0) { folders.clear() }
        coVerify(exactly = 0) { folders.invalidateAll() }
        coVerify(exactly = 0) { media.invalidateCache() }

        callback.captured.onChange(false)
        advanceUntilIdle()
        coVerify(exactly = 1) { folders.invalidateAll() }
        coVerify(exactly = 0) { folders.clear() }
        coVerify(exactly = 1) { media.invalidateCache() }
    }
}
