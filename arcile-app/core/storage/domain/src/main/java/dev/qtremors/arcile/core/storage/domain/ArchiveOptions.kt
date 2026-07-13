package dev.qtremors.arcile.core.storage.domain

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
