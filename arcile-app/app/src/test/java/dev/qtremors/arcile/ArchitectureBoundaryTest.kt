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
            "imagegallery",
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
            .that().resideInAPackage("dev.qtremors.arcile.core.ui..")
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
                "dev.qtremors.arcile.core.ui.theme..",
                "dev.qtremors.arcile.core.ui.image.."
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
            "java.io.File" to Regex("""\bjava\.io\.File\b|\bFile\s*\("""),
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
    fun `feature public api contains only routes destinations and platform entry points`() {
        val featureRoot = File(projectRoot(), "feature")
        val offenders = featureRoot.walkTopDown()
            .filter {
                it.isFile &&
                    it.extension == "kt" &&
                    "${File.separator}src${File.separator}main${File.separator}" in it.path
            }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    if (!PUBLIC_TOP_LEVEL_DECLARATION.containsMatchIn(line)) {
                        return@mapIndexedNotNull null
                    }
                    if (isApprovedFeatureApi(line.trim())) {
                        null
                    } else {
                        "${file.relativeTo(projectRoot()).invariantSeparatorsPath}:${index + 1}: ${line.trim()}"
                    }
                }
            }
            .toList()

        if (offenders.isNotEmpty()) {
            fail(
                "Feature internals must be internal; only route APIs, destination contracts, " +
                    "and platform entry points may be public:\n${offenders.joinToString("\n")}"
            )
        }
    }

    @Test
    fun `gradle module dependencies follow architecture direction`() {
        val dependencyPattern = Regex("""project\(\s*"(:[^"]+)"\s*\)""")
        val offenders = projectRoot().walkTopDown()
            .onEnter { it.name !in setOf("build", ".gradle", ".git", ".idea", ".kotlin") }
            .filter { it.isFile && it.name == "build.gradle.kts" }
            .flatMap { buildFile ->
                val ownerPath = requireNotNull(buildFile.parentFile)
                    .relativeTo(projectRoot())
                    .invariantSeparatorsPath
                val ownerModule = ":" + ownerPath.replace('/', ':')
                buildFile.readLines().flatMapIndexed { index, line ->
                    dependencyPattern.findAll(line).mapNotNull { match ->
                        val dependency = match.groupValues[1]
                        val invalid = when {
                            ownerModule.startsWith(":feature:") &&
                                dependency.startsWith(":feature:") -> true
                            ownerModule.startsWith(":core:") &&
                                (dependency == ":app" || dependency.startsWith(":feature:")) -> true
                            else -> false
                        }
                        if (invalid) {
                            "$ownerModule -> $dependency at " +
                                "${buildFile.relativeTo(projectRoot()).invariantSeparatorsPath}:${index + 1}"
                        } else {
                            null
                        }
                    }.toList()
                }
            }
            .toList()

        if (offenders.isNotEmpty()) {
            fail("Gradle module dependencies violate architecture direction:\n${offenders.joinToString("\n")}")
        }
    }

    @Test
    fun `source packages match their owning modules`() {
        val modulePackages = buildMap {
            File(projectRoot(), "feature").listFiles()
                .orEmpty()
                .filter(File::isDirectory)
                .forEach { module ->
                    val packageName = if (module.name == "import") "importing" else module.name
                    put("feature/${module.name}", "dev.qtremors.arcile.feature.$packageName")
                }
            put("app", "dev.qtremors.arcile")
            put("core/navigation/api", "dev.qtremors.arcile.navigation")
            put("core/operation/api", "dev.qtremors.arcile.core.operation")
            put("core/operation/android", "dev.qtremors.arcile.core.operation")
            put("core/plugin/android", "dev.qtremors.arcile.core.plugin")
            put("core/presentation", "dev.qtremors.arcile.core.presentation")
            put("core/runtime", "dev.qtremors.arcile.core.runtime")
            put("core/storage/domain", "dev.qtremors.arcile.core.storage.domain")
            put("core/storage/data", "dev.qtremors.arcile.core.storage.data")
            put("core/testing", "dev.qtremors.arcile.testutil")
            put("core/ui", "dev.qtremors.arcile.core.ui")
            put("core/ui/testing", "dev.qtremors.arcile.core.ui.testing")
            put("plugin-api", "dev.qtremors.arcile.plugin.api")
            put("plugin-ui", "dev.qtremors.arcile.plugin.ui")
            put("plugin-glb", "dev.qtremors.arcile.plugin.glb")
        }
        val ownedRoots = modulePackages.flatMap { (module, expectedPackage) ->
            listOf("main", "test", "androidTest").mapNotNull { sourceSet ->
                File(projectRoot(), "$module/src/$sourceSet/java")
                    .takeIf(File::exists)
                    ?.let { sourceRoot -> sourceRoot to expectedPackage }
            }
        }
        val offenders = ownedRoots.flatMap { (sourceRoot, expectedPackage) ->
            sourceRoot.kotlinFiles().mapNotNull { file ->
                val packageName = file.useLines { lines ->
                    lines.firstOrNull { it.startsWith("package ") }
                        ?.removePrefix("package ")
                        ?.trim()
                }
                val expectedPrefixMatches = packageName == expectedPackage ||
                    packageName?.startsWith("$expectedPackage.") == true
                val packagePath = packageName?.replace('.', '/')
                val actualPath = file.parentFile
                    ?.relativeTo(sourceRoot)
                    ?.invariantSeparatorsPath
                when {
                    !expectedPrefixMatches ->
                        "${file.relativeTo(projectRoot()).invariantSeparatorsPath}: " +
                            "${packageName ?: "missing package"}; expected $expectedPackage"
                    actualPath != packagePath ->
                        "${file.relativeTo(projectRoot()).invariantSeparatorsPath}: " +
                            "package path $actualPath does not match declaration $packagePath"
                    else -> null
                }
            }.toList()
        }

        if (offenders.isNotEmpty()) {
            fail("Source packages and paths must match their owning modules:\n${offenders.joinToString("\n")}")
        }

        val removedModuleNames = listOf(
            File(projectRoot(), "shared"),
            File(projectRoot(), "core/presentation/api")
        ).filter { directory ->
            directory.exists() &&
                directory.walkTopDown()
                    .onEnter { it == directory || it.name != "build" }
                    .any(File::isFile)
        }
        if (removedModuleNames.isNotEmpty()) {
            fail("Removed module names must not return: ${removedModuleNames.joinToString { it.path }}")
        }
    }

    @Test
    fun `feature composables do not own blocking filesystem work`() {
        val forbiddenPatterns = mapOf(
            "Dispatchers.IO" to Regex("""\bDispatchers\.IO\b"""),
            "filesystem existence" to Regex("""\.exists\s*\("""),
            "filesystem traversal" to Regex("""\.listFiles\s*\("""),
            "canonical path resolution" to Regex("""\.canonical(?:File|Path)?\b""")
        )
        val offenders = File(projectRoot(), "feature").kotlinFiles()
            .filter { file ->
                "${File.separator}src${File.separator}main${File.separator}" in file.path &&
                    "@Composable" in file.readText()
            }
            .flatMap { file ->
                file.readLines().flatMapIndexed { index, line ->
                    forbiddenPatterns.mapNotNull { (name, pattern) ->
                        if (pattern.containsMatchIn(line)) {
                            "${violation(file, index, line)} ($name)"
                        } else {
                            null
                        }
                    }
                }
            }
            .toList()

        if (offenders.isNotEmpty()) {
            fail(
                "Feature composables must delegate blocking filesystem work to owned services:\n" +
                    offenders.joinToString("\n")
            )
        }
    }

    @Test
    fun `native storage authorization stays behind the route platform adapter`() {
        val forbidden = Regex("""\b(?:IntentSender|NativeStorageAuthorizationGateway)\b""")
        val guardedRoots = listOf(
            File(projectRoot(), "feature"),
            File(projectRoot(), "core/presentation/src/main")
        )
        val offenders = guardedRoots.asSequence()
            .flatMap { root -> root.kotlinFiles() }
            .filter { file ->
                "${File.separator}src${File.separator}main${File.separator}" in file.path ||
                    "core${File.separator}presentation${File.separator}src${File.separator}main" in file.path
            }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    if (forbidden.containsMatchIn(line)) violation(file, index, line) else null
                }
            }
            .toList()

        if (offenders.isNotEmpty()) {
            fail(
                "Features and platform-neutral presentation must carry typed authorization " +
                    "requirements, not Android sender objects:\n${offenders.joinToString("\n")}"
            )
        }
    }

    @Test
    fun `completed migrations cannot restore compatibility scaffolding`() {
        val removedFiles = listOf(
            "core/operation/build.gradle.kts",
            "core/operation/src/main/java/dev/qtremors/arcile/core/operation/OperationMigrationMarker.kt",
            "core/storage/domain/src/main/java/dev/qtremors/arcile/core/storage/domain/FileRepository.kt",
            "core/storage/domain/src/main/java/dev/qtremors/arcile/core/storage/domain/StorageServiceContracts.kt",
            "core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/source/FileSystemDataSourceContracts.kt",
            "core/storage/domain/src/main/java/dev/qtremors/arcile/core/storage/domain/ActivityLogContracts.kt",
            "core/storage/domain/src/main/java/dev/qtremors/arcile/core/storage/domain/ArchiveModels.kt",
            "core/storage/domain/src/main/java/dev/qtremors/arcile/core/storage/domain/ConflictModels.kt",
            "core/storage/domain/src/main/java/dev/qtremors/arcile/core/storage/domain/StorageCleanerModels.kt",
            "core/storage/domain/src/main/java/dev/qtremors/arcile/core/storage/domain/StorageUsageModels.kt",
            "core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/BrowserPreferencesRepository.kt",
            "core/presentation/src/main/java/dev/qtremors/arcile/core/presentation/LocalSearchHelper.kt",
            "feature/settings/src/main/java/dev/qtremors/arcile/feature/settings/ui/SettingsContract.kt",
            "feature/import/src/main/java/dev/qtremors/arcile/feature/importing/SaveDestinationResolver.kt",
            "feature/import/src/main/java/dev/qtremors/arcile/feature/importing/SaveIncomingFiles.kt"
        ).map { File(projectRoot(), it) }.filter(File::exists)

        if (removedFiles.isNotEmpty()) {
            fail(
                "Completed migrations must not restore aggregate facades or marker modules:\n" +
                    removedFiles.joinToString("\n") { it.relativeTo(projectRoot()).invariantSeparatorsPath }
            )
        }

        val forbiddenDeclarations = listOf(
            "BrowserPresentationPreferences",
            "BrowserPreferencesStore",
            "BrowserPreferencesRepository",
            "BrowserViewMode",
            "FileRepository",
            "LocalSearchHelper",
            "NativeConfirmationRequiredException"
        )
        val offenders = productionKotlinFiles().flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                val declaration = forbiddenDeclarations.firstOrNull { name ->
                    Regex("""\b(?:class|interface|typealias)\s+$name\b""").containsMatchIn(line)
                }
                if (declaration == null) null else
                    "${file.relativeTo(projectRoot().parentFile ?: projectRoot()).invariantSeparatorsPath}:" +
                        "${index + 1}: $declaration"
            }
        }.toList()

        if (offenders.isNotEmpty()) {
            fail("Removed compatibility declarations must not return:\n${offenders.joinToString("\n")}")
        }
    }

    @Test
    fun `production files identify a cohesive responsibility`() {
        val genericBucketName = Regex("""(?:Models|Helpers|Utils|Contracts|StateSlices)\.kt$""")
        val offenders = productionKotlinFiles()
            .filter { genericBucketName.containsMatchIn(it.name) }
            .map { it.relativeTo(projectRoot()).invariantSeparatorsPath }
            .toList()

        if (offenders.isNotEmpty()) {
            fail(
                "Generic production buckets conceal unrelated ownership; use responsibility-specific files:\n" +
                    offenders.joinToString("\n")
            )
        }
    }

    @Test
    fun `storage data does not depend on presentation feature or app shell theme packages`() {
        noClasses()
            .that().resideInAPackage("dev.qtremors.arcile.core.storage.data..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "dev.qtremors.arcile.presentation..",
                "dev.qtremors.arcile.feature..",
                "dev.qtremors.arcile.core.ui.theme.."
            )
            .because("storage data must stay behind domain contracts")
            .check(productionClasses)
    }

    @Test
    fun `storage failures preserve coroutine cancellation`() {
        val storageRoot = File(projectRoot(), "core/storage/data/src/main/java")
        val rawResultWrapper = Regex("""\brunCatching\s*\{""")
        val broadCatch = Regex(
            """catch\s*\(\s*([A-Za-z][A-Za-z0-9_]*)\s*:\s*(?:Exception|Throwable)\s*\)\s*\{"""
        )
        val offenders = storageRoot.kotlinFiles().flatMap { file ->
            val lines = file.readLines()
            lines.asSequence().flatMapIndexed { index, line ->
                sequence {
                    if (rawResultWrapper.containsMatchIn(line)) {
                        yield(violation(file, index, line))
                    }
                    val caughtName = broadCatch.find(line)?.groupValues?.get(1)
                    if (caughtName != null) {
                        val catchBlock = collectBraceBlock(lines, index)
                        val preservesCancellation =
                            "$caughtName.rethrowIfCancellation()" in catchBlock ||
                                Regex("""\bthrow\s+$caughtName\b""").containsMatchIn(catchBlock)
                        if (!preservesCancellation) {
                            yield(
                                violation(file, index, line) +
                                    " must rethrow cancellation before handling the failure"
                            )
                        }
                    }
                }
            }
        }.toList()

        if (offenders.isNotEmpty()) {
            fail(
                "Storage failure handling must use cancellation-preserving wrappers and catches:\n" +
                    offenders.joinToString("\n")
            )
        }
    }

    @Test
    fun `presentation shell imports only approved feature entry points`() {
        val sourceRoot = File(projectRoot(), "app/src/main/java")
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
            "onboarding.OnboardingRoute",
            "plugins.registerPluginsRoute",
            "quickaccess.QuickAccessDestination",
            "quickaccess.registerQuickAccessRoute",
            "recentfiles.RecentFilesDestination",
            "recentfiles.registerRecentFilesRoute",
            "settings.SettingsDestination",
            "settings.registerSettingsRoute",
            "storagecleaner.StorageCleanerDestination",
            "storagecleaner.registerStorageCleanerRoute",
            "storageusage.StorageDashboardDestination",
            "storageusage.registerStorageDashboardRoute",
            "storageusage.registerStorageManagementRoute",
            "trash.registerTrashRoute"
        )
        val allowedShellFiles = setOf(
            "dev/qtremors/arcile/MainActivity.kt",
            "dev/qtremors/arcile/presentation/ui/AppFileRouteRegistration.kt",
            "dev/qtremors/arcile/presentation/ui/AppMainRouteRegistration.kt",
            "dev/qtremors/arcile/presentation/ui/AppNavigationGraph.kt",
            "dev/qtremors/arcile/presentation/ui/AppUtilityRouteRegistration.kt",
            "dev/qtremors/arcile/presentation/ui/ArcileAppShell.kt",
            "dev/qtremors/arcile/presentation/ui/ArchiveDestinationMapper.kt",
            "dev/qtremors/arcile/presentation/ui/GalleryDestinationMapper.kt",
            "dev/qtremors/arcile/presentation/ui/MainRoute.kt",
            "dev/qtremors/arcile/presentation/ui/MainShellCoordinator.kt",
            "dev/qtremors/arcile/presentation/ui/QuickAccessDestinationMapper.kt",
            "dev/qtremors/arcile/presentation/ui/RecentFilesDestinationMapper.kt",
            "dev/qtremors/arcile/presentation/ui/StorageCleanerDestinationMapper.kt"
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
    fun `browser has no flat state or aggregate child intent dependency`() {
        val browserRoot = File(projectRoot(), "feature/browser/src/main/java")
        val offenders = browserRoot.kotlinFiles()
            .flatMap { file ->
                val relativePath = file.relativeTo(projectRoot()).invariantSeparatorsPath
                file.readLines().mapIndexedNotNull { index, line ->
                    when {
                        Regex("""\bBrowserState\b""").containsMatchIn(line) ->
                            "$relativePath:${index + 1}: flat BrowserState: ${line.trim()}"
                        file.name !in setOf("BrowserRoute.kt", "BrowserScreen.kt", "BrowserIntentGroups.kt") &&
                            Regex("""\bBrowserIntents\b""").containsMatchIn(line) ->
                            "$relativePath:${index + 1}: aggregate BrowserIntents: ${line.trim()}"
                        else -> null
                    }
                }
            }
            .toList()

        if (offenders.isNotEmpty()) {
            fail(
                "Browser controllers and child UI must use focused state and intent groups:\n" +
                    offenders.joinToString("\n")
            )
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

    @Test
    fun `core ui processes its hilt entry points`() {
        val buildScript = File(projectRoot(), "core/ui/build.gradle.kts").readText()
        val missingConfiguration = buildList {
            if ("alias(libs.plugins.hilt.android)" !in buildScript) {
                add("Hilt Android Gradle plugin")
            }
            if ("alias(libs.plugins.ksp)" !in buildScript) {
                add("KSP Gradle plugin")
            }
            if ("ksp(libs.hilt.compiler)" !in buildScript) {
                add("Hilt compiler KSP dependency")
            }
        }

        if (missingConfiguration.isNotEmpty()) {
            fail(
                "core/ui declares an application Hilt entry point and must generate its " +
                    "aggregation metadata. Missing: ${missingConfiguration.joinToString()}"
            )
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

    private fun collectBraceBlock(lines: List<String>, startIndex: Int): String {
        val builder = StringBuilder()
        var depth = 0
        var started = false
        for (index in startIndex until lines.size) {
            val line = lines[index]
            builder.appendLine(line)
            line.forEach { char ->
                when (char) {
                    '{' -> {
                        depth += 1
                        started = true
                    }
                    '}' -> if (started) {
                        depth -= 1
                        if (depth == 0) return builder.toString()
                    }
                }
            }
        }
        return builder.toString()
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

    private fun isApprovedFeatureApi(declaration: String): Boolean =
        declaration.removePrefix("public ").let { publicDeclaration ->
            publicDeclaration.matches(Regex("""fun NavGraphBuilder\.register[A-Za-z0-9_]*Route\(.*""")) ||
                publicDeclaration.matches(Regex("""sealed interface [A-Za-z0-9_]*Destination\b.*""")) ||
                publicDeclaration.matches(Regex("""fun (HomeRoute|BrowserRoute|OnboardingRoute)\(.*""")) ||
                publicDeclaration.matches(
                    Regex(
                        """(sealed interface BrowserEntry|data class BrowserEntryRequest|""" +
                            """data class BrowserRouteStatus)\b.*"""
                    )
                ) ||
                publicDeclaration.matches(Regex("""class SaveToArcileActivity\b.*"""))
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
        val PUBLIC_TOP_LEVEL_DECLARATION = Regex(
            """^(?!(?:internal|private|protected)\s)""" +
                """(?:(?:public|data|sealed|enum|annotation|value|suspend|operator|inline|tailrec|infix|const)\s+)*""" +
                """(?:class|interface|object|fun|typealias|val|var)\s+"""
        )

        val LARGE_FILE_BASELINE = emptyMap<String, Int>()

        val LARGE_VIEWMODEL_BASELINE = emptyMap<String, Int>()

        val FEATURE_VIEWMODEL_BOUNDARY_BASELINE = emptyMap<String, Set<String>>()

        val COMPOSABLE_PARAMETER_BASELINE = emptyMap<String, Int>()
    }
}
