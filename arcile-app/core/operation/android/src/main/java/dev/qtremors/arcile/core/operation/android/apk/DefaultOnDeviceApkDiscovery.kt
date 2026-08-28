package dev.qtremors.arcile.core.operation.android.apk

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.qtremors.arcile.core.plugin.android.PluginManager
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import dev.qtremors.arcile.core.storage.domain.FileCategories
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.SearchRepository
import dev.qtremors.arcile.core.storage.domain.StorageScope
import dev.qtremors.arcile.plugin.api.PluginContract
import dev.qtremors.arcile.plugin.api.PluginMetadata
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultOnDeviceApkDiscovery @Inject constructor(
    @ApplicationContext private val context: Context,
    private val searchRepository: SearchRepository,
    private val apkMetadataReader: ApkArchiveMetadataReader,
    private val dispatchers: ArcileDispatchers
) : OnDeviceApkDiscovery {

    private val pluginManager by lazy { PluginManager(context) }
    private val parsingSemaphore = Semaphore(2)

    override suspend fun discoverPluginUpdates(scope: StorageScope): List<ApkUpdateCandidate> =
        withContext(dispatchers.io) {
            val installedPlugins = pluginManager.getInstalledPlugins()
            if (installedPlugins.isEmpty()) return@withContext emptyList()

            val apkFiles = searchRepository.getFilesByCategory(scope, FileCategories.APKs.displayName)
                .getOrDefault(emptyList())
            if (apkFiles.isEmpty()) return@withContext emptyList()

            val appSignatures = getInstalledSignatures(context.packageName)

            val parsedMetadataList = apkFiles.map { fileModel ->
                async {
                    parsingSemaphore.withPermit {
                        apkMetadataReader.readMetadata(fileModel.absolutePath)
                    }
                }
            }.awaitAll().filterNotNull()

            val candidates = parsedMetadataList.mapNotNull { metadata ->
                val matchingInstalled = installedPlugins.firstOrNull {
                    it.packageName == metadata.packageName &&
                        PluginManager.catalog.any { catalog -> catalog.matchesPackage(it.packageName) }
                } ?: return@mapNotNull null

                val reason = evaluatePluginRejection(metadata, matchingInstalled, appSignatures)
                ApkUpdateCandidate(
                    metadata = metadata,
                    installedVersionCode = matchingInstalled.versionCode,
                    installedVersionName = matchingInstalled.versionName,
                    isPlugin = true,
                    targetPlugin = matchingInstalled,
                    rejectionReason = reason
                )
            }

            // Deduplicate valid candidates per package, picking highest version code (tie breaker: lastModified)
            candidates.filter { it.isValid }
                .groupBy { it.metadata.packageName }
                .mapNotNull { (_, list) ->
                    list.maxWithOrNull(
                        compareBy<ApkUpdateCandidate> { it.metadata.versionCode }
                            .thenBy { it.metadata.lastModified }
                            .thenBy { it.metadata.filePath }
                    )
                }
                .sortedBy { it.targetPlugin?.name?.lowercase() ?: it.metadata.packageName }
        }

    override suspend fun discoverArcileUpdate(scope: StorageScope): ApkUpdateCandidate? =
        withContext(dispatchers.io) {
            val currentPackageName = context.packageName
            val currentPackageInfo = runCatching {
                context.packageManager.getPackageInfo(currentPackageName, 0)
            }.getOrNull() ?: return@withContext null

            val currentVersionCode = currentPackageInfo.longVersionCode
            val currentVersionName = currentPackageInfo.versionName
            val appSignatures = getInstalledSignatures(currentPackageName)

            val apkFiles = searchRepository.getFilesByCategory(scope, FileCategories.APKs.displayName)
                .getOrDefault(emptyList())
            if (apkFiles.isEmpty()) return@withContext null

            val parsedMetadataList = apkFiles.map { fileModel ->
                async {
                    parsingSemaphore.withPermit {
                        apkMetadataReader.readMetadata(fileModel.absolutePath)
                    }
                }
            }.awaitAll().filterNotNull()

            val arcileCandidates = parsedMetadataList
                .filter { it.packageName == currentPackageName }
                .map { metadata ->
                    val reason = evaluateArcileRejection(metadata, currentVersionCode, appSignatures)
                    ApkUpdateCandidate(
                        metadata = metadata,
                        installedVersionCode = currentVersionCode,
                        installedVersionName = currentVersionName,
                        isPlugin = false,
                        targetPlugin = null,
                        rejectionReason = reason
                    )
                }

            arcileCandidates.filter { it.isValid }
                .maxWithOrNull(
                    compareBy<ApkUpdateCandidate> { it.metadata.versionCode }
                        .thenBy { it.metadata.lastModified }
                        .thenBy { it.metadata.filePath }
                )
        }

    override suspend fun revalidateCandidate(candidate: ApkUpdateCandidate): ApkUpdateCandidate? =
        withContext(dispatchers.io) {
            val freshMetadata = apkMetadataReader.readMetadataFresh(candidate.metadata.filePath)
                ?: return@withContext candidate.copy(rejectionReason = ApkUpdateRejectionReason.CorruptedOrInaccessible)

            if (freshMetadata.packageName != candidate.metadata.packageName ||
                freshMetadata.versionCode != candidate.metadata.versionCode
            ) {
                return@withContext candidate.copy(rejectionReason = ApkUpdateRejectionReason.CorruptedOrInaccessible)
            }

            val appSignatures = getInstalledSignatures(context.packageName)
            val reason = if (candidate.isPlugin) {
                val installed = pluginManager.getInstalledPlugins()
                    .firstOrNull { it.packageName == freshMetadata.packageName }
                    ?: return@withContext null
                evaluatePluginRejection(freshMetadata, installed, appSignatures)
            } else {
                val currentPackageInfo = runCatching {
                    context.packageManager.getPackageInfo(context.packageName, 0)
                }.getOrNull() ?: return@withContext null
                evaluateArcileRejection(freshMetadata, currentPackageInfo.longVersionCode, appSignatures)
            }

            candidate.copy(metadata = freshMetadata, rejectionReason = reason)
        }

    private fun evaluatePluginRejection(
        metadata: ApkArchiveMetadata,
        installed: PluginMetadata,
        appSignatures: Set<String>
    ): ApkUpdateRejectionReason? {
        if (metadata.minSdkVersion > Build.VERSION.SDK_INT) {
            return ApkUpdateRejectionReason.IncompatibleSdk
        }
        if (metadata.pluginApiVersion != PluginContract.PLUGIN_API_VERSION) {
            return ApkUpdateRejectionReason.IncompatiblePluginApi
        }
        if (metadata.pluginName.isNullOrBlank() ||
            (metadata.pluginSupportedMimeTypes.isEmpty() && metadata.pluginSupportedExtensions.isEmpty())
        ) {
            return ApkUpdateRejectionReason.InvalidPluginMetadata
        }
        val signaturesMatch = appSignatures.isNotEmpty() &&
            metadata.signingDigests.isNotEmpty() &&
            metadata.signingDigests.any { it in appSignatures }
        if (!signaturesMatch) {
            return ApkUpdateRejectionReason.SignatureMismatch
        }
        if (metadata.versionCode <= installed.versionCode) {
            return ApkUpdateRejectionReason.DowngradeOrSameVersion
        }
        return null
    }

    private fun evaluateArcileRejection(
        metadata: ApkArchiveMetadata,
        currentVersionCode: Long,
        appSignatures: Set<String>
    ): ApkUpdateRejectionReason? {
        if (metadata.minSdkVersion > Build.VERSION.SDK_INT) {
            return ApkUpdateRejectionReason.IncompatibleSdk
        }
        val signaturesMatch = appSignatures.isNotEmpty() &&
            metadata.signingDigests.isNotEmpty() &&
            metadata.signingDigests.any { it in appSignatures }
        if (!signaturesMatch) {
            return ApkUpdateRejectionReason.SignatureMismatch
        }
        if (metadata.versionCode <= currentVersionCode) {
            return ApkUpdateRejectionReason.DowngradeOrSameVersion
        }
        return null
    }

    @Suppress("DEPRECATION")
    private fun getInstalledSignatures(packageName: String): Set<String> = runCatching {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        val info = pm.getPackageInfo(packageName, flags)
        val signatures: List<Signature> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && info.signingInfo != null) {
            val signingInfo = info.signingInfo!!
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners?.toList().orEmpty()
            } else {
                signingInfo.signingCertificateHistory?.toList().orEmpty()
            }
        } else {
            info.signatures?.toList().orEmpty()
        }
        signatures.mapTo(linkedSetOf()) { DefaultApkArchiveMetadataReader.sha256Digest(it.toByteArray()) }
    }.getOrDefault(emptySet())
}
