package dev.qtremors.arcile.plugins

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import dev.qtremors.arcile.plugin.api.PluginCompatibility
import dev.qtremors.arcile.plugin.api.PluginContract
import dev.qtremors.arcile.plugin.api.PluginMetadata
import dev.qtremors.arcile.presentation.utils.ExternalFileAccessHelper

class PluginManager(private val context: Context) {
    private val packageManager: PackageManager = context.packageManager

    fun getInstalledPlugins(): List<PluginMetadata> {
        val registerIntent = Intent(PluginContract.ACTION_REGISTER).addCategory(Intent.CATEGORY_DEFAULT)
        val flags = PackageManager.GET_META_DATA.toLong()
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(registerIntent, PackageManager.ResolveInfoFlags.of(flags))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(registerIntent, PackageManager.GET_META_DATA)
        }

        return resolved.asSequence()
            .mapNotNull { info ->
                val activity = info.activityInfo ?: return@mapNotNull null
                if (!activity.exported || !activity.enabled || !activity.applicationInfo.enabled) return@mapNotNull null
                if (packageManager.checkSignatures(context.packageName, activity.packageName) != PackageManager.SIGNATURE_MATCH) {
                    return@mapNotNull null
                }
                val metadata = activity.metaData ?: return@mapNotNull null
                val apiVersion = when (val value = metadata.get(PluginContract.METADATA_API_VERSION)) {
                    is Int -> value
                    is String -> value.toIntOrNull() ?: -1
                    else -> -1
                }
                val name = metadataString(activity.packageName, metadata.get(PluginContract.METADATA_PLUGIN_NAME))
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: return@mapNotNull null
                val mimeTypes = parseCsv(
                    metadataString(activity.packageName, metadata.get(PluginContract.METADATA_SUPPORTED_MIME_TYPES))
                )
                val extensions = parseCsv(
                    metadataString(activity.packageName, metadata.get(PluginContract.METADATA_SUPPORTED_EXTENSIONS))
                ).mapTo(linkedSetOf()) { it.removePrefix(".") }
                if (apiVersion < 0 || (mimeTypes.isEmpty() && extensions.isEmpty())) return@mapNotNull null

                val packageInfo = runCatching { packageManager.getPackageInfo(activity.packageName, 0) }.getOrNull()
                    ?: return@mapNotNull null
                PluginMetadata(
                    name = name,
                    packageName = activity.packageName,
                    activityName = activity.name,
                    versionName = packageInfo.versionName.orEmpty(),
                    versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        packageInfo.longVersionCode
                    } else {
                        @Suppress("DEPRECATION")
                        packageInfo.versionCode.toLong()
                    },
                    apiVersion = apiVersion,
                    supportedMimeTypes = mimeTypes,
                    supportedExtensions = extensions,
                    homepage = metadataString(activity.packageName, metadata.get(PluginContract.METADATA_HOMEPAGE)),
                    compatibility = if (apiVersion == PluginContract.PLUGIN_API_VERSION) {
                        PluginCompatibility.COMPATIBLE
                    } else {
                        PluginCompatibility.INCOMPATIBLE_API
                    }
                )
            }
            .groupBy { it.packageName }
            .mapNotNull { (_, plugins) -> plugins.maxWithOrNull(pluginVersionComparator) }
            .sortedBy { it.name.lowercase() }
    }

    fun findPluginForMimeType(mimeType: String?): PluginMetadata? =
        rankedCandidates(mimeType, null).firstOrNull()

    fun findPluginForExtension(extension: String?): PluginMetadata? =
        rankedCandidates(null, normalizeExtension(extension)).firstOrNull()

    fun findPlugin(mimeType: String?, extension: String?): PluginMetadata? =
        rankedCandidates(mimeType, normalizeExtension(extension)).firstOrNull()

    suspend fun launchPlugin(plugin: PluginMetadata, path: String): PluginLaunchResult {
        if (plugin.compatibility != PluginCompatibility.COMPATIBLE) {
            return PluginLaunchResult.Incompatible(plugin)
        }
        return runCatching {
            val baseIntent = ExternalFileAccessHelper.createOpenIntent(context, path)
            val displayName = path.substringAfterLast('/').substringAfterLast('\\').ifBlank { "File" }
            val launchIntent = buildPluginLaunchIntent(plugin, baseIntent, displayName)
            context.startActivity(launchIntent)
            PluginLaunchResult.Launched
        }.getOrElse(PluginLaunchResult::Failed)
    }

    private fun rankedCandidates(mimeType: String?, extension: String?): List<PluginMetadata> {
        val normalizedMime = mimeType?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        return rankPlugins(getInstalledPlugins(), normalizedMime, extension)
    }

    companion object {
        const val RELEASES_URL = "https://github.com/qtremors/arcile/releases"

        internal fun rankPlugins(
            plugins: List<PluginMetadata>,
            normalizedMime: String?,
            extension: String?
        ): List<PluginMetadata> = plugins.mapNotNull { plugin ->
            val rank = when {
                normalizedMime != null && normalizedMime in plugin.supportedMimeTypes -> 0
                extension != null && extension in plugin.supportedExtensions -> 1
                normalizedMime != null && plugin.supportedMimeTypes.any { wildcardMatches(it, normalizedMime) } -> 2
                else -> return@mapNotNull null
            }
            rank to plugin
        }.sortedWith(
            compareBy<Pair<Int, PluginMetadata>> { it.first }
                .thenByDescending { it.second.versionCode }
                .thenBy { it.second.packageName }
        ).map { it.second }

        val catalog = listOf(
            PluginCatalogEntry(
                name = "GLB Viewer",
                packageName = "dev.qtremors.arcile.plugin.glb",
                supportedMimeTypes = setOf(PluginContract.MIME_TYPE_GLB),
                supportedExtensions = setOf("glb"),
                available = true
            ),
            PluginCatalogEntry(
                name = "STL Viewer",
                packageName = "dev.qtremors.arcile.plugin.stl",
                supportedMimeTypes = setOf("model/stl"),
                supportedExtensions = setOf("stl"),
                available = false
            ),
            PluginCatalogEntry(
                name = "TIFF Viewer",
                packageName = "dev.qtremors.arcile.plugin.tiff",
                supportedMimeTypes = setOf("image/tiff"),
                supportedExtensions = setOf("tif", "tiff"),
                available = false
            )
        )

        fun parseCsv(value: String?): Set<String> =
            value.orEmpty().split(',').asSequence()
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }
                .toCollection(linkedSetOf())

        fun normalizeExtension(extension: String?): String? =
            extension?.trim()?.lowercase()?.removePrefix(".")?.takeIf { it.isNotEmpty() }

        fun wildcardMatches(pattern: String, mimeType: String): Boolean =
            pattern.endsWith("/*") && mimeType.startsWith(pattern.substringBefore('/') + "/")

        private val pluginVersionComparator =
            compareBy<PluginMetadata> { it.versionCode }.thenBy { it.activityName }
    }

    private fun metadataString(packageName: String, value: Any?): String? = when (value) {
        is String -> value
        is Int -> runCatching { packageManager.getResourcesForApplication(packageName).getString(value) }.getOrNull()
        else -> null
    }
}

internal fun buildPluginLaunchIntent(
    plugin: PluginMetadata,
    baseIntent: Intent,
    displayName: String
): Intent {
    val uri = requireNotNull(baseIntent.data) { "Missing file URI" }
    val mimeType = baseIntent.type ?: "*/*"
    return Intent(baseIntent).apply {
        action = PluginContract.ACTION_VIEW_FILE
        component = ComponentName(plugin.packageName, plugin.activityName)
        putExtra(PluginContract.EXTRA_FILE_URI, uri)
        putExtra(PluginContract.EXTRA_FILE_NAME, displayName)
        putExtra(PluginContract.EXTRA_MIME_TYPE, mimeType)
        clipData = ClipData.newRawUri(displayName, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

data class PluginCatalogEntry(
    val name: String,
    val packageName: String,
    val supportedMimeTypes: Set<String>,
    val supportedExtensions: Set<String>,
    val available: Boolean
) {
    fun matches(mimeType: String?, extension: String?): Boolean {
        val normalizedMime = mimeType?.lowercase()
        val normalizedExtension = PluginManager.normalizeExtension(extension)
        return normalizedMime in supportedMimeTypes || normalizedExtension in supportedExtensions
    }

    fun matchesPackage(installedPackage: String): Boolean =
        installedPackage == packageName || installedPackage == "$packageName.debug"
}

sealed interface PluginLaunchResult {
    data object Launched : PluginLaunchResult
    data class Incompatible(val plugin: PluginMetadata) : PluginLaunchResult
    data class Failed(val error: Throwable) : PluginLaunchResult
}

sealed interface PluginFileResolution {
    data object NotApplicable : PluginFileResolution
    data object Launched : PluginFileResolution
    data class Missing(val catalogEntry: PluginCatalogEntry) : PluginFileResolution
    data class Incompatible(val plugin: PluginMetadata) : PluginFileResolution
    data class Failed(val error: Throwable) : PluginFileResolution
}
