package dev.qtremors.arcile.core.presentation

import dev.qtremors.arcile.core.storage.domain.PropertiesAccessStatus
import dev.qtremors.arcile.core.storage.domain.SelectionProperties
import dev.qtremors.arcile.core.storage.domain.SelectionPropertiesRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SelectionPropertiesLoaderTest {
    @Test
    fun `dismiss rejects a late success`() = runTest {
        val deferred = CompletableDeferred<Result<SelectionProperties>>()
        val repository = mockk<SelectionPropertiesRepository>()
        coEvery { repository.getSelectionProperties(listOf("/a")) } coAnswers { deferred.await() }
        var state = SelectionPropertiesUiState()
        val loader = SelectionPropertiesLoader(this, repository, { state = it }, {})

        loader.open(listOf("/a"))
        runCurrent()
        assertTrue(state.isLoading)

        loader.dismiss()
        deferred.complete(Result.success(properties()))
        advanceUntilIdle()

        assertFalse(state.isVisible)
        assertNull(state.properties)
    }

    @Test
    fun `dismiss rejects a late failure without reporting it`() = runTest {
        val deferred = CompletableDeferred<Result<SelectionProperties>>()
        val repository = mockk<SelectionPropertiesRepository>()
        coEvery { repository.getSelectionProperties(listOf("/a")) } coAnswers { deferred.await() }
        var reportedError: Throwable? = null
        val loader = SelectionPropertiesLoader(this, repository, {}, { reportedError = it })

        loader.open(listOf("/a"))
        runCurrent()
        loader.dismiss()
        deferred.complete(Result.failure(IllegalStateException("late")))
        advanceUntilIdle()

        assertNull(reportedError)
    }

    private fun properties() = SelectionProperties(
        displayName = "a",
        pathSummary = "/a",
        itemCount = 1,
        fileCount = 1,
        folderCount = 0,
        totalBytes = 1,
        newestModifiedAt = 1,
        oldestModifiedAt = 1,
        mimeTypeSummary = "image/jpeg",
        extensionSummary = "jpg",
        hiddenCount = 0,
        accessStatus = PropertiesAccessStatus.Full
    )
}
