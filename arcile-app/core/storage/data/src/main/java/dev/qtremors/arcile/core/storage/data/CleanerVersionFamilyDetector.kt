package dev.qtremors.arcile.core.storage.data

import dev.qtremors.arcile.core.storage.domain.FilenameVersionEvidence
import java.io.File
import java.util.Locale

internal object CleanerVersionFamilyDetector {
    data class VersionMemberWithStatus(
        val snapshot: CleanerFileSnapshot,
        val rank: List<Long>,
        val isLikelyNewest: Boolean
    )

    data class VersionFamily(
        val key: String,
        val displayStem: String,
        val evidenceType: FilenameVersionEvidence,
        val members: List<VersionMemberWithStatus>
    )

    private data class ParsedVersionInfo(
        val normalizedStem: String,
        val displayStem: String,
        val evidenceType: FilenameVersionEvidence,
        val rank: List<Long>,
        val isExplicitVersion: Boolean,
        val packageName: String? = null
    )

    private fun parseFilenameVersion(
        snapshot: CleanerFileSnapshot,
        apkPackageResolver: (String) -> Pair<String, Long>?
    ): ParsedVersionInfo? {
        val stem = snapshot.name.substringBeforeLast('.', snapshot.name).trim()
        if (stem.isBlank()) return null
        if (isRejectedVersionCandidate(stem)) return null

        val ext = snapshot.extension.lowercase(Locale.ROOT)
        val isApk = ext == "apk"

        // 1. Check duplicate suffix: (1), (2), - Copy, - Copy (2), etc.
        val dupMatch = DUPLICATE_SUFFIX_REGEX.find(stem)
        if (dupMatch != null) {
            val baseStem = dupMatch.groupValues[1].trimEnd(' ', '-', '_', '.')
            if (baseStem.isNotBlank() && !isRejectedVersionCandidate(baseStem)) {
                val ordinal = dupMatch.groupValues[2].takeIf { it.isNotBlank() }?.toLongOrNull()
                    ?: dupMatch.groupValues[3].takeIf { it.isNotBlank() }?.toLongOrNull()
                    ?: 1L
                return ParsedVersionInfo(
                    normalizedStem = normalizeStemKey(baseStem),
                    displayStem = baseStem,
                    evidenceType = FilenameVersionEvidence.DuplicateSuffix,
                    rank = listOf(ordinal, snapshot.lastModified),
                    isExplicitVersion = true
                )
            }
        }

        // 2. Check explicit semantic version: at least 2 numeric components (e.g. 1.0, 2.1.3, v1.2, 1.0.0-rc1)
        val semMatch = SEMANTIC_VERSION_REGEX.find(stem)
        if (semMatch != null) {
            val baseStem = stem.substring(0, semMatch.range.first).trimEnd(' ', '-', '_', '.')
            val verString = semMatch.groupValues[1]
            val digits = extractVersionNumbers(verString)
            if (digits.size >= 2) {
                val effectiveBase = if (baseStem.isBlank()) "app" else baseStem
                if (!isRejectedVersionCandidate(effectiveBase)) {
                    var pkgName: String? = null
                    var pkgVersionCode = 0L
                    if (isApk) {
                        val pkgInfo = apkPackageResolver(snapshot.absolutePath)
                        if (pkgInfo != null) {
                            pkgName = pkgInfo.first
                            pkgVersionCode = pkgInfo.second
                        }
                    }
                    val rank = if (pkgVersionCode > 0L) {
                        listOf(pkgVersionCode) + digits + listOf(snapshot.lastModified)
                    } else {
                        digits + listOf(snapshot.lastModified)
                    }
                    return ParsedVersionInfo(
                        normalizedStem = normalizeStemKey(effectiveBase),
                        displayStem = effectiveBase,
                        evidenceType = if (pkgName != null) FilenameVersionEvidence.PackageVersion else FilenameVersionEvidence.SemanticVersion,
                        rank = rank,
                        isExplicitVersion = true,
                        packageName = pkgName
                    )
                }
            }
        }

        // 3. For APKs only: single numeric version when parsed package IDs match (e.g. arcile-1.apk, arcile-2.apk)
        if (isApk) {
            val singleNumMatch = SINGLE_NUMBER_VERSION_REGEX.find(stem)
            if (singleNumMatch != null) {
                val baseStem = stem.substring(0, singleNumMatch.range.first).trimEnd(' ', '-', '_', '.')
                val singleNum = singleNumMatch.groupValues[1].toLongOrNull() ?: 0L
                val pkgInfo = apkPackageResolver(snapshot.absolutePath)
                if (pkgInfo != null) {
                    val pkgName = pkgInfo.first
                    val pkgVersionCode = pkgInfo.second
                    val effectiveBase = if (baseStem.isBlank()) pkgName else baseStem
                    return ParsedVersionInfo(
                        normalizedStem = normalizeStemKey(effectiveBase),
                        displayStem = effectiveBase,
                        evidenceType = FilenameVersionEvidence.PackageVersion,
                        rank = listOf(pkgVersionCode, singleNum, snapshot.lastModified),
                        isExplicitVersion = true,
                        packageName = pkgName
                    )
                }
            }
        }

        // Base candidate for duplicate copy families (e.g. "document.pdf" paired with "document (1).pdf")
        if (!isRejectedVersionCandidate(stem)) {
            return ParsedVersionInfo(
                normalizedStem = normalizeStemKey(stem),
                displayStem = stem,
                evidenceType = FilenameVersionEvidence.DuplicateSuffix,
                rank = listOf(0L, snapshot.lastModified),
                isExplicitVersion = false
            )
        }

        return null
    }

    fun findFilenameVersionFamilies(
        files: List<CleanerFileSnapshot>,
        apkPackageResolver: (String) -> Pair<String, Long>?
    ): List<VersionFamily> {
        val parentMap = files.groupBy { File(it.absolutePath).parent.orEmpty() }
        val result = mutableListOf<VersionFamily>()

        for ((parentPath, siblingFiles) in parentMap) {
            if (parentPath.isBlank() || siblingFiles.size < 2) continue

            val parsedList = siblingFiles.mapNotNull { file ->
                val info = parseFilenameVersion(file, apkPackageResolver)
                if (info != null) file to info else null
            }

            val stemGroups = parsedList.groupBy { (file, info) ->
                Triple(info.normalizedStem, file.extension.lowercase(Locale.ROOT), info.packageName)
            }

            for ((key, members) in stemGroups) {
                if (members.size < 2) continue

                val hasExplicitVersion = members.any { it.second.isExplicitVersion }
                if (!hasExplicitVersion) continue

                val evidenceType = when {
                    members.any { it.second.evidenceType == FilenameVersionEvidence.PackageVersion } ->
                        FilenameVersionEvidence.PackageVersion
                    members.any { it.second.evidenceType == FilenameVersionEvidence.SemanticVersion } ->
                        FilenameVersionEvidence.SemanticVersion
                    else -> FilenameVersionEvidence.DuplicateSuffix
                }

                val displayStem = members.first { it.second.isExplicitVersion }.second.displayStem
                val sortedMembers = members.sortedWith(versionRankComparator.reversed())
                val newestMember = sortedMembers.first()

                val normalizedParent = parentPath.replace('\\', '/')
                val familyKey = "${evidenceType.name}:$normalizedParent/${key.first}.${key.second}"
                result += VersionFamily(
                    key = familyKey,
                    displayStem = displayStem,
                    evidenceType = evidenceType,
                    members = sortedMembers.map { member ->
                        VersionMemberWithStatus(
                            snapshot = member.first,
                            rank = member.second.rank,
                            isLikelyNewest = member == newestMember
                        )
                    }
                )
            }
        }
        return result
    }

    private val DUPLICATE_SUFFIX_REGEX = Regex("^(.*?)(?:[\\s._-]+(?:copy|copie))?(?:[\\s._-]*\\((\\d+)\\)|[\\s._-]+(?:copy|copie)(?:\\s+(\\d+))?)$", RegexOption.IGNORE_CASE)
    private val SEMANTIC_VERSION_REGEX = Regex("(?:[-_.\\s]+[vV]?|[vV])(\\d+(?:\\.\\d+)+(?:[-._]?[a-zA-Z0-9]+)*)$")
    private val SINGLE_NUMBER_VERSION_REGEX = Regex("(?:[-_.\\s]+[vV]?|[vV])(\\d+)$")
    private val DATE_REGEX = Regex("(?:^|[^0-9])(?:20\\d{2}[-_.]?(?:0[1-9]|1[0-2])[-_.]?(?:0[1-9]|[12][0-9]|3[01])|(?:0[1-9]|[12][0-9]|3[01])[-_.](?:0[1-9]|1[0-2])[-_.](?:19|20)\\d{2})(?:[^0-9]|$)")
    private val CAMERA_REGEX = Regex("^(?:img|vid|dsc|pano|sam|wp|screenshot|screen_recording|mov|aud)[-_]?\\d+", RegexOption.IGNORE_CASE)
    private val DOCUMENT_NUMBERING_REGEX = Regex("\\b(?:page|chapter|part|vol|volume|p|ch|track|ep|episode)[-_.\\s]*\\d+\\b", RegexOption.IGNORE_CASE)

    private fun isRejectedVersionCandidate(stem: String): Boolean {
        val lower = stem.lowercase(Locale.ROOT).trim()
        if (DATE_REGEX.containsMatchIn(lower)) return true
        if (CAMERA_REGEX.containsMatchIn(lower)) return true
        if (DOCUMENT_NUMBERING_REGEX.containsMatchIn(lower)) return true
        return false
    }

    private fun normalizeStemKey(stem: String): String =
        stem.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')

    private fun extractVersionNumbers(versionString: String): List<Long> {
        return Regex("\\d+").findAll(versionString).mapNotNull {
            it.value.toLongOrNull()
        }.toList()
    }

    private val versionRankComparator = Comparator<Pair<CleanerFileSnapshot, ParsedVersionInfo>> { a, b ->
        val rankA = a.second.rank
        val rankB = b.second.rank
        val maxLen = maxOf(rankA.size, rankB.size)
        for (i in 0 until maxLen) {
            val valA = rankA.getOrNull(i) ?: 0L
            val valB = rankB.getOrNull(i) ?: 0L
            if (valA != valB) {
                return@Comparator valA.compareTo(valB)
            }
        }
        a.first.lastModified.compareTo(b.first.lastModified)
    }
}
