package dev.qtremors.arcile.core.storage.domain


enum class ArchiveNameEncoding(val charsetName: String, val displayName: String) {
    UTF_8("UTF-8", "UTF-8"),
    CP437("Cp437", "CP437"),
    WINDOWS_1252("windows-1252", "Windows-1252"),
    SHIFT_JIS("Shift_JIS", "Shift JIS"),
    GBK("GBK", "GBK"),
    BIG5("Big5", "Big5")
}

enum class ArchiveFormat(
    val extension: String,
    val displayName: String,
    val canCreate: Boolean = true,
    val canBrowse: Boolean = true,
    val supportsPassword: Boolean = false
) {
    ZIP("zip", "ZIP", supportsPassword = true),
    SEVEN_Z("7z", "7z", supportsPassword = true),
    TAR("tar", "TAR"),
    TAR_GZIP("tar.gz", "TAR.GZ"),
    TGZ("tgz", "TGZ"),
    TAR_BZIP2("tar.bz2", "TAR.BZ2"),
    TBZ2("tbz2", "TBZ2"),
    TAR_XZ("tar.xz", "TAR.XZ"),
    TXZ("txz", "TXZ"),
    GZIP("gz", "GZIP", canCreate = false),
    BZIP2("bz2", "BZIP2", canCreate = false),
    XZ("xz", "XZ", canCreate = false),
    RAR("rar", "RAR", canCreate = false, canBrowse = false);

    companion object {
        fun fromPath(path: String): ArchiveFormat? {
            val name = path.substringAfterLast('/').substringAfterLast('\\').lowercase()
            return entries
                .filter { name.endsWith(".${it.extension}") }
                .maxByOrNull { it.extension.length }
        }

        fun isSupported(path: String): Boolean = fromPath(path)?.canBrowse == true

        fun creatableFormats(): List<ArchiveFormat> = entries.filter { it.canCreate }
    }
}

enum class ArchiveCompressionLevel(val displayName: String) {
    STORE("No compression"),
    FAST("Fast compression"),
    DEFAULT("Balanced"),
    MAXIMUM("Maximum compression")
}

@Immutable
data class ArchiveEntryModel(
    val name: String,
    val path: String,
    val size: Long,
    val compressedSize: Long?,
    val lastModified: Long?,
    val isDirectory: Boolean,
    val canRead: Boolean = true
)

@Immutable
data class ArchiveSummary(
    val archivePath: String,
    val format: ArchiveFormat,
    val archiveSize: Long,
    val totalUncompressedSize: Long,
    val fileCount: Int,
    val folderCount: Int,
    val newestModifiedAt: Long?,
    val oldestModifiedAt: Long?,
    val hasUnreadableEntries: Boolean
) {
    val entryCount: Int get() = fileCount + folderCount
    val compressionRatio: Double?
        get() = totalUncompressedSize.takeIf { it > 0L }?.let { archiveSize.toDouble() / it.toDouble() }
}

enum class ArchiveExtractionDestination {
    HERE,
    NAMED_FOLDER
}

@Immutable
data class ArchiveCreateOptions(
    val password: String? = null,
    val nameEncoding: ArchiveNameEncoding = ArchiveNameEncoding.UTF_8
) {
    val hasPassword: Boolean get() = !password.isNullOrEmpty()
}

@Immutable
data class ArchiveExtractOptions(
    val password: String? = null,
    val nameEncoding: ArchiveNameEncoding = ArchiveNameEncoding.UTF_8
) {
    val hasPassword: Boolean get() = !password.isNullOrEmpty()
}

interface ArchiveManager {
    suspend fun listArchiveEntries(archivePath: String): Result<List<ArchiveEntryModel>>
    suspend fun listArchiveEntries(
        archivePath: String,
        password: String?,
        nameEncoding: ArchiveNameEncoding = ArchiveNameEncoding.UTF_8
    ): Result<List<ArchiveEntryModel>> =
        listArchiveEntries(archivePath)
    suspend fun getArchiveMetadata(archivePath: String): Result<ArchiveSummary>
    suspend fun getArchiveMetadata(
        archivePath: String,
        password: String?,
        nameEncoding: ArchiveNameEncoding = ArchiveNameEncoding.UTF_8
    ): Result<ArchiveSummary> =
        getArchiveMetadata(archivePath)
    suspend fun extractArchive(
        archivePath: String,
        destinationPath: String,
        entryPrefix: String? = null,
        password: String? = null,
        nameEncoding: ArchiveNameEncoding = ArchiveNameEncoding.UTF_8,
        resolutions: Map<String, ConflictResolution> = emptyMap(),
        onProgress: ((FileOperationProgress) -> Unit)? = null
    ): Result<Unit>
    suspend fun detectArchiveConflicts(
        archivePath: String,
        destinationPath: String,
        entryPrefix: String? = null,
        password: String? = null,
        nameEncoding: ArchiveNameEncoding = ArchiveNameEncoding.UTF_8
    ): Result<List<FileConflict>> = Result.success(emptyList())
    suspend fun createArchive(
        sourcePaths: List<String>,
        destinationArchivePath: String,
        format: ArchiveFormat,
        password: String? = null,
        nameEncoding: ArchiveNameEncoding = ArchiveNameEncoding.UTF_8,
        compressionLevel: ArchiveCompressionLevel = ArchiveCompressionLevel.STORE,
        onProgress: ((FileOperationProgress) -> Unit)? = null
    ): Result<Unit>
}
