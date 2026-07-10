package dev.qtremors.arcile.feature.imagegallery

import dev.qtremors.arcile.core.operation.BulkFileOperationType

internal val galleryTrackedOperationTypes = setOf(
    BulkFileOperationType.MOVE,
    BulkFileOperationType.COPY,
    BulkFileOperationType.CREATE_ARCHIVE
)

internal val galleryClipboardOperationTypes = setOf(
    BulkFileOperationType.MOVE,
    BulkFileOperationType.COPY
)
