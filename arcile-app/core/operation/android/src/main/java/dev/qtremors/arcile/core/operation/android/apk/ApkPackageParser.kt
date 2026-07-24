package dev.qtremors.arcile.core.operation.android.apk

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

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

        val extractedApks = mutableListOf<File>()

        try {
            ZipFile(archiveFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (!entry.isDirectory && entry.name.endsWith(".apk", ignoreCase = true)) {
                        val outFile = File(tempExtractDir, File(entry.name).name)
                        zip.getInputStream(entry).use { input ->
                            FileOutputStream(outFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        extractedApks.add(outFile)
                    }
                }
            }

            if (extractedApks.isEmpty()) return null

            val details = parseApkFileList(context, extractedApks)
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
                if (apkFile.name.equals("base.apk", ignoreCase = true) || baseApkInfo == null) {
                    baseApkInfo = info
                    baseFile = apkFile
                }
                if (apkFile.name.equals("base.apk", ignoreCase = true)) {
                    break
                }
            }
        }

        val info = baseApkInfo ?: return null
        val primaryFile = baseFile ?: return null
        val appInfo = info.applicationInfo ?: return null

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
}
