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

interface ArchivePathResolver {
    suspend fun resolve(request: ArchivePathRequest): Result<String>
}
