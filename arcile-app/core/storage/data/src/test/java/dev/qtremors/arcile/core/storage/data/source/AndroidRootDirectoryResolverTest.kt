package dev.qtremors.arcile.core.storage.data.source

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidRootDirectoryResolverTest {
    @Test
    fun `root discovery is cached after first probe`() {
        val probeCounts = mutableMapOf<String, Int>()
        val resolver = AndroidRootDirectoryResolver(
            entryNames = listOf("system", "metadata", "storage"),
            entryExists = { entry ->
                probeCounts[entry.name] = probeCounts.getOrDefault(entry.name, 0) + 1
                entry.name != "metadata"
            }
        )
        val root = File(File.separator)

        val first = resolver.children(root).map(File::getName)
        val second = resolver.children(root).map(File::getName)

        assertEquals(listOf("system", "storage"), first)
        assertEquals(first, second)
        assertEquals(mapOf("system" to 1, "metadata" to 1, "storage" to 1), probeCounts)
    }
}
