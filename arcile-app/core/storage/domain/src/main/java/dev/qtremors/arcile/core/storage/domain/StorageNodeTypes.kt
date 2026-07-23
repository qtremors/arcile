package dev.qtremors.arcile.core.storage.domain

import java.io.File

@JvmInline
value class StorageVolumeId private constructor(val value: String) {
    companion object {
        fun of(value: String): StorageVolumeId {
            require(value.isNotBlank()) { "Storage volume id must not be blank" }
            return StorageVolumeId(value)
        }
    }
}

@JvmInline
value class StorageNodePath private constructor(val absolutePath: String) {
    companion object {
        fun of(path: String): StorageNodePath {
            require(path.isNotBlank()) { "Storage node path must not be blank" }
            require(path.indexOf('\u0000') < 0) { "Storage node path must not contain NUL" }
            val file = File(path)
            require(file.isAbsolute || path.startsWith("/") || path.startsWith("\\")) {
                "Storage node path must be absolute"
            }
            return StorageNodePath(if (path.startsWith("/") || path.startsWith("\\")) path else file.absolutePath)
        }
    }
}

@JvmInline
value class CanonicalStorageIdentity private constructor(val value: String) {
    companion object {
        fun of(value: String): CanonicalStorageIdentity {
            require(value.isNotBlank()) { "Canonical storage identity must not be blank" }
            return CanonicalStorageIdentity(value)
        }

        fun fromPath(path: StorageNodePath): CanonicalStorageIdentity {
            val canonical = runCatching { File(path.absolutePath).canonicalPath }
                .getOrDefault(File(path.absolutePath).absolutePath)
            return CanonicalStorageIdentity(canonical)
        }
    }
}

@JvmInline
value class TrashItemId private constructor(val value: String) {
    companion object {
        fun of(value: String): TrashItemId {
            require(value.isNotBlank()) { "Trash item id must not be blank" }
            require(value.indexOf('\u0000') < 0) { "Trash item id must not contain NUL" }
            return TrashItemId(value)
        }
    }
}

@JvmInline
value class CategoryId private constructor(val value: String) {
    companion object {
        fun of(value: String): CategoryId {
            require(value.isNotBlank()) { "Category id must not be blank" }
            return CategoryId(value.trim())
        }
    }
}

@JvmInline
value class ByteCount private constructor(val value: Long) {
    companion object {
        val Zero = ByteCount(0L)

        fun of(value: Long): ByteCount {
            require(value >= 0L) { "Byte count must not be negative" }
            return ByteCount(value)
        }
    }
}

@JvmInline
value class EpochMillis private constructor(val value: Long) {
    companion object {
        val Unknown = EpochMillis(0L)

        fun of(value: Long): EpochMillis {
            require(value >= 0L) { "Epoch milliseconds must not be negative" }
            return EpochMillis(value)
        }
    }
}

@Immutable
data class StorageNodeCapabilities(
    val canRead: Boolean = true,
    val canWrite: Boolean = true,
    val canDelete: Boolean = true,
    val canTrash: Boolean = false,
    val canArchive: Boolean = true,
    val canRename: Boolean = canWrite,
    val canCopy: Boolean = canRead,
    val canMove: Boolean = canWrite,
    val canExport: Boolean = canRead,
    val canShare: Boolean = canRead,
    val canOpenWith: Boolean = canRead
)

@Immutable
data class StorageNodeRef(
    val backendId: String = LOCAL_BACKEND_ID,
    val volumeId: StorageVolumeId? = null,
    val displayPath: StorageNodePath,
    val canonicalIdentity: CanonicalStorageIdentity = CanonicalStorageIdentity.fromPath(displayPath),
    val capabilities: StorageNodeCapabilities = StorageNodeCapabilities(),
    val contentUri: String? = null,
    val backendIdentity: String? = null
) {
    companion object {
        const val LOCAL_BACKEND_ID = "local"
        const val MEDIA_STORE_BACKEND_ID = "mediastore"
        const val ONLYFILES_BACKEND_ID = "onlyfiles"

        fun local(
            path: String,
            volumeId: String? = null,
            capabilities: StorageNodeCapabilities = StorageNodeCapabilities()
        ): StorageNodeRef {
            val absolutePath = File(path).let { file ->
                if (file.isAbsolute || path.startsWith("/") || path.startsWith("\\")) path else file.absoluteFile.absolutePath
            }
            val nodePath = StorageNodePath.of(absolutePath)
            return StorageNodeRef(
                volumeId = volumeId?.takeIf { it.isNotBlank() }?.let(StorageVolumeId::of),
                displayPath = nodePath,
                canonicalIdentity = CanonicalStorageIdentity.fromPath(nodePath),
                capabilities = capabilities
            )
        }

        fun mediaStore(
            id: Long,
            volumeName: String?,
            contentUri: String,
            displayPath: String,
            volumeId: String? = null,
            localPath: String? = null,
            capabilities: StorageNodeCapabilities = StorageNodeCapabilities(
                canRead = true,
                canWrite = false,
                canDelete = true,
                canTrash = false,
                canArchive = false
            )
        ): StorageNodeRef {
            val nodePath = StorageNodePath.of(displayPath)
            val identity = "mediastore:${volumeName.orEmpty()}:$id"
            return StorageNodeRef(
                backendId = MEDIA_STORE_BACKEND_ID,
                volumeId = volumeId?.takeIf { it.isNotBlank() }?.let(StorageVolumeId::of),
                displayPath = nodePath,
                canonicalIdentity = localPath
                    ?.takeIf { it.isNotBlank() }
                    ?.let { CanonicalStorageIdentity.fromPath(StorageNodePath.of(it)) }
                    ?: CanonicalStorageIdentity.of(identity),
                capabilities = capabilities,
                contentUri = contentUri,
                backendIdentity = identity
            )
        }

        /** Creates a path-free reference whose visible path contains opaque identifiers only. */
        fun vault(
            vaultId: String,
            nodeId: String,
            capabilities: StorageNodeCapabilities = StorageNodeCapabilities(
                canRead = true,
                canWrite = true,
                canDelete = true,
                canTrash = false,
                canArchive = false
            )
        ): StorageNodeRef {
            require(vaultId.isNotBlank() && nodeId.isNotBlank())
            require(vaultId.none { it == '/' || it == '\\' || it == '\u0000' })
            require(nodeId.none { it == '/' || it == '\\' || it == '\u0000' })
            val identity = "$vaultId:$nodeId"
            val opaquePath = StorageNodePath.of("/.onlyfiles/$vaultId/$nodeId")
            return StorageNodeRef(
                backendId = ONLYFILES_BACKEND_ID,
                displayPath = opaquePath,
                canonicalIdentity = CanonicalStorageIdentity.of("onlyfiles:$identity"),
                capabilities = capabilities,
                backendIdentity = identity
            )
        }
    }
}

fun String.toStorageNodePath(): StorageNodePath = StorageNodePath.of(this)
