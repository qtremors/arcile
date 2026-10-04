package dev.qtremors.arcile.feature.home

import dev.qtremors.arcile.core.storage.domain.CategoryStorage
import dev.qtremors.arcile.core.storage.domain.StorageAnalyticsRepository
import dev.qtremors.arcile.core.storage.domain.StorageInfo
import dev.qtremors.arcile.core.storage.domain.StorageKind
import dev.qtremors.arcile.core.storage.domain.StorageScope
import dev.qtremors.arcile.testutil.testVolume
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeDashboardControllerTest {
    @Test
    fun `forced refresh retains displayed volume totals until replacement and on failure`() = runTest {
        val volumes = listOf(testVolume("primary", "/storage/emulated/0"),
            testVolume("sd", "/storage/1234", kind = StorageKind.SD_CARD))
        val old = persistentListOf(CategoryStorage("Images", 100L, emptySet()))
        val fresh = listOf(CategoryStorage("Images", 200L, emptySet()))
        var state = HomeState(storageInfo = StorageInfo(volumes),
            categoryStoragesByVolume = persistentMapOf("primary" to old, "sd" to old))
        val release = CompletableDeferred<Unit>()
        val repository = mockk<StorageAnalyticsRepository>()
        coEvery { repository.getCategoryStorageSizes(any()) } coAnswers {
            release.await()
            if (firstArg<StorageScope>() == StorageScope.Volume("primary")) Result.success(fresh)
            else Result.failure(IllegalStateException("unavailable"))
        }
        val controller = HomeDashboardController(this, repository, { state }) { update ->
            state = state.copy(
                categoryStoragesByVolume = (state.categoryStoragesByVolume +
                    update.categoriesByVolume.mapValues { it.value.toPersistentList() }).toPersistentMap(),
                isCalculatingStorage = update.isCalculating ?: state.isCalculatingStorage
            )
        }
        controller.load(forceRefresh = true)
        runCurrent()
        assertEquals(old, state.categoryStoragesByVolume["primary"])
        assertEquals(old, state.categoryStoragesByVolume["sd"])
        release.complete(Unit)
        advanceUntilIdle()
        assertEquals(fresh, state.categoryStoragesByVolume["primary"])
        assertEquals(old, state.categoryStoragesByVolume["sd"])
        assertFalse(state.isCalculatingStorage)
        coVerify(exactly = 2) { repository.getCategoryStorageSizes(any()) }
        coVerify(exactly = 0) { repository.getCachedCategoryStorageSizes(any()) }
    }
}
