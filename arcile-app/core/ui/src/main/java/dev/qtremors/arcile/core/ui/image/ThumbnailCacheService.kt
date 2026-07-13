package dev.qtremors.arcile.core.ui.image

import android.content.Context
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import java.nio.file.Files
import java.nio.file.LinkOption
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class ThumbnailCacheStats(
    val diskBytes: Long = 0L,
    val loadedCount: Int = 0,
    val failedCount: Int = 0,
    val inFlightCount: Int = 0
)

interface ThumbnailCacheService {
    suspend fun stats(): Result<ThumbnailCacheStats>
    suspend fun clear(): Result<ThumbnailCacheStats>
}

object NoOpThumbnailCacheService : ThumbnailCacheService {
    override suspend fun stats(): Result<ThumbnailCacheStats> = Result.success(ThumbnailCacheStats())
    override suspend fun clear(): Result<ThumbnailCacheStats> = Result.success(ThumbnailCacheStats())
}

@Singleton
@OptIn(ExperimentalCoilApi::class)
class DefaultThumbnailCacheService @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dispatchers: ArcileDispatchers
) : ThumbnailCacheService {
    override suspend fun stats(): Result<ThumbnailCacheStats> = execute(::loadStats)

    override suspend fun clear(): Result<ThumbnailCacheStats> = execute {
        val failures = mutableListOf<Throwable>()
        suspend fun attempt(action: suspend () -> Unit) {
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                failures += error
            }
        }

        attempt { context.imageLoader.memoryCache?.clear() }
        attempt { context.imageLoader.diskCache?.clear() }
        attempt { GlobalThumbnailFailureCache.clear() }
        attempt { GlobalThumbnailLoadStateStore.clear() }
        attempt { GlobalThumbnailStatePersistence.delegate?.clear() }

        failures.firstOrNull()?.let { first ->
            failures.drop(1).forEach(first::addSuppressed)
            throw first
        }
        loadStats()
    }

    private suspend fun execute(
        action: suspend () -> ThumbnailCacheStats
    ): Result<ThumbnailCacheStats> = withContext(dispatchers.io) {
        try {
            Result.success(action())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    private suspend fun loadStats(): ThumbnailCacheStats {
        val state = GlobalThumbnailLoadStateStore.stats()
        return ThumbnailCacheStats(
            diskBytes = diskCacheSize(),
            loadedCount = state.loadedCount,
            failedCount = state.failedCount,
            inFlightCount = state.inFlightCount
        )
    }

    private suspend fun diskCacheSize(): Long {
        val root = context.imageLoader.diskCache?.directory?.toFile()
            ?: context.cacheDir.resolve("image_cache")
        if (!root.exists()) return 0L

        var total = 0L
        Files.walk(root.toPath()).use { paths ->
            val iterator = paths.iterator()
            while (iterator.hasNext()) {
                currentCoroutineContext().ensureActive()
                val path = iterator.next()
                if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    val size = runCatching { Files.size(path) }.getOrDefault(0L).coerceAtLeast(0L)
                    total = if (Long.MAX_VALUE - total < size) Long.MAX_VALUE else total + size
                }
            }
        }
        return total
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ThumbnailCacheServiceModule {
    @Binds
    abstract fun bindThumbnailCacheService(
        implementation: DefaultThumbnailCacheService
    ): ThumbnailCacheService
}
