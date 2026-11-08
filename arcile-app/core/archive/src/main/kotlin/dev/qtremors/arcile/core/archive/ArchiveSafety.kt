package dev.qtremors.arcile.core.archive

data class ArchiveSafetyPolicy(
    val maxEntries: Int = 10_000,
    val maxUncompressedBytes: Long = 20L * 1024L * 1024L * 1024L,
    val maxEntryPathLength: Int = 4_096,
    val maxNestedDepth: Int = 64,
    val maxCompressionRatio: Double = 1_000.0
)

class UnsafeArchiveEntryException(message: String) : IllegalArgumentException(message)

class ArchiveSafetyTally(private val safetyPolicy: ArchiveSafetyPolicy) {
    private var entries = 0
    private var uncompressedBytes = 0L

    fun accept(rawName: String, uncompressedSize: Long, compressedSize: Long?) {
        entries += 1
        require(entries <= safetyPolicy.maxEntries) { "Archive contains too many entries" }

        val normalized = rawName.replace('\\', '/')
        if (normalized.isBlank()) throw UnsafeArchiveEntryException("Archive contains an empty entry name")
        if (normalized.startsWith("/") || normalized.substringBefore('/').contains(':')) {
            throw UnsafeArchiveEntryException("Archive contains unsafe absolute paths")
        }
        if (normalized.split('/').any { it == ".." } || '\u0000' in normalized) {
            throw UnsafeArchiveEntryException("Archive contains unsafe relative paths")
        }
        if (normalized.length > safetyPolicy.maxEntryPathLength) {
            throw UnsafeArchiveEntryException("Archive entry path is too long")
        }
        val segments = normalized.split('/').filter { it.isNotBlank() }
        if (segments.size > safetyPolicy.maxNestedDepth) {
            throw UnsafeArchiveEntryException("Archive nesting is too deep")
        }

        val safeUncompressedSize = uncompressedSize.coerceAtLeast(0L)
        if (safeUncompressedSize > 0L && compressedSize != null) {
            val safeCompressedSize = compressedSize.coerceAtLeast(0L)
            val ratio = if (safeCompressedSize == 0L) {
                Double.POSITIVE_INFINITY
            } else {
                safeUncompressedSize.toDouble() / safeCompressedSize.toDouble()
            }
            if (ratio > safetyPolicy.maxCompressionRatio) {
                throw UnsafeArchiveEntryException("Archive compression ratio is too high")
            }
        }

        uncompressedBytes = Math.addExact(uncompressedBytes, safeUncompressedSize)
        require(uncompressedBytes <= safetyPolicy.maxUncompressedBytes) { "Archive is too large to process safely" }
    }

    fun acceptForExtraction(rawName: String, uncompressedSize: Long, compressedSize: Long?): Boolean =
        try {
            accept(rawName, uncompressedSize, compressedSize)
            true
        } catch (_: UnsafeArchiveEntryException) {
            false
        }

    internal fun entryPath(name: String, size: Long, compressedSize: Long?, allowBackslashes: Boolean): List<String>? {
        if (!acceptForExtraction(name, size, compressedSize)) return null
        return archiveEntrySegments(name, allowBackslashes, safetyPolicy.maxNestedDepth)
    }
}

fun archiveEntrySegments(name: String, allowBackslashes: Boolean = true, maxDepth: Int = 64): List<String>? {
    if (!allowBackslashes && '\\' in name || '\u0000' in name) return null
    var normalized = name.replace('\\', '/').trimEnd('/')
    if (normalized.startsWith('/')) return null
    while (normalized.startsWith("./")) normalized = normalized.removePrefix("./")
    val parts = normalized.split('/')
    if (parts.size > maxDepth || ':' in parts.first() || parts.any { it.isBlank() || it == "." || it == ".." }) return null
    return parts
}

class ArchiveExpansionLimiter(
    private val safetyPolicy: ArchiveSafetyPolicy,
    compressedBytes: Long
) {
    private val ratioByteLimit = compressionRatioByteLimit(
        compressedBytes = compressedBytes.coerceAtLeast(0L),
        maxCompressionRatio = safetyPolicy.maxCompressionRatio
    )

    var expandedBytes: Long = 0L
        private set

    fun accept(byteCount: Int): Long {
        require(byteCount >= 0) { "Expanded byte count cannot be negative" }
        val nextSize = try {
            Math.addExact(expandedBytes, byteCount.toLong())
        } catch (_: ArithmeticException) {
            throw UnsafeArchiveEntryException("Archive is too large to process safely")
        }
        if (nextSize > safetyPolicy.maxUncompressedBytes) {
            throw UnsafeArchiveEntryException("Archive is too large to process safely")
        }
        if (nextSize > ratioByteLimit) {
            throw UnsafeArchiveEntryException("Archive compression ratio is too high")
        }
        expandedBytes = nextSize
        return expandedBytes
    }
}

private fun compressionRatioByteLimit(compressedBytes: Long, maxCompressionRatio: Double): Long {
    require(maxCompressionRatio >= 0.0 && !maxCompressionRatio.isNaN()) {
        "Archive compression ratio limit must be non-negative"
    }
    if (maxCompressionRatio.isInfinite()) return Long.MAX_VALUE
    val limit = compressedBytes.toDouble() * maxCompressionRatio
    return if (limit >= Long.MAX_VALUE.toDouble()) Long.MAX_VALUE else kotlin.math.floor(limit).toLong()
}
