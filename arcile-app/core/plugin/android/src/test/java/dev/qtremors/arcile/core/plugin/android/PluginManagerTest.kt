package dev.qtremors.arcile.core.plugin.android

import android.content.Intent
import android.net.Uri
import dev.qtremors.arcile.plugin.api.PluginCompatibility
import dev.qtremors.arcile.plugin.api.PluginContract
import dev.qtremors.arcile.plugin.api.PluginMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PluginManagerTest {
    @Test
    fun `metadata values normalize and discard blanks`() {
        assertEquals(setOf("image/tiff", "image/*"), PluginManager.parseCsv(" IMAGE/TIFF, ,image/* "))
        assertEquals("tiff", PluginManager.normalizeExtension(" .TIFF "))
        assertNull(PluginManager.normalizeExtension(" "))
    }

    @Test
    fun `mime wildcard matches only its own type`() {
        assertTrue(PluginManager.wildcardMatches("image/*", "image/tiff"))
        assertFalse(PluginManager.wildcardMatches("image/*", "application/pdf"))
        assertFalse(PluginManager.wildcardMatches("image/tiff", "image/tiff"))
    }

    @Test
    fun `ranking prefers exact mime then extension then wildcard and newer versions`() {
        val wildcard = plugin("wildcard", 5, setOf("image/*"), emptySet())
        val extension = plugin("extension", 1, emptySet(), setOf("tiff"))
        val exactOld = plugin("exact.old", 1, setOf("image/tiff"), emptySet())
        val exactNew = plugin("exact.new", 2, setOf("image/tiff"), emptySet())

        val ranked = PluginManager.rankPlugins(
            listOf(wildcard, extension, exactOld, exactNew),
            "image/tiff",
            "tiff"
        )

        assertEquals(listOf("exact.new", "exact.old", "extension", "wildcard"), ranked.map { it.packageName })
    }

    @Test
    fun `launch intent is explicit and grants the content uri`() {
        val plugin = plugin("dev.example.arcile.plugin", 1, setOf("image/tiff"), setOf("tiff"))
        val uri = Uri.parse("content://dev.qtremors.arcile.externalfileaccess/open/scan.tiff")
        val base = Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, "image/tiff") }

        val launch = buildPluginLaunchIntent(plugin, base, "scan.tiff")

        assertEquals(PluginContract.ACTION_VIEW_FILE, launch.action)
        assertEquals(plugin.packageName, launch.component?.packageName)
        assertEquals(plugin.activityName, launch.component?.className)
        assertEquals(uri, launch.data)
        assertEquals(uri, launch.clipData?.getItemAt(0)?.uri)
        assertEquals("scan.tiff", launch.getStringExtra(PluginContract.EXTRA_FILE_NAME))
        assertTrue(launch.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    private fun plugin(
        packageName: String,
        versionCode: Long,
        mimeTypes: Set<String>,
        extensions: Set<String>
    ) = PluginMetadata(
        name = packageName,
        packageName = packageName,
        activityName = "$packageName.ViewerActivity",
        versionName = versionCode.toString(),
        versionCode = versionCode,
        apiVersion = 1,
        supportedMimeTypes = mimeTypes,
        supportedExtensions = extensions,
        homepage = null,
        compatibility = PluginCompatibility.COMPATIBLE
    )
}
