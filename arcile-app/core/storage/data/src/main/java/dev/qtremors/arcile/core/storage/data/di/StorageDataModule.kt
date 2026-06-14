package dev.qtremors.arcile.core.storage.data.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.qtremors.arcile.core.storage.data.DefaultFolderStatsStore
import dev.qtremors.arcile.core.storage.data.DefaultImageCatalogRepository
import dev.qtremors.arcile.core.storage.data.DefaultStorageCleanerScanner
import dev.qtremors.arcile.core.storage.data.DefaultStorageMutationNotifier
import dev.qtremors.arcile.core.storage.data.DefaultStorageUsageScanner
import dev.qtremors.arcile.core.storage.data.DefaultStorageWorkCoordinator
import dev.qtremors.arcile.core.storage.data.FolderStatsStore
import dev.qtremors.arcile.core.storage.data.LocalFileRepository
import dev.qtremors.arcile.core.storage.data.MutationFinalizer
import dev.qtremors.arcile.core.storage.data.DefaultMutationJournal
import dev.qtremors.arcile.core.storage.data.MutationJournal
import dev.qtremors.arcile.core.storage.data.StorageClassificationRepository
import dev.qtremors.arcile.core.storage.data.RecentFilesSnapshotStore
import dev.qtremors.arcile.core.storage.data.StorageCleanerSnapshotStore
import dev.qtremors.arcile.core.storage.data.StorageUsageSnapshotStore
import dev.qtremors.arcile.core.storage.data.DefaultThumbnailCacheStore
import dev.qtremors.arcile.core.storage.data.ThumbnailCacheStore
import dev.qtremors.arcile.core.storage.data.db.ArcileDatabase
import dev.qtremors.arcile.core.storage.data.db.CategorySummaryDao
import dev.qtremors.arcile.core.storage.data.db.FolderStatsDao
import dev.qtremors.arcile.core.storage.data.db.RecentFilesSnapshotDao
import dev.qtremors.arcile.core.storage.data.db.StorageCleanerSnapshotDao
import dev.qtremors.arcile.core.storage.data.db.StorageNodeDao
import dev.qtremors.arcile.core.storage.data.db.StorageUsageSnapshotDao
import dev.qtremors.arcile.core.storage.data.db.ThumbnailDao
import dev.qtremors.arcile.core.storage.data.manager.DefaultArchiveManager
import dev.qtremors.arcile.core.storage.data.manager.DefaultTrashManager
import dev.qtremors.arcile.core.storage.data.manager.TrashManager
import dev.qtremors.arcile.core.storage.data.provider.DefaultVolumeProvider
import dev.qtremors.arcile.core.storage.data.provider.VolumeProvider
import dev.qtremors.arcile.core.storage.data.source.DefaultFileSystemDataSource
import dev.qtremors.arcile.core.storage.data.source.DefaultMediaStoreClient
import dev.qtremors.arcile.core.storage.data.source.FileSystemDataSource
import dev.qtremors.arcile.core.storage.data.source.MediaStoreClient
import dev.qtremors.arcile.core.storage.domain.ArchiveManager
import dev.qtremors.arcile.core.storage.domain.ArchiveRepository
import dev.qtremors.arcile.core.storage.domain.ClipboardRepository
import dev.qtremors.arcile.core.storage.domain.FileBrowserRepository
import dev.qtremors.arcile.core.storage.domain.FileMutationRepository
import dev.qtremors.arcile.core.storage.domain.FileRepository
import dev.qtremors.arcile.core.storage.domain.ImageCatalogRepository
import dev.qtremors.arcile.core.storage.domain.SearchRepository
import dev.qtremors.arcile.core.storage.domain.StorageAnalyticsRepository
import dev.qtremors.arcile.core.storage.domain.StorageClassificationStore
import dev.qtremors.arcile.core.storage.domain.StorageCleanerScanner
import dev.qtremors.arcile.core.storage.domain.StorageMutationNotifier
import dev.qtremors.arcile.core.storage.domain.StorageUsageScanner
import dev.qtremors.arcile.core.storage.domain.StorageWorkCoordinator
import dev.qtremors.arcile.core.storage.domain.TrashRepository
import dev.qtremors.arcile.core.storage.domain.VolumeRepository
import dev.qtremors.arcile.di.ApplicationScope
import dev.qtremors.arcile.di.ArcileDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StorageDataModule {

    @OptIn(ExperimentalCoroutinesApi::class)
    @Provides
    @Singleton
    fun provideArcileDispatchers(): ArcileDispatchers {
        return ArcileDispatchers(
            io = Dispatchers.IO,
            default = Dispatchers.Default,
            main = Dispatchers.Main,
            storage = Dispatchers.IO.limitedParallelism(2)
        )
    }

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(dispatchers: ArcileDispatchers): CoroutineScope {
        return CoroutineScope(SupervisorJob() + dispatchers.io)
    }

    @Provides
    @Singleton
    fun provideArcileDatabase(
        @ApplicationContext context: Context
    ): ArcileDatabase {
        return ArcileDatabase.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideFolderStatsDao(database: ArcileDatabase): FolderStatsDao =
        database.folderStatsDao()

    @Provides
    @Singleton
    fun provideStorageNodeDao(database: ArcileDatabase): StorageNodeDao =
        database.storageNodeDao()

    @Provides
    @Singleton
    fun provideCategorySummaryDao(database: ArcileDatabase): CategorySummaryDao =
        database.categorySummaryDao()

    @Provides
    @Singleton
    fun provideThumbnailDao(database: ArcileDatabase): ThumbnailDao =
        database.thumbnailDao()

    @Provides
    @Singleton
    fun provideRecentFilesSnapshotDao(database: ArcileDatabase): RecentFilesSnapshotDao =
        database.recentFilesSnapshotDao()

    @Provides
    @Singleton
    fun provideStorageUsageSnapshotDao(database: ArcileDatabase): StorageUsageSnapshotDao =
        database.storageUsageSnapshotDao()

    @Provides
    @Singleton
    fun provideStorageCleanerSnapshotDao(database: ArcileDatabase): StorageCleanerSnapshotDao =
        database.storageCleanerSnapshotDao()

    @Provides
    @Singleton
    fun provideThumbnailCacheStore(store: DefaultThumbnailCacheStore): ThumbnailCacheStore = store

    @Provides
    @Singleton
    fun provideStorageClassificationRepository(
        @ApplicationContext context: Context
    ): StorageClassificationRepository {
        return StorageClassificationRepository(context)
    }

    @Provides
    @Singleton
    fun provideStorageClassificationStore(
        repository: StorageClassificationRepository
    ): StorageClassificationStore {
        return repository
    }

    @Provides
    @Singleton
    fun provideVolumeProvider(
        @ApplicationContext context: Context,
        classificationRepository: StorageClassificationRepository,
        @ApplicationScope applicationScope: CoroutineScope,
        dispatchers: ArcileDispatchers
    ): VolumeProvider {
        return DefaultVolumeProvider(context, classificationRepository, applicationScope, dispatchers)
    }

    @Provides
    @Singleton
    fun provideMediaStoreClient(
        @ApplicationContext context: Context,
        volumeProvider: VolumeProvider,
        categorySummaryDao: CategorySummaryDao,
        dispatchers: ArcileDispatchers
    ): MediaStoreClient {
        return DefaultMediaStoreClient(context, volumeProvider, categorySummaryDao, dispatchers)
    }

    @Provides
    @Singleton
    fun provideTrashManager(
        @ApplicationContext context: Context,
        volumeProvider: VolumeProvider,
        mutationFinalizer: MutationFinalizer,
        mutationJournal: MutationJournal,
        dispatchers: ArcileDispatchers
    ): TrashManager {
        return DefaultTrashManager(
            context,
            volumeProvider,
            mutationFinalizer,
            dispatchers = dispatchers,
            mutationJournal = mutationJournal
        )
    }

    @Provides
    @Singleton
    fun provideArchiveManager(
        volumeProvider: VolumeProvider,
        mutationFinalizer: MutationFinalizer,
        mutationJournal: MutationJournal,
        dispatchers: ArcileDispatchers
    ): ArchiveManager {
        return DefaultArchiveManager(
            volumeProvider,
            mutationFinalizer,
            dispatchers = dispatchers,
            mutationJournal = mutationJournal
        )
    }

    @Provides
    @Singleton
    fun provideFileSystemDataSource(
        @ApplicationContext context: Context,
        volumeProvider: VolumeProvider,
        mutationFinalizer: MutationFinalizer,
        mutationJournal: MutationJournal,
        storageNodeDao: StorageNodeDao,
        dispatchers: ArcileDispatchers
    ): FileSystemDataSource {
        return DefaultFileSystemDataSource(
            context,
            volumeProvider,
            mutationFinalizer,
            dispatchers = dispatchers,
            storageNodeDao = storageNodeDao,
            mutationJournal = mutationJournal
        )
    }

    @Provides
    @Singleton
    fun provideMutationFinalizer(
        @ApplicationContext context: Context,
        mediaStoreClient: MediaStoreClient,
        volumeProvider: VolumeProvider,
        folderStatsStore: FolderStatsStore,
        thumbnailCacheStore: ThumbnailCacheStore,
        storageNodeDao: StorageNodeDao,
        storageMutationNotifier: StorageMutationNotifier
    ): MutationFinalizer {
        return MutationFinalizer(context, mediaStoreClient, volumeProvider, folderStatsStore, thumbnailCacheStore, storageNodeDao, storageMutationNotifier)
    }

    @Provides
    @Singleton
    fun provideStorageMutationNotifier(
        notifier: DefaultStorageMutationNotifier
    ): StorageMutationNotifier = notifier

    @Provides
    @Singleton
    fun provideMutationJournal(
        @ApplicationContext context: Context,
        volumeProvider: VolumeProvider,
        dispatchers: ArcileDispatchers
    ): MutationJournal {
        return DefaultMutationJournal(context, volumeProvider, dispatchers)
    }

    @Provides
    @Singleton
    fun provideFolderStatsStore(
        @ApplicationContext context: Context,
        folderStatsDao: FolderStatsDao,
        storageWorkCoordinator: StorageWorkCoordinator,
        dispatchers: ArcileDispatchers
    ): FolderStatsStore {
        return DefaultFolderStatsStore(
            context,
            folderStatsDao = folderStatsDao,
            workerScope = CoroutineScope(SupervisorJob() + dispatchers.storage),
            storageWorkCoordinator = storageWorkCoordinator
        )
    }

    @Provides
    @Singleton
    fun provideStorageWorkCoordinator(
        coordinator: DefaultStorageWorkCoordinator
    ): StorageWorkCoordinator {
        return coordinator
    }

    @Provides
    @Singleton
    fun provideLocalFileRepository(
        volumeProvider: VolumeProvider,
        mediaStoreClient: MediaStoreClient,
        trashManager: TrashManager,
        archiveManager: ArchiveManager,
        fileSystemDataSource: FileSystemDataSource,
        folderStatsStore: FolderStatsStore,
        recentFilesSnapshotStore: RecentFilesSnapshotStore,
        @ApplicationScope applicationScope: CoroutineScope,
        dispatchers: ArcileDispatchers
    ): LocalFileRepository {
        return LocalFileRepository(
            volumeProvider,
            mediaStoreClient,
            trashManager,
            fileSystemDataSource,
            folderStatsStore,
            archiveManager,
            recentFilesSnapshotStore,
            applicationScope,
            dispatchers
        )
    }

    @Provides
    @Singleton
    fun provideFileRepository(repository: LocalFileRepository): FileRepository = repository

    @Provides
    @Singleton
    fun provideFileBrowserRepository(repository: LocalFileRepository): FileBrowserRepository = repository

    @Provides
    @Singleton
    fun provideFileMutationRepository(repository: LocalFileRepository): FileMutationRepository = repository

    @Provides
    @Singleton
    fun provideSearchRepository(repository: LocalFileRepository): SearchRepository = repository

    @Provides
    @Singleton
    fun provideStorageAnalyticsRepository(repository: LocalFileRepository): StorageAnalyticsRepository = repository

    @Provides
    @Singleton
    fun provideTrashRepository(repository: LocalFileRepository): TrashRepository = repository

    @Provides
    @Singleton
    fun provideArchiveRepository(repository: LocalFileRepository): ArchiveRepository = repository

    @Provides
    @Singleton
    fun provideVolumeRepository(repository: LocalFileRepository): VolumeRepository = repository

    @Provides
    @Singleton
    fun provideClipboardRepository(repository: LocalFileRepository): ClipboardRepository = repository

    @Provides
    @Singleton
    fun provideStorageUsageScanner(scanner: DefaultStorageUsageScanner): StorageUsageScanner = scanner

    @Provides
    @Singleton
    fun provideStorageCleanerScanner(scanner: DefaultStorageCleanerScanner): StorageCleanerScanner = scanner

    @Provides
    @Singleton
    fun provideImageCatalogRepository(repository: DefaultImageCatalogRepository): ImageCatalogRepository = repository
}
