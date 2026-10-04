package dev.qtremors.arcile.buildlogic

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArcileBuildVerificationPluginTest {
    @Test
    fun `production string check catches spoken labels and allows animation labels`() {
        val projectDir = Files.createTempDirectory("arcile-semantics-string-test")
        projectDir.resolve("settings.gradle.kts").writeText("")
        projectDir.resolve("build.gradle.kts").writeText("plugins { id(\"arcile.build.verification\") }")
        val source = projectDir.resolve("core/ui/src/main/java/example/FileSemantics.kt")
        source.parent.createDirectories()
        source.writeText(
            """
            fun fileSemantics() {
                animateFloatAsState(1f, label = "Row scale")
                Modifier.semantics {
                    val type = if (folder) "Folder" else "File"
                    onClick(label = if (selected) "Toggle selection" else "Open folder") { true }
                    customActions = listOf(CustomAccessibilityAction(label = "Select item", action = { true }))
                }
            }
            """.trimIndent()
        )
        val runner = GradleRunner.create().withProjectDir(projectDir.toFile()).withPluginClasspath()
            .withArguments("checkProductionStrings", "--offline", "--max-workers=1", "--console=plain")
        val failure = runner.buildAndFail()
        assertEquals(TaskOutcome.FAILED, failure.task(":checkProductionStrings")?.outcome)
        assertTrue(failure.output.contains("FileSemantics.kt:4:"))
        assertTrue(failure.output.contains("FileSemantics.kt:5:"))
        assertTrue(failure.output.contains("FileSemantics.kt:6:"))
        assertTrue(!failure.output.contains("FileSemantics.kt:2:"))
        source.writeText(
            """
            fun fileSemantics() {
                animateFloatAsState(1f, label = "Row scale")
                Modifier.semantics {
                    onClick(label = localizedOpenLabel) { true }
                    customActions = listOf(CustomAccessibilityAction(label = localizedSelectLabel, action = { true }))
                }
            }
            """.trimIndent()
        )
        assertEquals(TaskOutcome.SUCCESS, runner.build().task(":checkProductionStrings")?.outcome)
    }

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
                id("arcile.build.verification")
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
