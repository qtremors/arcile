package dev.qtremors.arcile.core.storage.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class BackupRulesTest {
    @Test
    fun `backup rules exclude local private state`() {
        val excludes = excludesFrom("src/main/res/xml/backup_rules.xml")
        val excludedPaths = excludes.map { it.path }.toSet()

        assertTrue("datastore should be excluded", "datastore" in excludedPaths)
        assertTrue("browser preferences should be excluded", "datastore/browser_prefs.preferences_pb" in excludedPaths)
        assertTrue("quick access preferences should be excluded", "datastore/quick_access_prefs.preferences_pb" in excludedPaths)
        assertTrue("storage classifications should be excluded", "datastore/storage_classifications_prefs.preferences_pb" in excludedPaths)
        assertTrue("activity log should be excluded", "datastore/activity_log.preferences_pb" in excludedPaths)
        assertTrue("operation journal should be excluded", "operation_journal" in excludedPaths)
        assertTrue("Arcile storage metadata should be excluded", ".arcile" in excludedPaths)
        assertTrue(
            "Room cache database should be excluded",
            excludes.any { it.domain == "database" && it.path == "arcile-cache.db" }
        )
    }

    @Test
    fun `data extraction rules exclude local private state for backup and transfer`() {
        val excludes = excludesFrom("src/main/res/xml/data_extraction_rules.xml")
        val excludedPaths = excludes.map { it.path }.toSet()

        assertTrue("datastore should be excluded", "datastore" in excludedPaths)
        assertTrue("browser preferences should be excluded", "datastore/browser_prefs.preferences_pb" in excludedPaths)
        assertTrue("quick access preferences should be excluded", "datastore/quick_access_prefs.preferences_pb" in excludedPaths)
        assertTrue("storage classifications should be excluded", "datastore/storage_classifications_prefs.preferences_pb" in excludedPaths)
        assertTrue("activity log should be excluded", "datastore/activity_log.preferences_pb" in excludedPaths)
        assertTrue("operation journal should be excluded", "operation_journal" in excludedPaths)
        assertTrue("Arcile storage metadata should be excluded", ".arcile" in excludedPaths)
        assertTrue(
            "Room cache database should be excluded from cloud backup",
            excludes.any { it.parent == "cloud-backup" && it.domain == "database" && it.path == "arcile-cache.db" }
        )
        assertTrue(
            "Room cache database should be excluded from device transfer",
            excludes.any { it.parent == "device-transfer" && it.domain == "database" && it.path == "arcile-cache.db" }
        )
    }

    private fun excludesFrom(path: String): Set<ExcludeRule> {
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(File(path))
        val excludes = document.getElementsByTagName("exclude")
        return buildSet {
            for (index in 0 until excludes.length) {
                val node = excludes.item(index)
                val domainValue = node.attributes?.getNamedItem("domain")?.nodeValue
                val pathValue = node.attributes?.getNamedItem("path")?.nodeValue
                if (domainValue != null && pathValue != null) {
                    add(
                        ExcludeRule(
                            parent = node.parentNode.nodeName,
                            domain = domainValue,
                            path = pathValue
                        )
                    )
                }
            }
        }
    }

    private data class ExcludeRule(
        val parent: String,
        val domain: String,
        val path: String
    )
}
