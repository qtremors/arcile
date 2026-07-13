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
        fun creatableFormats(): List<ArchiveFormat> = entries.filter(ArchiveFormat::canCreate)
    }
}

enum class ArchiveCompressionLevel(val displayName: String) {
    STORE("No compression"),
    FAST("Fast compression"),
    DEFAULT("Balanced"),
    MAXIMUM("Maximum compression")
}
