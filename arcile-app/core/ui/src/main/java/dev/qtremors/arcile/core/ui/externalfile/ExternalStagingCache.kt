package dev.qtremors.arcile.core.ui.externalfile

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

data class ExternalStagingCacheStats(
    val fileCount: Int = 0,
    val sizeBytes: Long = 0L
)

interface ExternalStagingCache {
    suspend fun stats(): Result<ExternalStagingCacheStats>
    suspend fun clear(): Result<ExternalStagingCacheStats>
}

@Singleton
class DefaultExternalStagingCache @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dispatchers: ArcileDispatchers
) : ExternalStagingCache {
    override suspend fun stats(): Result<ExternalStagingCacheStats> = execute {
        ExternalFileAccessHelper.getStagingCacheStats(context)
    }

    override suspend fun clear(): Result<ExternalStagingCacheStats> = execute {
        ExternalFileAccessHelper.clearStagingArea(context)
    }

    private suspend fun execute(
        action: () -> ExternalFileAccessHelper.StagingCacheStats
    ): Result<ExternalStagingCacheStats> = withContext(dispatchers.io) {
        try {
            val stats = action()
            Result.success(ExternalStagingCacheStats(stats.fileCount, stats.sizeBytes))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ExternalStagingCacheModule {
    @Binds
    abstract fun bindExternalStagingCache(
        implementation: DefaultExternalStagingCache
    ): ExternalStagingCache
}
