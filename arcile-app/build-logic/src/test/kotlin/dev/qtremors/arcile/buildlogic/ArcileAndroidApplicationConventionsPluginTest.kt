package dev.qtremors.arcile.buildlogic

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArcileAndroidApplicationConventionsPluginTest {
    @Test
    fun `production string check fails for hardcoded feature ui string`() {
        val projectDir = Files.createTempDirectory("arcile-build-logic-test")
        projectDir.resolve("settings.gradle.kts").writeText("")
        val catalog = projectDir.resolve("gradle/libs.versions.toml")
        catalog.parent.createDirectories()
        catalog.writeText(
            """
            [versions]
            sample = "1.0"

            [libraries]
            sample = { module = "example:sample", version.ref = "sample" }

            [plugins]
            sample = { id = "example.sample", version.ref = "sample" }
            """.trimIndent()
        )
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("arcile.android.application.conventions")
            }
            """.trimIndent()
        )
        val source = projectDir
            .resolve("feature/sample/src/main/java/dev/qtremors/arcile/feature/sample/SampleScreen.kt")
        source.parent.createDirectories()
        source.writeText(
            """
            package dev.qtremors.arcile.feature.sample

            fun SampleScreen() {
                Text("Hardcoded production string")
            }
            """.trimIndent()
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("checkProductionStrings")
            .buildAndFail()

        assertEquals(TaskOutcome.FAILED, result.task(":checkProductionStrings")?.outcome)
        assertTrue(result.output.contains("Found hardcoded production UI strings:"))
        assertTrue(result.output.contains("feature/sample/src/main/java/dev/qtremors/arcile/feature/sample/SampleScreen.kt:4"))
    }
}
