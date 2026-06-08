package dev.qtremors.arcile.image

import dev.qtremors.arcile.core.storage.domain.BrowserViewMode
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThumbnailPolicyTest {
    private val failureCache = ThumbnailFailureCache()
    private val loadStateStore = ThumbnailLoadStateStore()
    private val policy = ThumbnailPolicy(
        failureCache = failureCache,
        loadStateStore = loadStateStore,
        bufferItems = 2
    )

    @Test
    fun `blocks thumbnails when user preference is disabled`() {
        assertFalse(policy.shouldLoad(input(userEnabled = false)))
    }

    @Test
    fun `blocks thumbnails while file operation is active`() {
        assertFalse(policy.shouldLoad(input(isOperationActive = true)))
    }

    @Test
    fun `allows visible and buffered items`() {
        assertTrue(policy.shouldLoad(input(itemIndex = 8, visibleRange = 10..15)))
        assertTrue(policy.shouldLoad(input(itemIndex = 16, visibleRange = 10..15)))
    }

    @Test
    fun `blocks items outside visible budget`() {
        assertFalse(policy.shouldLoad(input(itemIndex = 3, visibleRange = 10..15)))
        assertFalse(policy.shouldLoad(input(itemIndex = 20, visibleRange = 10..15)))
    }

    @Test
    fun `blocks unsafe expensive thumbnail sizes`() {
        assertFalse(
            policy.shouldLoad(
                input(
                    thumbnailSizePx = 720,
                    key = key(path = "/storage/emulated/0/Docs/report.pdf", extension = "pdf")
                )
            )
        )
    }

    @Test
    fun `blocks failed keys until failure is cleared`() {
        val key = key()
        policy.recordFailure(key, thumbnailSizePx = 192)

        assertFalse(policy.shouldLoad(input(key = key)))

        policy.clearFailure(key)
        loadStateStore.clear()
        assertTrue(policy.shouldLoad(input(key = key)))
    }

    @Test
    fun `loaded variant remains reusable outside visible budget`() {
        val key = key()
        policy.recordLoaded(key, thumbnailSizePx = 192)

        assertTrue(policy.shouldLoad(input(key = key, thumbnailSizePx = 192, itemIndex = 40, visibleRange = 8..12)))
    }

    @Test
    fun `loaded small variant does not satisfy larger variant`() {
        val key = key()
        policy.recordLoaded(key, thumbnailSizePx = 128)

        assertFalse(policy.shouldLoad(input(key = key, thumbnailSizePx = 768, itemIndex = 40, visibleRange = 8..12)))
    }

    @Test
    fun `in flight variant remains visible while inside budget`() {
        val key = key()
        policy.recordInFlight(key, thumbnailSizePx = 256)

        assertTrue(policy.shouldLoad(input(key = key, thumbnailSizePx = 256)))
        assertFalse(policy.shouldLoad(input(key = key, thumbnailSizePx = 256, itemIndex = 40, visibleRange = 8..12)))
        assertTrue(policy.shouldLoad(input(key = key, thumbnailSizePx = 384)))
    }

    @Test
    fun `variant key buckets noisy sizes`() {
        val key = key()

        assertEquals(256, key.variantKey(191).sizeBucketPx)
        assertEquals(key.variantKey(191), key.variantKey(192))
        assertEquals(384, key.variantKey(257).sizeBucketPx)
    }

    @Test
    fun `identity key changes when source metadata changes`() {
        assertEquals(key().identityKey, key().identityKey)
        assertFalse(key().identityKey == key(sizeBytes = 2048L).identityKey)
        assertFalse(key().identityKey == key(lastModifiedMillis = 2L).identityKey)
    }

    @Test
    fun `uses wider initial budget for grid before layout is available`() {
        assertTrue(policy.shouldLoad(input(viewMode = BrowserViewMode.GRID, itemIndex = 24, visibleRange = null)))
        assertFalse(policy.shouldLoad(input(viewMode = BrowserViewMode.LIST, itemIndex = 24, visibleRange = null)))
    }

    @Test
    fun `thumbnail key preserves media store content uri`() {
        val model = FileModel(
            name = "clip.mp4",
            absolutePath = "/storage/emulated/0/Movies/clip.mp4",
            extension = "mp4",
            nodeRef = StorageNodeRef.mediaStore(
                id = 12L,
                volumeName = "external_primary",
                contentUri = "content://media/external_primary/file/12",
                displayPath = "/storage/emulated/0/Movies/clip.mp4"
            )
        )

        val key = ThumbnailKey.from(model)

        assertEquals("content://media/external_primary/file/12", key.contentUri)
        assertTrue(key.cacheKey.contains("content://media/external_primary/file/12"))
    }

    private fun input(
        userEnabled: Boolean = true,
        viewMode: BrowserViewMode = BrowserViewMode.LIST,
        thumbnailSizePx: Int = 192,
        itemIndex: Int = 10,
        visibleRange: IntRange? = 8..12,
        isOperationActive: Boolean = false,
        key: ThumbnailKey = key()
    ): ThumbnailPolicyInput =
        ThumbnailPolicyInput(
            userEnabled = userEnabled,
            viewMode = viewMode,
            thumbnailSizePx = thumbnailSizePx,
            itemIndex = itemIndex,
            visibleRange = visibleRange,
            isOperationActive = isOperationActive,
            key = key
        )

    private fun key(
        path: String = "/storage/emulated/0/Pictures/photo.jpg",
        extension: String = "jpg",
        sizeBytes: Long = 1024L,
        lastModifiedMillis: Long = 1L
    ): ThumbnailKey =
        ThumbnailKey(
            path = path,
            extension = extension,
            sizeBytes = sizeBytes,
            lastModifiedMillis = lastModifiedMillis
        )
}
