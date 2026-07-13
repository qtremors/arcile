package dev.qtremors.arcile.feature.browser.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

@Stable
internal class BrowserDialogVisibility(
    private val createFolderState: MutableState<Boolean>,
    private val renameState: MutableState<Boolean>,
    private val createFileState: MutableState<Boolean>,
    private val createFakeFileState: MutableState<Boolean>,
    private val createArchiveState: MutableState<Boolean>,
    private val extractArchiveState: MutableState<Boolean>,
    private val sortState: MutableState<Boolean>,
    private val clipboardContentsState: MutableState<Boolean>
) {
    var showCreateFolderDialog by createFolderState
    var showRenameDialog by renameState
    var showCreateFileDialog by createFileState
    var showCreateFakeFileDialog by createFakeFileState
    var showCreateArchiveDialog by createArchiveState
    var showExtractArchiveDialog by extractArchiveState
    var showSortDialog by sortState
    var showClipboardContents by clipboardContentsState

    val hasVisibleDialog: Boolean
        get() = showCreateFolderDialog ||
            showCreateFileDialog ||
            showCreateFakeFileDialog ||
            showCreateArchiveDialog ||
            showExtractArchiveDialog ||
            showRenameDialog ||
            showSortDialog ||
            showClipboardContents
}

@Composable
internal fun rememberBrowserDialogVisibility(): BrowserDialogVisibility =
    BrowserDialogVisibility(
        createFolderState = rememberSaveable { mutableStateOf(false) },
        renameState = rememberSaveable { mutableStateOf(false) },
        createFileState = rememberSaveable { mutableStateOf(false) },
        createFakeFileState = rememberSaveable { mutableStateOf(false) },
        createArchiveState = rememberSaveable { mutableStateOf(false) },
        extractArchiveState = rememberSaveable { mutableStateOf(false) },
        sortState = rememberSaveable { mutableStateOf(false) },
        clipboardContentsState = rememberSaveable { mutableStateOf(false) }
    )
