package dev.qtremors.arcile

import java.io.File
import org.junit.Assert.fail
import org.junit.Test

class ArchitectureBoundaryTest {
    @Test
    fun `feature packages do not import concrete storage data implementations`() {
        val sourceRoot = sourceRoot("feature")
        val concreteDataImport = Regex("""import dev\.qtremors\.arcile\.core\.storage\.data\.(.+)""")
        val allowedStorageDataImports = setOf(
            "BrowserPreferencesStore"
        )
        val offenders = sourceRoot.kotlinFiles()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    val importedName = concreteDataImport.matchEntire(line.trim())?.groupValues?.get(1)
                    if (importedName != null && importedName !in allowedStorageDataImports) {
                        violation(file, index, line)
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

    @Test
    fun `presentation packages do not import concrete storage data implementations except public app services`() {
        val sourceRoot = sourceRoot("presentation")
        val concreteDataImport = Regex("""import dev\.qtremors\.arcile\.core\.storage\.data\.(.+)""")
        val allowedStorageDataImports = setOf(
            "BrowserPreferencesStore",
            "OnboardingPreferencesStore",
            "QuickAccessPreferencesRepository",
            "StorageClassificationStore",
            "StorageCleanerScanner",
            "StorageUsageScanner",
            "StorageWorkCoordinator"
        )
        val offenders = sourceRoot.kotlinFiles()
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    val importedName = concreteDataImport.matchEntire(line.trim())?.groupValues?.get(1)
                    if (importedName != null && importedName !in allowedStorageDataImports) {
                        violation(file, index, line)
                    } else {
                        null
                    }
                }
            }
            .toList()

        if (offenders.isNotEmpty()) {
            fail("Presentation packages must depend on storage public APIs, not concrete data implementations:\n${offenders.joinToString("\n")}")
        }
    }

    @Test
    fun `core packages do not import presentation or feature packages`() {
        val sourceRoot = sourceRoot("core")
        val forbiddenImport = Regex("""import dev\.qtremors\.arcile\.(presentation|feature)\..+""")
        val offenders = sourceRoot.kotlinFiles()
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    if (forbiddenImport.matches(line.trim())) violation(file, index, line) else null
                }
            }
            .toList()

        if (offenders.isNotEmpty()) {
            fail("Core packages must not depend on presentation or feature packages:\n${offenders.joinToString("\n")}")
        }
    }

    @Test
    fun `large files stay within the architecture budget`() {
        val projectRoot = projectRoot()
        val allowedLargeFiles = setOf(
            "app/src/main/java/dev/qtremors/arcile/feature/browser/BrowserViewModel.kt",
            "app/src/main/java/dev/qtremors/arcile/presentation/ui/RecentFilesScreen.kt",
            "app/src/main/java/dev/qtremors/arcile/core/storage/data/source/MediaStoreClient.kt",
            "app/src/test/java/dev/qtremors/arcile/feature/browser/BrowserViewModelTest.kt"
        )
        val roots = listOf(
            File(projectRoot, "app/src/main/java"),
            File(projectRoot, "app/src/test/java"),
            File(projectRoot.parentFile ?: projectRoot, "docs")
        )
        val offenders = roots
            .filter { it.exists() }
            .flatMap { root ->
                root.walkTopDown()
                    .filter { it.isFile && (it.extension == "kt" || it.extension == "html") }
                    .mapNotNull { file ->
                        val relativePath = if (file.absolutePath.startsWith(projectRoot.absolutePath)) {
                            file.relativeTo(projectRoot).invariantSeparatorsPath
                        } else {
                            file.relativeTo(projectRoot.parentFile ?: projectRoot).invariantSeparatorsPath
                        }
                        val lineCount = file.readLines().size
                        if (lineCount > MAX_FILE_LINES && relativePath !in allowedLargeFiles) {
                            "$relativePath: $lineCount lines"
                        } else {
                            null
                        }
                    }
            }

        if (offenders.isNotEmpty()) {
            fail("Files above $MAX_FILE_LINES lines must be split or explicitly allowlisted:\n${offenders.joinToString("\n")}")
        }
    }

    private fun sourceRoot(packageName: String): File =
        File(projectRoot(), "app/src/main/java/dev/qtremors/arcile/$packageName")

    private fun projectRoot(): File =
        generateSequence(File(System.getProperty("user.dir") ?: ".").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "app/src/main/java/dev/qtremors/arcile").exists() }
            ?: File(".").absoluteFile

    private fun File.kotlinFiles(): Sequence<File> =
        walkTopDown().filter { it.isFile && it.extension == "kt" }

    private fun violation(file: File, index: Int, line: String): String =
        "${file.relativeTo(File(projectRoot(), "app/src/main/java")).invariantSeparatorsPath}:${index + 1}: ${line.trim()}"

    private companion object {
        const val MAX_FILE_LINES = 700
    }
}
