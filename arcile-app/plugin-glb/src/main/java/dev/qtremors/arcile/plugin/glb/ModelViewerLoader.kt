package dev.qtremors.arcile.plugin.glb

import android.net.Uri
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.model.ModelInstance
import java.io.File
import java.nio.ByteBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun loadSceneViewModelInstance(
    modelLoader: ModelLoader,
    reference: String
): ModelInstance {
    val uri = runCatching { Uri.parse(reference) }.getOrNull()
    val modelInstance = when (uri?.scheme) {
        "content", "file", "http", "https", "android.resource" ->
            modelLoader.loadModelInstance(reference)
        null, "" -> {
            val file = File(reference)
            if (file.isAbsolute || file.exists()) {
                val bytes = withContext(Dispatchers.IO) { file.readBytes() }
                withContext(Dispatchers.Main) {
                    modelLoader.createModelInstance(ByteBuffer.wrap(bytes))
                }
            } else {
                modelLoader.loadModelInstance(reference)
            }
        }
        else -> modelLoader.loadModelInstance(reference)
    }
    return modelInstance ?: error("Unable to load GLB file")
}
