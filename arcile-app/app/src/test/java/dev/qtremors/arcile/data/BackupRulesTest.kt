package dev.qtremors.arcile.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class BackupRulesTest {
    @Test
    fun `backup rules exclude local private state`() {
        val excludedPaths = excludedPathsFrom("src/main/res/xml/backup_rules.xml")

        assertTrue("datastore should be excluded", "datastore" in excludedPaths)
        assertTrue("browser preferences should be excluded", "datastore/browser_prefs.preferences_pb" in excludedPaths)
        assertTrue("quick access preferences should be excluded", "datastore/quick_access_prefs.preferences_pb" in excludedPaths)
        assertTrue("storage classifications should be excluded", "datastore/storage_classifications_prefs.preferences_pb" in excludedPaths)
        assertTrue("operation journal should be excluded", "operation_journal" in excludedPaths)
        assertTrue("Arcile storage metadata should be excluded", ".arcile" in excludedPaths)
    }

    @Test
    fun `data extraction rules exclude local private state for backup and transfer`() {
        val excludedPaths = excludedPathsFrom("src/main/res/xml/data_extraction_rules.xml")

        assertTrue("datastore should be excluded", "datastore" in excludedPaths)
        assertTrue("browser preferences should be excluded", "datastore/browser_prefs.preferences_pb" in excludedPaths)
        assertTrue("quick access preferences should be excluded", "datastore/quick_access_prefs.preferences_pb" in excludedPaths)
        assertTrue("storage classifications should be excluded", "datastore/storage_classifications_prefs.preferences_pb" in excludedPaths)
        assertTrue("operation journal should be excluded", "operation_journal" in excludedPaths)
        assertTrue("Arcile storage metadata should be excluded", ".arcile" in excludedPaths)
    }

    private fun excludedPathsFrom(path: String): Set<String> {
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(File(path))
        val excludes = document.getElementsByTagName("exclude")
        return buildSet {
            for (index in 0 until excludes.length) {
                val node = excludes.item(index)
                val pathValue = node.attributes?.getNamedItem("path")?.nodeValue
                if (pathValue != null) add(pathValue)
            }
        }
    }
}
