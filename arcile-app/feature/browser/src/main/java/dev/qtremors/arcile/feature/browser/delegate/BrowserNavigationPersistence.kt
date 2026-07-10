package dev.qtremors.arcile.feature.browser.delegate

import androidx.lifecycle.SavedStateHandle
import dev.qtremors.arcile.core.storage.domain.StorageBrowserLocation
import dev.qtremors.arcile.core.storage.domain.StorageScope
import dev.qtremors.arcile.feature.browser.BrowserNavigationState
import java.util.ArrayDeque

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
                val restoredVolumeId = volumeId?.takeIf(String::isNotEmpty) ?: return null
                StorageBrowserLocation.Directory(StorageScope.Path(restoredVolumeId, path))
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
    }

    fun clear() = history.clear()
    fun push(entry: BrowserHistoryEntry) = history.push(entry)
    fun pop(): BrowserHistoryEntry = history.pop()
    fun isNotEmpty(): Boolean = history.isNotEmpty()
}

internal sealed interface BrowserHistoryEntry {
    data class Directory(val path: String) : BrowserHistoryEntry
    data class Archive(val archivePath: String, val entryPrefix: String?) : BrowserHistoryEntry

    fun toSavedValue(): String = when (this) {
        is Directory -> "dir:$path"
        is Archive -> "archive:$archivePath|${entryPrefix.orEmpty()}"
    }

    companion object {
        fun fromSavedValue(value: String): BrowserHistoryEntry? = when {
            value.startsWith("dir:") -> Directory(value.removePrefix("dir:"))
            value.startsWith("archive:") -> {
                val payload = value.removePrefix("archive:")
                val archivePath = payload.substringBefore('|').takeIf(String::isNotBlank) ?: return null
                Archive(archivePath, payload.substringAfter('|', "").takeIf(String::isNotBlank))
            }
            value.startsWith(BrowserNavigationController.ARCHIVE_VIRTUAL_PREFIX) -> null
            else -> Directory(value)
        }
    }
}

internal fun BrowserNavigationState.historyEntry(): BrowserHistoryEntry? =
    archiveContext?.let { BrowserHistoryEntry.Archive(it.archivePath, it.entryPrefix) }
        ?: currentPath
            .takeIf {
                it.isNotBlank() &&
                    !it.startsWith(BrowserNavigationController.ARCHIVE_VIRTUAL_PREFIX)
            }
            ?.let(BrowserHistoryEntry::Directory)
