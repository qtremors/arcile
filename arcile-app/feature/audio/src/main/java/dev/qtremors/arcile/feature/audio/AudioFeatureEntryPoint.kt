package dev.qtremors.arcile.feature.audio

import android.content.Context
import android.content.Intent
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef

object AudioFeatureEntryPoint {
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
}
