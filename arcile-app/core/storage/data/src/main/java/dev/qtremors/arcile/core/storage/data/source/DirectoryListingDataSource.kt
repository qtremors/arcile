package dev.qtremors.arcile.core.storage.data.source

import dev.qtremors.arcile.core.storage.domain.ListingPage
import dev.qtremors.arcile.core.storage.domain.StorageNodePath
import kotlinx.coroutines.flow.Flow

interface DirectoryListingDataSource {
    fun list(
        path: StorageNodePath,
        pageSize: Int = ListingPage.DEFAULT_PAGE_SIZE
    ): Flow<ListingPage>
}
