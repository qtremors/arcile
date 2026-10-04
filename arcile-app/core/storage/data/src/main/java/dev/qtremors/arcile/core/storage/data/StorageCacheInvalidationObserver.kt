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
import dev.qtremors.arcile.core.storage.data.source.StorageQueryClient
import dev.qtremors.arcile.core.storage.data.util.PathSafety
import dev.qtremors.arcile.core.storage.domain.StorageMutationNotifier
import dev.qtremors.arcile.core.runtime.di.ApplicationScope
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
    private val storageQueryClient: StorageQueryClient,
    private val volumeProvider: VolumeProvider,
    private val storageNodeDao: StorageNodeDao,
    private val folderStatsStore: FolderStatsStore,
    private val storageUsageSnapshotStore: StorageUsageSnapshotStore,
    private val storageCleanerSnapshotStore: StorageCleanerSnapshotStore,
    private val storageMutationNotifier: StorageMutationNotifier
) {
    private val registered = AtomicBoolean(false)
    private var invalidationJob: Job? = null
    private val pendingUris = linkedSetOf<Uri>()
    private var pendingBroadInvalidation = false
    private var pendingCleanerInvalidation = false
    private var pendingMutationNotification = false

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
            uris.forEach { uri -> scheduleInvalidation(uri) }
        }
    }

    fun register() {
        if (!registered.compareAndSet(false, true)) return
        context.contentResolver.registerContentObserver(
            MediaStore.Files.getContentUri("external"),
            true,
            observer
        )
        // Registration is not a file change. Keep persisted values across process starts;
        // actual notifications and cache freshness policies decide what needs refreshing.
    }

    private fun scheduleInvalidation(
        uri: Uri?,
        invalidateCleaner: Boolean = true,
        notifyMutation: Boolean = true
    ) {
        synchronized(pendingUris) {
            if (uri == null) {
                pendingBroadInvalidation = true
            } else {
                pendingUris += uri
            }
            pendingCleanerInvalidation = pendingCleanerInvalidation || invalidateCleaner
            pendingMutationNotification = pendingMutationNotification || notifyMutation
        }
        invalidationJob?.cancel()
        invalidationJob = applicationScope.launch {
            delay(INVALIDATION_DEBOUNCE_MS)
            val (uris, flags) = synchronized(pendingUris) {
                val snapshot = pendingUris.toList() to Triple(
                    pendingBroadInvalidation,
                    pendingCleanerInvalidation,
                    pendingMutationNotification
                )
                pendingUris.clear()
                pendingBroadInvalidation = false
                pendingCleanerInvalidation = false
                pendingMutationNotification = false
                snapshot
            }
            val (broadInvalidation, invalidateCleaner, notifyMutation) = flags

            if (broadInvalidation) {
                storageQueryClient.invalidateCache()
                storageNodeDao.clear()
                folderStatsStore.invalidateAll()
                storageUsageSnapshotStore.invalidate(emptyList())
                if (invalidateCleaner) storageCleanerSnapshotStore.invalidate(emptyList())
                if (notifyMutation) storageMutationNotifier.notify(emptyList())
                return@launch
            }

            val targets = uris.mapNotNull { storageQueryClient.resolveInvalidationUri(it) }
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
                    storageQueryClient.invalidateCache()
                } else {
                    storageQueryClient.invalidateCache(*paths.toTypedArray())
                }
                storageNodeDao.delete(paths)
                paths.forEach { path -> storageNodeDao.deleteTree(path, "$path/%") }
                parentPaths.forEach { parent -> storageNodeDao.deleteChildren(parent) }
                folderStatsStore.invalidate(affectedPaths)
                storageUsageSnapshotStore.invalidate(affectedPaths)
                if (invalidateCleaner) storageCleanerSnapshotStore.invalidate(paths)
                if (notifyMutation) storageMutationNotifier.notify((paths + parentPaths).distinct())
            } else {
                storageQueryClient.invalidateCache()
                folderStatsStore.invalidateAll()
                storageUsageSnapshotStore.invalidate(emptyList())
                if (invalidateCleaner) storageCleanerSnapshotStore.invalidate(emptyList())
                if (notifyMutation) storageMutationNotifier.notify(emptyList())
            }
            if (contentUris.isNotEmpty()) {
                storageNodeDao.deleteByContentUris(contentUris)
            }
            if (mediaStoreIds.isNotEmpty()) {
                storageNodeDao.deleteByMediaStoreIds(mediaStoreIds)
            }
            // Recent snapshots are presentation fallbacks; getRecentFiles still queries live data.
        }
    }

    private fun pathWithAncestors(path: String): List<String> =
        PathSafety.pathWithAncestors(path, volumeProvider.activeStorageRoots)

    private companion object {
        const val INVALIDATION_DEBOUNCE_MS = 200L
    }
}
