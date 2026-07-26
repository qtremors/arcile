package dev.qtremors.arcile.core.operation.android.apk

import android.graphics.drawable.Drawable

/* ============================================================================
 * APK PACKAGE DETAILS
 * ============================================================================
 * Model representing extracted metadata for single APKs or Split APK containers.
 */

data class ApkPackageDetails(
    val label: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val icon: Drawable? = null,
    val isSplitApk: Boolean = false,
    val splitCount: Int = 1,
    val totalSizeBytes: Long = 0L,
    val minSdkVersion: Int = 0,
    val targetSdkVersion: Int = 0,
    val apkPaths: List<String> = emptyList(),
    val installedVersionName: String? = null,
    val installedVersionCode: Long? = null
) {
    val isInstalled: Boolean
        get() = installedVersionCode != null

    val isUpdate: Boolean
        get() = installedVersionCode != null && versionCode > installedVersionCode

    val isDowngrade: Boolean
        get() = installedVersionCode != null && versionCode < installedVersionCode

    val isSameVersion: Boolean
        get() = installedVersionCode != null && versionCode == installedVersionCode
}
