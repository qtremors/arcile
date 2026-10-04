package dev.qtremors.arcile.feature.settings

import android.content.Context
import dev.qtremors.arcile.core.plugin.android.PluginManager
import dev.qtremors.arcile.plugin.api.PluginCompatibility
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun queryCompatiblePluginExtensions(context: Context): Set<String> =
    withContext(Dispatchers.IO) {
        PluginManager(context).getInstalledPlugins()
            .filter { it.compatibility == PluginCompatibility.COMPATIBLE }
            .flatMapTo(linkedSetOf()) { it.supportedExtensions }
    }
