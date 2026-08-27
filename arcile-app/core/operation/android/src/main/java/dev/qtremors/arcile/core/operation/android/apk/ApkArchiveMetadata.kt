package dev.qtremors.arcile.core.operation.android.apk

data class ApkArchiveMetadata(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val minSdkVersion: Int,
    val targetSdkVersion: Int,
    val isSplitApk: Boolean = false,
    val splitCount: Int = 1,
    val signingDigestSha256: String? = null,
    val signingDigests: List<String> = emptyList(),
    val isPlugin: Boolean = false,
    val pluginApiVersion: Int = -1,
    val pluginName: String? = null,
    val pluginSupportedMimeTypes: Set<String> = emptySet(),
    val pluginSupportedExtensions: Set<String> = emptySet(),
    val pluginHomepage: String? = null,
    val filePath: String,
    val fileSizeBytes: Long = 0L,
    val lastModified: Long = 0L
)
