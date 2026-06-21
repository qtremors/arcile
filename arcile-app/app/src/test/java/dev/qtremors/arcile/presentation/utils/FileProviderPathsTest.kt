package dev.qtremors.arcile.presentation.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class FileProviderPathsTest {
    @Test
    fun `file provider paths only expose staged cache handoffs`() {
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(File("src/main/res/xml/file_provider_paths.xml"))
        val rootPaths = document.getElementsByTagName("root-path")
        val externalPaths = document.getElementsByTagName("external-path")
        val cachePaths = document.getElementsByTagName("cache-path")

        assertFalse("root-path must not be exposed", rootPaths.length > 0)
        for (index in 0 until externalPaths.length) {
            val path = externalPaths.item(index).attributes?.getNamedItem("path")?.nodeValue
            assertFalse("external-path '.' must not be exposed", path == ".")
            assertFalse("external-path '/' must not be exposed", path == "/")
        }

        var hasExternalAccessCache = false
        for (index in 0 until cachePaths.length) {
            val node = cachePaths.item(index)
            val name = node.attributes?.getNamedItem("name")?.nodeValue
            val path = node.attributes?.getNamedItem("path")?.nodeValue
            hasExternalAccessCache = hasExternalAccessCache || (name == "external_access" && path == "external_access/")
        }
        assertTrue("external access cache staging root should remain available", hasExternalAccessCache)
    }
}
