package dev.qtremors.arcile.presentation.ui

import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.plugin.android.PluginCatalogEntry
import dev.qtremors.arcile.core.plugin.android.PluginFileResolution
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AppFileOpenResolverTest {

    @Test
    fun `installed plugin launch handles file without fallback`() = runTest {
        val resolver = resolver(PluginFileResolution.Launched)

        val result = resolver.resolve("/storage/document.pdf", emptyList())

        assertSame(AppFileOpenResolution.Handled, result)
    }

    @Test
    fun `missing plugin is returned as a prompt`() = runTest {
        val catalogEntry = PluginCatalogEntry(
            name = "TIFF Viewer",
            packageName = "dev.example.viewer",
            supportedMimeTypes = setOf("image/tiff"),
            supportedExtensions = setOf("tiff"),
            available = true
        )
        val prompt = PluginFileResolution.Missing(catalogEntry)
        val resolver = resolver(prompt)

        val result = resolver.resolve("/storage/scan.tiff", emptyList())

        assertEquals(AppFileOpenResolution.PluginPrompt(prompt), result)
    }

    @Test
    fun `browsable archive resolves to archive destination`() = runTest {
        val result = resolver().resolve("/storage/files.zip", emptyList())

        assertEquals(AppFileOpenResolution.BrowseArchive("/storage/files.zip"), result)
    }

    @Test
    fun `unsupported archive resolves to an error outcome`() = runTest {
        val result = resolver().resolve("/storage/files.rar", emptyList())

        assertSame(AppFileOpenResolution.UnsupportedArchive, result)
    }

    @Test
    fun `image resolution keeps only unique image context paths`() = runTest {
        val files = listOf(
            file("/storage/a.jpg", "jpg", "image/jpeg"),
            file("/storage/folder", "", null, isDirectory = true),
            file("/storage/readme.txt", "txt", "text/plain"),
            file("/storage/a.jpg", "jpg", "image/jpeg"),
            file("/storage/b.png", "png", "image/png")
        )

        val result = resolver().resolve("/storage/a.jpg", files)

        assertEquals(
            AppFileOpenResolution.ViewImage(
                path = "/storage/a.jpg",
                contextPaths = listOf("/storage/a.jpg", "/storage/b.png")
            ),
            result
        )
    }

    @Test
    fun `ordinary file resolves to external opening`() = runTest {
        val result = resolver().resolve("/storage/report.pdf", emptyList())

        assertEquals(AppFileOpenResolution.External("/storage/report.pdf"), result)
    }

    @Test
    fun `video resolves to Arcile video viewer`() = runTest {
        val result = resolver().resolve("/storage/movie.mp4", emptyList())

        assertEquals(AppFileOpenResolution.ViewVideo("/storage/movie.mp4"), result)
    }

    @Test
    fun `known metadata is forwarded to plugin gateway`() = runTest {
        var request: Triple<String, String?, String>? = null
        val resolver = AppFileOpenResolver(
            pluginGateway = PluginFileResolutionGateway { path, mimeType, extension ->
                request = Triple(path, mimeType, extension)
                PluginFileResolution.NotApplicable
            },
            mimeTypeForExtension = { error("Known MIME type should be used") }
        )

        resolver.resolve(
            path = "/storage/photo.unknown",
            surroundingFiles = listOf(file("/storage/photo.unknown", "JPG", "image/jpeg"))
        )

        assertEquals(Triple("/storage/photo.unknown", "image/jpeg", "jpg"), request)
        assertTrue(request != null)
    }

    private fun resolver(
        pluginResolution: PluginFileResolution = PluginFileResolution.NotApplicable
    ) = AppFileOpenResolver(
        pluginGateway = PluginFileResolutionGateway { _, _, _ -> pluginResolution },
        mimeTypeForExtension = { extension ->
            when (extension) {
                "jpg" -> "image/jpeg"
                "png" -> "image/png"
                "pdf" -> "application/pdf"
                else -> null
            }
        }
    )

    private fun file(
        path: String,
        extension: String,
        mimeType: String?,
        isDirectory: Boolean = false
    ) = FileModel(
        name = path.substringAfterLast('/'),
        absolutePath = path,
        size = 1L,
        lastModified = 0L,
        isDirectory = isDirectory,
        extension = extension,
        mimeType = mimeType
    )
}
