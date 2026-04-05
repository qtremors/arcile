package dev.qtremors.arcile.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.qtremors.arcile.domain.BrowserPreferences
import dev.qtremors.arcile.domain.BrowserPresentationPreferences
import dev.qtremors.arcile.domain.BrowserViewMode
import dev.qtremors.arcile.presentation.FileSortOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.browserDataStore by preferencesDataStore(name = "browser_prefs")

interface BrowserPreferencesStore {
    val preferencesFlow: Flow<BrowserPreferences>

    suspend fun updateGlobalPresentation(presentation: BrowserPresentationPreferences)

    suspend fun updatePathPresentation(
        path: String,
        presentation: BrowserPresentationPreferences?,
        applyToSubfolders: Boolean = false
    )
}

class BrowserPreferencesRepository(private val context: Context) : BrowserPreferencesStore {
    private val GLOBAL_SORT_KEY = stringPreferencesKey("global_sort_option")
    private val GLOBAL_VIEW_MODE_KEY = stringPreferencesKey("global_view_mode")
    private val GLOBAL_LIST_ZOOM_KEY = floatPreferencesKey("global_list_zoom")
    private val GLOBAL_GRID_MIN_CELL_SIZE_KEY = floatPreferencesKey("global_grid_min_cell_size")

    override val preferencesFlow: Flow<BrowserPreferences> = context.browserDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            val globalPresentation = BrowserPresentationPreferences(
                sortOption = parseSortOption(
                    prefs[GLOBAL_SORT_KEY],
                    BrowserPresentationPreferences.DEFAULT_SORT_OPTION
                ),
                viewMode = parseViewMode(prefs[GLOBAL_VIEW_MODE_KEY]),
                listZoom = prefs[GLOBAL_LIST_ZOOM_KEY] ?: BrowserPresentationPreferences.DEFAULT_LIST_ZOOM,
                gridMinCellSize = prefs[GLOBAL_GRID_MIN_CELL_SIZE_KEY]
                    ?: BrowserPresentationPreferences.DEFAULT_GRID_MIN_CELL_SIZE
            ).normalized()

            val pathMap = mutableMapOf<String, BrowserPresentationPreferences>()
            val exactPathMap = mutableMapOf<String, BrowserPresentationPreferences>()
            prefs.asMap().forEach { (key, value) ->
                when {
                    key.name.startsWith("path_sort_") && value is String -> {
                        val path = key.name.removePrefix("path_sort_")
                        pathMap[path] = currentPresentation(
                            pathMap[path],
                            globalPresentation
                        ).copy(sortOption = parseSortOption(value, globalPresentation.sortOption))
                    }

                    key.name.startsWith("exact_path_sort_") && value is String -> {
                        val path = key.name.removePrefix("exact_path_sort_")
                        exactPathMap[path] = currentPresentation(
                            exactPathMap[path],
                            globalPresentation
                        ).copy(sortOption = parseSortOption(value, globalPresentation.sortOption))
                    }

                    key.name.startsWith("path_view_mode_") && value is String -> {
                        val path = key.name.removePrefix("path_view_mode_")
                        pathMap[path] = currentPresentation(
                            pathMap[path],
                            globalPresentation
                        ).copy(viewMode = parseViewMode(value))
                    }

                    key.name.startsWith("exact_path_view_mode_") && value is String -> {
                        val path = key.name.removePrefix("exact_path_view_mode_")
                        exactPathMap[path] = currentPresentation(
                            exactPathMap[path],
                            globalPresentation
                        ).copy(viewMode = parseViewMode(value))
                    }

                    key.name.startsWith("path_list_zoom_") && value is Float -> {
                        val path = key.name.removePrefix("path_list_zoom_")
                        pathMap[path] = currentPresentation(
                            pathMap[path],
                            globalPresentation
                        ).copy(listZoom = value)
                    }

                    key.name.startsWith("exact_path_list_zoom_") && value is Float -> {
                        val path = key.name.removePrefix("exact_path_list_zoom_")
                        exactPathMap[path] = currentPresentation(
                            exactPathMap[path],
                            globalPresentation
                        ).copy(listZoom = value)
                    }

                    key.name.startsWith("path_grid_min_cell_size_") && value is Float -> {
                        val path = key.name.removePrefix("path_grid_min_cell_size_")
                        pathMap[path] = currentPresentation(
                            pathMap[path],
                            globalPresentation
                        ).copy(gridMinCellSize = value)
                    }

                    key.name.startsWith("exact_path_grid_min_cell_size_") && value is Float -> {
                        val path = key.name.removePrefix("exact_path_grid_min_cell_size_")
                        exactPathMap[path] = currentPresentation(
                            exactPathMap[path],
                            globalPresentation
                        ).copy(gridMinCellSize = value)
                    }
                }
            }

            BrowserPreferences(
                globalPresentation = globalPresentation,
                pathPresentationOptions = pathMap.mapValues { it.value.normalized() },
                exactPathPresentationOptions = exactPathMap.mapValues { it.value.normalized() }
            )
        }

    override suspend fun updateGlobalPresentation(presentation: BrowserPresentationPreferences) {
        val normalized = presentation.normalized()
        context.browserDataStore.edit { prefs ->
            prefs[GLOBAL_SORT_KEY] = normalized.sortOption.name
            prefs[GLOBAL_VIEW_MODE_KEY] = normalized.viewMode.name
            prefs[GLOBAL_LIST_ZOOM_KEY] = normalized.listZoom
            prefs[GLOBAL_GRID_MIN_CELL_SIZE_KEY] = normalized.gridMinCellSize
        }
    }

    override suspend fun updatePathPresentation(
        path: String,
        presentation: BrowserPresentationPreferences?,
        applyToSubfolders: Boolean
    ) {
        val normalizedPath = if (path.length > 1) path.trimEnd('/') else path
        val keys = presentationKeys(normalizedPath, applyToSubfolders)
        val keysToClear = presentationKeys(normalizedPath, true).all() + presentationKeys(normalizedPath, false).all()

        context.browserDataStore.edit { prefs ->
            keysToClear.forEach { prefs.remove(it) }
            if (presentation != null) {
                val normalized = presentation.normalized()
                prefs[keys.sort] = normalized.sortOption.name
                prefs[keys.viewMode] = normalized.viewMode.name
                prefs[keys.listZoom] = normalized.listZoom
                prefs[keys.gridMinCellSize] = normalized.gridMinCellSize
            }
        }
    }

    private fun parseSortOption(value: String?, fallback: FileSortOption): FileSortOption {
        return FileSortOption.entries.find { it.name == value } ?: fallback
    }

    private fun parseViewMode(value: String?): BrowserViewMode {
        return BrowserViewMode.entries.find { it.name == value } ?: BrowserPresentationPreferences.DEFAULT_VIEW_MODE
    }

    private fun currentPresentation(
        existing: BrowserPresentationPreferences?,
        globalPresentation: BrowserPresentationPreferences
    ): BrowserPresentationPreferences {
        return existing ?: globalPresentation
    }

    private fun presentationKeys(path: String, recursive: Boolean): PresentationKeys {
        val prefix = if (recursive) "path" else "exact_path"
        return PresentationKeys(
            sort = stringPreferencesKey("${prefix}_sort_$path"),
            viewMode = stringPreferencesKey("${prefix}_view_mode_$path"),
            listZoom = floatPreferencesKey("${prefix}_list_zoom_$path"),
            gridMinCellSize = floatPreferencesKey("${prefix}_grid_min_cell_size_$path")
        )
    }

    private data class PresentationKeys(
        val sort: androidx.datastore.preferences.core.Preferences.Key<String>,
        val viewMode: androidx.datastore.preferences.core.Preferences.Key<String>,
        val listZoom: androidx.datastore.preferences.core.Preferences.Key<Float>,
        val gridMinCellSize: androidx.datastore.preferences.core.Preferences.Key<Float>
    ) {
        fun all(): List<androidx.datastore.preferences.core.Preferences.Key<*>> =
            listOf(sort, viewMode, listZoom, gridMinCellSize)
    }
}
