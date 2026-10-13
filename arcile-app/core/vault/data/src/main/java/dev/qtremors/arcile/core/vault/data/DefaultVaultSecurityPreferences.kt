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
            }.toMap()
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
        val LIST_ZOOM = floatPreferencesKey("browse_list_zoom")
        val GRID_SIZE = floatPreferencesKey("browse_grid_size")
    }
}
