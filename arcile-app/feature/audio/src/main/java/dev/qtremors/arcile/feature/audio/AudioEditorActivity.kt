package dev.qtremors.arcile.feature.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.qtremors.arcile.core.ui.theme.ArcileTheme
import dev.qtremors.arcile.core.ui.theme.UiPreferencesStore
import dev.qtremors.arcile.core.ui.theme.UiPreferences

@AndroidEntryPoint
class AudioEditorActivity : ComponentActivity() {
    private val viewModel by viewModels<AudioEditorViewModel>()
    private val uiPreferencesStore by lazy { UiPreferencesStore(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        viewModel.load(intent.getStringArrayListExtra(EXTRA_AUDIO_EDIT_PATHS).orEmpty())
        setContent {
            val uiPreferences by uiPreferencesStore.uiPreferences.collectAsStateWithLifecycle(
                initialValue = UiPreferences()
            )
            ArcileTheme(uiPreferences = uiPreferences) {
                val state by viewModel.state.collectAsStateWithLifecycle()
                AudioEditorScreen(
                    state = state,
                    onBack = {
                        if (state.isExporting) viewModel.cancelExport()
                        finish()
                    },
                    onExport = viewModel::export,
                    onCancelExport = viewModel::cancelExport,
                    onPreview = viewModel::previewSelection,
                    onTogglePreview = viewModel::togglePreview,
                    onStopPreview = viewModel::stopPreview,
                    onSeekPreview = viewModel::seekPreview,
                    onLoopChanged = viewModel::setLoopEnabled,
                    onAddAudio = viewModel::addAudioUris,
                    onRemoveAudio = viewModel::removeSource,
                    onSourceOrderChanged = viewModel::reorderSources
                )
            }
        }
    }
}

internal fun createAudioEditorIntent(context: Context, paths: List<String>): Intent =
    Intent(context, AudioEditorActivity::class.java).apply {
        putStringArrayListExtra(EXTRA_AUDIO_EDIT_PATHS, ArrayList(paths))
    }

private const val EXTRA_AUDIO_EDIT_PATHS =
    "dev.qtremors.arcile.feature.audio.extra.EDIT_PATHS"
