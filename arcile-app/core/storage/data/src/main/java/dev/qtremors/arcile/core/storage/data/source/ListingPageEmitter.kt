package dev.qtremors.arcile.core.storage.data.source

import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.ListingPage
import dev.qtremors.arcile.core.storage.domain.StorageNodePath
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal suspend fun emitListingPages(
    path: StorageNodePath,
    files: List<FileModel>,
    pageSize: Int,
    emitPage: suspend (ListingPage) -> Unit
) {
    if (files.isEmpty()) {
        emitPage(ListingPage(path = path, files = emptyList(), pageIndex = 0, isComplete = true))
        return
    }
    val boundedPageSize = pageSize.coerceIn(1, ListingPage.MAX_PAGE_SIZE)
    files.chunked(boundedPageSize).forEachIndexed { index, chunk ->
        currentCoroutineContext().ensureActive()
        emitPage(
            ListingPage(
                path = path,
                files = chunk,
                pageIndex = index,
                isComplete = (index + 1) * boundedPageSize >= files.size
            )
        )
    }
}
