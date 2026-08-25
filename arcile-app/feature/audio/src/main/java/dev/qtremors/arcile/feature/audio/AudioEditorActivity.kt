package dev.qtremors.arcile.feature.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.qtremors.arcile.core.ui.theme.ArcileTheme
import dev.qtremors.arcile.core.ui.theme.ThemePreferences
import dev.qtremors.arcile.core.ui.theme.ThemeState

class AudioEditorActivity : ComponentActivity() {
    private val viewModel by viewModels<AudioEditorViewModel>()
    private val themePreferences by lazy { ThemePreferences(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        viewModel.load(intent.getStringArrayListExtra(EXTRA_AUDIO_EDIT_PATHS).orEmpty())
        setContent {
            val themeState by themePreferences.themeState.collectAsStateWithLifecycle(
                initialValue = ThemeState()
            )
            ArcileTheme(themeState = themeState) {
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

fun createAudioEditorIntent(context: Context, paths: List<String>): Intent =
    Intent(context, AudioEditorActivity::class.java).apply {
        putStringArrayListExtra(EXTRA_AUDIO_EDIT_PATHS, ArrayList(paths))
    }

private const val EXTRA_AUDIO_EDIT_PATHS =
    "dev.qtremors.arcile.feature.audio.extra.EDIT_PATHS"
