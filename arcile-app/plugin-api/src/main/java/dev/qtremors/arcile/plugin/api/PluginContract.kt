package dev.qtremors.arcile.plugin.api

object PluginContract {
    const val PLUGIN_API_VERSION = 1

    const val ACTION_REGISTER = "dev.qtremors.arcile.plugin.REGISTER"
    const val ACTION_VIEW_FILE = "dev.qtremors.arcile.plugin.VIEW_FILE"

    const val EXTRA_FILE_URI = "dev.qtremors.arcile.plugin.extra.FILE_URI"
    const val EXTRA_FILE_NAME = "dev.qtremors.arcile.plugin.extra.FILE_NAME"
    const val EXTRA_MIME_TYPE = "dev.qtremors.arcile.plugin.extra.MIME_TYPE"

    const val METADATA_API_VERSION = "dev.qtremors.arcile.plugin.API_VERSION"
    const val METADATA_PLUGIN_NAME = "dev.qtremors.arcile.plugin.NAME"
    const val METADATA_SUPPORTED_MIME_TYPES = "dev.qtremors.arcile.plugin.SUPPORTED_MIME_TYPES"
    const val METADATA_SUPPORTED_EXTENSIONS = "dev.qtremors.arcile.plugin.SUPPORTED_EXTENSIONS"
    const val METADATA_HOMEPAGE = "dev.qtremors.arcile.plugin.HOMEPAGE"

    const val MIME_TYPE_GLB = "model/gltf-binary"
}

enum class PluginCompatibility {
    COMPATIBLE,
    INCOMPATIBLE_API
}

data class PluginMetadata(
    val name: String,
    val packageName: String,
    val activityName: String,
    val versionName: String,
    val versionCode: Long,
    val apiVersion: Int,
    val supportedMimeTypes: Set<String>,
    val supportedExtensions: Set<String>,
    val homepage: String?,
    val compatibility: PluginCompatibility
)
