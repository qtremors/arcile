package dev.qtremors.arcile.core.vault.domain

enum class VaultNodeKind { FILE, DIRECTORY }

data class VaultNodeMetadata(
    val ref: VaultNodeRef,
    val name: String,
    val kind: VaultNodeKind,
    val sizeBytes: Long,
    val modifiedAtMillis: Long,
    val revision: Long,
    val mimeType: String? = null
) {
    init {
        VaultName.of(name)
        require(sizeBytes >= 0L)
        require(modifiedAtMillis >= 0L)
        require(revision >= 0L)
        require(kind != VaultNodeKind.DIRECTORY || sizeBytes == 0L)
    }

    val isDirectory: Boolean get() = kind == VaultNodeKind.DIRECTORY
    val extension: String
        get() = if (isDirectory || '.' !in name) "" else name.substringAfterLast('.').lowercase()
}

/** Legacy UI projection while OnlyFiles migrates fully to opaque references. */
data class VaultNode(
    val id: String,
    val path: VaultPath,
    val sizeBytes: Long,
    val modifiedAtMillis: Long,
    val isDirectory: Boolean,
    val mimeType: String? = null
) {
    val name: String get() = path.name
    val extension: String
        get() = if (isDirectory || '.' !in name) "" else name.substringAfterLast('.').lowercase()
}

enum class VaultSortField { NAME, MODIFIED, SIZE, TYPE }
enum class VaultSortDirection { ASCENDING, DESCENDING }

data class VaultListOptions(
    val sortField: VaultSortField = VaultSortField.NAME,
    val direction: VaultSortDirection = VaultSortDirection.ASCENDING,
    val pageSize: Int = 256,
    val pageToken: String? = null
) {
    init {
        require(pageSize in 1..256)
    }
}

data class VaultPage<T>(
    val items: List<T>,
    val nextPageToken: String?,
    val generation: Long
)

data class VaultSearchQuery(
    val text: String,
    val recursive: Boolean,
    val pageSize: Int = 256,
    val pageToken: String? = null
) {
    init {
        require(text.indexOf('\u0000') < 0)
        require(pageSize in 1..256)
    }
}
