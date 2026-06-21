package dev.qtremors.arcile.core.storage.data

import dev.qtremors.arcile.di.ArcileDispatchers
import dev.qtremors.arcile.core.storage.domain.CleanerCandidate
import dev.qtremors.arcile.core.storage.domain.FileCategories
import dev.qtremors.arcile.core.storage.domain.CleanerGroup
import dev.qtremors.arcile.core.storage.domain.CleanerGroupType
import dev.qtremors.arcile.core.storage.domain.CleanerRiskLevel
import dev.qtremors.arcile.core.storage.domain.CleanerRiskReason
import dev.qtremors.arcile.core.storage.domain.StorageCleanerResult
import dev.qtremors.arcile.core.storage.domain.StorageCleanerScanLimits
import dev.qtremors.arcile.core.storage.domain.StorageCleanerScanner
import dev.qtremors.arcile.core.storage.domain.StorageCleanerRules
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject

class DefaultStorageCleanerScanner @Inject constructor(
    private val dispatchers: ArcileDispatchers,
    private val snapshotStore: StorageCleanerSnapshotStore? = null
) : StorageCleanerScanner {
    override suspend fun cachedScan(
        rootPaths: List<String>,
        limits: StorageCleanerScanLimits,
        rules: StorageCleanerRules
    ): StorageCleanerResult? =
        snapshotStore?.get(rootPaths, limits, rules)

    override suspend fun scan(
        rootPaths: List<String>,
        now: Long,
        limits: StorageCleanerScanLimits,
        rules: StorageCleanerRules
    ): StorageCleanerResult = withContext(dispatchers.storage) {
        val normalizedRules = rules.normalized()
        val files = mutableListOf<FileSnapshot>()
        var partial = false

        rootPaths.distinct().forEach { rootPath ->
            val root = File(rootPath)
            if (root.exists() && root.isDirectory) {
                val result = walk(root, limits, files)
                partial = partial || result
            }
        }

        val scanFiles = files.filterNot { it.absolutePath in normalizedRules.ignoredPaths }
        val duplicateGroupKeysByPath = findDuplicateGroupKeys(scanFiles)

        val largeFileThreshold = normalizedRules.section(CleanerGroupType.LargeFiles)
            .largeFileThresholdBytes ?: limits.largeFileThresholdBytes
        val oldDownloadAgeMs = normalizedRules.section(CleanerGroupType.OldDownloads)
            .oldDownloadAgeMs ?: limits.oldDownloadAgeMs

        val grouped = CleanerGroupType.entries.associateWith { mutableListOf<CleanerCandidate>() }
        scanFiles.forEach { file ->
            val groups = buildSet {
                if (file.isDirectory) {
                    add(CleanerGroupType.EmptyFolders)
                } else if (isMarkerFile(file)) {
                    add(CleanerGroupType.MarkerFiles)
                } else {
                    if (file.size >= largeFileThreshold) add(CleanerGroupType.LargeFiles)
                    if (file.isInDownloads && now - file.lastModified >= oldDownloadAgeMs) add(CleanerGroupType.OldDownloads)
                    if (file.extension == "apk") add(CleanerGroupType.Apks)
                    if (file.extension in videoExtensions) add(CleanerGroupType.Videos)
                    if (isJunk(file)) add(CleanerGroupType.Junk)
                }
                if (file.absolutePath in duplicateGroupKeysByPath) add(CleanerGroupType.Duplicates)
            }.filterTo(linkedSetOf()) { group ->
                normalizedRules.includes(group, file)
            }

            groups.forEach { group ->
                val risk = classifyRisk(file)
                grouped.getValue(group) += CleanerCandidate(
                    name = file.name,
                    absolutePath = file.absolutePath,
                    size = file.size,
                    lastModified = file.lastModified,
                    groupTypes = groups,
                    riskLevel = risk.level,
                    riskReasons = risk.reasons,
                    isDirectory = file.isDirectory,
                    duplicateGroupKey = duplicateGroupKeysByPath[file.absolutePath]
                )
            }
        }

        val resultGroups = CleanerGroupType.entries.map { type ->
            val sorted = grouped.getValue(type)
                .distinctBy { it.absolutePath }
                .sortedWith(compareByDescending<CleanerCandidate> { it.size }.thenBy { it.name.lowercase(Locale.ROOT) })
                .take(limits.maxCandidatesPerGroup)
            CleanerGroup(type, sorted)
        }

        val result = StorageCleanerResult(
            groups = resultGroups,
            scannedFiles = files.size,
            isPartial = partial || files.size >= limits.maxFiles
        )
        snapshotStore?.put(rootPaths, limits, rules, result)
        result
    }

    override suspend fun invalidateStorageCleaner(paths: Collection<String>) {
        snapshotStore?.clear()
    }

    private suspend fun findDuplicateGroupKeys(files: List<FileSnapshot>): Map<String, String> {
        val duplicates = linkedMapOf<String, String>()
        val sameSizeGroups = files
            .filterNot { it.isDirectory }
            .filter { it.size > 0L }
            .groupBy { it.size }
            .filterValues { it.size > 1 }

        sameSizeGroups.values.forEach { sameSizeFiles ->
            currentCoroutineContext().ensureActive()
            sameSizeFiles
                .groupBy { sampleHash(File(it.absolutePath), it.size) }
                .filterKeys { it != null }
                .filterValues { it.size > 1 }
                .values
                .forEach { sampledFiles ->
                    sampledFiles
                        .groupBy { fullHash(File(it.absolutePath)) }
                        .filterKeys { it != null }
                        .filterValues { it.size > 1 }
                        .forEach { (hash, matchingFiles) ->
                            val groupKey = "${matchingFiles.first().size}:$hash"
                            matchingFiles.forEach { duplicates[it.absolutePath] = groupKey }
                        }
                }
        }
        return duplicates
    }

    private fun StorageCleanerRules.includes(type: CleanerGroupType, file: FileSnapshot): Boolean {
        val rule = section(type)
        if (!rule.enabled) return false
        val lowerName = file.name.lowercase(Locale.ROOT)
        val lowerPath = file.absolutePath.lowercase(Locale.ROOT)
        return rule.ignoredNamePatterns.none { patternMatches(it, lowerName) } &&
            rule.ignoredPathPatterns.none { patternMatches(it, lowerPath) }
    }

    private fun patternMatches(pattern: String, lowerValue: String): Boolean {
        val lowerPattern = pattern.lowercase(Locale.ROOT)
        if ('*' !in lowerPattern && '?' !in lowerPattern) {
            return lowerValue.contains(lowerPattern)
        }
        val regex = buildString {
            append("^")
            lowerPattern.forEach { char ->
                when (char) {
                    '*' -> append(".*")
                    '?' -> append(".")
                    else -> append(Regex.escape(char.toString()))
                }
            }
            append("$")
        }.toRegex()
        return regex.matches(lowerValue)
    }

    private fun sampleHash(file: File, size: Long): String? = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            if (size <= SAMPLE_WINDOW_BYTES * 3L) {
                input.copyTo(DigestOutputStreamAdapter(digest))
            } else {
                updateDigestAt(file, digest, 0L)
                updateDigestAt(file, digest, (size / 2L - SAMPLE_WINDOW_BYTES / 2L).coerceAtLeast(0L))
                updateDigestAt(file, digest, (size - SAMPLE_WINDOW_BYTES).coerceAtLeast(0L))
            }
        }
        digest.digest().toHex()
    }.getOrNull()

    private fun updateDigestAt(file: File, digest: MessageDigest, offset: Long) {
        file.inputStream().use { input ->
            var remainingSkip = offset
            while (remainingSkip > 0L) {
                val skipped = input.skip(remainingSkip)
                if (skipped <= 0L) return
                remainingSkip -= skipped
            }
            val buffer = ByteArray(SAMPLE_WINDOW_BYTES)
            val read = input.read(buffer)
            if (read > 0) digest.update(buffer, 0, read)
        }
    }

    private fun fullHash(file: File): String? = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> input.copyTo(DigestOutputStreamAdapter(digest)) }
        digest.digest().toHex()
    }.getOrNull()

    private class DigestOutputStreamAdapter(
        private val digest: MessageDigest
    ) : java.io.OutputStream() {
        override fun write(b: Int) {
            digest.update(b.toByte())
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            digest.update(b, off, len)
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private suspend fun walk(
        root: File,
        limits: StorageCleanerScanLimits,
        out: MutableList<FileSnapshot>
    ): Boolean {
        val pending = ArrayDeque<Pair<File, Int>>()
        pending.add(root to 0)
        var partial = false

        while (pending.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            if (out.size >= limits.maxFiles) return true

            val (current, depth) = pending.removeFirst()
            if (shouldSkip(current)) continue

            if (current.isFile) {
                out += current.toSnapshot()
                continue
            }

            if (!current.isDirectory) continue
            if (depth >= limits.maxDepth) {
                partial = true
                continue
            }

            val children = try {
                current.listFiles()
            } catch (_: Exception) {
                null
            }
            if (children == null) {
                partial = true
                continue
            }
            if (children.isEmpty() && depth > 0) {
                out += current.toSnapshot(isDirectory = true)
                continue
            }
            children.forEach { child -> pending.add(child to depth + 1) }
        }

        return partial
    }

    private fun File.toSnapshot(isDirectory: Boolean = false): FileSnapshot {
        val ext = extension.lowercase(Locale.ROOT)
        val segments = absolutePath.split(File.separatorChar)
        return FileSnapshot(
            name = name,
            absolutePath = absolutePath,
            size = if (isDirectory) 0L else length().coerceAtLeast(0L),
            lastModified = lastModified(),
            extension = ext,
            pathSegments = segments,
            isInDownloads = segments.any { it.equals("download", ignoreCase = true) || it.equals("downloads", ignoreCase = true) },
            isDirectory = isDirectory
        )
    }

    private fun shouldSkip(file: File): Boolean {
        val name = file.name.lowercase(Locale.ROOT)
        val path = file.absolutePath.lowercase(Locale.ROOT)
        return name == ".arcile" ||
            name == ".trash" ||
            name == ".thumbnails" ||
            path.contains("${File.separator}.arcile${File.separator}.trash")
    }

    private fun isJunk(file: FileSnapshot): Boolean {
        val lowerName = file.name.lowercase(Locale.ROOT)
        return file.extension in junkExtensions ||
            lowerName.endsWith(".tmp") ||
            lowerName.endsWith(".temp")
    }

    private fun isMarkerFile(file: FileSnapshot): Boolean =
        file.name.lowercase(Locale.ROOT) in markerFileNames

    private fun classifyRisk(file: FileSnapshot): RiskClassification {
        val reasons = linkedSetOf<CleanerRiskReason>()
        val lowerSegments = file.pathSegments.map { it.lowercase(Locale.ROOT) }
        val lowerPath = file.absolutePath.lowercase(Locale.ROOT)
        val parentSegments = lowerSegments.dropLast(1)

        if (".arcile" in lowerSegments) reasons += CleanerRiskReason.ArcileInternal
        if (isAndroidSensitivePath(lowerSegments)) reasons += CleanerRiskReason.SystemOwnedPath
        if (parentSegments.any(::isPackageLikeSegment)) reasons += CleanerRiskReason.AppLikeFolder
        if (parentSegments.any { it in tempOrCacheFolderNames }) reasons += CleanerRiskReason.TemporaryOrCache
        if (parentSegments.any { it in userFolderNames }) reasons += CleanerRiskReason.UserFolder
        if (parentSegments.any { it in mediaFolderNames }) reasons += CleanerRiskReason.MediaFolder

        when (file.extension) {
            "log" -> reasons += CleanerRiskReason.LogFile
            "bak", "old" -> reasons += CleanerRiskReason.BackupFile
            "dmp" -> reasons += CleanerRiskReason.DumpFile
            "tmp", "temp" -> reasons += CleanerRiskReason.TemporaryOrCache
        }
        if (file.name.lowercase(Locale.ROOT).endsWith(".tmp") || lowerPath.endsWith(".temp")) {
            reasons += CleanerRiskReason.TemporaryOrCache
        }

        val level = when {
            reasons.any {
                it == CleanerRiskReason.ArcileInternal ||
                    it == CleanerRiskReason.SystemOwnedPath ||
                    it == CleanerRiskReason.AppLikeFolder
            } -> CleanerRiskLevel.High
            reasons.any {
                it == CleanerRiskReason.UserFolder ||
                    it == CleanerRiskReason.MediaFolder ||
                    it == CleanerRiskReason.LogFile ||
                    it == CleanerRiskReason.BackupFile ||
                    it == CleanerRiskReason.DumpFile
            } -> CleanerRiskLevel.Review
            else -> CleanerRiskLevel.Low
        }
        return RiskClassification(level, reasons)
    }

    private fun isAndroidSensitivePath(segments: List<String>): Boolean {
        val androidIndex = segments.indexOf("android")
        if (androidIndex < 0 || androidIndex == segments.lastIndex) return false
        return segments[androidIndex + 1] == "data" || segments[androidIndex + 1] == "obb"
    }

    private fun isPackageLikeSegment(segment: String): Boolean =
        packageSegmentRegex.matches(segment)

    private data class FileSnapshot(
        val name: String,
        val absolutePath: String,
        val size: Long,
        val lastModified: Long,
        val extension: String,
        val pathSegments: List<String>,
        val isInDownloads: Boolean,
        val isDirectory: Boolean
    )

    private data class RiskClassification(
        val level: CleanerRiskLevel,
        val reasons: Set<CleanerRiskReason>
    )

    private companion object {
        val videoExtensions = FileCategories.Videos.extensions
        val junkExtensions = setOf("tmp", "temp", "log", "bak", "old", "dmp")
        val markerFileNames = setOf(".nomedia", "desktop.ini", "thumbs.db", ".ds_store")
        val tempOrCacheFolderNames = setOf("temp", "tmp", "cache", "caches")
        val userFolderNames = setOf("download", "downloads", "documents", "document")
        val mediaFolderNames = setOf("dcim", "pictures", "picture", "movies", "movie", "videos", "video")
        val packageSegmentRegex = Regex("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*){1,}")
        const val SAMPLE_WINDOW_BYTES = 4096
    }
}
