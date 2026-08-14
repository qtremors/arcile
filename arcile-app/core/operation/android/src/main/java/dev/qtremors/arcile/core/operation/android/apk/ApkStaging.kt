package dev.qtremors.arcile.core.operation.android.apk

import android.content.Context
import android.net.Uri
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal const val APK_STAGING_PREFIX = "apk_staging_"
private const val APK_STAGING_BUFFER_SIZE = 64 * 1024

internal data class ApkStagingPolicy(
    val maxArchiveEntries: Int = 512,
    val maxApkEntries: Int = 128,
    val maxEntryPathLength: Int = 4_096,
    val maxEntryBytes: Long = 1L * 1024L * 1024L * 1024L,
    val maxTotalBytes: Long = 4L * 1024L * 1024L * 1024L,
    val maxCompressionRatio: Double = 100.0,
    val maxElapsedMillis: Long = 30_000L,
    val freeSpaceReserveBytes: Long = 50L * 1024L * 1024L
)

internal data class ApkStagingResult(
    val directory: File,
    val apks: List<ApkPackageParser.ExtractedApk>
)

internal suspend fun stageCompatibleArchiveApks(
    cacheDir: File,
    archiveFile: File,
    target: ApkPackageParser.ApkArchiveDeviceTarget,
    policy: ApkStagingPolicy = ApkStagingPolicy(),
    elapsedRealtimeNanos: () -> Long = System::nanoTime
): ApkStagingResult {
    val startedAt = elapsedRealtimeNanos()
    suspend fun checkWorkBudget() {
        currentCoroutineContext().ensureActive()
        val elapsedMillis = (elapsedRealtimeNanos() - startedAt).coerceAtLeast(0L) / 1_000_000L
        require(elapsedMillis <= policy.maxElapsedMillis) { "APK package took too long to inspect safely" }
    }

    ZipFile(archiveFile).use { zip ->
        val candidates = mutableListOf<Pair<ZipEntry, ApkPackageParser.ExtractedApk>>()
        val seenPaths = mutableSetOf<String>()
        val entries = zip.entries()
        var archiveEntryCount = 0
        while (entries.hasMoreElements()) {
            checkWorkBudget()
            val entry = entries.nextElement()
            archiveEntryCount += 1
            require(archiveEntryCount <= policy.maxArchiveEntries) { "APK package contains too many entries" }
            if (entry.isDirectory || !entry.name.endsWith(".apk", ignoreCase = true)) continue
            require(candidates.size < policy.maxApkEntries) { "APK package contains too many APK files" }
            val normalizedPath = entry.name.replace('\\', '/').trimStart('/').lowercase()
            require(normalizedPath.length <= policy.maxEntryPathLength) { "APK package entry path is too long" }
            require(normalizedPath.isNotBlank() && seenPaths.add(normalizedPath)) {
                "APK package contains duplicate APK paths"
            }
            validateDeclaredEntry(entry, policy)
            val leafName = normalizedPath.substringAfterLast('/')
            val placeholder = File(
                "${candidates.size.toString().padStart(4, '0')}_$leafName"
            )
            candidates += entry to ApkPackageParser.ExtractedApk(placeholder, entry.name)
        }

        require(candidates.isNotEmpty()) { "APK package does not contain installable APK files" }
        val selected = ApkPackageParser.selectCompatibleArchiveApks(candidates.map { it.second }, target)
        require(selected.isNotEmpty()) { "APK package has no files compatible with this device" }
        val selectedPaths = selected.mapTo(linkedSetOf()) { it.archivePath }
        val selectedEntries = candidates.filter { it.second.archivePath in selectedPaths }
        val declaredTotal = selectedEntries.sumOf { (entry, _) -> entry.size.coerceAtLeast(0L) }
        require(declaredTotal <= policy.maxTotalBytes) { "APK package is too large to stage safely" }

        val usableSpace = cacheDir.usableSpace
        val availableBytes = usableSpace
            .takeIf { it > 0L }
            ?.minus(policy.freeSpaceReserveBytes)
            ?.coerceAtLeast(0L)
        require(availableBytes == null || declaredTotal <= availableBytes) {
            "Not enough free space to stage this APK package"
        }

        val stagingDirectory = createUniqueStagingDirectory(cacheDir)
        val extracted = mutableListOf<ApkPackageParser.ExtractedApk>()
        var totalBytes = 0L
        try {
            selectedEntries.forEachIndexed { index, (entry, descriptor) ->
                checkWorkBudget()
                val output = File(
                    stagingDirectory,
                    "${index.toString().padStart(4, '0')}_${entry.name.replace('\\', '/').substringAfterLast('/')}"
                )
                var entryBytes = 0L
                zip.getInputStream(entry).use { rawInput ->
                    BufferedInputStream(rawInput).use { input ->
                        BufferedOutputStream(output.outputStream()).use { targetOutput ->
                            val buffer = ByteArray(APK_STAGING_BUFFER_SIZE)
                            while (true) {
                                checkWorkBudget()
                                val count = input.read(buffer)
                                if (count < 0) break
                                entryBytes = Math.addExact(entryBytes, count.toLong())
                                totalBytes = Math.addExact(totalBytes, count.toLong())
                                require(entryBytes <= policy.maxEntryBytes) { "APK package entry is too large" }
                                require(totalBytes <= policy.maxTotalBytes) { "APK package is too large to stage safely" }
                                require(availableBytes == null || totalBytes <= availableBytes) {
                                    "Not enough free space to stage this APK package"
                                }
                                enforceCompressionRatio(entryBytes, entry.compressedSize, policy.maxCompressionRatio)
                                targetOutput.write(buffer, 0, count)
                            }
                        }
                    }
                }
                extracted += ApkPackageParser.ExtractedApk(output, descriptor.archivePath)
            }
            return ApkStagingResult(stagingDirectory, extracted)
        } catch (throwable: Throwable) {
            stagingDirectory.deleteRecursively()
            throw throwable
        }
    }
}

internal fun cleanupApkStaging(context: Context, details: ApkPackageDetails?) {
    val path = details?.stagingDirectoryPath ?: return
    runCatching { cleanupOwnedApkStagingDirectory(context.cacheDir, File(path)) }
}

internal fun cleanupAbandonedApkStaging(context: Context) {
    context.cacheDir.listFiles().orEmpty()
        .filter { it.isDirectory && it.name.startsWith(APK_STAGING_PREFIX) }
        .forEach { directory -> runCatching { directory.deleteRecursively() } }
}

internal fun cleanupOwnedApkStagingDirectory(cacheDir: File, directory: File): Boolean {
    val canonicalCache = cacheDir.canonicalFile
    val canonicalDirectory = directory.canonicalFile
    if (canonicalDirectory.parentFile != canonicalCache || !canonicalDirectory.name.startsWith(APK_STAGING_PREFIX)) {
        return false
    }
    return !canonicalDirectory.exists() || canonicalDirectory.deleteRecursively()
}

internal fun createUniqueStagingDirectory(cacheDir: File): File {
    require(cacheDir.mkdirs() || cacheDir.isDirectory) { "APK staging cache is unavailable" }
    repeat(8) {
        val candidate = File(cacheDir, "$APK_STAGING_PREFIX${UUID.randomUUID()}")
        if (candidate.mkdir()) return candidate
    }
    error("Could not create a unique APK staging directory")
}

internal suspend fun stageContentApkPackage(
    context: Context,
    uri: Uri,
    displayName: String,
    maximumBytes: Long = ApkStagingPolicy().maxTotalBytes
): Pair<File, File> {
    require(uri.scheme == "content") { "APK capability must be a content URI" }
    val safeName = displayName.substringAfterLast('/').substringAfterLast('\\')
    require(safeName.isNotBlank() && safeName.none { it == '\u0000' }) {
        "APK package name is invalid"
    }
    val extension = safeName.substringAfterLast('.', "").lowercase()
    require(extension in setOf("apk", "apks", "apkm", "xapk")) {
        "Unsupported APK package type"
    }
    val directory = createUniqueStagingDirectory(context.cacheDir)
    val destination = File(directory, "source.$extension")
    try {
        val available = context.cacheDir.usableSpace.takeIf { it > 0L }
            ?.minus(ApkStagingPolicy().freeSpaceReserveBytes)
            ?.coerceAtLeast(0L)
        context.contentResolver.openInputStream(uri)?.use { input ->
            BufferedInputStream(input).use { bufferedInput ->
                BufferedOutputStream(destination.outputStream()).use { output ->
                    val buffer = ByteArray(APK_STAGING_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = bufferedInput.read(buffer)
                        if (count < 0) break
                        total = Math.addExact(total, count.toLong())
                        require(total <= maximumBytes) { "APK package is too large to stage safely" }
                        require(available == null || total <= available) {
                            "Not enough free space to stage this APK package"
                        }
                        output.write(buffer, 0, count)
                    }
                }
            }
        } ?: error("APK package could not be opened")
        require(destination.isFile && destination.length() > 0L) { "APK package is empty" }
        return directory to destination
    } catch (error: Throwable) {
        directory.deleteRecursively()
        throw error
    }
}

private fun validateDeclaredEntry(entry: ZipEntry, policy: ApkStagingPolicy) {
    if (entry.size >= 0L) {
        require(entry.size <= policy.maxEntryBytes) { "APK package entry is too large" }
        enforceCompressionRatio(entry.size, entry.compressedSize, policy.maxCompressionRatio)
    }
}

private fun enforceCompressionRatio(expandedBytes: Long, compressedBytes: Long, maxRatio: Double) {
    require(maxRatio >= 0.0 && !maxRatio.isNaN()) { "APK compression ratio limit is invalid" }
    if (expandedBytes == 0L || compressedBytes < 0L || maxRatio.isInfinite()) return
    val ratio = if (compressedBytes == 0L) Double.POSITIVE_INFINITY else expandedBytes.toDouble() / compressedBytes
    require(ratio <= maxRatio) { "APK package compression ratio is too high" }
}
