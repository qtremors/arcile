package dev.qtremors.arcile

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import java.io.File
import org.junit.Assert.assertFalse
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
            "activitylog",
            "archive",
            "browser",
            "home",
            "importing",
            "settings",
            "onboarding",
            "plugins",
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
    fun `domain and api modules do not depend on android or compose classes`() {
        noClasses()
            .that().resideInAnyPackage(
                "dev.qtremors.arcile.core.navigation..",
                "dev.qtremors.arcile.core.operation..",
                "dev.qtremors.arcile.core.storage.domain.."
            )
            .and().resideOutsideOfPackage("dev.qtremors.arcile.core.operation.android..")
            .should().dependOnClassesThat().resideInAnyPackage("android..", "androidx.compose..")
            .because("domain and API modules must be platform-neutral")
            .check(productionClasses)
    }

    @Test
    fun `feature viewmodels do not use platform or concrete storage implementation details`() {
        val forbiddenPatterns = mapOf(
            "Context" to Regex("""\bandroid\.content\.Context\b|\bContext\b"""),
            "ApplicationContext" to Regex("""\bApplicationContext\b"""),
            "Dispatchers.IO" to Regex("""\bDispatchers\.IO\b"""),
            "LocalFileRepository" to Regex("""\bLocalFileRepository\b"""),
            "core.storage.data" to Regex("""\bdev\.qtremors\.arcile\.core\.storage\.data\b""")
        )

        val offenders = featureViewModelFiles()
            .flatMap { file ->
                val relativePath = file.relativeTo(projectRoot()).invariantSeparatorsPath
                file.readLines().flatMapIndexed { index, line ->
                    forbiddenPatterns.mapNotNull { (name, pattern) ->
                        if (!pattern.containsMatchIn(line)) return@mapNotNull null
                        if (FEATURE_VIEWMODEL_BOUNDARY_BASELINE[relativePath]?.contains(name) == true) {
                            null
                        } else {
                            "$relativePath:${index + 1}: $name: ${line.trim()}"
                        }
                    }
                }
            }
            .toList()

        if (offenders.isNotEmpty()) {
            fail("Feature ViewModels must avoid platform and concrete storage implementation details:\n${offenders.joinToString("\n")}")
        }
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
            "activitylog.registerActivityLogRoute",
            "archive.ArchiveDestination",
            "archive.registerArchiveViewerRoute",
            "browser.BrowserDestination",
            "browser.BrowserEntry",
            "browser.BrowserEntryRequest",
            "browser.BrowserRoute",
            "browser.BrowserRouteStatus",
            "home.HomeDestination",
            "home.HomeRoute",
            "imagegallery.GalleryDestination",
            "imagegallery.registerImageGalleryRoute",
            "imagegallery.registerImageViewerRoute",
            "plugins.registerPluginsRoute",
            "quickaccess.QuickAccessDestination",
            "quickaccess.registerQuickAccessRoute",
            "recentfiles.RecentFilesDestination",
            "recentfiles.registerRecentFilesRoute",
            "settings.SettingsDestination",
            "settings.registerSettingsRoute",
            "storagecleaner.StorageCleanerDestination",
            "storagecleaner.registerStorageCleanerRoute",
            "storageusage.StorageUsageViewModel",
            "storageusage.StorageDashboardDestination",
            "storageusage.registerStorageDashboardRoute",
            "storageusage.registerStorageManagementRoute",
            "storageusage.ui.StorageUsageMap",
            "trash.registerTrashRoute",
            "trash.TrashViewModel",
            "recentfiles.RecentFilesViewModel"
        )
        val allowedShellFiles = setOf(
            "dev/qtremors/arcile/presentation/ui/AppNavigationGraph.kt",
            "dev/qtremors/arcile/presentation/ui/ArcileAppShell.kt",
            "dev/qtremors/arcile/presentation/ui/FeatureDestinationMapper.kt",
            "dev/qtremors/arcile/presentation/ui/MainRoute.kt"
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
    fun `app navigation graph does not own viewmodels`() {
        val graph = File(
            projectRoot(),
            "app/src/main/java/dev/qtremors/arcile/presentation/ui/AppNavigationGraph.kt"
        ).readText()

        assertFalse(graph.contains("ViewModel"))
        assertFalse(graph.contains("hiltViewModel"))
    }

    @Test
    fun `large files stay within the architecture budget`() {
        val projectRoot = projectRoot()
        val repoRoot = projectRoot.parentFile ?: projectRoot

        val offenders = repoRoot.walkTopDown()
            .onEnter { dir ->
                val name = dir.name
                name != "build" && name != ".gradle" && name != ".git" &&
                    name != ".idea" && name != ".kotlin" && name != "assets" &&
                    name != "docs"
            }
            .filter { file ->
                val path = file.invariantSeparatorsPath
                file.isFile &&
                    "/src/test/" !in path &&
                    "/src/androidTest/" !in path &&
                    when (file.extension) {
                    "kt", "html", "xml", "gradle" -> true
                    "kts" -> file.name.endsWith(".gradle.kts")
                    else -> false
                }
            }
            .mapNotNull { file ->
                val relativePath = file.relativeTo(repoRoot).invariantSeparatorsPath
                val lineCount = file.readLines().size
                val baseline = LARGE_FILE_BASELINE[relativePath]
                when {
                    lineCount <= MAX_FILE_LINES -> null
                    baseline != null && lineCount <= baseline -> null
                    baseline != null -> "$relativePath: $lineCount lines exceeds baseline $baseline"
                    else -> "$relativePath: $lineCount lines"
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
                val relativePath = file.relativeTo(projectRoot()).invariantSeparatorsPath
                val lineCount = file.readLines().size
                val baseline = LARGE_VIEWMODEL_BASELINE[relativePath]
                when {
                    lineCount <= MAX_VIEWMODEL_LINES -> null
                    baseline != null && lineCount <= baseline -> null
                    baseline != null -> "$relativePath: $lineCount lines exceeds baseline $baseline"
                    else -> "$relativePath: $lineCount lines"
                }
            }
            .toList()

        if (offenders.isNotEmpty()) {
            fail("ViewModels above $MAX_VIEWMODEL_LINES lines must be split:\n${offenders.joinToString("\n")}")
        }
    }

    @Test
    fun `public composables stay within the parameter budget`() {
        val offenders = productionKotlinFiles()
            .flatMap { file ->
                val relativePath = file.relativeTo(projectRoot().parentFile ?: projectRoot()).invariantSeparatorsPath
                publicComposableSignatures(file).mapNotNull { signature ->
                    val baseline = COMPOSABLE_PARAMETER_BASELINE["$relativePath:${signature.name}"]
                    when {
                        signature.parameterCount <= MAX_COMPOSABLE_PARAMETERS -> null
                        baseline != null && signature.parameterCount <= baseline -> null
                        baseline != null -> "$relativePath:${signature.line}: ${signature.name} has ${signature.parameterCount} parameters, exceeding baseline $baseline"
                        else -> "$relativePath:${signature.line}: ${signature.name} has ${signature.parameterCount} parameters"
                    }
                }
            }
            .toList()

        if (offenders.isNotEmpty()) {
            fail("Public composables above $MAX_COMPOSABLE_PARAMETERS parameters must be split or explicitly allowlisted:\n${offenders.joinToString("\n")}")
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
            "core/operation/android",
            "core/plugin/android",
            "core/presentation",
            "core/storage/data",
            "core/ui",
            "feature/archive",
            "feature/activitylog",
            "feature/browser",
            "feature/home",
            "feature/import",
            "feature/settings",
            "feature/imagegallery",
            "feature/onboarding",
            "feature/plugins",
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

    private fun featureViewModelFiles(): Sequence<File> =
        File(projectRoot(), "feature").walkTopDown()
            .filter {
                it.isFile &&
                    it.name.endsWith("ViewModel.kt") &&
                    "${File.separator}src${File.separator}main${File.separator}" in it.path
            }

    private fun productionKotlinFiles(): Sequence<File> {
        val root = projectRoot()
        return sequenceOf(
            File(root, "app/src/main/java"),
            File(root, "core"),
            File(root, "feature"),
            File(root, "plugin-glb/src/main/java")
        )
            .filter(File::exists)
            .flatMap { it.walkTopDown() }
            .filter { it.isFile && it.extension == "kt" && "${File.separator}src${File.separator}main${File.separator}" in it.path }
    }

    private fun publicComposableSignatures(file: File): Sequence<ComposableSignature> = sequence {
        val lines = file.readLines()
        lines.forEachIndexed { index, line ->
            if (line.trim() != "@Composable") return@forEachIndexed
            var functionLineIndex = index + 1
            while (functionLineIndex < lines.size && lines[functionLineIndex].trim().startsWith("@")) {
                functionLineIndex += 1
            }
            if (functionLineIndex >= lines.size) return@forEachIndexed

            val functionLine = lines[functionLineIndex].trim()
            val match = PUBLIC_FUNCTION.matchEntire(functionLine) ?: return@forEachIndexed
            val signatureText = collectSignature(lines, functionLineIndex) ?: return@forEachIndexed
            yield(
                ComposableSignature(
                    name = match.groupValues[1],
                    line = functionLineIndex + 1,
                    parameterCount = countTopLevelParameters(signatureText)
                )
            )
        }
    }

    private fun collectSignature(lines: List<String>, startIndex: Int): String? {
        val builder = StringBuilder()
        var depth = 0
        var started = false
        for (index in startIndex until lines.size) {
            val line = lines[index]
            builder.appendLine(line)
            line.forEach { char ->
                when (char) {
                    '(' -> {
                        depth += 1
                        started = true
                    }
                    ')' -> {
                        depth -= 1
                        if (started && depth == 0) return builder.toString()
                    }
                }
            }
        }
        return null
    }

    private fun countTopLevelParameters(signature: String): Int {
        val params = signature.substringAfter('(', "").substringBeforeLast(')', "")
        if (params.isBlank()) return 0

        var depth = 0
        var commas = 0
        params.forEach { char ->
            when (char) {
                '(', '<', '[' -> depth += 1
                ')', '>', ']' -> if (depth > 0) depth -= 1
                ',' -> if (depth == 0) commas += 1
            }
        }
        return commas + 1
    }

    private fun violation(file: File, index: Int, line: String): String =
        "${file.relativeTo(File(projectRoot(), "app/src/main/java")).invariantSeparatorsPath}:${index + 1}: ${line.trim()}"

    private data class ComposableSignature(
        val name: String,
        val line: Int,
        val parameterCount: Int
    )

    private companion object {
        const val MAX_FILE_LINES = 500
        const val MAX_VIEWMODEL_LINES = 400
        const val MAX_COMPOSABLE_PARAMETERS = 15
        val PUBLIC_FUNCTION = Regex("""^(?:public\s+)?fun\s+([A-Za-z0-9_]+)\s*\(.*""")

        val LARGE_FILE_BASELINE = mapOf(
            "arcile-app/app/src/main/java/dev/qtremors/arcile/presentation/ui/AppNavigationGraph.kt" to 545,
            "arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/manager/TrashManager.kt" to 540,
            "arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/source/DefaultFileSystemDataSource.kt" to 506,
            "arcile-app/core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/source/MediaStoreClient.kt" to 529,
            "arcile-app/core/testing/src/main/java/dev/qtremors/arcile/testutil/FocusedStorageRepositoryFakes.kt" to 685,
            "arcile-app/core/ui/src/main/java/dev/qtremors/arcile/shared/ui/SearchFiltersBottomSheet.kt" to 597,
            "arcile-app/core/ui/src/main/java/dev/qtremors/arcile/shared/ui/SortOptionDialog.kt" to 566,
            "arcile-app/core/ui/src/main/java/dev/qtremors/arcile/shared/ui/lists/FileList.kt" to 530,
            "arcile-app/core/ui/src/main/res/values/strings.xml" to 827,
            "arcile-app/feature/browser/src/main/java/dev/qtremors/arcile/feature/browser/BrowserViewModel.kt" to 741,
            "arcile-app/feature/browser/src/main/java/dev/qtremors/arcile/feature/browser/delegate/NavigationDelegate.kt" to 729,
            "arcile-app/feature/browser/src/main/java/dev/qtremors/arcile/feature/browser/ui/BrowserArchiveDialogs.kt" to 568,
            "arcile-app/feature/browser/src/main/java/dev/qtremors/arcile/feature/browser/ui/BrowserFloatingSurfaces.kt" to 694,
            "arcile-app/feature/browser/src/main/java/dev/qtremors/arcile/feature/browser/ui/BrowserScreen.kt" to 650,
            "arcile-app/feature/home/src/main/java/dev/qtremors/arcile/feature/home/HomeViewModel.kt" to 584,
            "arcile-app/feature/home/src/main/java/dev/qtremors/arcile/feature/home/ui/HomeScreen.kt" to 590,
            "arcile-app/feature/home/src/main/java/dev/qtremors/arcile/feature/home/ui/components/StorageSummaryCards.kt" to 629,
            "arcile-app/feature/imagegallery/src/main/java/dev/qtremors/arcile/feature/imagegallery/ImageGalleryChrome.kt" to 614,
            "arcile-app/feature/imagegallery/src/main/java/dev/qtremors/arcile/feature/imagegallery/ImageGalleryContent.kt" to 647,
            "arcile-app/feature/imagegallery/src/main/java/dev/qtremors/arcile/feature/imagegallery/ImageGalleryItems.kt" to 598,
            "arcile-app/feature/imagegallery/src/main/java/dev/qtremors/arcile/feature/imagegallery/ImageGalleryScreen.kt" to 982,
            "arcile-app/feature/imagegallery/src/main/java/dev/qtremors/arcile/feature/imagegallery/ImageGalleryViewModel.kt" to 990,
            "arcile-app/feature/imagegallery/src/main/java/dev/qtremors/arcile/feature/imagegallery/ImageGalleryViewOptions.kt" to 612,
            "arcile-app/feature/imagegallery/src/main/java/dev/qtremors/arcile/feature/imagegallery/ImageViewerMetadata.kt" to 732,
            "arcile-app/feature/imagegallery/src/main/java/dev/qtremors/arcile/feature/imagegallery/ImageViewerScreen.kt" to 782,
            "arcile-app/feature/onboarding/src/main/java/dev/qtremors/arcile/feature/onboarding/ui/OnboardingPages.kt" to 860,
            "arcile-app/feature/quickaccess/src/main/java/dev/qtremors/arcile/feature/quickaccess/QuickAccessScreen.kt" to 919,
            "arcile-app/feature/recentfiles/src/main/java/dev/qtremors/arcile/feature/recentfiles/RecentFilesViewModel.kt" to 522,
            "arcile-app/feature/settings/src/main/java/dev/qtremors/arcile/feature/settings/ui/SettingsScreen.kt" to 915,
            "arcile-app/feature/storagecleaner/src/main/java/dev/qtremors/arcile/feature/storagecleaner/ui/StorageCleanerDetailsSheet.kt" to 895,
            "arcile-app/feature/storagecleaner/src/main/java/dev/qtremors/arcile/feature/storagecleaner/ui/StorageCleanerScreen.kt" to 508,
            "arcile-app/feature/storageusage/src/main/java/dev/qtremors/arcile/feature/storageusage/ui/StorageDashboardScreen.kt" to 586,
            "arcile-app/feature/trash/src/main/java/dev/qtremors/arcile/feature/trash/TrashScreen.kt" to 659,
            "arcile-app/plugin-glb/src/main/java/dev/qtremors/arcile/plugin/glb/ModelViewerScreen.kt" to 643
        )

        val LARGE_VIEWMODEL_BASELINE = mapOf(
            "feature/home/src/main/java/dev/qtremors/arcile/feature/home/HomeViewModel.kt" to 584,
            "feature/archive/src/main/java/dev/qtremors/arcile/feature/archive/ArchiveViewerViewModel.kt" to 468,
            "feature/browser/src/main/java/dev/qtremors/arcile/feature/browser/BrowserViewModel.kt" to 741,
            "feature/imagegallery/src/main/java/dev/qtremors/arcile/feature/imagegallery/ImageGalleryViewModel.kt" to 990,
            "feature/recentfiles/src/main/java/dev/qtremors/arcile/feature/recentfiles/RecentFilesViewModel.kt" to 522,
            "feature/trash/src/main/java/dev/qtremors/arcile/feature/trash/TrashViewModel.kt" to 426
        )

        val FEATURE_VIEWMODEL_BOUNDARY_BASELINE = mapOf(
            "feature/archive/src/main/java/dev/qtremors/arcile/feature/archive/ArchiveViewerViewModel.kt" to setOf(
                "Context",
                "ApplicationContext"
            ),
            "feature/imagegallery/src/main/java/dev/qtremors/arcile/feature/imagegallery/ImageGalleryViewModel.kt" to setOf(
                "Context",
                "ApplicationContext",
                "Dispatchers.IO"
            ),
        )

        val COMPOSABLE_PARAMETER_BASELINE = mapOf(
            "arcile-app/feature/settings/src/main/java/dev/qtremors/arcile/feature/settings/ui/SettingsScreen.kt:SettingsScreen" to 22,
            "arcile-app/core/ui/src/main/java/dev/qtremors/arcile/shared/ui/ArcileTopBar.kt:ArcileTopBar" to 20,
            "arcile-app/core/ui/src/main/java/dev/qtremors/arcile/shared/ui/lists/FileGrid.kt:FileGrid" to 16,
            "arcile-app/core/ui/src/main/java/dev/qtremors/arcile/shared/ui/lists/FileGrid.kt:FileGridItem" to 16,
            "arcile-app/core/ui/src/main/java/dev/qtremors/arcile/shared/ui/lists/FileList.kt:FileList" to 16,
            "arcile-app/core/ui/src/main/java/dev/qtremors/arcile/shared/ui/lists/FileList.kt:FileItemRow" to 17,
            "arcile-app/feature/archive/src/main/java/dev/qtremors/arcile/feature/archive/ArchiveViewerScreen.kt:ArchiveViewerScreen" to 21,
            "arcile-app/feature/browser/src/main/java/dev/qtremors/arcile/feature/browser/ui/BrowserScreen.kt:BrowserScreen" to 76,
            "arcile-app/feature/imagegallery/src/main/java/dev/qtremors/arcile/feature/imagegallery/ImageGalleryScreen.kt:ImageGalleryScreen" to 44,
            "arcile-app/feature/recentfiles/src/main/java/dev/qtremors/arcile/feature/recentfiles/ui/RecentFilesScreen.kt:RecentFilesScreen" to 25,
            "arcile-app/feature/trash/src/main/java/dev/qtremors/arcile/feature/trash/TrashScreen.kt:TrashScreen" to 24
        )
    }
}
