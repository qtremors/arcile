package dev.qtremors.arcile.core.operation.android.apk

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.os.Bundle
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.qtremors.arcile.plugin.api.PluginContract
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultApkArchiveMetadataReader @Inject constructor(
    @ApplicationContext private val context: Context
) : ApkArchiveMetadataReader {

    private data class CacheKey(
        val canonicalPath: String,
        val size: Long,
        val lastModified: Long
    )

    private val cache = ConcurrentHashMap<CacheKey, ApkArchiveMetadata>()
    private val mutex = Mutex()

    override suspend fun readMetadata(filePath: String): ApkArchiveMetadata? {
        val file = File(filePath)
        if (!file.exists() || !file.isFile) return null

        val canonical = runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)
        val key = CacheKey(canonical, file.length(), file.lastModified())
        cache[key]?.let { return it }

        return mutex.withLock {
            cache[key]?.let { return it }
            val metadata = extractMetadata(file)
            if (metadata != null) {
                cache[key] = metadata
            }
            metadata
        }
    }

    override suspend fun readMetadataFresh(filePath: String): ApkArchiveMetadata? {
        val file = File(filePath)
        if (!file.exists() || !file.isFile) return null
        val metadata = extractMetadata(file) ?: return null
        val canonical = runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)
        cache[CacheKey(canonical, file.length(), file.lastModified())] = metadata
        return metadata
    }

    override suspend fun readMetadataForContentUri(
        contentUri: String,
        displayName: String
    ): ApkArchiveMetadata? {
        var sourceDirectory: File? = null
        return try {
            val (directory, source) = stageContentApkPackage(
                context = context,
                uri = contentUri.toUri(),
                displayName = displayName
            )
            sourceDirectory = directory
            readMetadata(source.absolutePath)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            null
        } finally {
            sourceDirectory?.deleteRecursively()
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun extractMetadata(file: File): ApkArchiveMetadata? {
        val ext = file.extension.lowercase(Locale.ROOT)
        return when {
            ext == "apk" -> extractSingleApkMetadata(file)
            ext in SPLIT_EXTENSIONS -> extractSplitArchiveMetadata(file)
            else -> null
        }
    }

    @Suppress("DEPRECATION")
    private fun extractSingleApkMetadata(file: File): ApkArchiveMetadata? {
        val pm = context.packageManager
        val flags = PackageManager.GET_META_DATA or PackageManager.GET_ACTIVITIES or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES)

        val info = pm.getPackageArchiveInfo(file.absolutePath, flags) ?: return null
        val appInfo = info.applicationInfo ?: return null
        appInfo.sourceDir = file.absolutePath
        appInfo.publicSourceDir = file.absolutePath

        val signatures = extractSignatures(info)
        val signingDigests = signatures.map { sha256Digest(it.toByteArray()) }
        val primaryDigest = signingDigests.firstOrNull()

        val pluginInfo = info.activities.orEmpty()
            .asSequence()
            .map { parsePluginInfo(info.packageName, it.metaData) }
            .firstOrNull(PluginParsedInfo::isPlugin)
            ?: parsePluginInfo(info.packageName, appInfo.metaData)
        val (isPlugin, pluginApiVersion, pluginName, mimeTypes, extensions, homepage) = pluginInfo

        return ApkArchiveMetadata(
            packageName = info.packageName,
            versionName = info.versionName ?: "1.0",
            versionCode = info.longVersionCode,
            minSdkVersion = appInfo.minSdkVersion,
            targetSdkVersion = appInfo.targetSdkVersion,
            isSplitApk = false,
            splitCount = 1,
            signingDigestSha256 = primaryDigest,
            signingDigests = signingDigests,
            isPlugin = isPlugin,
            pluginApiVersion = pluginApiVersion,
            pluginName = pluginName,
            pluginSupportedMimeTypes = mimeTypes,
            pluginSupportedExtensions = extensions,
            pluginHomepage = homepage,
            filePath = file.absolutePath,
            fileSizeBytes = file.length(),
            lastModified = file.lastModified()
        )
    }

    private suspend fun extractSplitArchiveMetadata(archiveFile: File): ApkArchiveMetadata? {
        var staging: ApkStagingResult? = null
        return try {
            staging = stageCompatibleArchiveApks(
                cacheDir = context.cacheDir,
                archiveFile = archiveFile,
                target = ApkPackageParser.ApkArchiveDeviceTarget(
                    supportedAbis = Build.SUPPORTED_ABIS.toList(),
                    densityDpi = context.resources.displayMetrics.densityDpi,
                    language = (context.resources.configuration.locales[0] ?: Locale.getDefault()).language,
                    region = (context.resources.configuration.locales[0] ?: Locale.getDefault()).country
                )
            )
            val baseExtracted = if (staging.apks.size == 1) {
                staging.apks.firstOrNull()
            } else {
                staging.apks.firstOrNull { it.isBase }
            } ?: return null
            val baseMetadata = extractSingleApkMetadata(baseExtracted.file) ?: return null
            val splitMetadata = staging.apks.mapNotNull { extractSingleApkMetadata(it.file) }
            if (splitMetadata.size != staging.apks.size || splitMetadata.any {
                    it.packageName != baseMetadata.packageName ||
                        it.versionCode != baseMetadata.versionCode ||
                        it.signingDigests.toSet() != baseMetadata.signingDigests.toSet()
                }
            ) return null

            baseMetadata.copy(
                isSplitApk = staging.apks.size > 1,
                splitCount = staging.apks.size,
                filePath = archiveFile.absolutePath,
                fileSizeBytes = archiveFile.length(),
                lastModified = archiveFile.lastModified()
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            null
        } finally {
            staging?.directory?.deleteRecursively()
        }
    }

    @Suppress("DEPRECATION")
    private fun extractSignatures(info: PackageInfo): List<Signature> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && info.signingInfo != null) {
            val signingInfo = info.signingInfo!!
            if (signingInfo.hasMultipleSigners()) {
                return signingInfo.apkContentsSigners?.toList().orEmpty()
            }
            return signingInfo.signingCertificateHistory?.toList().orEmpty()
        }
        return info.signatures?.toList().orEmpty()
    }

    private fun parsePluginInfo(
        packageName: String,
        metaData: Bundle?
    ): PluginParsedInfo {
        if (metaData == null) {
            return PluginParsedInfo(isPlugin = false, apiVersion = -1, name = null, mimeTypes = emptySet(), extensions = emptySet(), homepage = null)
        }
        val apiVersion = metadataInt(metaData, PluginContract.METADATA_API_VERSION)
        if (apiVersion < 0) {
            return PluginParsedInfo(isPlugin = false, apiVersion = -1, name = null, mimeTypes = emptySet(), extensions = emptySet(), homepage = null)
        }
        val name = metadataString(packageName, metaData, PluginContract.METADATA_PLUGIN_NAME)?.trim()?.takeIf { it.isNotEmpty() }
        val mimeTypes = parseCsv(metadataString(packageName, metaData, PluginContract.METADATA_SUPPORTED_MIME_TYPES))
        val extensions = parseCsv(metadataString(packageName, metaData, PluginContract.METADATA_SUPPORTED_EXTENSIONS))
            .mapTo(linkedSetOf()) { it.removePrefix(".") }
        val homepage = metadataString(packageName, metaData, PluginContract.METADATA_HOMEPAGE)

        return PluginParsedInfo(
            isPlugin = true,
            apiVersion = apiVersion,
            name = name,
            mimeTypes = mimeTypes,
            extensions = extensions,
            homepage = homepage
        )
    }

    private data class PluginParsedInfo(
        val isPlugin: Boolean,
        val apiVersion: Int,
        val name: String?,
        val mimeTypes: Set<String>,
        val extensions: Set<String>,
        val homepage: String?
    )

    private fun metadataInt(metadata: Bundle, key: String): Int {
        val directValue = metadata.getInt(key, -1)
        return if (directValue >= 0) directValue else metadata.getString(key)?.toIntOrNull() ?: -1
    }

    private fun metadataString(packageName: String, metadata: Bundle, key: String): String? =
        metadata.getString(key) ?: metadata.getInt(key, 0).takeIf { it != 0 }?.let { resourceId ->
            runCatching { context.packageManager.getResourcesForApplication(packageName).getString(resourceId) }.getOrNull()
        }

    private fun parseCsv(value: String?): Set<String> =
        value.orEmpty().split(',').asSequence()
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotEmpty() }
            .toCollection(linkedSetOf())

    companion object {
        private val SPLIT_EXTENSIONS = setOf("apks", "xapk", "apkm")

        internal fun sha256Digest(bytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
