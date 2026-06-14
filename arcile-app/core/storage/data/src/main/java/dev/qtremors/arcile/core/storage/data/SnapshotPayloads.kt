package dev.qtremors.arcile.core.storage.data

import dev.qtremors.arcile.core.storage.domain.CleanerCandidate
import dev.qtremors.arcile.core.storage.domain.CleanerGroup
import dev.qtremors.arcile.core.storage.domain.CleanerGroupType
import dev.qtremors.arcile.core.storage.domain.CleanerRiskLevel
import dev.qtremors.arcile.core.storage.domain.CleanerRiskReason
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.StorageCleanerResult
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import dev.qtremors.arcile.core.storage.domain.StorageUsageNode
import dev.qtremors.arcile.core.storage.domain.StorageUsageNodeKind
import dev.qtremors.arcile.core.storage.domain.StorageUsageScanStatus
import kotlinx.serialization.Serializable

@Serializable
internal data class CachedFileModel(
    val name: String,
    val absolutePath: String,
    val size: Long,
    val lastModified: Long,
    val isDirectory: Boolean,
    val extension: String,
    val isHidden: Boolean,
    val mimeType: String?,
    val backendId: String,
    val volumeId: String?,
    val contentUri: String?,
    val backendIdentity: String?
) {
    fun toDomain(): FileModel {
        val mediaIdentity = backendIdentity
            ?.takeIf { backendId == StorageNodeRef.MEDIA_STORE_BACKEND_ID }
            ?.split(':')
        val ref = if (contentUri != null && mediaIdentity?.size == 3) {
            StorageNodeRef.mediaStore(
                id = mediaIdentity[2].toLongOrNull() ?: 0L,
                volumeName = mediaIdentity[1].ifBlank { null },
                contentUri = contentUri,
                displayPath = absolutePath,
                volumeId = volumeId,
                localPath = absolutePath
            )
        } else {
            StorageNodeRef.local(path = absolutePath, volumeId = volumeId)
        }
        return FileModel(
            name = name,
            absolutePath = absolutePath,
            size = size,
            lastModified = lastModified,
            isDirectory = isDirectory,
            extension = extension,
            isHidden = isHidden,
            mimeType = mimeType,
            nodeRef = ref
        )
    }

    companion object {
        fun from(file: FileModel): CachedFileModel =
            CachedFileModel(
                name = file.name,
                absolutePath = file.absolutePath,
                size = file.size,
                lastModified = file.lastModified,
                isDirectory = file.isDirectory,
                extension = file.extension,
                isHidden = file.isHidden,
                mimeType = file.mimeType,
                backendId = file.nodeRef.backendId,
                volumeId = file.nodeRef.volumeId?.value,
                contentUri = file.nodeRef.contentUri,
                backendIdentity = file.nodeRef.backendIdentity
            )
    }
}

@Serializable
internal data class CachedStorageUsageNode(
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val kind: String,
    val childCount: Int,
    val status: String,
    val children: List<CachedStorageUsageNode>
) {
    fun toDomain(): StorageUsageNode =
        StorageUsageNode(
            name = name,
            path = path,
            sizeBytes = sizeBytes,
            kind = StorageUsageNodeKind.valueOf(kind),
            childCount = childCount,
            status = StorageUsageScanStatus.valueOf(status),
            children = children.map { it.toDomain() }
        )

    companion object {
        fun from(node: StorageUsageNode): CachedStorageUsageNode =
            CachedStorageUsageNode(
                name = node.name,
                path = node.path,
                sizeBytes = node.sizeBytes,
                kind = node.kind.name,
                childCount = node.childCount,
                status = node.status.name,
                children = node.children.map { from(it) }
            )
    }
}

@Serializable
internal data class CachedCleanerCandidate(
    val name: String,
    val absolutePath: String,
    val size: Long,
    val lastModified: Long,
    val groupTypes: Set<String>,
    val riskLevel: String,
    val riskReasons: Set<String>,
    val isDirectory: Boolean,
    val duplicateGroupKey: String?
) {
    fun toDomain(): CleanerCandidate =
        CleanerCandidate(
            name = name,
            absolutePath = absolutePath,
            size = size,
            lastModified = lastModified,
            groupTypes = groupTypes.mapTo(linkedSetOf()) { CleanerGroupType.valueOf(it) },
            riskLevel = CleanerRiskLevel.valueOf(riskLevel),
            riskReasons = riskReasons.mapTo(linkedSetOf()) { CleanerRiskReason.valueOf(it) },
            isDirectory = isDirectory,
            duplicateGroupKey = duplicateGroupKey
        )

    companion object {
        fun from(candidate: CleanerCandidate): CachedCleanerCandidate =
            CachedCleanerCandidate(
                name = candidate.name,
                absolutePath = candidate.absolutePath,
                size = candidate.size,
                lastModified = candidate.lastModified,
                groupTypes = candidate.groupTypes.mapTo(linkedSetOf()) { it.name },
                riskLevel = candidate.riskLevel.name,
                riskReasons = candidate.riskReasons.mapTo(linkedSetOf()) { it.name },
                isDirectory = candidate.isDirectory,
                duplicateGroupKey = candidate.duplicateGroupKey
            )
    }
}

@Serializable
internal data class CachedCleanerGroup(
    val type: String,
    val candidates: List<CachedCleanerCandidate>
) {
    fun toDomain(): CleanerGroup =
        CleanerGroup(CleanerGroupType.valueOf(type), candidates.map { it.toDomain() })

    companion object {
        fun from(group: CleanerGroup): CachedCleanerGroup =
            CachedCleanerGroup(group.type.name, group.candidates.map { CachedCleanerCandidate.from(it) })
    }
}

@Serializable
internal data class CachedCleanerResult(
    val groups: List<CachedCleanerGroup>,
    val scannedFiles: Int,
    val isPartial: Boolean
) {
    fun toDomain(): StorageCleanerResult =
        StorageCleanerResult(groups.map { it.toDomain() }, scannedFiles, isPartial)

    companion object {
        fun from(result: StorageCleanerResult): CachedCleanerResult =
            CachedCleanerResult(
                groups = result.groups.map { CachedCleanerGroup.from(it) },
                scannedFiles = result.scannedFiles,
                isPartial = result.isPartial
            )
    }
}

