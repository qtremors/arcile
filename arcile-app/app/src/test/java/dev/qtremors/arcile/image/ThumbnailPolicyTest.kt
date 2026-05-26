package dev.qtremors.arcile.image

import dev.qtremors.arcile.core.storage.domain.BrowserViewMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThumbnailPolicyTest {
    private val failureCache = ThumbnailFailureCache()
    private val policy = ThumbnailPolicy(failureCache = failureCache, bufferItems = 2)

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
        policy.recordFailure(key)

        assertFalse(policy.shouldLoad(input(key = key)))

        policy.clearFailure(key)
        assertTrue(policy.shouldLoad(input(key = key)))
    }

    @Test
    fun `uses wider initial budget for grid before layout is available`() {
        assertTrue(policy.shouldLoad(input(viewMode = BrowserViewMode.GRID, itemIndex = 24, visibleRange = null)))
        assertFalse(policy.shouldLoad(input(viewMode = BrowserViewMode.LIST, itemIndex = 24, visibleRange = null)))
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
