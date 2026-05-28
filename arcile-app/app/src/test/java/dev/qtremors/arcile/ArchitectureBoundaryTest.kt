package dev.qtremors.arcile

import java.io.File
import org.junit.Assert.fail
import org.junit.Test

class ArchitectureBoundaryTest {
    @Test
    fun `feature packages do not import concrete storage data implementations`() {
        val sourceRoot = sourceRoot("feature")
        val concreteDataImport = Regex("""import dev\.qtremors\.arcile\.core\.storage\.data\.(.+)""")
        val allowedStorageDataImports = emptySet<String>()
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
    fun `feature packages do not import presentation internals`() {
        val sourceRoot = sourceRoot("feature")
        val forbiddenImport = Regex("""import dev\.qtremors\.arcile\.presentation\..+""")
        val offenders = sourceRoot.kotlinFiles()
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    if (forbiddenImport.matches(line.trim())) violation(file, index, line) else null
                }
            }
            .toList()

        if (offenders.isNotEmpty()) {
            fail("Feature packages must depend on core/shared or their own feature contracts, not presentation internals:\n${offenders.joinToString("\n")}")
        }
    }

    @Test
    fun `presentation shell imports only approved feature entry points`() {
        val sourceRoot = sourceRoot("presentation")
        val featureImport = Regex("""import dev\.qtremors\.arcile\.feature\.(.+)""")
        val allowedFeatureImports = setOf(
            "archive.ArchiveViewerScreen",
            "archive.ArchiveViewerViewModel",
            "browser.BrowserViewModel",
            "browser.ui.BrowserScreen",
            "quickaccess.QuickAccessScreen",
            "quickaccess.QuickAccessViewModel",
            "recentfiles.RecentFilesViewModel",
            "recentfiles.ui.RecentFilesScreen",
            "storagecleaner.StorageCleanerViewModel",
            "storagecleaner.ui.StorageCleanerScreen",
            "storageusage.StorageUsageViewModel",
            "storageusage.ui.StorageUsageMap",
            "trash.TrashScreen",
            "trash.TrashViewModel"
        )
        val allowedShellFiles = setOf(
            "dev/qtremors/arcile/presentation/ui/AppNavigationGraph.kt",
            "dev/qtremors/arcile/presentation/ui/ArcileAppShell.kt",
            "dev/qtremors/arcile/presentation/ui/StorageDashboardScreen.kt"
        )
        val offenders = sourceRoot.kotlinFiles()
            .flatMap { file ->
                val relativePath = file.relativeTo(File(projectRoot(), "app/src/main/java")).invariantSeparatorsPath
                file.readLines().mapIndexedNotNull { index, line ->
                    val importedName = featureImport.matchEntire(line.trim())?.groupValues?.get(1)
                    if (importedName != null &&
                        (relativePath !in allowedShellFiles || importedName !in allowedFeatureImports)
                    ) {
                        violation(file, index, line)
                    } else {
                        null
                    }
                }
            }
            .toList()

        if (offenders.isNotEmpty()) {
            fail("Presentation may import feature code only from approved app-shell composition files:\n${offenders.joinToString("\n")}")
        }
    }

    @Test
    fun `feature packages do not import unrelated feature packages`() {
        val sourceRoot = sourceRoot("feature")
        val featureImport = Regex("""import dev\.qtremors\.arcile\.feature\.([^.]+)\..+""")
        val offenders = sourceRoot.kotlinFiles()
            .flatMap { file ->
                val relativePath = file.relativeTo(sourceRoot).invariantSeparatorsPath
                val owningFeature = relativePath.substringBefore('/', missingDelimiterValue = "")
                file.readLines().mapIndexedNotNull { index, line ->
                    val importedFeature = featureImport.matchEntire(line.trim())?.groupValues?.get(1)
                    if (importedFeature != null && importedFeature != owningFeature) {
                        violation(file, index, line)
                    } else {
                        null
                    }
                }
            }
            .toList()

        if (offenders.isNotEmpty()) {
            fail("Feature packages must not depend on unrelated feature packages:\n${offenders.joinToString("\n")}")
        }
    }

    @Test
    fun `shared ui does not import feature or app shell presentation code`() {
        val sourceRoot = sourceRoot("shared/ui")
        val forbiddenImport = Regex("""import dev\.qtremors\.arcile\.(feature\..+|presentation\.ui\..+)""")
        val offenders = sourceRoot.kotlinFiles()
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    if (forbiddenImport.matches(line.trim())) violation(file, index, line) else null
                }
            }
            .toList()

        if (offenders.isNotEmpty()) {
            fail("Shared UI must stay feature-neutral and must not import app shell presentation UI:\n${offenders.joinToString("\n")}")
        }
    }

    @Test
    fun `presentation packages do not import concrete storage data implementations except public app services`() {
        val sourceRoot = sourceRoot("presentation")
        val concreteDataImport = Regex("""import dev\.qtremors\.arcile\.core\.storage\.data\.(.+)""")
        val allowedStorageDataImports = emptySet<String>()
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
    fun `extracted core modules do not import app feature presentation or concrete data from contracts`() {
        val forbiddenImport = Regex("""import dev\.qtremors\.arcile\.(presentation|feature|core\.storage\.data)\..+""")
        val moduleSourceRoots = listOf(
            File(projectRoot(), "core/runtime/src/main/java"),
            File(projectRoot(), "core/operation/api/src/main/java"),
            File(projectRoot(), "core/operation/src/main/java"),
            File(projectRoot(), "core/storage/domain/src/main/java")
        )
        val offenders = moduleSourceRoots
            .asSequence()
            .flatMap { it.kotlinFiles() }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    if (forbiddenImport.matches(line.trim())) moduleViolation(file, index, line) else null
                }
            }
            .toList()

        if (offenders.isNotEmpty()) {
            fail("Extracted core API/domain modules must not import app, feature, presentation, or concrete storage data code:\n${offenders.joinToString("\n")}")
        }
    }

    @Test
    fun `storage data module does not import presentation feature or app shell packages`() {
        val sourceRoot = File(projectRoot(), "core/storage/data/src/main/java")
        val forbiddenImport = Regex("""import dev\.qtremors\.arcile\.(presentation|feature|ui\.theme)\..+""")
        val offenders = sourceRoot.kotlinFiles()
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    if (forbiddenImport.matches(line.trim())) moduleViolation(file, index, line) else null
                }
            }
            .toList()

        if (offenders.isNotEmpty()) {
            fail("Storage data must stay behind domain contracts and must not import presentation, feature, or app shell code:\n${offenders.joinToString("\n")}")
        }
    }

    @Test
    fun `large files stay within the architecture budget`() {
        val projectRoot = projectRoot()
        // Keep in-module feature/core boundaries enforceable by preventing catch-all files.
        val allowedLargeFiles = emptySet<String>()
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
        if (exists()) walkTopDown().filter { it.isFile && it.extension == "kt" } else emptySequence()

    private fun violation(file: File, index: Int, line: String): String =
        "${file.relativeTo(File(projectRoot(), "app/src/main/java")).invariantSeparatorsPath}:${index + 1}: ${line.trim()}"

    private fun moduleViolation(file: File, index: Int, line: String): String =
        "${file.relativeTo(projectRoot()).invariantSeparatorsPath}:${index + 1}: ${line.trim()}"

    private companion object {
        const val MAX_FILE_LINES = 700
    }
}
