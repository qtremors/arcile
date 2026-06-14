package dev.qtremors.arcile.core.storage.data

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.qtremors.arcile.core.storage.data.db.StorageNodeDao
import dev.qtremors.arcile.core.storage.data.source.MediaStoreClient
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
    private val storageNodeDao: StorageNodeDao,
    private val recentFilesSnapshotStore: RecentFilesSnapshotStore,
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
    }

    fun register() {
        if (!registered.compareAndSet(false, true)) return
        context.contentResolver.registerContentObserver(
            MediaStore.Files.getContentUri("external"),
            true,
            observer
        )
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
                recentFilesSnapshotStore.clear()
                storageMutationNotifier.notify(emptyList())
                return@launch
            }

            val targets = uris.mapNotNull { mediaStoreClient.resolveInvalidationUri(it) }
            val paths = targets.mapNotNull { it.path }.distinct()
            val parentPaths = targets.mapNotNull { it.parentPath }.distinct()
            val contentUris = (targets.mapNotNull { it.contentUri } + uris.map { it.toString() }).distinct()
            val mediaStoreIds = targets.mapNotNull { it.mediaStoreId }.distinct()

            if (paths.isNotEmpty()) {
                mediaStoreClient.invalidateCache(*paths.toTypedArray())
                storageNodeDao.delete(paths)
                paths.forEach { path -> storageNodeDao.deleteTree(path, "$path/%") }
                parentPaths.forEach { parent -> storageNodeDao.deleteChildren(parent) }
                storageMutationNotifier.notify((paths + parentPaths).distinct())
            } else {
                mediaStoreClient.invalidateCache()
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

    private companion object {
        const val INVALIDATION_DEBOUNCE_MS = 750L
    }
}
