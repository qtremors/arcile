package dev.qtremors.arcile.feature.audio

import android.content.ClipData
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.core.net.toUri
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import dev.qtremors.arcile.core.ui.showArcileToast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dev.qtremors.arcile.core.storage.domain.AudioLibraryRepository
import dev.qtremors.arcile.core.storage.domain.AudioTrack
import dev.qtremors.arcile.core.storage.domain.FileCategories
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import dev.qtremors.arcile.core.storage.domain.StorageScope
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import dev.qtremors.arcile.core.ui.ExternalViewerLoadScreen
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.externalfile.ExternalFileAccessHelper
import dev.qtremors.arcile.core.ui.externalfile.resolveExternalContentMetadata
import dev.qtremors.arcile.core.ui.theme.ArcileTheme
import dev.qtremors.arcile.core.ui.theme.ThemePreferences
import dev.qtremors.arcile.core.ui.theme.ThemeState
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class AudioPlayerActivity : ComponentActivity() {

    @Inject
    internal lateinit var playback: AudioPlaybackController

    @Inject
    internal lateinit var repository: AudioLibraryRepository

    @Inject
    internal lateinit var themePreferences: ThemePreferences

    @Inject
    internal lateinit var dispatchers: ArcileDispatchers

    private var queue by mutableStateOf<List<AudioTrack>>(emptyList())
    private var initialPath by mutableStateOf<String?>(null)
    private var playerLaunchId by mutableIntStateOf(0)
    private var miniPlayerBottomClearanceDp = 0
    private var queueLoadJob: Job? = null
    private var loadError by mutableStateOf<String?>(null)
    private val closePlayerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_CLOSE_AUDIO_PLAYER) finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ContextCompat.registerReceiver(
            this,
            closePlayerReceiver,
            IntentFilter(ACTION_CLOSE_AUDIO_PLAYER),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setPlayerWindowExpanded(false)
        setContent {
            val themeState by themePreferences.themeState.collectAsStateWithLifecycle(
                initialValue = ThemeState()
            )
            ArcileTheme(themeState = themeState) {
                val playbackState by playback.state.collectAsStateWithLifecycle()
                val current = queue.firstOrNull {
                    it.file.absolutePath == playbackState.currentMediaId
                } ?: queue.firstOrNull { it.file.absolutePath == initialPath }
                if (current == null) {
                    ExternalViewerLoadScreen(loadError) { openIntent(intent) }
                } else {
                    StandaloneAudioPlayer(
                        track = current,
                        queue = queue,
                        playbackState = playbackState,
                        playbackController = playback,
                        launchId = playerLaunchId,
                        miniPlayerBottomClearanceDp = miniPlayerBottomClearanceDp,
                        onWindowModeChange = ::setPlayerWindowExpanded,
                        onFinish = ::finish,
                        onShare = { share(it.file) },
                        onEdit = { openEditor(it.file) },
                        onOpenWith = { openWith(it.file) }
                    )
                }
            }
        }
        openIntent(intent)
    }

    override fun onDestroy() {
        unregisterReceiver(closePlayerReceiver)
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openIntent(intent)
        setPlayerWindowExpanded(false)
    }

    private fun setPlayerWindowExpanded(expanded: Boolean) {
        val layoutParams = window.attributes.apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = if (expanded) {
                WindowManager.LayoutParams.MATCH_PARENT
            } else {
                WindowManager.LayoutParams.WRAP_CONTENT
            }
            gravity = if (expanded) Gravity.FILL else Gravity.BOTTOM
            y = if (expanded) {
                0
            } else {
                (miniPlayerBottomClearanceDp * resources.displayMetrics.density).toInt()
            }
            flags = audioPlayerWindowFlags(flags, expanded)
        }
        window.attributes = layoutParams
    }

    private fun openIntent(intent: Intent) {
        queueLoadJob?.cancel()
        playerLaunchId += 1
        queue = emptyList()
        initialPath = null
        loadError = null
        miniPlayerBottomClearanceDp = intent.getIntExtra(EXTRA_BOTTOM_CLEARANCE_DP, 0)
        queueLoadJob = lifecycleScope.launch {
            val target = resolveStandaloneAudioTarget(
                this@AudioPlayerActivity,
                intent,
                dispatchers.io
            )
            if (target == null) {
                loadError = getString(R.string.error_unsupported_provider)
                return@launch
            }
            val immediateTrack = target.toBasicAudioTrack()
            queue = listOf(immediateTrack)
            initialPath = target.reference
            if (intent.getBooleanExtra(EXTRA_START_PLAYBACK, true)) {
                playback.playQueue(queue, target.reference)
            }
            val tracks = buildQueue(
                target,
                intent.getStringArrayListExtra(EXTRA_QUEUE_PATHS).orEmpty()
            )
            if (tracks.isEmpty()) {
                return@launch
            }
            queue = tracks
            if (intent.getBooleanExtra(EXTRA_START_PLAYBACK, true)) {
                playback.expandQueue(tracks, target.reference)
            }
        }
    }

    private suspend fun buildQueue(
        target: StandaloneAudioTarget,
        contextPaths: List<String>
    ): List<AudioTrack> = withContext(dispatchers.io) {
        if (target.uri.scheme == "content" && target.internalPath == null) {
            return@withContext listOf(
                target.toAudioTrack(this@AudioPlayerActivity)
            )
        }
        val indexed = repository.getTracks(StorageScope.AllStorage).getOrNull().orEmpty()
        val queue = if (contextPaths.isEmpty()) {
            indexed.filter {
                File(it.file.absolutePath).parent == File(target.reference).parent
            }
        } else {
            orderAudioTracksByContext(indexed, contextPaths)
        }
        val withTarget = if (queue.any { it.file.absolutePath == target.reference }) {
            queue
        } else {
            queue + indexed.firstOrNull { it.file.absolutePath == target.reference }
                .orFallback(target.toAudioTrack(this@AudioPlayerActivity))
        }
        withTarget.distinctBy { it.file.absolutePath }
    }

    private fun share(file: FileModel) {
        lifecycleScope.launch {
            val reference = runCatching { file.toHandoffReference() }
                .getOrElse { return@launch showFailure() }
            val target = ExternalFileAccessHelper.createShareTargets(
                this@AudioPlayerActivity,
                listOf(reference)
            ).singleOrNull() ?: return@launch showFailure()
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = target.mimeType
                putExtra(Intent.EXTRA_STREAM, target.uri)
                clipData = ClipData.newUri(contentResolver, target.displayName, target.uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching {
                startActivity(Intent.createChooser(shareIntent, target.displayName))
            }.onFailure { showFailure() }
        }
    }

    private fun openWith(file: FileModel) {
        lifecycleScope.launch {
            runCatching {
                val openIntent = ExternalFileAccessHelper.createOpenIntent(
                    this@AudioPlayerActivity,
                    file.toHandoffReference()
                )
                startActivity(Intent.createChooser(openIntent, file.name))
            }.onFailure { showFailure() }
        }
    }

    private fun openEditor(file: FileModel) {
        if (!File(file.absolutePath).isFile) {
            showFailure()
            return
        }
        startActivity(createAudioEditorIntent(this, listOf(file.absolutePath)))
    }

    private fun FileModel.toHandoffReference() =
        ExternalFileAccessHelper.ExternalFileReference(
            path = absolutePath,
            displayName = name,
            sizeBytes = size,
            mimeType = mimeType,
            nodeRef = nodeRef
        )

    private fun showFailure() {
        showArcileToast(getString(R.string.cannot_open_file, ""))
    }
}

internal fun orderAudioTracksByContext(
    indexedTracks: List<AudioTrack>,
    contextPaths: List<String>
): List<AudioTrack> {
    val indexedByPath = indexedTracks.associateBy { it.file.absolutePath }
    return contextPaths.mapNotNull(indexedByPath::get)
        .distinctBy { it.file.absolutePath }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@androidx.compose.runtime.Composable
private fun StandaloneAudioPlayer(
    track: AudioTrack,
    queue: List<AudioTrack>,
    playbackState: AudioPlaybackState,
    playbackController: AudioPlaybackController,
    launchId: Int,
    miniPlayerBottomClearanceDp: Int,
    onWindowModeChange: (Boolean) -> Unit,
    onFinish: () -> Unit,
    onShare: (AudioTrack) -> Unit,
    onEdit: (AudioTrack) -> Unit,
    onOpenWith: (AudioTrack) -> Unit
) {
    var presentation by remember(launchId) {
        mutableStateOf(AudioPlayerPresentation.MINI)
    }
    val expanded = presentation == AudioPlayerPresentation.EXPANDED
    val playerTransition = updateTransition(
        targetState = expanded,
        label = "audio-player-expansion"
    )
    val usesFullWindow = presentation != AudioPlayerPresentation.MINI
    val backdropAlpha by playerTransition.animateFloat(
        transitionSpec = { tween(durationMillis = 220) },
        label = "audio-player-backdrop"
    ) { isExpanded ->
        if (isExpanded) 1f else 0f
    }

    LaunchedEffect(
        presentation,
        playerTransition.currentState,
        playerTransition.targetState
    ) {
        if (
            presentation == AudioPlayerPresentation.COLLAPSING &&
            !playerTransition.currentState &&
            !playerTransition.targetState
        ) {
            presentation = AudioPlayerPresentation.MINI
            onWindowModeChange(false)
        }
    }
    BackHandler {
        if (presentation != AudioPlayerPresentation.MINI) {
            presentation = AudioPlayerPresentation.COLLAPSING
        }
    }
    SharedTransitionLayout {
        Box(
            modifier = if (usesFullWindow) {
                Modifier
                    .fillMaxSize()
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = backdropAlpha)
                    )
            } else {
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            }
        ) {
            playerTransition.AnimatedContent(
                modifier = if (usesFullWindow) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                },
                contentAlignment = Alignment.BottomCenter,
                transitionSpec = {
                    EnterTransition.None togetherWith ExitTransition.None
                },
                contentKey = { it }
            ) { isExpanded ->
                val visibilityScope = this
                if (!isExpanded) {
                    Box(
                        modifier = if (usesFullWindow) {
                            Modifier
                                .fillMaxSize()
                                .navigationBarsPadding()
                                .padding(horizontal = 12.dp, vertical = 12.dp)
                                .padding(bottom = miniPlayerBottomClearanceDp.dp)
                        } else {
                            Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 12.dp, vertical = 12.dp)
                        }.onGloballyPositioned { coordinates ->
                            val parentHeight = coordinates.parentCoordinates?.size?.height
                                ?: return@onGloballyPositioned
                            if (
                                shouldStartAudioPlayerExpansion(
                                    presentation = presentation,
                                    parentHeightPx = parentHeight,
                                    miniPlayerHeightPx = coordinates.size.height
                                )
                            ) {
                                presentation = AudioPlayerPresentation.EXPANDED
                            }
                        },
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        AudioMiniPlayer(
                            track = track,
                            playback = playbackState,
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedVisibilityScope = visibilityScope,
                            onExpand = {
                                if (presentation == AudioPlayerPresentation.MINI) {
                                    presentation =
                                        AudioPlayerPresentation.PREPARING_EXPANSION
                                    onWindowModeChange(true)
                                }
                            },
                            onDismiss = onFinish,
                            onTogglePlayback = playbackController::togglePlayback,
                            onNext = playbackController::seekToNext
                        )
                    }
                } else {
                    AudioNowPlayingScreen(
                        track = track,
                        queue = queue,
                        playback = playbackState,
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = visibilityScope,
                        onCollapse = {
                            presentation = AudioPlayerPresentation.COLLAPSING
                        },
                        onTogglePlayback = playbackController::togglePlayback,
                        onPrevious = playbackController::seekToPrevious,
                        onNext = playbackController::seekToNext,
                        onQueueTrack = playbackController::seekToQueueIndex,
                        onToggleRepeat = playbackController::toggleRepeatMode,
                        onToggleShuffle = playbackController::toggleShuffle,
                        onSeek = playbackController::seekTo,
                        onShare = { onShare(track) },
                        onEdit = { onEdit(track) },
                        onOpenWith = { onOpenWith(track) }
                    )
                }
            }
        }
    }
}

internal enum class AudioPlayerPresentation {
    MINI,
    PREPARING_EXPANSION,
    EXPANDED,
    COLLAPSING
}

internal fun shouldStartAudioPlayerExpansion(
    presentation: AudioPlayerPresentation,
    parentHeightPx: Int,
    miniPlayerHeightPx: Int
): Boolean =
    presentation == AudioPlayerPresentation.PREPARING_EXPANSION &&
        parentHeightPx > miniPlayerHeightPx

internal fun audioPlayerWindowFlags(currentFlags: Int, expanded: Boolean): Int {
    val miniFlags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
    return if (expanded) currentFlags and miniFlags.inv() else currentFlags or miniFlags
}

internal data class StandaloneAudioTarget(
    val reference: String,
    val uri: Uri,
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long?,
    val internalPath: String?,
    val nodeRef: StorageNodeRef? = null
)

internal fun createAudioPlayerIntent(
    context: Context,
    path: String,
    contextPaths: List<String> = emptyList(),
    startPlayback: Boolean = true,
    contentUri: String? = null,
    mimeType: String? = null,
    displayName: String? = null,
    nodeRef: StorageNodeRef? = null
): Intent = Intent(context, AudioPlayerActivity::class.java).apply {
    action = Intent.ACTION_VIEW
    if (path.isNotBlank()) {
        putExtra(EXTRA_INTERNAL_PATH, path)
    }
    if (!contentUri.isNullOrBlank()) {
        setDataAndType(contentUri.toUri(), mimeType ?: "audio/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        putExtra(EXTRA_DISPLAY_NAME, displayName)
    }

    putStringArrayListExtra(EXTRA_QUEUE_PATHS, ArrayList(contextPaths))
    putExtra(EXTRA_START_PLAYBACK, startPlayback)
    putExtra(EXTRA_BOTTOM_CLEARANCE_DP, IN_APP_PLAYER_BOTTOM_CLEARANCE_DP)
}

internal fun canResolveStandaloneAudio(context: Context, intent: Intent): Boolean =
    intent.action == Intent.ACTION_VIEW && intent.data != null && (
        intent.type?.startsWith("audio/") == true ||
            intent.data?.lastPathSegment?.substringAfterLast('.', "")?.lowercase() in FileCategories.Audio.extensions
        )

private suspend fun resolveStandaloneAudioTarget(
    context: Context,
    intent: Intent,
    ioDispatcher: kotlinx.coroutines.CoroutineDispatcher
): StandaloneAudioTarget? {
    if (intent.action != Intent.ACTION_VIEW) return null
    intent.getStringExtra(EXTRA_INTERNAL_PATH)?.takeIf(String::isNotBlank)?.let { path ->
        val internalTarget = withContext(ioDispatcher) {
            val file = File(path)
            if (
                !file.isFile ||
                !ExternalFileAccessHelper.isAllowedUserFile(context, file) ||
                file.extension.lowercase() !in FileCategories.Audio.extensions
            ) {
                null
            } else {
                StandaloneAudioTarget(
                    reference = file.absolutePath,
                    uri = Uri.fromFile(file),
                    displayName = file.name,
                    mimeType = "audio/${file.extension.lowercase()}",
                    sizeBytes = file.length(),
                    internalPath = file.absolutePath
                )
            }
        }
        if (internalTarget != null) return internalTarget
    }

    val uri = intent.data ?: return null
    val extension = uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase().orEmpty()
    return when (uri.scheme) {
        "content" -> {
            val metadata = resolveExternalContentMetadata(
                context.contentResolver, uri, intent.type, ioDispatcher
            ).getOrNull() ?: return null
            if (metadata.mimeType?.startsWith("audio/") != true && extension !in FileCategories.Audio.extensions) return null
            StandaloneAudioTarget(
                reference = uri.toString(), uri = uri,
                displayName = intent.getStringExtra(EXTRA_DISPLAY_NAME)
                    ?: metadata.displayName
                    ?: uri.lastPathSegment
                    ?: "Audio",
                mimeType = metadata.mimeType,
                sizeBytes = metadata.sizeBytes,
                internalPath = null
            )
        }
        "file", null -> withContext(ioDispatcher) {
            val file = File(uri.path.orEmpty())
            if (!file.isFile || !ExternalFileAccessHelper.isAllowedUserFile(context, file)) return@withContext null
            val mimeType = intent.type
            if (mimeType?.startsWith("audio/") != true && extension !in FileCategories.Audio.extensions) return@withContext null
            StandaloneAudioTarget(
                file.absolutePath,
                Uri.fromFile(file),
                file.name,
                mimeType,
                file.length(),
                file.absolutePath
            )
        }
        else -> null
    }
}

private fun StandaloneAudioTarget.toBasicAudioTrack(): AudioTrack {
    val extension = displayName.substringAfterLast('.', "").lowercase()
    val nodeRef = nodeRef ?: if (uri.scheme == "content") {
        StorageNodeRef.mediaStore(
            id = uri.toString().hashCode().toLong(),
            volumeName = null,
            contentUri = uri.toString(),
            displayPath = "/external/$displayName"
        )
    } else {
        StorageNodeRef.local(reference)
    }
    return AudioTrack(
        file = FileModel(
            name = displayName,
            absolutePath = reference,
            size = sizeBytes ?: 0L,
            extension = extension,
            mimeType = mimeType,
            nodeRef = nodeRef
        ),
        title = displayName.substringBeforeLast('.', displayName)
    )
}

private fun StandaloneAudioTarget.toAudioTrack(context: Context): AudioTrack {
    val metadata = android.media.MediaMetadataRetriever()
    val values = runCatching {
        if (uri.scheme == "content") metadata.setDataSource(context, uri)
        else metadata.setDataSource(reference)
        AudioMetadataValues(
            title = metadata.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE),
            artist = metadata.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST),
            album = metadata.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM),
            duration = metadata.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
        )
    }.getOrDefault(AudioMetadataValues())
    metadata.release()
    return toBasicAudioTrack().copy(
        title = values.title ?: displayName.substringBeforeLast('.', displayName),
        artist = values.artist,
        album = values.album,
        durationMs = values.duration
    )
}

private data class AudioMetadataValues(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val duration: Long = 0L
)

private fun AudioTrack?.orFallback(fallback: AudioTrack): AudioTrack = this ?: fallback

private const val EXTRA_INTERNAL_PATH =
    "dev.qtremors.arcile.feature.audio.extra.INTERNAL_PATH"
private const val EXTRA_QUEUE_PATHS =
    "dev.qtremors.arcile.feature.audio.extra.QUEUE_PATHS"
private const val EXTRA_START_PLAYBACK =
    "dev.qtremors.arcile.feature.audio.extra.START_PLAYBACK"
private const val EXTRA_BOTTOM_CLEARANCE_DP =
    "dev.qtremors.arcile.feature.audio.extra.BOTTOM_CLEARANCE_DP"
private const val EXTRA_DISPLAY_NAME =
    "dev.qtremors.arcile.feature.audio.extra.DISPLAY_NAME"
private const val IN_APP_PLAYER_BOTTOM_CLEARANCE_DP = 80
internal const val ACTION_CLOSE_AUDIO_PLAYER =
    "dev.qtremors.arcile.feature.audio.action.CLOSE_PLAYER"
