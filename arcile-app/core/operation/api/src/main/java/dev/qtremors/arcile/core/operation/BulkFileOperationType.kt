package dev.qtremors.arcile.core.operation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class BulkFileOperationType {
    COPY,
    MOVE,
    TRASH,
    DELETE,
    SHRED,
    @SerialName("CREATE_FAKE")
    CREATE_SYNTHETIC,
    EXTRACT_ARCHIVE,
    CREATE_ARCHIVE,
    SAVE_TO_ARCILE_IMPORT
}
