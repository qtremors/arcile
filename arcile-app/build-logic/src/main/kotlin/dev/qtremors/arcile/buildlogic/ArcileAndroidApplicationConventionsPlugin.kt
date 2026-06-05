package dev.qtremors.arcile.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.GradleException

class ArcileAndroidApplicationConventionsPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            val versionCatalog = rootProject.layout.projectDirectory.file("gradle/libs.versions.toml")
            val appBuildFile = layout.projectDirectory.file("build.gradle.kts")

            val verifyVersionCatalogFreshness = tasks.register("verifyVersionCatalogFreshness") {
                group = "verification"
                description = "Verifies that dependency freshness checks have an active version catalog to inspect."
                inputs.file(versionCatalog)
                doLast {
                    val catalog = versionCatalog.asFile
                    require(catalog.exists()) {
                        "Missing gradle/libs.versions.toml; dependency freshness checks need a version catalog."
                    }
                    val text = catalog.readText()
                    require("[versions]" in text && "[libraries]" in text && "[plugins]" in text) {
                        "gradle/libs.versions.toml must keep [versions], [libraries], and [plugins] sections."
                    }
                }
            }

            val verifyReleaseVersionMetadata = tasks.register("verifyReleaseVersionMetadata") {
                group = "verification"
                description = "Checks that the app declares explicit versionName and versionCode metadata."
                inputs.file(appBuildFile)
                doLast {
                    val buildFileText = appBuildFile.asFile.readText()
                    require(Regex("""versionName\s*=\s*"[^"]+"""").containsMatchIn(buildFileText)) {
                        "app/build.gradle.kts must declare versionName."
                    }
                    require(Regex("""versionCode\s*=\s*\d+""").containsMatchIn(buildFileText)) {
                        "app/build.gradle.kts must declare versionCode."
                    }
                }
            }

            if (rootProject.tasks.findByName("checkProductionStrings") == null) {
                rootProject.tasks.register("checkProductionStrings") {
                    group = "verification"
                    description = "Flags obvious hardcoded production UI strings across production Android sources."

                    doLast {
                        val suspiciousPattern = Regex(
                            """(Text\(\s*"[^"]*[A-Za-z][^"]*"|contentDescription\s*=\s*"[^"]*[A-Za-z][^"]*"|placeholder\s*=\s*"[^"]*[A-Za-z][^"]*"|title\s*=\s*"[^"]*[A-Za-z][^"]*"|Toast\.makeText\([^,]+,\s*"[^"]*[A-Za-z][^"]*"|createChooser\([^,]+,\s*"[^"]*[A-Za-z][^"]*"|error\.message\s*\?:\s*"[^"]*[A-Za-z][^"]*"|fileOperationStatusMessage\s*=\s*"[^"]*[A-Za-z][^"]*"|setContentTitle\("([^"]*[A-Za-z][^"]*)"|setContentText\("([^"]*[A-Za-z][^"]*)"|addAction\([^"]*"[^"]*[A-Za-z][^"]*")"""
                        )
                        val allowedFragments = listOf(
                            "android.os.Build.",
                            "Text(\".\${",
                            "AppLogger.",
                            "Regex(",
                            "SimpleDateFormat(",
                            "DateTimeFormatter",
                            "ImageRequest.Builder",
                            "mutableStateOf(\"\")",
                            "SavedStateHandle",
                            "MIME",
                            "mimeType",
                            "contentType =",
                            "label =",
                            "label = \"",
                            "label = {",
                            "cacheKey",
                            "content://",
                            "Arcile-$"
                        )
                        val offenders = rootProject.fileTree(rootProject.projectDir) {
                            include("**/src/main/java/**/*.kt")
                            include("**/src/main/kotlin/**/*.kt")
                            exclude("**/build/**")
                            exclude("**/src/test/**")
                            exclude("**/src/androidTest/**")
                            exclude("**/ui/theme/**")
                        }.files.flatMap { sourceFile ->
                            val relativePath = sourceFile.relativeTo(rootProject.projectDir).invariantSeparatorsPath
                            sourceFile.readLines().mapIndexedNotNull { index, line ->
                                val trimmed = line.trim()
                                if (suspiciousPattern.containsMatchIn(trimmed) &&
                                    allowedFragments.none { trimmed.contains(it) } &&
                                    !trimmed.contains("R.string.") &&
                                    !trimmed.contains("R.plurals.") &&
                                    !trimmed.contains("stringResource(") &&
                                    !trimmed.contains("pluralStringResource(") &&
                                    !trimmed.contains("getString(")
                                ) {
                                    "$relativePath:${index + 1}: $trimmed"
                                } else {
                                    null
                                }
                            }
                        }
                        if (offenders.isNotEmpty()) {
                            throw GradleException(buildString {
                                appendLine("Found hardcoded production UI strings:")
                                offenders.forEach { appendLine(it) }
                            })
                        }
                    }
                }
            }

            tasks.register("verifyArcileBuildConventions") {
                group = "verification"
                description = "Runs Arcile build convention checks used for release readiness."
                dependsOn(verifyVersionCatalogFreshness, verifyReleaseVersionMetadata)
            }
        }
    }
}
