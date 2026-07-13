package dev.qtremors.arcile.core.storage.domain

enum class ArchiveCollisionStyle {
    PARENTHESIZED,
    UNDERSCORE
}

data class ArchivePathRequest(
    val sourcePaths: List<String>,
    val parentPath: String? = null,
    val requestedName: String? = null,
    val format: ArchiveFormat = ArchiveFormat.ZIP,
    val collisionStyle: ArchiveCollisionStyle = ArchiveCollisionStyle.PARENTHESIZED
)

enum class ArchiveExtractionDestinationStyle {
    NAMED_FOLDER,
    SAME_FOLDER,
    CUSTOM_FOLDER
}

data class ArchiveExtractionPathRequest(
    val archivePath: String,
    val style: ArchiveExtractionDestinationStyle = ArchiveExtractionDestinationStyle.NAMED_FOLDER,
    val currentPath: String? = null,
    val customDestination: String? = null
)

interface ArchivePathResolver {
    suspend fun resolve(request: ArchivePathRequest): Result<String>
    suspend fun resolveExtraction(request: ArchiveExtractionPathRequest): Result<String>
}
