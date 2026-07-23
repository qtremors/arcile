package dev.qtremors.arcile.core.storage.domain

/** Marker preventing sensitive thumbnail source bytes from entering a generic disk cache. */
interface SensitiveThumbnailRequest {
    val memoryCacheKey: String
}

data class VaultThumbnailRequest(
    val vaultId: String,
    val nodeId: String,
    val parentId: String,
    val revision: Long,
    val requestedSizePx: Int
) : SensitiveThumbnailRequest {
    init {
        require(vaultId.isNotBlank() && nodeId.isNotBlank() && parentId.isNotBlank())
        require(revision >= 0L)
        require(requestedSizePx in 1..4096)
    }

    override val memoryCacheKey: String =
        "vault-thumbnail:$vaultId:$nodeId:$revision:${sizeBucket(requestedSizePx)}"

    companion object {
        fun sizeBucket(sizePx: Int): Int {
            require(sizePx > 0)
            return when {
                sizePx <= 96 -> 96
                sizePx <= 192 -> 192
                sizePx <= 384 -> 384
                sizePx <= 768 -> 768
                else -> 1536
            }
        }
    }
}
