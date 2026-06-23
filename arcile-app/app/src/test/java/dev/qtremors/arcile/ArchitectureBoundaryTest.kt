package dev.qtremors.arcile

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import java.io.File
import org.junit.Assert.fail
import org.junit.Test

class ArchitectureBoundaryTest {
    private val productionClasses: JavaClasses by lazy {
        ClassFileImporter()
            .importPaths(productionClassDirs().map { it.toPath() })
    }

    @Test
    fun `feature packages do not depend on concrete storage data implementations`() {
        noClasses()
            .that().resideInAPackage("dev.qtremors.arcile.feature..")
            .should().dependOnClassesThat().resideInAPackage("dev.qtremors.arcile.core.storage.data..")
            .because("features must depend on storage public APIs, not concrete data implementations")
            .check(productionClasses)
    }

    @Test
    fun `feature packages do not depend on app presentation internals`() {
        noClasses()
            .that().resideInAPackage("dev.qtremors.arcile.feature..")
            .should().dependOnClassesThat().resideInAPackage("dev.qtremors.arcile.presentation..")
            .because("features must depend on core/shared contracts or their own feature contracts")
            .check(productionClasses)
    }

    @Test
    fun `feature packages do not depend on unrelated feature packages`() {
        val features = listOf(
            "archive",
            "browser",
            "onboarding",
            "quickaccess",
            "recentfiles",
            "storagecleaner",
            "storageusage",
            "trash"
        )

        features.forEach { owningFeature ->
            val unrelatedPackages = features
                .filterNot { it == owningFeature }
                .map { "dev.qtremors.arcile.feature.$it.." }
                .toTypedArray()

            noClasses()
                .that().resideInAPackage("dev.qtremors.arcile.feature.$owningFeature..")
                .should().dependOnClassesThat().resideInAnyPackage(*unrelatedPackages)
                .because("feature modules must not depend on unrelated feature modules")
                .check(productionClasses)
        }
    }

    @Test
    fun `shared ui does not depend on feature or app shell presentation code`() {
        noClasses()
            .that().resideInAPackage("dev.qtremors.arcile.shared.ui..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "dev.qtremors.arcile.feature..",
                "dev.qtremors.arcile.presentation.ui.."
            )
            .because("shared UI must stay feature-neutral")
            .check(productionClasses)
    }

    @Test
    fun `presentation packages do not depend on concrete storage data implementations`() {
        noClasses()
            .that().resideInAPackage("dev.qtremors.arcile.presentation..")
            .should().dependOnClassesThat().resideInAPackage("dev.qtremors.arcile.core.storage.data..")
            .because("presentation packages must depend on storage public APIs")
            .check(productionClasses)
    }

    @Test
    fun `core and shared packages do not depend on app presentation or feature packages`() {
        noClasses()
            .that().resideInAnyPackage(
                "dev.qtremors.arcile.core..",
                "dev.qtremors.arcile.shared..",
                "dev.qtremors.arcile.ui.theme..",
                "dev.qtremors.arcile.image.."
            )
            .should().dependOnClassesThat().resideInAnyPackage(
                "dev.qtremors.arcile.presentation..",
                "dev.qtremors.arcile.feature.."
            )
            .because("core/shared modules must stay below app and feature layers")
            .check(productionClasses)
    }

    @Test
    fun `core storage domain does not depend on android or compose classes`() {
        noClasses()
            .that().resideInAPackage("dev.qtremors.arcile.core.storage.domain..")
            .should().dependOnClassesThat().resideInAnyPackage("android..", "androidx.compose..")
            .because("core storage domain must be platform-neutral")
            .check(productionClasses)
    }

    @Test
    fun `storage data does not depend on presentation feature or app shell theme packages`() {
        noClasses()
            .that().resideInAPackage("dev.qtremors.arcile.core.storage.data..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "dev.qtremors.arcile.presentation..",
                "dev.qtremors.arcile.feature..",
                "dev.qtremors.arcile.ui.theme.."
            )
            .because("storage data must stay behind domain contracts")
            .check(productionClasses)
    }

    @Test
    fun `presentation shell imports only approved feature entry points`() {
        val sourceRoot = sourceRoot("presentation")
        val featureImport = Regex("""import dev\.qtremors\.arcile\.feature\.(.+)""")
        val allowedFeatureImports = setOf(
            "archive.archiveViewerScreen",
            "browser.BrowserViewModel",
            "browser.ui.BrowserScreen",
            "imagegallery.imageGalleryScreen",
            "imagegallery.imageViewerScreen",
            "imagegallery.modelViewerScreen",
            "quickaccess.QuickAccessViewModel",
            "quickaccess.quickAccessScreen",
            "recentfiles.recentFilesScreen",
            "storagecleaner.storageCleanerScreen",
            "storageusage.StorageUsageViewModel",
            "storageusage.ui.StorageUsageMap",
            "trash.trashScreen",
            "trash.TrashViewModel",
            "recentfiles.RecentFilesViewModel"
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
    fun `large files stay within the architecture budget`() {
        val projectRoot = projectRoot()
        val repoRoot = projectRoot.parentFile ?: projectRoot
        val allowedLargeFiles = setOf(
            "docs/index.html"
        )

        val offenders = repoRoot.walkTopDown()
            .onEnter { dir ->
                val name = dir.name
                name != "build" && name != ".gradle" && name != ".git" &&
                    name != ".idea" && name != ".kotlin" && name != "assets"
            }
            .filter { file ->
                file.isFile && when (file.extension) {
                    "kt", "html", "xml", "gradle" -> true
                    "kts" -> file.name.endsWith(".gradle.kts")
                    else -> false
                }
            }
            .mapNotNull { file ->
                val relativePath = file.relativeTo(repoRoot).invariantSeparatorsPath
                val lineCount = file.readLines().size
                if (lineCount > MAX_FILE_LINES && relativePath !in allowedLargeFiles) {
                    "$relativePath: $lineCount lines"
                } else {
                    null
                }
            }
            .toList()

        if (offenders.isNotEmpty()) {
            fail("Files above $MAX_FILE_LINES lines must be split or explicitly allowlisted:\n${offenders.joinToString("\n")}")
        }
    }

    @Test
    fun `viewmodels stay within the architecture budget`() {
        val offenders = projectRoot().walkTopDown()
            .onEnter { dir ->
                val name = dir.name
                name != "build" && name != ".gradle" && name != ".git" &&
                    name != ".idea" && name != ".kotlin"
            }
            .filter { it.isFile && it.name.endsWith("ViewModel.kt") && it.path.contains("${File.separator}src${File.separator}main${File.separator}") }
            .mapNotNull { file ->
                val lineCount = file.readLines().size
                if (lineCount > MAX_VIEWMODEL_LINES) {
                    "${file.relativeTo(projectRoot()).invariantSeparatorsPath}: $lineCount lines"
                } else {
                    null
                }
            }
            .toList()

        if (offenders.isNotEmpty()) {
            fail("ViewModels above $MAX_VIEWMODEL_LINES lines must be split:\n${offenders.joinToString("\n")}")
        }
    }

    private fun sourceRoot(packageName: String): File =
        File(projectRoot(), "app/src/main/java/dev/qtremors/arcile/$packageName")

    private fun projectRoot(): File =
        generateSequence(File(System.getProperty("user.dir") ?: ".").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "app/src/main/java/dev/qtremors/arcile").exists() }
            ?: File(".").absoluteFile

    private fun productionClassDirs(): List<File> {
        val root = projectRoot()
        val androidModules = listOf(
            "app",
            "core/runtime",
            "core/presentation/api",
            "core/storage/data",
            "core/ui",
            "feature/archive",
            "feature/browser",
            "feature/imagegallery",
            "feature/onboarding",
            "feature/quickaccess",
            "feature/recentfiles",
            "feature/storagecleaner",
            "feature/storageusage",
            "feature/trash"
        ).flatMap { module ->
            listOf(
                File(root, "$module/build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes"),
                File(root, "$module/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes")
            )
        }
        val jvmModules = listOf(
            "core/navigation/api",
            "core/operation",
            "core/operation/api",
            "core/storage/domain"
        ).map { File(root, "$it/build/classes/kotlin/main") }

        return (androidModules + jvmModules).filter { it.exists() }
    }

    private fun File.kotlinFiles(): Sequence<File> =
        if (exists()) walkTopDown().filter { it.isFile && it.extension == "kt" } else emptySequence()

    private fun violation(file: File, index: Int, line: String): String =
        "${file.relativeTo(File(projectRoot(), "app/src/main/java")).invariantSeparatorsPath}:${index + 1}: ${line.trim()}"

    private companion object {
        const val MAX_FILE_LINES = 1000
        const val MAX_VIEWMODEL_LINES = 1000
    }
}
