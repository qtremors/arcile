package dev.qtremors.arcile.core.storage.domain

data class SaveDestinationDirectory(
    val path: String,
    val name: String,
    val canSave: Boolean
)

interface SaveDestinationBrowser {
    suspend fun resolve(
        path: String?,
        volumes: List<StorageVolume>
    ): Result<SaveDestinationDirectory?>

    suspend fun children(
        path: String,
        volumes: List<StorageVolume>
    ): Result<List<SaveDestinationDirectory>>

    suspend fun parent(
        path: String,
        volumes: List<StorageVolume>
    ): Result<SaveDestinationDirectory?>
}
