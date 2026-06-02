package dev.qtremors.arcile.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.qtremors.arcile.core.operation.BulkFileOperationCoordinator
import dev.qtremors.arcile.presentation.operations.DefaultOperationJournal
import dev.qtremors.arcile.presentation.operations.ForegroundBulkFileOperationCoordinator
import dev.qtremors.arcile.presentation.operations.OperationJournal
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DeferOperationJournalRecovery

@Module
@InstallIn(SingletonComponent::class)
object OperationModule {

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
    @DeferOperationJournalRecovery
    fun provideDeferOperationJournalRecovery(): Boolean = true
}
