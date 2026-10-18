package dev.qtremors.arcile.feature.onlyfiles

import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OnlyFilesBrowserImportsTest {
    @Test
    fun `route exposes only a single-use opaque identifier and keeps selection order`() {
        val first = "/storage/shared/private-name.txt"
        val second = "/storage/shared/folder"
        val route = requireNotNull(createOnlyFilesImportRoute(listOf(first, second, first)))
        val id = requireNotNull(route.importRequestId)

        assertNull(route.vaultId)
        assertNull(route.directoryId)
        assertFalse(route.toString().contains(first))
        assertTrue(id.length >= 32)
        assertEquals(listOf(first, second), OnlyFilesBrowserImports.take(id))
        assertNull(OnlyFilesBrowserImports.take(id))
    }

    @Test
    fun `expired requests cannot recover local source paths`() {
        val route = requireNotNull(createOnlyFilesImportRoute(listOf("/storage/shared/a.txt")))
        ShadowSystemClock.advanceBy(Duration.ofMinutes(1))
        assertNull(OnlyFilesBrowserImports.take(requireNotNull(route.importRequestId)))
    }

    @Test
    fun `virtual and malformed source references reject the whole handoff`() {
        assertNull(createOnlyFilesImportRoute(emptyList()))
        assertNull(createOnlyFilesImportRoute(listOf("content://documents/document/item")))
        assertNull(createOnlyFilesImportRoute(listOf("/storage/shared/ok.txt", "/storage/shared/bad\u0000.txt")))
    }

    @Test
    fun `registering many requests discards the oldest unclaimed selection`() {
        val ids = (0..8).map { requireNotNull(OnlyFilesBrowserImports.register(listOf("/storage/item-$it"))) }
        assertNull(OnlyFilesBrowserImports.take(ids.first()))
        ids.drop(1).forEachIndexed { index, id ->
            assertEquals(listOf("/storage/item-${index + 1}"), OnlyFilesBrowserImports.take(id))
        }
    }
}
