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
    private val storageMutationNotifier: StorageMutationNotifier
) {
    private val registered = AtomicBoolean(false)
    private var invalidationJob: Job? = null

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            scheduleInvalidation()
        }

        override fun onChange(selfChange: Boolean, uri: Uri?) {
            scheduleInvalidation()
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

    private fun scheduleInvalidation() {
        invalidationJob?.cancel()
        invalidationJob = applicationScope.launch {
            delay(INVALIDATION_DEBOUNCE_MS)
            mediaStoreClient.invalidateCache()
            storageNodeDao.clear()
            storageMutationNotifier.notify(emptyList())
        }
    }

    private companion object {
        const val INVALIDATION_DEBOUNCE_MS = 750L
    }
}
