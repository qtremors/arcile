package dev.qtremors.arcile.core.storage.data

import android.content.Context
import android.media.MediaScannerConnection
import dev.qtremors.arcile.core.storage.data.provider.VolumeProvider
import dev.qtremors.arcile.core.storage.data.source.MediaStoreClient
import dev.qtremors.arcile.core.storage.data.util.PathSafety
import dev.qtremors.arcile.core.storage.domain.NoOpStorageMutationNotifier
import dev.qtremors.arcile.core.storage.domain.StorageMutationNotifier

class MutationFinalizer(
    private val context: Context,
    private val mediaStoreClient: MediaStoreClient,
    private val volumeProvider: VolumeProvider,
    private val folderStatsStore: FolderStatsStore,
    private val storageMutationNotifier: StorageMutationNotifier = NoOpStorageMutationNotifier
) {
    suspend fun finalize(vararg paths: String) {
        mediaStoreClient.invalidateCache(*paths)
        volumeProvider.invalidateCache()
        folderStatsStore.invalidate(paths.flatMap(::pathWithAncestors))
        scanMediaFiles(*paths)
        storageMutationNotifier.notify(paths.toList())
    }

    private fun pathWithAncestors(path: String): List<String> {
        return PathSafety.pathWithAncestors(path, volumeProvider.activeStorageRoots)
    }

    private fun scanMediaFiles(vararg paths: String) {
        if (paths.isEmpty()) return
        MediaScannerConnection.scanFile(context.applicationContext, paths, null, null)
    }
}
