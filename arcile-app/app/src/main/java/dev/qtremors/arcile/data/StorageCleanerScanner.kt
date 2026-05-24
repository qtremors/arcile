package dev.qtremors.arcile.data

import dev.qtremors.arcile.di.ArcileDispatchers
import dev.qtremors.arcile.domain.CleanerCandidate
import dev.qtremors.arcile.domain.CleanerGroup
import dev.qtremors.arcile.domain.CleanerGroupType
import dev.qtremors.arcile.domain.StorageCleanerResult
import dev.qtremors.arcile.domain.StorageCleanerScanLimits
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import javax.inject.Inject

class StorageCleanerScanner @Inject constructor(
    private val dispatchers: ArcileDispatchers
) {
    suspend fun scan(
        rootPaths: List<String>,
        now: Long = System.currentTimeMillis(),
        limits: StorageCleanerScanLimits = StorageCleanerScanLimits()
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
            .filter { it.size > 0L }
            .groupBy { it.name.lowercase(Locale.ROOT) to it.size }
            .filterValues { it.size > 1 }
            .keys

        val grouped = CleanerGroupType.entries.associateWith { mutableListOf<CleanerCandidate>() }
        files.forEach { file ->
            val groups = buildSet {
                if (file.size >= limits.largeFileThresholdBytes) add(CleanerGroupType.LargeFiles)
                if (file.isInDownloads && now - file.lastModified >= limits.oldDownloadAgeMs) add(CleanerGroupType.OldDownloads)
                if (file.extension == "apk") add(CleanerGroupType.Apks)
                if (file.extension in videoExtensions) add(CleanerGroupType.Videos)
                if (isJunk(file)) add(CleanerGroupType.Junk)
                if ((file.name.lowercase(Locale.ROOT) to file.size) in duplicateKeys) add(CleanerGroupType.Duplicates)
            }

            groups.forEach { group ->
                grouped.getValue(group) += CleanerCandidate(
                    name = file.name,
                    absolutePath = file.absolutePath,
                    size = file.size,
                    lastModified = file.lastModified,
                    groupTypes = groups
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
            children.forEach { child -> pending.add(child to depth + 1) }
        }

        return partial
    }

    private fun File.toSnapshot(): FileSnapshot {
        val ext = extension.lowercase(Locale.ROOT)
        return FileSnapshot(
            name = name,
            absolutePath = absolutePath,
            size = length().coerceAtLeast(0L),
            lastModified = lastModified(),
            extension = ext,
            isInDownloads = absolutePath.split(File.separatorChar).any { it.equals("download", ignoreCase = true) || it.equals("downloads", ignoreCase = true) }
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
            lowerName.endsWith(".temp") ||
            lowerName == "thumbs.db"
    }

    private data class FileSnapshot(
        val name: String,
        val absolutePath: String,
        val size: Long,
        val lastModified: Long,
        val extension: String,
        val isInDownloads: Boolean
    )

    private companion object {
        val videoExtensions = setOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "m4v")
        val junkExtensions = setOf("tmp", "temp", "log", "bak", "old", "dmp")
    }
}
