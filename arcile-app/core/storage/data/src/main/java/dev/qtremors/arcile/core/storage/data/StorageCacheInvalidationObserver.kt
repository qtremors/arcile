package dev.qtremors.arcile.core.storage.data

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.qtremors.arcile.core.storage.data.db.StorageNodeDao
import dev.qtremors.arcile.core.storage.data.provider.VolumeProvider
import dev.qtremors.arcile.core.storage.data.source.MediaStoreClient
import dev.qtremors.arcile.core.storage.data.util.PathSafety
import dev.qtremors.arcile.core.storage.domain.StorageMutationNotifier
import dev.qtremors.arcile.di.ApplicationScope
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Singleton
class StorageCacheInvalidationObserver @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
    private val mediaStoreClient: MediaStoreClient,
    private val volumeProvider: VolumeProvider,
    private val storageNodeDao: StorageNodeDao,
    private val folderStatsStore: FolderStatsStore,
    private val recentFilesSnapshotStore: RecentFilesSnapshotStore,
    private val storageUsageSnapshotStore: StorageUsageSnapshotStore,
    private val storageCleanerSnapshotStore: StorageCleanerSnapshotStore,
    private val storageMutationNotifier: StorageMutationNotifier
) {
    private val registered = AtomicBoolean(false)
    private var invalidationJob: Job? = null
    private val pendingUris = linkedSetOf<Uri>()
    private var pendingBroadInvalidation = false

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            scheduleInvalidation(null)
        }

        override fun onChange(selfChange: Boolean, uri: Uri?) {
            scheduleInvalidation(uri)
        }

        override fun onChange(selfChange: Boolean, uris: Collection<Uri>, flags: Int) {
            if (uris.isEmpty()) {
                scheduleInvalidation(null)
                return
            }
            uris.forEach(::scheduleInvalidation)
        }
    }

    fun register() {
        if (!registered.compareAndSet(false, true)) return
        context.contentResolver.registerContentObserver(
            MediaStore.Files.getContentUri("external"),
            true,
            observer
        )
        scheduleInvalidation(null)
    }

    private fun scheduleInvalidation(uri: Uri?) {
        synchronized(pendingUris) {
            if (uri == null) {
                pendingBroadInvalidation = true
            } else {
                pendingUris += uri
            }
        }
        invalidationJob?.cancel()
        invalidationJob = applicationScope.launch {
            delay(INVALIDATION_DEBOUNCE_MS)
            val (uris, broadInvalidation) = synchronized(pendingUris) {
                val snapshot = pendingUris.toList() to pendingBroadInvalidation
                pendingUris.clear()
                pendingBroadInvalidation = false
                snapshot
            }

            if (broadInvalidation) {
                mediaStoreClient.invalidateCache()
                storageNodeDao.clear()
                folderStatsStore.clear()
                recentFilesSnapshotStore.clear()
                storageUsageSnapshotStore.invalidate(emptyList())
                storageCleanerSnapshotStore.clear()
                storageMutationNotifier.notify(emptyList())
                return@launch
            }

            val targets = uris.mapNotNull { mediaStoreClient.resolveInvalidationUri(it) }
            val hasUnresolvedTarget = targets.any { it.path.isNullOrBlank() }
            val paths = targets.mapNotNull { it.path }.distinct()
            val parentPaths = targets.mapNotNull { it.parentPath }.distinct()
            val affectedPaths = (paths + parentPaths)
                .flatMap(::pathWithAncestors)
                .distinct()
            val contentUris = (targets.mapNotNull { it.contentUri } + uris.map { it.toString() }).distinct()
            val mediaStoreIds = targets.mapNotNull { it.mediaStoreId }.distinct()

            if (paths.isNotEmpty()) {
                if (hasUnresolvedTarget) {
                    mediaStoreClient.invalidateCache()
                } else {
                    mediaStoreClient.invalidateCache(*paths.toTypedArray())
                }
                storageNodeDao.delete(paths)
                paths.forEach { path -> storageNodeDao.deleteTree(path, "$path/%") }
                parentPaths.forEach { parent -> storageNodeDao.deleteChildren(parent) }
                folderStatsStore.invalidate(affectedPaths)
                storageUsageSnapshotStore.invalidate(affectedPaths)
                storageCleanerSnapshotStore.clear()
                storageMutationNotifier.notify((paths + parentPaths).distinct())
            } else {
                mediaStoreClient.invalidateCache()
                folderStatsStore.clear()
                storageUsageSnapshotStore.invalidate(emptyList())
                storageCleanerSnapshotStore.clear()
                storageMutationNotifier.notify(emptyList())
            }
            if (contentUris.isNotEmpty()) {
                storageNodeDao.deleteByContentUris(contentUris)
            }
            if (mediaStoreIds.isNotEmpty()) {
                storageNodeDao.deleteByMediaStoreIds(mediaStoreIds)
            }
            recentFilesSnapshotStore.clear()
        }
    }

    private fun pathWithAncestors(path: String): List<String> =
        PathSafety.pathWithAncestors(path, volumeProvider.activeStorageRoots)

    private companion object {
        const val INVALIDATION_DEBOUNCE_MS = 200L
    }
}
