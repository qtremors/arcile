package dev.qtremors.arcile.feature.audio

import android.content.Context
import android.content.Intent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.qtremors.arcile.core.storage.domain.AudioTrack
import dev.qtremors.arcile.core.storage.domain.AudioLibraryRepository
import dev.qtremors.arcile.core.storage.domain.AudioLibraryPreferencesStore
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AudioFeatureEntryPoint {
    const val EXTRA_OPEN_PLAYER = "dev.qtremors.arcile.feature.audio.extra.OPEN_PLAYER"
    private val hostRequested = MutableStateFlow(false)

    fun playInApp(context: Context, track: AudioTrack, queue: List<AudioTrack>, startPlayback: Boolean) {
        hostRequested.value = true
        dependencies(context).coordinator().open(track, queue, startPlayback)
    }

    suspend fun playFilesInApp(context: Context, path: String, contextFiles: List<FileModel>) {
        val selected = contextFiles.firstOrNull { it.absolutePath == path }
            ?: FileModel(
                name = path.substringAfterLast('/'),
                absolutePath = path,
                extension = path.substringAfterLast('.', "").lowercase()
            )
        val immediate = AudioTrack(
            file = selected,
            title = selected.name.substringBeforeLast('.', selected.name)
        )
        playInApp(context, immediate, listOf(immediate), startPlayback = true)
        val dependencies = dependencies(context)
        val indexed = dependencies.repository().getTracks().getOrNull().orEmpty()
            .associateBy { it.file.absolutePath }
        val ordered = contextFiles.map { indexed[it.absolutePath] ?: AudioTrack(
            file = it,
            title = it.name.substringBeforeLast('.', it.name)
        ) }.let { tracks ->
            if (tracks.any { it.file.absolutePath == path }) tracks else tracks + immediate
        }
        dependencies.coordinator().expandQueue(ordered, path)
    }

    fun expandPlayer(context: Context) {
        hostRequested.value = true
        dependencies(context).coordinator().expand()
    }

    @OptIn(ExperimentalSharedTransitionApi::class)
    @Composable
    fun MiniPlayer(sharedTransitionScope: SharedTransitionScope) {
        val context = LocalContext.current
        val requested = hostRequested.collectAsStateWithLifecycle().value
        LaunchedEffect(context.applicationContext) {
            if (!hostRequested.value && withContext(Dispatchers.IO) {
                AudioPlaybackQueueStore(context).hasSavedQueue()
            }) hostRequested.value = true
        }
        if (!requested) return
        val dependencies = remember(context.applicationContext) { dependencies(context) }
        AudioPlayerDock(sharedTransitionScope, dependencies.coordinator(), dependencies.playback())
    }

    @OptIn(ExperimentalSharedTransitionApi::class)
    @Composable
    fun ExpandedPlayer(sharedTransitionScope: SharedTransitionScope) {
        val context = LocalContext.current
        val requested = hostRequested.collectAsStateWithLifecycle().value
        if (!requested) return
        val dependencies = remember(context.applicationContext) { dependencies(context) }
        AudioPlayerExpanded(
            sharedTransitionScope,
            dependencies.coordinator(),
            dependencies.playback(),
            dependencies.preferences(),
            dependencies.tagEditor()
        )
    }

    fun createPlayerIntent(
        context: Context,
        path: String,
        contextPaths: List<String> = emptyList(),
        startPlayback: Boolean = true,
        contentUri: String? = null,
        mimeType: String? = null,
        displayName: String? = null,
        nodeRef: StorageNodeRef? = null
    ): Intent = createAudioPlayerIntent(
        context = context,
        path = path,
        contextPaths = contextPaths,
        startPlayback = startPlayback,
        contentUri = contentUri,
        mimeType = mimeType,
        displayName = displayName,
        nodeRef = nodeRef
    )

    fun createEditorIntent(context: Context, paths: List<String>): Intent =
        createAudioEditorIntent(context, paths)

    fun canResolveStandaloneAudio(context: Context, intent: Intent): Boolean =
        dev.qtremors.arcile.feature.audio.canResolveStandaloneAudio(context, intent)

    private fun dependencies(context: Context): AudioPlayerDependencies =
        EntryPointAccessors.fromApplication(context.applicationContext, AudioPlayerDependencies::class.java)
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface AudioPlayerDependencies {
    fun coordinator(): AudioPlayerCoordinator
    fun playback(): AudioPlaybackController
    fun preferences(): AudioLibraryPreferencesStore
    fun tagEditor(): AudioTagEditor
    fun repository(): AudioLibraryRepository
}
