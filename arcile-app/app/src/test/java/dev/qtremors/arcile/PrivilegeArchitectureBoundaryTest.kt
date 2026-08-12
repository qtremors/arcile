package dev.qtremors.arcile

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PrivilegeArchitectureBoundaryTest {
    @Test
    fun `libsu and Shizuku stay inside the Android privilege module`() {
        val forbiddenImports = Regex(
            """^import\s+(?:rikka\.shizuku|com\.topjohnwu\.superuser)"""
        )
        val offenders = productionSources()
            .filterNot { it.relativePath.startsWith("core/privilege/android/") }
            .flatMap { source -> source.matches(forbiddenImports) }
            .toList()

        assertNoOffenders(
            "libsu and Shizuku imports must stay inside core:privilege:android",
            offenders
        )
    }

    @Test
    fun `production code exposes no arbitrary shell execution interface`() {
        val forbidden = Regex(
            """Runtime\.getRuntime\(\)\.exec|ProcessBuilder\s*\(|Shell\.cmd\s*\("""
        )
        val offenders = productionSources()
            .flatMap { source -> source.matches(forbidden) }
            .toList()

        assertNoOffenders("Arbitrary shell execution is not an Arcile filesystem API", offenders)
    }

    @Test
    fun `presentation and features do not depend on Binder descriptors`() {
        val forbidden = Regex(
            """\b(?:android\.os\.)?(?:Binder|IBinder|ParcelFileDescriptor)\b"""
        )
        val guardedPrefixes = listOf("app/src/main/java/dev/qtremors/arcile/presentation/", "feature/")
        val offenders = productionSources()
            .filter { source -> guardedPrefixes.any(source.relativePath::startsWith) }
            .flatMap { source -> source.matches(forbidden) }
            .toList()

        assertNoOffenders("Presentation and feature code must use neutral storage contracts", offenders)
    }

    @Test
    fun `protected content URI carries only the opaque token`() {
        val manager = source("core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/DefaultPrivilegedContentAccessManager.kt")
        val provider = source("core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/PrivilegedContentProvider.kt")

        assertTrue(manager.contains(".appendPath(token)"))
        assertTrue(!manager.contains(".appendPath(authoritativeNode"))
        assertTrue(provider.contains("uri.pathSegments.singleOrNull()"))
        assertTrue(!provider.contains("getQueryParameter(\"path\")"))
    }

    @Test
    fun `external provider tokens are never logged`() {
        val loggerAndToken = Regex("""(?:AppLogger|Log\.)[^\n]*\btoken\b|\btoken\b[^\n]*(?:AppLogger|Log\.)""")
        val offenders = productionSources()
            .flatMap { source -> source.matches(loggerAndToken) }
            .toList()

        assertNoOffenders("Protected-content tokens must never enter logs", offenders)
    }

    @Test
    fun `recursive privileged deletion is guarded by path scope validation`() {
        val relativePath =
            "core/storage/data/src/main/java/dev/qtremors/arcile/core/storage/data/source/PrivilegedFileSystemDataSource.kt"
        val lines = File(projectRoot(), relativePath).readLines()
        val offenders = lines.mapIndexedNotNull { index, line ->
            if ("client.deleteRecursively(" !in line) return@mapIndexedNotNull null
            val guardWindow = lines.subList((index - 12).coerceAtLeast(0), index + 1)
                .joinToString("\n")
            val hasScope = "PrivilegedPathOperation.RECURSIVE_DELETE" in guardWindow
            val hasValidation = "pathPolicy.validate(" in guardWindow
            if (hasScope && hasValidation) null else "$relativePath:${index + 1}: ${line.trim()}"
        }

        assertNoOffenders(
            "Recursive privileged deletion must validate the protected-path scope first",
            offenders
        )
    }

    private fun productionSources(): Sequence<SourceFile> = projectRoot().walkTopDown()
        .onEnter { it.name !in setOf("build", ".gradle", ".git", ".idea", ".kotlin") }
        .filter { file ->
            file.isFile &&
                file.extension in setOf("kt", "java") &&
                "/src/main/" in file.invariantSeparatorsPath
        }
        .map { file ->
            SourceFile(
                file = file,
                relativePath = file.relativeTo(projectRoot()).invariantSeparatorsPath
            )
        }

    private fun SourceFile.matches(pattern: Regex): Sequence<String> = file.useLines { lines ->
        lines.mapIndexedNotNull { index, line ->
            if (pattern.containsMatchIn(line)) "$relativePath:${index + 1}: ${line.trim()}" else null
        }.toList().asSequence()
    }

    private fun source(relativePath: String): String = File(projectRoot(), relativePath).readText()

    private fun assertNoOffenders(message: String, offenders: List<String>) {
        if (offenders.isNotEmpty()) fail("$message:\n${offenders.joinToString("\n")}")
    }

    private fun projectRoot(): File =
        generateSequence(File(System.getProperty("user.dir") ?: ".").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "app/src/main/java/dev/qtremors/arcile").exists() }
            ?: File(".").absoluteFile

    private data class SourceFile(val file: File, val relativePath: String)
}
