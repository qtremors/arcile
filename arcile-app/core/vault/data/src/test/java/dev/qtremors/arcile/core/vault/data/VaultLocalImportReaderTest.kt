package dev.qtremors.arcile.core.vault.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VaultLocalImportReaderTest {
    @Test
    fun `Arcile local folder selection is traversed without a document provider`() {
        val root = Files.createTempDirectory("arcile-local-import").toFile()
        val nested = root.resolve("nested").also { assertTrue(it.mkdir()) }
        root.resolve("one.txt").writeText("one")
        nested.resolve("two.txt").writeText("two")
        val context = ApplicationProvider.getApplicationContext<Context>()

        val sources = VaultUriTreeReader(context.contentResolver).collect(listOf(root.absolutePath))

        assertEquals(4, sources.size)
        assertTrue(sources.any { it.isDirectory && it.relativeParent == listOf(root.name) })
        assertTrue(sources.any { !it.isDirectory && it.name == "one.txt" && it.relativeParent == listOf(root.name) })
        assertTrue(sources.any { !it.isDirectory && it.name == "two.txt" && it.relativeParent == listOf(root.name, "nested") })
        root.deleteRecursively()
    }
}
