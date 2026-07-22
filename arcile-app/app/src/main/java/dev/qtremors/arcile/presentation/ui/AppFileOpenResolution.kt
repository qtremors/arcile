package dev.qtremors.arcile.presentation.ui

import dev.qtremors.arcile.core.plugin.android.PluginFileResolution

internal sealed interface AppFileOpenResolution {
    data object Handled : AppFileOpenResolution
    data class PluginPrompt(val prompt: PluginFileResolution) : AppFileOpenResolution
    data class Failed(val error: Throwable) : AppFileOpenResolution
    data class BrowseArchive(val path: String) : AppFileOpenResolution
    data object UnsupportedArchive : AppFileOpenResolution
    data class ViewImage(
        val path: String,
        val contextPaths: List<String>
    ) : AppFileOpenResolution
    data class ViewVideo(
        val path: String,
        val contextPaths: List<String>
    ) : AppFileOpenResolution
    data class External(val path: String) : AppFileOpenResolution
}
