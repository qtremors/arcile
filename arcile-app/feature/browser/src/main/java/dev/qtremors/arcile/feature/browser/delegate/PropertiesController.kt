package dev.qtremors.arcile.feature.browser.delegate

import dev.qtremors.arcile.core.presentation.SelectionPropertiesLoader
import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.core.storage.domain.ArchiveRepository
import dev.qtremors.arcile.core.storage.domain.FileBrowserRepository
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.PropertiesAccessStatus
import dev.qtremors.arcile.core.storage.domain.SelectionProperties
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.feature.browser.BrowserArchiveContext
import dev.qtremors.arcile.feature.browser.BrowserPropertiesState
import dev.qtremors.arcile.core.presentation.toUiModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class BrowserPropertiesContext(
    val selectedPaths: List<String>,
    val files: List<FileModel>,
    val archiveContext: BrowserArchiveContext?
)

internal class PropertiesController(
    initialState: BrowserPropertiesState,
    private val scope: CoroutineScope,
    private val fileBrowserRepository: FileBrowserRepository,
    private val archiveRepository: ArchiveRepository,
    private val contextProvider: () -> BrowserPropertiesContext,
    private val onStateChange: (BrowserPropertiesState) -> Unit,
    private val onError: (UiText) -> Unit
) {
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<BrowserPropertiesState> = _state.asStateFlow()
    private val localLoader = SelectionPropertiesLoader(
        scope = scope,
        repository = fileBrowserRepository,
        onStateChange = {
            update(
                BrowserPropertiesState(
                    isVisible = it.isVisible,
                    isLoading = it.isLoading,
                    properties = it.properties
                )
            )
        },
        onError = { error ->
            onError(
                error.message?.let(UiText::Dynamic)
                    ?: UiText.StringResource(R.string.error_load_properties_failed)
            )
        },
        transform = { paths, properties ->
            val archiveSummary = paths.singleOrNull()
                ?.takeIf(ArchiveFormat::isSupported)
                ?.let { archiveRepository.getArchiveMetadata(it).getOrNull() }
            properties.copy(archiveSummary = archiveSummary)
        }
    )

    fun openForSelection() {
        val context = contextProvider()
        if (context.selectedPaths.isEmpty()) return

        val archiveContext = context.archiveContext
        if (archiveContext != null) {
            localLoader.dismiss()
            val selectedFiles = context.files.filter { it.absolutePath in context.selectedPaths }
            val modified = selectedFiles.map { it.lastModified }.filter { it > 0L }
            update(
                BrowserPropertiesState(
                    isVisible = true,
                    properties = SelectionProperties(
                        displayName = selectedFiles.singleOrNull()?.name ?: "${selectedFiles.size} items",
                        pathSummary = listOfNotNull(
                            archiveContext.archiveName,
                            archiveContext.entryPrefix
                        ).joinToString("/"),
                        itemCount = selectedFiles.size,
                        fileCount = selectedFiles.count { !it.isDirectory },
                        folderCount = selectedFiles.count(FileModel::isDirectory),
                        totalBytes = selectedFiles.filterNot(FileModel::isDirectory).sumOf(FileModel::size),
                        newestModifiedAt = modified.maxOrNull(),
                        oldestModifiedAt = modified.minOrNull(),
                        mimeTypeSummary = null,
                        extensionSummary = selectedFiles
                            .filterNot(FileModel::isDirectory)
                            .map(FileModel::extension)
                            .filter(String::isNotBlank)
                            .distinct()
                            .joinToString(", ")
                            .takeIf(String::isNotBlank),
                        hiddenCount = selectedFiles.count(FileModel::isHidden),
                        accessStatus = PropertiesAccessStatus.Full,
                        isSingleItem = selectedFiles.size == 1,
                        isDirectory = selectedFiles.singleOrNull()?.isDirectory
                    ).toUiModel()
                )
            )
            return
        }

        localLoader.open(context.selectedPaths)
    }

    fun dismiss() {
        localLoader.dismiss()
    }

    private fun update(next: BrowserPropertiesState) {
        _state.value = next
        onStateChange(next)
    }
}
