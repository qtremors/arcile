package dev.qtremors.arcile.core.vault.data

import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import dev.qtremors.arcile.core.storage.domain.VaultThumbnailRequest
import dev.qtremors.arcile.core.vault.domain.DirectoryId
import dev.qtremors.arcile.core.vault.domain.NodeId
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultNodeCapabilities
import dev.qtremors.arcile.core.vault.domain.VaultNodeRef
import dev.qtremors.arcile.core.vault.domain.VaultThumbnailCache

class VaultThumbnailFetcher(
    private val request: VaultThumbnailRequest,
    private val options: Options,
    private val cache: VaultThumbnailCache
) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        val ref = VaultNodeRef(
            VaultId.of(request.vaultId), NodeId.of(request.nodeId), DirectoryId.of(request.parentId), VaultNodeCapabilities()
        )
        val bytes = cache.loadOrCreate(ref, request.revision, request.requestedSizePx).getOrNull() ?: return null
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        return DrawableResult(BitmapDrawable(options.context.resources, bitmap), true, DataSource.DISK)
    }

    class Factory(private val cache: VaultThumbnailCache) : Fetcher.Factory<VaultThumbnailRequest> {
        override fun create(data: VaultThumbnailRequest, options: Options, imageLoader: ImageLoader): Fetcher =
            VaultThumbnailFetcher(data, options, cache)
    }
}
