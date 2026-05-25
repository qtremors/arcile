package dev.qtremors.arcile.domain

import androidx.compose.runtime.Immutable

@Immutable
data class ListingPage(
    val path: StorageNodePath,
    val files: List<FileModel>,
    val pageIndex: Int,
    val isComplete: Boolean,
    val error: Throwable? = null
) {
    companion object {
        const val DEFAULT_PAGE_SIZE = 256
        const val MAX_PAGE_SIZE = 2_000

        fun failed(path: StorageNodePath, error: Throwable): ListingPage =
            ListingPage(
                path = path,
                files = emptyList(),
                pageIndex = 0,
                isComplete = true,
                error = error
            )
    }
}

