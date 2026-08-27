package dev.qtremors.arcile.core.operation.android.apk

import dev.qtremors.arcile.core.storage.domain.StorageScope

interface OnDeviceApkDiscovery {
    suspend fun discoverPluginUpdates(scope: StorageScope = StorageScope.AllStorage): List<ApkUpdateCandidate>
    suspend fun discoverArcileUpdate(scope: StorageScope = StorageScope.AllStorage): ApkUpdateCandidate?
    suspend fun revalidateCandidate(candidate: ApkUpdateCandidate): ApkUpdateCandidate?
}
