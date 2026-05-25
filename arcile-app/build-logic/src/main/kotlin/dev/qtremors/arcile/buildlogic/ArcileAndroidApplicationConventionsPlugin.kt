package dev.qtremors.arcile.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

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

        tasks.register("verifyArcileBuildConventions") {
            group = "verification"
            description = "Runs Arcile build convention checks used for release readiness."
            dependsOn(verifyVersionCatalogFreshness, verifyReleaseVersionMetadata)
        }
        }
    }
}
