package dev.qtremors.arcile.feature.browser.delegate

import androidx.lifecycle.SavedStateHandle
import dev.qtremors.arcile.core.storage.domain.StorageBrowserLocation
import dev.qtremors.arcile.core.storage.domain.StorageNodePath
import dev.qtremors.arcile.core.storage.domain.CanonicalStorageIdentity
import dev.qtremors.arcile.core.storage.domain.StorageNodeCapabilities
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import dev.qtremors.arcile.core.storage.domain.StorageVolumeId
import dev.qtremors.arcile.core.storage.domain.StorageScope
import dev.qtremors.arcile.feature.browser.BrowserNavigationState
import java.util.ArrayDeque
import java.util.Base64

internal class BrowserNavigationPersistence(
    private val savedStateHandle: SavedStateHandle
) {
    private val history = ArrayDeque<BrowserHistoryEntry>()

    fun restoreLocation(): StorageBrowserLocation? {
        savedStateHandle.get<Array<String>>("pathHistory")?.let { saved ->
            history.clear()
            history.addAll(saved.mapNotNull(BrowserHistoryEntry::fromSavedValue))
        }
        val archivePath = savedStateHandle.get<String>("archivePath")
        if (!archivePath.isNullOrEmpty()) {
            return StorageBrowserLocation.Archive(
                archivePath = archivePath,
                entryPrefix = savedStateHandle.get<String>("archiveEntryPrefix")
                    ?.takeIf(String::isNotEmpty)
            )
        }
        val volumeId = savedStateHandle.get<String>("currentVolumeId")
        return when {
            savedStateHandle.get<Boolean>("isVolumeRootScreen") == true ->
                StorageBrowserLocation.Roots
            savedStateHandle.get<Boolean>("isCategoryScreen") == true -> {
                val category = savedStateHandle.get<String>("activeCategoryName")
                    ?.takeIf(String::isNotEmpty)
                    ?: return null
                StorageBrowserLocation.Category(StorageScope.Category(volumeId, category))
            }
            else -> {
                val path = savedStateHandle.get<String>("currentPath")
                    ?.takeIf(String::isNotEmpty)
                    ?: return null
                val restoredVolumeId = volumeId?.takeIf(String::isNotEmpty)
                if (restoredVolumeId != null) {
                    StorageBrowserLocation.Directory(StorageScope.Path(restoredVolumeId, path))
                } else {
                    StorageBrowserLocation.DirectDirectory(StorageNodePath.of(path))
                }
            }
        }
    }

    fun save(state: BrowserNavigationState) {
        savedStateHandle["currentPath"] = state.currentPath
        savedStateHandle["currentVolumeId"] = state.currentVolumeId
        savedStateHandle["isVolumeRootScreen"] = state.isVolumeRootScreen
        savedStateHandle["isCategoryScreen"] = state.isCategoryScreen
        savedStateHandle["activeCategoryName"] = state.activeCategoryName
        savedStateHandle["pathHistory"] = history.map(BrowserHistoryEntry::toSavedValue).toTypedArray()
        savedStateHandle["archivePath"] = state.archiveContext?.archivePath
        savedStateHandle["archiveEntryPrefix"] = state.archiveContext?.entryPrefix
        val archiveRef = state.archiveContext?.archiveNodeRef
        savedStateHandle["archiveBackendId"] = archiveRef?.backendId
        savedStateHandle["archiveCanonicalIdentity"] = archiveRef?.canonicalIdentity?.value
        savedStateHandle["archiveBackendIdentity"] = archiveRef?.backendIdentity
        savedStateHandle["archiveNodeVolumeId"] = archiveRef?.volumeId?.value
        val ref = state.currentNodeRef
        savedStateHandle["currentBackendId"] = ref?.backendId
        savedStateHandle["currentCanonicalIdentity"] = ref?.canonicalIdentity?.value
        savedStateHandle["currentBackendIdentity"] = ref?.backendIdentity
        savedStateHandle["currentNodeVolumeId"] = ref?.volumeId?.value
    }

    fun restoredCurrentNodeRef(path: String): StorageNodeRef? {
        val backendId = savedStateHandle.get<String>("currentBackendId")
            ?.takeIf(String::isNotBlank)
            ?: return null
        val canonical = savedStateHandle.get<String>("currentCanonicalIdentity")
            ?.takeIf(String::isNotBlank)
            ?: return null
        return runCatching {
            StorageNodeRef(
                backendId = backendId,
                volumeId = savedStateHandle.get<String>("currentNodeVolumeId")
                    ?.takeIf(String::isNotBlank)
                    ?.let(StorageVolumeId::of),
                displayPath = StorageNodePath.of(path),
                canonicalIdentity = CanonicalStorageIdentity.of(canonical),
                capabilities = StorageNodeCapabilities(),
                backendIdentity = savedStateHandle.get<String>("currentBackendIdentity")
            )
        }.getOrNull()
    }

    fun restoredArchiveNodeRef(path: String): StorageNodeRef? = restoredNodeRef(
        path = path,
        backendKey = "archiveBackendId",
        canonicalKey = "archiveCanonicalIdentity",
        backendIdentityKey = "archiveBackendIdentity",
        volumeKey = "archiveNodeVolumeId"
    )

    private fun restoredNodeRef(
        path: String,
        backendKey: String,
        canonicalKey: String,
        backendIdentityKey: String,
        volumeKey: String
    ): StorageNodeRef? {
        val backendId = savedStateHandle.get<String>(backendKey)
            ?.takeIf(String::isNotBlank)
            ?: return null
        val canonical = savedStateHandle.get<String>(canonicalKey)
            ?.takeIf(String::isNotBlank)
            ?: return null
        return runCatching {
            StorageNodeRef(
                backendId = backendId,
                volumeId = savedStateHandle.get<String>(volumeKey)
                    ?.takeIf(String::isNotBlank)
                    ?.let(StorageVolumeId::of),
                displayPath = StorageNodePath.of(path),
                canonicalIdentity = CanonicalStorageIdentity.of(canonical),
                capabilities = StorageNodeCapabilities(),
                backendIdentity = savedStateHandle.get<String>(backendIdentityKey)
            )
        }.getOrNull()
    }

    fun clear() = history.clear()
    fun push(entry: BrowserHistoryEntry) = history.push(entry)
    fun pop(): BrowserHistoryEntry = history.pop()
    fun isNotEmpty(): Boolean = history.isNotEmpty()
}

internal sealed interface BrowserHistoryEntry {
    data class Directory(
        val path: String,
        val nodeRef: StorageNodeRef? = null
    ) : BrowserHistoryEntry
    data class Archive(
        val archivePath: String,
        val entryPrefix: String?,
        val archiveNodeRef: StorageNodeRef? = null
    ) : BrowserHistoryEntry

    fun toSavedValue(): String = when (this) {
        is Directory -> nodeRef?.let { ref ->
            listOf(
                "dir2",
                ref.backendId.encodeHistoryPart(),
                path.encodeHistoryPart(),
                ref.canonicalIdentity.value.encodeHistoryPart(),
                ref.backendIdentity.orEmpty().encodeHistoryPart(),
                ref.volumeId?.value.orEmpty().encodeHistoryPart()
            ).joinToString(":")
        } ?: "dir:$path"
        is Archive -> archiveNodeRef?.let { ref ->
            listOf(
                "archive2",
                ref.backendId.encodeHistoryPart(),
                archivePath.encodeHistoryPart(),
                entryPrefix.orEmpty().encodeHistoryPart(),
                ref.canonicalIdentity.value.encodeHistoryPart(),
                ref.backendIdentity.orEmpty().encodeHistoryPart(),
                ref.volumeId?.value.orEmpty().encodeHistoryPart()
            ).joinToString(":")
        } ?: "archive:$archivePath|${entryPrefix.orEmpty()}"
    }

    companion object {
        fun fromSavedValue(value: String): BrowserHistoryEntry? = when {
            value.startsWith("dir2:") -> decodeDirectory(value)
            value.startsWith("dir:") -> Directory(value.removePrefix("dir:"))
            value.startsWith("archive2:") -> decodeArchive(value)
            value.startsWith("archive:") -> {
                val payload = value.removePrefix("archive:")
                val archivePath = payload.substringBefore('|').takeIf(String::isNotBlank) ?: return null
                Archive(archivePath, payload.substringAfter('|', "").takeIf(String::isNotBlank))
            }
            value.startsWith(BrowserNavigationController.ARCHIVE_VIRTUAL_PREFIX) -> null
            else -> Directory(value)
        }

        private fun decodeDirectory(value: String): Directory? {
            val parts = value.split(':')
            if (parts.size != 6) return null
            return runCatching {
                val backendId = parts[1].decodeHistoryPart()
                val path = parts[2].decodeHistoryPart()
                val canonical = parts[3].decodeHistoryPart()
                val backendIdentity = parts[4].decodeHistoryPart().takeIf(String::isNotBlank)
                val volumeId = parts[5].decodeHistoryPart().takeIf(String::isNotBlank)
                Directory(
                    path = path,
                    nodeRef = StorageNodeRef(
                        backendId = backendId,
                        volumeId = volumeId?.let(StorageVolumeId::of),
                        displayPath = StorageNodePath.of(path),
                        canonicalIdentity = CanonicalStorageIdentity.of(canonical),
                        capabilities = StorageNodeCapabilities(),
                        backendIdentity = backendIdentity
                    )
                )
            }.getOrNull()
        }

        private fun decodeArchive(value: String): Archive? {
            val parts = value.split(':')
            if (parts.size != 7) return null
            return runCatching {
                val backendId = parts[1].decodeHistoryPart()
                val archivePath = parts[2].decodeHistoryPart()
                val entryPrefix = parts[3].decodeHistoryPart().takeIf(String::isNotBlank)
                val canonical = parts[4].decodeHistoryPart()
                val backendIdentity = parts[5].decodeHistoryPart().takeIf(String::isNotBlank)
                val volumeId = parts[6].decodeHistoryPart().takeIf(String::isNotBlank)
                Archive(
                    archivePath = archivePath,
                    entryPrefix = entryPrefix,
                    archiveNodeRef = StorageNodeRef(
                        backendId = backendId,
                        volumeId = volumeId?.let(StorageVolumeId::of),
                        displayPath = StorageNodePath.of(archivePath),
                        canonicalIdentity = CanonicalStorageIdentity.of(canonical),
                        capabilities = StorageNodeCapabilities(),
                        backendIdentity = backendIdentity
                    )
                )
            }.getOrNull()
        }
    }
}

internal fun BrowserNavigationState.historyEntry(): BrowserHistoryEntry? =
    archiveContext?.let { BrowserHistoryEntry.Archive(it.archivePath, it.entryPrefix, it.archiveNodeRef) }
        ?: currentPath
            .takeIf {
                it.isNotBlank() &&
                    !it.startsWith(BrowserNavigationController.ARCHIVE_VIRTUAL_PREFIX)
            }
            ?.let { BrowserHistoryEntry.Directory(it, currentNodeRef) }

private fun String.encodeHistoryPart(): String =
    Base64.getUrlEncoder().withoutPadding().encodeToString(toByteArray(Charsets.UTF_8))

private fun String.decodeHistoryPart(): String =
    String(Base64.getUrlDecoder().decode(this), Charsets.UTF_8)
