package dev.qtremors.arcile.core.vault.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.qtremors.arcile.core.runtime.di.ApplicationScope
import dev.qtremors.arcile.core.vault.domain.VaultSecurityPreferences
import dev.qtremors.arcile.core.vault.domain.VaultSecuritySettings
import dev.qtremors.arcile.core.vault.domain.VaultSortDirection
import dev.qtremors.arcile.core.vault.domain.VaultSortField
import dev.qtremors.arcile.core.vault.domain.VaultViewOptions
import dev.qtremors.arcile.core.vault.domain.VaultFolderViewOptions
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.DirectoryId
import dev.qtremors.arcile.core.vault.domain.vaultFolderViewKey
import dev.qtremors.arcile.core.vault.domain.VAULT_MAX_WORKSPACE_TABS
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

val Context.vaultSecurityDataStore: DataStore<Preferences> by preferencesDataStore("onlyfiles_security")

@Singleton
class DefaultVaultSecurityPreferences @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope scope: CoroutineScope
) : VaultSecurityPreferences {
    private val store = context.vaultSecurityDataStore
    override val settings = store.data.map { preferences ->
        VaultSecuritySettings(
            screenshotProtectionEnabled = preferences[SCREENSHOT_PROTECTION] ?: true,
            viewOptions = VaultViewOptions(
                grid = preferences[GRID] ?: false,
                sortField = VaultSortField.entries.firstOrNull { it.name == preferences[SORT_FIELD] }
                    ?.takeUnless { it == VaultSortField.TYPE } ?: VaultSortField.NAME,
                sortDirection = VaultSortDirection.entries.firstOrNull { it.name == preferences[SORT_DIRECTION] }
                    ?: VaultSortDirection.ASCENDING,
                recursiveSearch = preferences[RECURSIVE_SEARCH] ?: VaultViewOptions().recursiveSearch,
                showThumbnails = preferences[SHOW_THUMBNAILS] ?: true,
                listZoom = preferences[LIST_ZOOM]?.takeIf(Float::isFinite) ?: 1f,
                gridMinCellSize = preferences[GRID_SIZE]?.takeIf(Float::isFinite) ?: 132f,
                showHiddenFiles = preferences[SHOW_HIDDEN_FILES] ?: true,
                foldersFirst = preferences[FOLDERS_FIRST] ?: true
            ),
            folderViewOptions = preferences.asMap().mapNotNull { (key, value) ->
                if (!key.name.startsWith(FOLDER_PREFIX) || value !is String) return@mapNotNull null
                decodeFolderViewOptions(value)?.let { key.name.removePrefix(FOLDER_PREFIX) to it }
            }.toMap(),
            lastDirectories = preferences.asMap().mapNotNull { (key, value) ->
                val vault = decodeVaultKey(key.name, LAST_DIRECTORY_PREFIX) ?: return@mapNotNull null
                val directory = (value as? String)?.let(::decodeDirectory) ?: return@mapNotNull null
                vault to directory
            }.toMap(),
            pinnedDirectories = preferences.asMap().mapNotNull { (key, value) ->
                val vault = decodeVaultKey(key.name, PINNED_DIRECTORIES_PREFIX) ?: return@mapNotNull null
                val ids = (value as? Set<*>)?.mapNotNull { (it as? String)?.let(::decodeDirectory) }
                    ?: return@mapNotNull null
                vault to ids.toSet()
            }.toMap(),
            workspaceTabsEnabled = preferences[WORKSPACE_TABS_ENABLED] ?: false,
            pinnedWorkspaceTabs = preferences.asMap().mapNotNull { (key, value) ->
                val vault = decodeVaultKey(key.name, PINNED_WORKSPACE_PREFIX) ?: return@mapNotNull null
                val ids = (value as? String)?.split('/')
                    ?.mapNotNull(::decodeDirectory)?.take(VAULT_MAX_WORKSPACE_TABS - 1)
                    ?: return@mapNotNull null
                vault to ids
            }.toMap(),
            pinnedVaults = preferences[PINNED_VAULTS].orEmpty().mapNotNullTo(mutableSetOf()) {
                runCatching { VaultId.of(it) }.getOrNull()
            }
        )
    }.stateIn(scope, SharingStarted.Eagerly, VaultSecuritySettings())

    override suspend fun setScreenshotProtectionEnabled(enabled: Boolean) {
        store.edit { it[SCREENSHOT_PROTECTION] = enabled }
    }

    override suspend fun setViewOptions(options: VaultViewOptions) {
        store.edit {
            it[GRID] = options.grid
            it[SORT_FIELD] = options.sortField.name
            it[SORT_DIRECTION] = options.sortDirection.name
            it[RECURSIVE_SEARCH] = options.recursiveSearch
            it[SHOW_THUMBNAILS] = options.showThumbnails
            it[LIST_ZOOM] = options.listZoom
            it[GRID_SIZE] = options.gridMinCellSize
            it[SHOW_HIDDEN_FILES] = options.showHiddenFiles
            it[FOLDERS_FIRST] = options.foldersFirst
        }
    }

    override suspend fun setFolderViewOptions(vaultId: VaultId, directoryId: DirectoryId, options: VaultFolderViewOptions) {
        val view = options.options
        val encoded = listOf(
            view.grid, view.sortField.name, view.sortDirection.name, view.showThumbnails,
            view.listZoom, view.gridMinCellSize, view.foldersFirst, options.applyToSubfolders
        ).joinToString(",")
        store.edit { it[stringPreferencesKey(FOLDER_PREFIX + vaultFolderViewKey(vaultId, directoryId))] = encoded }
    }

    override suspend fun setLastDirectory(vaultId: VaultId, directoryId: DirectoryId) {
        store.edit { it[stringPreferencesKey(LAST_DIRECTORY_PREFIX + vaultId.value)] = directoryId.value }
    }

    override suspend fun resetFolderViewOptions(vaultId: VaultId, directoryId: DirectoryId) {
        val key = stringPreferencesKey(FOLDER_PREFIX + vaultFolderViewKey(vaultId, directoryId))
        store.edit { it.remove(key) }
    }

    override suspend fun setDirectoryPinned(vaultId: VaultId, directoryId: DirectoryId, pinned: Boolean) {
        val key = stringSetPreferencesKey(PINNED_DIRECTORIES_PREFIX + vaultId.value)
        store.edit {
            val current = it[key].orEmpty()
            it[key] = if (pinned) current + directoryId.value else current - directoryId.value
        }
    }

    override suspend fun setWorkspaceTabsEnabled(enabled: Boolean) {
        store.edit { it[WORKSPACE_TABS_ENABLED] = enabled }
    }

    override suspend fun setVaultPinned(vaultId: VaultId, pinned: Boolean) {
        store.edit {
            val current = it[PINNED_VAULTS].orEmpty()
            val updated = if (pinned) current + vaultId.value else current - vaultId.value
            if (updated.isEmpty()) it.remove(PINNED_VAULTS) else it[PINNED_VAULTS] = updated
        }
    }

    override suspend fun resetVaultFolderViews(vaultId: VaultId) {
        store.edit { preferences ->
            val prefix = FOLDER_PREFIX + vaultId.value + "/"
            preferences.asMap().keys.filter { it.name.startsWith(prefix) }.forEach { preferences.remove(it) }
        }
    }

    override suspend fun removeDirectoryPreferences(vaultId: VaultId, deleted: Set<DirectoryId>) {
        if (deleted.isEmpty()) return
        val values = deleted.mapTo(mutableSetOf()) { it.value }
        store.edit { preferences ->
            deleted.forEach {
                preferences.remove(stringPreferencesKey(FOLDER_PREFIX + vaultFolderViewKey(vaultId, it)))
            }
            val last = stringPreferencesKey(LAST_DIRECTORY_PREFIX + vaultId.value)
            if (preferences[last] in values) preferences.remove(last)
            val pins = stringSetPreferencesKey(PINNED_DIRECTORIES_PREFIX + vaultId.value)
            val remainingPins = preferences[pins].orEmpty() - values
            if (remainingPins.isEmpty()) preferences.remove(pins) else preferences[pins] = remainingPins
            val workspace = stringPreferencesKey(PINNED_WORKSPACE_PREFIX + vaultId.value)
            val remainingTabs = preferences[workspace]?.split('/')?.filterNot { it in values }.orEmpty()
            if (remainingTabs.isEmpty()) preferences.remove(workspace) else preferences[workspace] = remainingTabs.joinToString("/")
        }
    }

    override suspend fun removeVaultPreferences(vaultId: VaultId) {
        store.edit { preferences ->
            val prefix = FOLDER_PREFIX + vaultId.value + "/"
            preferences.asMap().keys.filter { it.name.startsWith(prefix) }.forEach { preferences.remove(it) }
            preferences.remove(stringPreferencesKey(LAST_DIRECTORY_PREFIX + vaultId.value))
            preferences.remove(stringPreferencesKey(PINNED_WORKSPACE_PREFIX + vaultId.value))
            preferences.remove(stringSetPreferencesKey(PINNED_DIRECTORIES_PREFIX + vaultId.value))
            val pins = preferences[PINNED_VAULTS].orEmpty() - vaultId.value
            if (pins.isEmpty()) preferences.remove(PINNED_VAULTS) else preferences[PINNED_VAULTS] = pins
        }
    }

    override suspend fun setPinnedWorkspaceTabs(vaultId: VaultId, directories: List<DirectoryId>) {
        require(directories.size < VAULT_MAX_WORKSPACE_TABS)
        val key = stringPreferencesKey(PINNED_WORKSPACE_PREFIX + vaultId.value)
        store.edit {
            if (directories.isEmpty()) it.remove(key)
            else it[key] = directories.joinToString("/") { directory -> directory.value }
        }
    }

    private fun decodeVaultKey(name: String, prefix: String): VaultId? =
        name.takeIf { it.startsWith(prefix) }?.removePrefix(prefix)?.let { runCatching { VaultId.of(it) }.getOrNull() }

    private fun decodeDirectory(value: String): DirectoryId? = runCatching { DirectoryId.of(value) }.getOrNull()

    private fun decodeFolderViewOptions(encoded: String): VaultFolderViewOptions? {
        val fields = encoded.split(',')
        if (fields.size != 8) return null
        return VaultFolderViewOptions(
            options = VaultViewOptions(
                grid = fields[0].toBooleanStrictOrNull() ?: return null,
                sortField = VaultSortField.entries.firstOrNull { it.name == fields[1] && it != VaultSortField.TYPE }
                    ?: return null,
                sortDirection = VaultSortDirection.entries.firstOrNull { it.name == fields[2] } ?: return null,
                showThumbnails = fields[3].toBooleanStrictOrNull() ?: return null,
                listZoom = fields[4].toFloatOrNull()?.takeIf(Float::isFinite) ?: return null,
                gridMinCellSize = fields[5].toFloatOrNull()?.takeIf(Float::isFinite) ?: return null,
                foldersFirst = fields[6].toBooleanStrictOrNull() ?: return null
            ),
            applyToSubfolders = fields[7].toBooleanStrictOrNull() ?: return null
        )
    }

    private companion object {
        val SCREENSHOT_PROTECTION = booleanPreferencesKey("screenshot_protection_enabled")
        val GRID = booleanPreferencesKey("browse_grid")
        val SORT_FIELD = stringPreferencesKey("browse_sort_field")
        val SORT_DIRECTION = stringPreferencesKey("browse_sort_direction")
        val RECURSIVE_SEARCH = booleanPreferencesKey("browse_recursive_search")
        val SHOW_THUMBNAILS = booleanPreferencesKey("browse_show_thumbnails")
        val SHOW_HIDDEN_FILES = booleanPreferencesKey("browse_show_hidden_files")
        val FOLDERS_FIRST = booleanPreferencesKey("browse_folders_first")
        const val FOLDER_PREFIX = "browse_folder/"
        const val LAST_DIRECTORY_PREFIX = "browse_last_directory/"
        const val PINNED_DIRECTORIES_PREFIX = "browse_pinned_directories/"
        const val PINNED_WORKSPACE_PREFIX = "browse_pinned_workspace/"
        val WORKSPACE_TABS_ENABLED = booleanPreferencesKey("browse_workspace_enabled")
        val PINNED_VAULTS = stringSetPreferencesKey("browse_pinned_vaults")
        val LIST_ZOOM = floatPreferencesKey("browse_list_zoom")
        val GRID_SIZE = floatPreferencesKey("browse_grid_size")
    }
}
