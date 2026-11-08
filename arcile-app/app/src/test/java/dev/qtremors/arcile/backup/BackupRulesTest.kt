package dev.qtremors.arcile.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class BackupRulesTest {
    private val legacyRules = excludesFrom("src/main/res/xml/backup_rules.xml")
    private val extractionRules = excludesFrom("src/main/res/xml/data_extraction_rules.xml")

    @Test
    fun `backup rules exclude local private state`() {
        assertPrivateStateExcluded(legacyRules)
    }

    @Test
    fun `data extraction rules exclude local private state for backup and transfer`() {
        listOf("cloud-backup", "device-transfer").forEach { parent ->
            assertPrivateStateExcluded(extractionRules.filterTo(mutableSetOf()) { it.parent == parent })
        }
    }

    @Test
    fun `all backup paths list the same exclusions`() {
        val legacy = legacyRules.map { it.domain to it.path }.toSet()
        listOf("cloud-backup", "device-transfer").forEach { parent ->
            assertEquals(
                "$parent must match backup_rules.xml",
                legacy,
                extractionRules.filter { it.parent == parent }.map { it.domain to it.path }.toSet()
            )
        }
    }

    private fun assertPrivateStateExcluded(excludes: Set<ExcludeRule>) {
        // Excluding the folder covers every DataStore file, including ones added later.
        assertTrue("datastore should be excluded", excludes.any { it.domain == "file" && it.path == "datastore" })
        setOf(
            "trash_crypto_prefs.xml",
            "operation_journal.xml",
            "mutation_journal.xml",
            "onlyfiles_vault_locations.xml"
        ).forEach { path ->
            assertTrue(
                "$path should be excluded from shared preferences",
                excludes.any { it.domain == "sharedpref" && it.path == path }
            )
        }
        assertTrue("Arcile storage metadata should be excluded", excludes.any { it.path == ".arcile" })
        assertTrue(
            "Room cache database should be excluded",
            excludes.any { it.domain == "database" && it.path == "arcile-cache.db" }
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
                    add(ExcludeRule(node.parentNode.nodeName, domainValue, pathValue))
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
