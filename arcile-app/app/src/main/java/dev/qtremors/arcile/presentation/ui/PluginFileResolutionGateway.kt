package dev.qtremors.arcile.presentation.ui

import dev.qtremors.arcile.core.plugin.android.PluginFileResolution
import dev.qtremors.arcile.core.plugin.android.PluginLaunchResult
import dev.qtremors.arcile.core.plugin.android.PluginManager

internal fun interface PluginFileResolutionGateway {
    suspend fun resolve(
        path: String,
        mimeType: String?,
        extension: String
    ): PluginFileResolution
}

internal class InstalledPluginFileResolutionGateway(
    private val pluginManager: PluginManager
) : PluginFileResolutionGateway {
    override suspend fun resolve(
        path: String,
        mimeType: String?,
        extension: String
    ): PluginFileResolution {
        val installedPlugin = pluginManager.findPlugin(mimeType, extension)
        if (installedPlugin != null) {
            return when (val result = pluginManager.launchPlugin(installedPlugin, path)) {
                PluginLaunchResult.Launched -> PluginFileResolution.Launched
                is PluginLaunchResult.Incompatible -> {
                    PluginFileResolution.Incompatible(result.plugin)
                }
                is PluginLaunchResult.Failed -> PluginFileResolution.Failed(result.error)
            }
        }
        return PluginManager.catalog
            .firstOrNull { it.available && it.matches(mimeType, extension) }
            ?.let(PluginFileResolution::Missing)
            ?: PluginFileResolution.NotApplicable
    }
}
