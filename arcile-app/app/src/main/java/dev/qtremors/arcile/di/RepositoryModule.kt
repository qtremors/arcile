package dev.qtremors.arcile.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.qtremors.arcile.core.storage.data.BrowserPreferencesRepository
import dev.qtremors.arcile.core.storage.data.BrowserPreferencesStore
import dev.qtremors.arcile.core.storage.data.DefaultFolderStatsStore
import dev.qtremors.arcile.core.storage.data.DefaultStorageWorkCoordinator
import dev.qtremors.arcile.core.storage.data.FolderStatsStore
import dev.qtremors.arcile.core.storage.data.LocalFileRepository
import dev.qtremors.arcile.core.storage.data.MutationFinalizer
import dev.qtremors.arcile.core.storage.data.DefaultMutationJournal
import dev.qtremors.arcile.core.storage.data.MutationJournal
import dev.qtremors.arcile.core.storage.data.OnboardingPreferencesRepository
import dev.qtremors.arcile.core.storage.data.OnboardingPreferencesStore
import dev.qtremors.arcile.core.storage.data.StorageClassificationRepository
import dev.qtremors.arcile.core.storage.data.StorageClassificationStore
import dev.qtremors.arcile.core.storage.data.StorageWorkCoordinator
import dev.qtremors.arcile.core.storage.data.manager.DefaultTrashManager
import dev.qtremors.arcile.core.storage.data.manager.DefaultArchiveManager
import dev.qtremors.arcile.core.storage.data.manager.TrashManager
import dev.qtremors.arcile.core.storage.domain.ArchiveManager
import dev.qtremors.arcile.core.storage.domain.ArchiveRepository
import dev.qtremors.arcile.core.storage.domain.ClipboardRepository
import dev.qtremors.arcile.core.storage.domain.FileBrowserRepository
import dev.qtremors.arcile.core.storage.domain.FileMutationRepository
import dev.qtremors.arcile.core.storage.data.provider.DefaultVolumeProvider
import dev.qtremors.arcile.core.storage.data.provider.VolumeProvider
import dev.qtremors.arcile.core.storage.data.source.DefaultFileSystemDataSource
import dev.qtremors.arcile.core.storage.data.source.DefaultMediaStoreClient
import dev.qtremors.arcile.core.storage.data.source.FileSystemDataSource
import dev.qtremors.arcile.core.storage.data.source.MediaStoreClient
import dev.qtremors.arcile.core.storage.domain.FileRepository
import dev.qtremors.arcile.core.storage.domain.SearchRepository
import dev.qtremors.arcile.core.storage.domain.StorageAnalyticsRepository
import dev.qtremors.arcile.core.storage.domain.TrashRepository
import dev.qtremors.arcile.core.storage.domain.VolumeRepository
import dev.qtremors.arcile.presentation.operations.BulkFileOperationCoordinator
import dev.qtremors.arcile.presentation.operations.DefaultOperationJournal
import dev.qtremors.arcile.presentation.operations.ForegroundBulkFileOperationCoordinator
import dev.qtremors.arcile.presentation.operations.OperationJournal
import dev.qtremors.arcile.ui.theme.ThemePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ExperimentalCoroutinesApi
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

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
    fun provideThemePreferences(
        @ApplicationContext context: Context
    ): ThemePreferences {
        return ThemePreferences(context)
    }

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
        dispatchers: ArcileDispatchers
    ): MediaStoreClient {
        return DefaultMediaStoreClient(context, volumeProvider, dispatchers)
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
        dispatchers: ArcileDispatchers
    ): ArchiveManager {
        return DefaultArchiveManager(volumeProvider, mutationFinalizer, dispatchers = dispatchers)
    }

    @Provides
    @Singleton
    fun provideFileSystemDataSource(
        @ApplicationContext context: Context,
        volumeProvider: VolumeProvider,
        mutationFinalizer: MutationFinalizer,
        mutationJournal: MutationJournal,
        dispatchers: ArcileDispatchers
    ): FileSystemDataSource {
        return DefaultFileSystemDataSource(
            context,
            volumeProvider,
            mutationFinalizer,
            dispatchers = dispatchers,
            mutationJournal = mutationJournal
        )
    }

    @Provides
    @Singleton
    fun provideMutationFinalizer(
        @ApplicationContext context: Context,
        mediaStoreClient: MediaStoreClient,
        volumeProvider: VolumeProvider,
        folderStatsStore: FolderStatsStore
    ): MutationFinalizer {
        return MutationFinalizer(context, mediaStoreClient, volumeProvider, folderStatsStore)
    }

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
        storageWorkCoordinator: StorageWorkCoordinator,
        dispatchers: ArcileDispatchers
    ): FolderStatsStore {
        return DefaultFolderStatsStore(
            context,
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
        dispatchers: ArcileDispatchers
    ): LocalFileRepository {
        return LocalFileRepository(
            volumeProvider,
            mediaStoreClient,
            trashManager,
            fileSystemDataSource,
            folderStatsStore,
            archiveManager,
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
    fun provideBrowserPreferencesRepository(
        @ApplicationContext context: Context,
        dispatchers: ArcileDispatchers
    ): BrowserPreferencesRepository {
        return BrowserPreferencesRepository(context, dispatchers = dispatchers)
    }

    @Provides
    @Singleton
    fun provideBrowserPreferencesStore(
        repository: BrowserPreferencesRepository
    ): BrowserPreferencesStore {
        return repository
    }

    @Provides
    @Singleton
    fun provideOnboardingPreferencesRepository(
        @ApplicationContext context: Context,
        dispatchers: ArcileDispatchers
    ): OnboardingPreferencesRepository {
        return OnboardingPreferencesRepository(context, dispatchers = dispatchers)
    }

    @Provides
    @Singleton
    fun provideOnboardingPreferencesStore(
        repository: OnboardingPreferencesRepository
    ): OnboardingPreferencesStore {
        return repository
    }

    @Provides
    @Singleton
    fun provideBulkFileOperationCoordinator(
        coordinator: ForegroundBulkFileOperationCoordinator
    ): BulkFileOperationCoordinator {
        return coordinator
    }

    @Provides
    @Singleton
    fun provideOperationJournal(
        @ApplicationContext context: Context
    ): OperationJournal {
        return DefaultOperationJournal(context)
    }

    @Provides
    @Singleton
    fun provideQuickAccessPreferencesRepository(
        @ApplicationContext context: Context
    ): dev.qtremors.arcile.core.storage.data.QuickAccessPreferencesRepository {
        return dev.qtremors.arcile.core.storage.data.QuickAccessPreferencesRepository(context)
    }
}
