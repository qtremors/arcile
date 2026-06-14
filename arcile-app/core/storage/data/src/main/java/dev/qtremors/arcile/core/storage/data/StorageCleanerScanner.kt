package dev.qtremors.arcile.core.storage.data

import dev.qtremors.arcile.di.ArcileDispatchers
import dev.qtremors.arcile.core.storage.domain.CleanerCandidate
import dev.qtremors.arcile.core.storage.domain.CleanerGroup
import dev.qtremors.arcile.core.storage.domain.CleanerGroupType
import dev.qtremors.arcile.core.storage.domain.CleanerRiskLevel
import dev.qtremors.arcile.core.storage.domain.CleanerRiskReason
import dev.qtremors.arcile.core.storage.domain.StorageCleanerResult
import dev.qtremors.arcile.core.storage.domain.StorageCleanerScanLimits
import dev.qtremors.arcile.core.storage.domain.StorageCleanerScanner
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import javax.inject.Inject

class DefaultStorageCleanerScanner @Inject constructor(
    private val dispatchers: ArcileDispatchers
) : StorageCleanerScanner {
    override suspend fun scan(
        rootPaths: List<String>,
        now: Long,
        limits: StorageCleanerScanLimits
    ): StorageCleanerResult = withContext(dispatchers.storage) {
        val files = mutableListOf<FileSnapshot>()
        var partial = false

        rootPaths.distinct().forEach { rootPath ->
            val root = File(rootPath)
            if (root.exists() && root.isDirectory) {
                val result = walk(root, limits, files)
                partial = partial || result
            }
        }

        val duplicateKeys = files
            .filterNot { it.isDirectory }
            .filter { it.size > 0L }
            .groupBy { it.name.lowercase(Locale.ROOT) to it.size }
            .filterValues { it.size > 1 }
            .keys

        val grouped = CleanerGroupType.entries.associateWith { mutableListOf<CleanerCandidate>() }
        files.forEach { file ->
            val groups = buildSet {
                if (file.isDirectory) {
                    add(CleanerGroupType.EmptyFolders)
                } else if (isMarkerFile(file)) {
                    add(CleanerGroupType.MarkerFiles)
                } else {
                    if (file.size >= limits.largeFileThresholdBytes) add(CleanerGroupType.LargeFiles)
                    if (file.isInDownloads && now - file.lastModified >= limits.oldDownloadAgeMs) add(CleanerGroupType.OldDownloads)
                    if (file.extension == "apk") add(CleanerGroupType.Apks)
                    if (file.extension in videoExtensions) add(CleanerGroupType.Videos)
                    if (isJunk(file)) add(CleanerGroupType.Junk)
                }
                if ((file.name.lowercase(Locale.ROOT) to file.size) in duplicateKeys) add(CleanerGroupType.Duplicates)
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
                    isDirectory = file.isDirectory
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

        StorageCleanerResult(
            groups = resultGroups,
            scannedFiles = files.size,
            isPartial = partial || files.size >= limits.maxFiles
        )
    }

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
        val videoExtensions = setOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "m4v")
        val junkExtensions = setOf("tmp", "temp", "log", "bak", "old", "dmp")
        val markerFileNames = setOf(".nomedia", "desktop.ini", "thumbs.db", ".ds_store")
        val tempOrCacheFolderNames = setOf("temp", "tmp", "cache", "caches")
        val userFolderNames = setOf("download", "downloads", "documents", "document")
        val mediaFolderNames = setOf("dcim", "pictures", "picture", "movies", "movie", "videos", "video")
        val packageSegmentRegex = Regex("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*){1,}")
    }
}
