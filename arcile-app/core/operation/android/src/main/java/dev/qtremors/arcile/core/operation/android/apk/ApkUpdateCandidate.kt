package dev.qtremors.arcile.core.operation.android.apk

import dev.qtremors.arcile.plugin.api.PluginMetadata

data class ApkUpdateCandidate(
    val metadata: ApkArchiveMetadata,
    val installedVersionCode: Long,
    val installedVersionName: String?,
    val isPlugin: Boolean,
    val targetPlugin: PluginMetadata? = null,
    val rejectionReason: ApkUpdateRejectionReason? = null
) {
    val isValid: Boolean
        get() = rejectionReason == null && metadata.versionCode > installedVersionCode
}
