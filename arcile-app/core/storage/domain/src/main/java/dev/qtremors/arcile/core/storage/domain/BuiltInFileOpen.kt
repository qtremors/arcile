package dev.qtremors.arcile.core.storage.domain

enum class BuiltInFileOpen {
    PACKAGE, ARCHIVE, IMAGE, VIDEO, AUDIO, PDF, TEXT, EXTERNAL;

    companion object {
        fun resolve(
            name: String,
            extension: String,
            mimeTypeHint: String? = null,
            canBrowse: (ArchiveFormat) -> Boolean = ArchiveFormat::canBrowse
        ): BuiltInFileOpen {
            val ext = extension.lowercase()
            if (ext in FileCategories.APKs.extensions) return PACKAGE
            val archive = ArchiveFormat.fromPath(name)
            if (archive != null) return if (canBrowse(archive)) ARCHIVE else EXTERNAL
            return when {
                ext in FileCategories.Images.extensions -> IMAGE
                ext in FileCategories.Videos.extensions -> VIDEO
                ext in FileCategories.Audio.extensions -> AUDIO
                ext == "pdf" -> PDF
                ext in ArcileOpenExtensions.text -> TEXT
                mimeTypeHint.equals("application/pdf", ignoreCase = true) -> PDF
                mimeTypeHint?.startsWith("text/", ignoreCase = true) == true -> TEXT
                mimeTypeHint == null -> EXTERNAL
                else -> when (FileCategories.getCategoryForFile(ext, mimeTypeHint)) {
                    FileCategories.Images -> IMAGE
                    FileCategories.Videos -> VIDEO
                    FileCategories.Audio -> AUDIO
                    else -> EXTERNAL
                }
            }
        }
    }
}
