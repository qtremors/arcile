package dev.qtremors.arcile

import java.io.File
import org.junit.Assert.fail
import org.junit.Test

class ArchitectureBoundaryTest {
    @Test
    fun `feature packages do not import concrete storage data implementations`() {
        val sourceRoot = generateSequence(File(System.getProperty("user.dir") ?: ".").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main/java/dev/qtremors/arcile/feature") }
            .firstOrNull { it.exists() }
            ?: File("src/main/java/dev/qtremors/arcile/feature")
        val concreteDataImport = Regex("""import dev\.qtremors\.arcile\.core\.storage\.data\.(?!.*Store\b).+""")
        val offenders = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    if (concreteDataImport.matches(line.trim())) {
                        "${file.relativeTo(File("src/main/java")).invariantSeparatorsPath}:${index + 1}: ${line.trim()}"
                    } else {
                        null
                    }
                }
            }
            .toList()

        if (offenders.isNotEmpty()) {
            fail("Feature packages must depend on storage public APIs, not concrete data implementations:\n${offenders.joinToString("\n")}")
        }
    }
}
