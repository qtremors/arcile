package dev.qtremors.arcile.core.storage.data

import android.content.Context
import android.media.MediaScannerConnection
import dev.qtremors.arcile.core.storage.data.provider.VolumeProvider
import dev.qtremors.arcile.core.storage.data.source.MediaStoreClient
import dev.qtremors.arcile.core.storage.data.util.PathSafety

class MutationFinalizer(
    private val context: Context,
    private val mediaStoreClient: MediaStoreClient,
    private val volumeProvider: VolumeProvider,
    private val folderStatsStore: FolderStatsStore
) {
    suspend fun finalize(vararg paths: String) {
        mediaStoreClient.invalidateCache(*paths)
        volumeProvider.invalidateCache()
        folderStatsStore.invalidate(paths.flatMap(::pathWithAncestors))
        scanMediaFiles(*paths)
    }

    private fun pathWithAncestors(path: String): List<String> {
        return PathSafety.pathWithAncestors(path, volumeProvider.activeStorageRoots)
    }

    private fun scanMediaFiles(vararg paths: String) {
        if (paths.isEmpty()) return
        MediaScannerConnection.scanFile(context.applicationContext, paths, null, null)
    }
}
