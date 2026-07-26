package dev.qtremors.arcile.core.operation.android.apk

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.zip.ZipFile
import kotlin.math.abs

/* ============================================================================
 * APK PACKAGE PARSER
 * ============================================================================
 * Handles parsing single APK files and split APK containers (.apks, .xapk, .apkm)
 * to extract app metadata, icons, installed version context, and component lists.
 */

object ApkPackageParser {

    /* ------------------------------------------------------------------------
     * Primary Entry Point
     * ------------------------------------------------------------------------ */

    fun parse(context: Context, primaryPath: String, selectedSplitPaths: List<String> = emptyList()): ApkPackageDetails? {
        val file = File(primaryPath)
        if (!file.exists()) return null

        return when {
            // Case 1: Multi-select split APK paths provided explicitly
            selectedSplitPaths.size > 1 -> parseApkFileList(context, selectedSplitPaths.map { File(it) })

            // Case 2: Split APK container archive (.apks, .xapk, .apkm)
            isSplitContainer(file) -> parseSplitArchiveContainer(context, file)

            // Case 3: Single standalone .apk file
            file.extension.lowercase() == "apk" -> parseSingleApkFile(context, file)

            else -> null
        }
    }

    /* ------------------------------------------------------------------------
     * Single APK File Parsing
     * ------------------------------------------------------------------------ */

    @Suppress("DEPRECATION")
    private fun parseSingleApkFile(context: Context, file: File): ApkPackageDetails? {
        val pm = context.packageManager
        val info = pm.getPackageArchiveInfo(file.absolutePath, 0) ?: return null
        val appInfo = info.applicationInfo ?: return null

        appInfo.sourceDir = file.absolutePath
        appInfo.publicSourceDir = file.absolutePath

        val label = appInfo.loadLabel(pm).toString().ifBlank { info.packageName }
        val icon = appInfo.loadIcon(pm)
        val versionCode = getVersionCode(info)
        val installedInfo = getInstalledPackageInfo(pm, info.packageName)

        return ApkPackageDetails(
            label = label,
            packageName = info.packageName,
            versionName = info.versionName ?: "1.0",
            versionCode = versionCode,
            icon = icon,
            isSplitApk = false,
            splitCount = 1,
            totalSizeBytes = file.length(),
            minSdkVersion = appInfo.minSdkVersion,
            targetSdkVersion = appInfo.targetSdkVersion,
            apkPaths = listOf(file.absolutePath),
            installedVersionName = installedInfo?.versionName,
            installedVersionCode = installedInfo?.let { getVersionCode(it) }
        )
    }

    /* ------------------------------------------------------------------------
     * Split Archive Container Parsing (.apks, .xapk, .apkm)
     * ------------------------------------------------------------------------ */

    private fun parseSplitArchiveContainer(context: Context, archiveFile: File): ApkPackageDetails? {
        val tempExtractDir = File(context.cacheDir, "apk_staging_${archiveFile.name.hashCode()}")
        if (tempExtractDir.exists()) {
            tempExtractDir.deleteRecursively()
        }
        tempExtractDir.mkdirs()

        val extractedApks = mutableListOf<ExtractedApk>()

        try {
            ZipFile(archiveFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (!entry.isDirectory && entry.name.endsWith(".apk", ignoreCase = true)) {
                        val outputName = "${extractedApks.size.toString().padStart(4, '0')}_${File(entry.name).name}"
                        val outFile = File(tempExtractDir, outputName)
                        zip.getInputStream(entry).use { input ->
                            FileOutputStream(outFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        extractedApks.add(ExtractedApk(outFile, entry.name))
                    }
                }
            }

            if (extractedApks.isEmpty()) return null

            val compatibleApks = selectCompatibleArchiveApks(context, extractedApks)
            if (compatibleApks.isEmpty()) return null
            val details = parseApkFileList(context, compatibleApks.map(ExtractedApk::file))
            return details?.copy(
                totalSizeBytes = archiveFile.length()
            )
        } catch (_: Exception) {
            tempExtractDir.deleteRecursively()
            return null
        }
    }

    /* ------------------------------------------------------------------------
     * Multiple Split APK Files Parsing
     * ------------------------------------------------------------------------ */

    @Suppress("DEPRECATION")
    private fun parseApkFileList(context: Context, apkFiles: List<File>): ApkPackageDetails? {
        val pm = context.packageManager
        var baseApkInfo: PackageInfo? = null
        var baseFile: File? = null

        // 1. Identify base.apk or primary APK file
        for (apkFile in apkFiles) {
            val info = pm.getPackageArchiveInfo(apkFile.absolutePath, 0)
            if (info != null) {
                val normalizedName = normalizeQualifier(apkFile.name)
                val isBaseApk = normalizedName == "base_apk" ||
                    normalizedName.endsWith("_base_apk") ||
                    normalizedName.contains("base_master")
                if (isBaseApk || baseApkInfo == null) {
                    baseApkInfo = info
                    baseFile = apkFile
                }
                if (isBaseApk) {
                    break
                }
            }
        }

        val info = baseApkInfo ?: return null
        val primaryFile = baseFile ?: return null
        val appInfo = info.applicationInfo ?: return null
        val packageFilesAreConsistent = apkFiles.all { apkFile ->
            pm.getPackageArchiveInfo(apkFile.absolutePath, 0)?.let { candidate ->
                candidate.packageName == info.packageName &&
                    getVersionCode(candidate) == getVersionCode(info)
            } == true
        }
        if (!packageFilesAreConsistent) return null

        appInfo.sourceDir = primaryFile.absolutePath
        appInfo.publicSourceDir = primaryFile.absolutePath

        val label = appInfo.loadLabel(pm).toString().ifBlank { info.packageName }
        val icon = appInfo.loadIcon(pm)
        val versionCode = getVersionCode(info)
        val installedInfo = getInstalledPackageInfo(pm, info.packageName)
        val totalSize = apkFiles.sumOf { it.length() }

        return ApkPackageDetails(
            label = label,
            packageName = info.packageName,
            versionName = info.versionName ?: "1.0",
            versionCode = versionCode,
            icon = icon,
            isSplitApk = apkFiles.size > 1,
            splitCount = apkFiles.size,
            totalSizeBytes = totalSize,
            minSdkVersion = appInfo.minSdkVersion,
            targetSdkVersion = appInfo.targetSdkVersion,
            apkPaths = apkFiles.map { it.absolutePath },
            installedVersionName = installedInfo?.versionName,
            installedVersionCode = installedInfo?.let { getVersionCode(it) }
        )
    }

    /* ------------------------------------------------------------------------
     * Helpers & Compatibility
     * ------------------------------------------------------------------------ */

    private fun isSplitContainer(file: File): Boolean {
        val ext = file.extension.lowercase()
        return ext == "apks" || ext == "xapk" || ext == "apkm"
    }

    private fun selectCompatibleArchiveApks(
        context: Context,
        extractedApks: List<ExtractedApk>
    ): List<ExtractedApk> =
        selectCompatibleArchiveApks(
            extractedApks = extractedApks,
            target = ApkArchiveDeviceTarget(
                supportedAbis = Build.SUPPORTED_ABIS.toList(),
                densityDpi = context.resources.displayMetrics.densityDpi,
                language = (context.resources.configuration.locales[0] ?: Locale.getDefault()).language,
                region = (context.resources.configuration.locales[0] ?: Locale.getDefault()).country
            )
        )

    internal fun selectCompatibleArchiveApks(
        extractedApks: List<ExtractedApk>,
        target: ApkArchiveDeviceTarget
    ): List<ExtractedApk> {
        val splitSet = extractedApks.filter {
            it.archivePath.substringBeforeLast('/', "").split('/').any { segment ->
                segment.equals("splits", ignoreCase = true)
            }
        }
        val candidates = splitSet.ifEmpty { extractedApks }
        val standaloneCandidates = candidates.filter { it.isStandalone }
        val hasBaseAndSplits = candidates.any { it.isBase } &&
            candidates.any { !it.isBase && !it.isStandalone }

        if (!hasBaseAndSplits && standaloneCandidates.isNotEmpty()) {
            return listOf(selectBestStandalone(target, standaloneCandidates))
        }

        val supportedAbis = target.supportedAbis.map(::normalizeQualifier)
        val selectedAbi = supportedAbis.firstOrNull { abi ->
            candidates.any { it.hasQualifier(abi) }
        }
        val availableDensities = DENSITY_QUALIFIERS.entries.filter { entry ->
            candidates.any { it.hasQualifier(entry.key) }
        }
        val selectedDensity = availableDensities.minByOrNull { entry ->
            abs(entry.value - target.densityDpi)
        }?.key
        val language = target.language.lowercase(Locale.ROOT)
        val region = target.region.lowercase(Locale.ROOT)

        return candidates.filter { candidate ->
            !candidate.isStandalone &&
                candidate.matchesAbi(selectedAbi) &&
                candidate.matchesDensity(selectedDensity) &&
                candidate.matchesLocale(language, region)
        }
    }

    private fun selectBestStandalone(
        target: ApkArchiveDeviceTarget,
        candidates: List<ExtractedApk>
    ): ExtractedApk {
        val supportedAbis = target.supportedAbis.map(::normalizeQualifier)
        return candidates.minByOrNull { candidate ->
            val abiPenalty = supportedAbis.indexOfFirst(candidate::hasQualifier)
                .let { if (it < 0) supportedAbis.size else it }
            val density = DENSITY_QUALIFIERS.entries.firstOrNull { entry ->
                candidate.hasQualifier(entry.key)
            }?.value
            val densityPenalty = density?.let { abs(it - target.densityDpi) } ?: 0
            abiPenalty * 10_000 + densityPenalty
        } ?: candidates.first()
    }

    private fun ExtractedApk.matchesAbi(selectedAbi: String?): Boolean {
        val presentAbis = KNOWN_ABI_QUALIFIERS.filter(::hasQualifier)
        return presentAbis.isEmpty() || selectedAbi == null || selectedAbi in presentAbis
    }

    private fun ExtractedApk.matchesDensity(selectedDensity: String?): Boolean {
        val presentDensities = DENSITY_QUALIFIERS.keys.filter(::hasQualifier)
        return presentDensities.isEmpty() ||
            presentDensities.any { it == "anydpi" || it == "nodpi" } ||
            selectedDensity == null ||
            selectedDensity in presentDensities
    }

    private fun ExtractedApk.matchesLocale(language: String, region: String): Boolean {
        val localeMatches = LOCALE_QUALIFIER.findAll(qualifierText).map { match ->
            match.groupValues[1] to match.groupValues[2]
        }.filter { (candidateLanguage, _) ->
            candidateLanguage in ISO_LANGUAGES
        }.toList()
        if (localeMatches.isEmpty()) return true
        return localeMatches.any { (candidateLanguage, candidateRegion) ->
            candidateLanguage == language &&
                (candidateRegion.isBlank() || region.isBlank() || candidateRegion == region)
        }
    }

    @Suppress("DEPRECATION")
    private fun getVersionCode(packageInfo: PackageInfo): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            packageInfo.versionCode.toLong()
        }
    }

    private fun getInstalledPackageInfo(pm: PackageManager, packageName: String): PackageInfo? {
        return try {
            pm.getPackageInfo(packageName, 0)
        } catch (_: Exception) {
            null
        }
    }

    internal data class ExtractedApk(
        val file: File,
        val archivePath: String
    ) {
        val qualifierText: String = normalizeQualifier(archivePath)
        val isStandalone: Boolean =
            qualifierText.contains("standalone") || qualifierText.contains("universal")
        val isBase: Boolean =
            file.name.substringAfter('_').equals("base.apk", ignoreCase = true) ||
                qualifierText.contains("base_master") ||
                qualifierText.endsWith("base_apk")

        fun hasQualifier(qualifier: String): Boolean =
            "_${qualifierText.trim('_')}_".contains("_${qualifier.trim('_')}_")
    }

    private fun normalizeQualifier(value: String): String =
        value.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "_")

    private val KNOWN_ABI_QUALIFIERS = listOf(
        "arm64_v8a",
        "armeabi_v7a",
        "armeabi",
        "x86_64",
        "x86"
    )
    private val DENSITY_QUALIFIERS = mapOf(
        "ldpi" to 120,
        "mdpi" to 160,
        "tvdpi" to 213,
        "hdpi" to 240,
        "xhdpi" to 320,
        "xxhdpi" to 480,
        "xxxhdpi" to 640,
        "anydpi" to 0,
        "nodpi" to 0
    )
    private val LOCALE_QUALIFIER = Regex("(?:^|_)([a-z]{2,3})(?:_r?([a-z]{2}))?(?:_|$)")
    private val ISO_LANGUAGES = Locale.getISOLanguages().toSet()

    internal data class ApkArchiveDeviceTarget(
        val supportedAbis: List<String>,
        val densityDpi: Int,
        val language: String,
        val region: String
    )
}
