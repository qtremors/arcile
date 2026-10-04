package dev.qtremors.arcile.feature.audio

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dev.qtremors.arcile.core.operation.BulkFileOperationCoordinator
import dev.qtremors.arcile.core.storage.domain.AudioLibraryPreferences
import dev.qtremors.arcile.core.storage.domain.AudioLibraryPreferencesStore
import dev.qtremors.arcile.core.storage.domain.AudioLibraryRepository
import dev.qtremors.arcile.core.storage.domain.AudioTrack
import dev.qtremors.arcile.core.storage.domain.CategoryLibraryPage
import dev.qtremors.arcile.core.storage.domain.ClipboardOperation
import dev.qtremors.arcile.core.storage.domain.ClipboardRepository
import dev.qtremors.arcile.core.storage.domain.ClipboardState
import dev.qtremors.arcile.core.storage.domain.FileListingPreferences
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AudioLibraryClipboardTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    @Test
    fun `copy selected song clears selection after presentation publication`() = runBlocking {
        assertStoresSelection(ClipboardOperation.COPY)
    }

    @Test
    fun `cut selected song clears selection after presentation publication`() = runBlocking {
        assertStoresSelection(ClipboardOperation.CUT)
    }

    private suspend fun assertStoresSelection(operation: ClipboardOperation) {
        val track = AudioTrack(
            FileModel(name = "song.mp3", reference = "/Music/song.mp3", size = 1L,
                lastModified = 1L, extension = "mp3", mimeType = "audio/mpeg"),
            "Song"
        )
        val music = mockk<AudioCollectionStore> {
            every { musicOnly() } returns false
            every { playlists } returns MutableStateFlow(emptyList())
            every { mediaChanges } returns MutableSharedFlow()
            every { defaultSection(any()) } answers { firstArg() }
            every { presentation(any()) } returns FileListingPreferences()
        }
        val listening = mockk<AudioListeningStore> {
            every { favoritePaths } returns MutableStateFlow(emptySet())
            every { trackRecords } returns MutableStateFlow(emptyList())
        }
        val observer = mockk<AudioMediaObserver> {
            every { changes } returns MutableSharedFlow()
        }
        val repository = mockk<AudioLibraryRepository> {
            coEvery { getTracks(any()) } returns Result.success(listOf(track))
        }
        val preferences = mockk<AudioLibraryPreferencesStore> {
            every { audioLibraryPreferencesFlow } returns MutableStateFlow(AudioLibraryPreferences())
        }
        val clipboardFlow = MutableStateFlow<ClipboardState?>(null)
        val clipboard = mockk<ClipboardRepository> {
            every { clipboardState } returns clipboardFlow
            every { setClipboardState(any()) } answers { clipboardFlow.value = firstArg() }
        }
        val operations = mockk<BulkFileOperationCoordinator> {
            every { activeRequest } returns MutableStateFlow(null)
            every { events } returns MutableSharedFlow()
        }
        val model = AudioLibraryViewModel(
            observer, repository, music, listening, mockk(relaxed = true), preferences,
            clipboard, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), operations, mockk(relaxed = true), SavedStateHandle()
        )
        try {
            withTimeout(5_000) {
                model.state.first { !it.isLoading && it.tracks.size == 1 }
            }
            model.toggleSelection(track.file.reference)

            val stored = if (operation == ClipboardOperation.COPY) model.copySelection()
                else model.cutSelection()
            assertEquals(1, stored)
            assertTrue(model.state.value.selectedPaths.isEmpty())
            withTimeout(5_000) { model.state.first { it.tab == CategoryLibraryPage.FOLDERS } }

            assertEquals(operation, clipboardFlow.value?.operation)
            assertEquals(listOf(track.file), clipboardFlow.value?.files)
            assertTrue(model.state.value.selectedPaths.isEmpty())
        } finally {
            model.viewModelScope.cancel()
        }
    }
}
