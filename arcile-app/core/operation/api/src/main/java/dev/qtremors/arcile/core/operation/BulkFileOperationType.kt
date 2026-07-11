package dev.qtremors.arcile.core.operation

import kotlinx.serialization.Serializable

@Serializable
enum class BulkFileOperationType {
    COPY,
    MOVE,
    TRASH,
    DELETE,
    SHRED,
    CREATE_FAKE,
    EXTRACT_ARCHIVE,
    CREATE_ARCHIVE,
    SAVE_TO_ARCILE_IMPORT
}
