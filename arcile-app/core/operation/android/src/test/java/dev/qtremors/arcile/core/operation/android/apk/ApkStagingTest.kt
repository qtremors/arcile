package dev.qtremors.arcile.core.operation.android.apk

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.random.Random
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ApkStagingTest {
    private lateinit var root: File
    private lateinit var cache: File

    @Before
    fun setup() {
        root = createTempDir(prefix = "apk-staging-test").canonicalFile
        cache = File(root, "cache").apply { mkdirs() }
    }

    @After
    fun teardown() {
        root.deleteRecursively()
    }

    @Test
    fun `high ratio package is rejected without a staging directory`() = runTest {
        val archive = zip("ratio.apks", "base.apk" to ByteArray(128 * 1_024) { 1 })

        val result = runCatching {
            stageCompatibleArchiveApks(
                cache,
                archive,
                target(),
                ApkStagingPolicy(maxCompressionRatio = 2.0)
            )
        }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("ratio", ignoreCase = true) == true)
        assertNoStagingDirectories()
    }

    @Test
    fun `entry count and duplicate paths are rejected`() = runTest {
        val excessive = zip(
            "entries.apks",
            "base.apk" to byteArrayOf(1),
            "split.apk" to byteArrayOf(2)
        )
        val duplicates = zip(
            "duplicates.apks",
            "base.apk" to byteArrayOf(1),
            "BASE.APK" to byteArrayOf(2)
        )

        assertTrue(
            runCatching {
                stageCompatibleArchiveApks(
                    cache,
                    excessive,
                    target(),
                    ApkStagingPolicy(maxArchiveEntries = 1)
                )
            }.isFailure
        )
        assertTrue(runCatching { stageCompatibleArchiveApks(cache, duplicates, target()) }.isFailure)
        assertNoStagingDirectories()
    }

    @Test
    fun `only compatible APKs are extracted with collision safe names`() = runTest {
        val archive = zip(
            "compatible.apks",
            "splits/base-master.apk" to randomBytes(128),
            "splits/base-arm64_v8a.apk" to randomBytes(128),
            "splits/base-x86.apk" to randomBytes(128),
            "splits/base-en.apk" to randomBytes(128),
            "assets/base.apk" to randomBytes(128)
        )

        val staged = stageCompatibleArchiveApks(cache, archive, target())

        assertEquals(
            listOf(
                "splits/base-master.apk",
                "splits/base-arm64_v8a.apk",
                "splits/base-en.apk"
            ),
            staged.apks.map { it.archivePath }
        )
        assertEquals(staged.apks.size, staged.apks.map { it.file.name }.distinct().size)
        assertTrue(staged.apks.all { it.file.isFile })
    }

    @Test
    fun `concurrent staging owns unique directories`() = runTest {
        val archive = zip("concurrent.apks", "base.apk" to randomBytes(512))

        val results = listOf(
            async { stageCompatibleArchiveApks(cache, archive, target()) },
            async { stageCompatibleArchiveApks(cache, archive, target()) }
        ).awaitAll()

        assertNotEquals(results[0].directory, results[1].directory)
        assertTrue(results.all { it.directory.isDirectory })
        results.forEach { assertTrue(cleanupOwnedApkStagingDirectory(cache, it.directory)) }
        assertNoStagingDirectories()
    }

    @Test
    fun `cancellation and elapsed work limit leave no staging`() = runTest {
        val archive = zip("cancel.apks", "base.apk" to randomBytes(256 * 1_024))
        lateinit var extraction: kotlinx.coroutines.Job
        extraction = launch(start = CoroutineStart.LAZY) {
            stageCompatibleArchiveApks(
                cache,
                archive,
                target(),
                elapsedRealtimeNanos = {
                    extraction.cancel()
                    System.nanoTime()
                }
            )
        }
        extraction.start()
        extraction.join()

        var tick = 0L
        val timedOut = runCatching {
            stageCompatibleArchiveApks(
                cache,
                archive,
                target(),
                ApkStagingPolicy(maxElapsedMillis = 1L),
                elapsedRealtimeNanos = { tick.also { tick += 2_000_000L } }
            )
        }

        assertTrue(extraction.isCancelled)
        assertTrue(timedOut.isFailure)
        assertNoStagingDirectories()
    }

    @Test
    fun `free space rejection and cleanup never remove foreign cache directories`() = runTest {
        val archive = zip("space.apks", "base.apk" to randomBytes(128))
        val foreign = File(cache, "image_cache").apply { mkdirs() }
        val abandoned = File(cache, "${APK_STAGING_PREFIX}abandoned").apply { mkdirs() }

        val result = runCatching {
            stageCompatibleArchiveApks(
                cache,
                archive,
                target(),
                ApkStagingPolicy(freeSpaceReserveBytes = Long.MAX_VALUE)
            )
        }

        assertTrue(result.isFailure)
        assertFalse(cleanupOwnedApkStagingDirectory(cache, foreign))
        assertTrue(cleanupOwnedApkStagingDirectory(cache, abandoned))
        assertTrue(foreign.isDirectory)
        assertNoStagingDirectories()
    }

    private fun zip(name: String, vararg entries: Pair<String, ByteArray>): File {
        val file = File(root, name)
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (entryName, bytes) ->
                zip.putNextEntry(ZipEntry(entryName))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return file
    }

    private fun target() = ApkPackageParser.ApkArchiveDeviceTarget(
        supportedAbis = listOf("arm64-v8a"),
        densityDpi = 420,
        language = "en",
        region = "US"
    )

    private fun randomBytes(size: Int): ByteArray = Random(size).nextBytes(size)

    private fun assertNoStagingDirectories() {
        assertTrue(cache.listFiles().orEmpty().none { it.name.startsWith(APK_STAGING_PREFIX) })
    }
}
