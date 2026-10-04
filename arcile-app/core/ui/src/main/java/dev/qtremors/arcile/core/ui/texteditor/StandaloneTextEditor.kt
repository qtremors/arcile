package dev.qtremors.arcile.core.ui.texteditor

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.core.net.toUri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.externalfile.ExternalFileAccessHelper
import java.io.File
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

enum class TextEditorMode {
    EDIT, PREVIEW
}

private sealed interface TextLoadState {
    data object Loading : TextLoadState
    data object Ready : TextLoadState
    data class Failed(val message: String?) : TextLoadState
}

@Composable
fun StandaloneTextEditor(
    reference: String,
    title: String,
    sizeBytes: Long,
    writable: Boolean,
    supportsMarkdownPreview: Boolean,
    onNavigateBack: () -> Unit,
    onShare: () -> Unit,
    onOpenWith: () -> Unit,
    modifier: Modifier = Modifier,
    loadContent: (suspend () -> String)? = null,
    persistContent: (suspend (String) -> Result<Unit>)? = null
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val coroutineScope = rememberCoroutineScope()
    var loadState by remember(reference) { mutableStateOf<TextLoadState>(TextLoadState.Loading) }
    var loadRequest by remember { mutableIntStateOf(0) }
    val session: TextEditorSession = viewModel(key = "text-editor:$reference")
    var textState by session::text
    var sessionInitialized by session::initialized
    var originalText by session::original
    var recoveryDraft by session::recoveryDraft
    var cursorStart by rememberSaveable(reference) { mutableIntStateOf(0) }
    var cursorEnd by rememberSaveable(reference) { mutableIntStateOf(0) }
    var mode by rememberSaveable(reference) {
        mutableStateOf(
            if (supportsMarkdownPreview && !writable) TextEditorMode.PREVIEW else TextEditorMode.EDIT
        )
    }
    var isSaving by remember { mutableStateOf(false) }
    var showUnsavedDialog by rememberSaveable { mutableStateOf(false) }
    var infoVisible by rememberSaveable { mutableStateOf(false) }
    var draftFailureShown by remember { mutableStateOf(false) }
    var undoStack by session::undo
    var redoStack by session::redo
    val editorScrollState = rememberScrollState()
    val previewScrollState = rememberScrollState()
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }

    val isDirty = writable && textState.text != originalText

    LaunchedEffect(reference, loadRequest) {
        loadState = TextLoadState.Loading
        val loaded = withContext(Dispatchers.IO) {
            runCatching {
                val source = loadContent?.invoke() ?: readTextFileContent(context, reference)
                if (!textFitsEditor(source)) throw TextTooLargeException()
                val draft = if (writable && !sessionInitialized) readRecoveryDraft(context, reference) else null
                source to draft
            }
        }
        loaded.fold(
            onSuccess = { (sourceText, restoredDraft) ->
                if (!sessionInitialized) {
                    originalText = sourceText
                    recoveryDraft = restoredDraft?.takeUnless { it.matches(sourceText) || it.text == sourceText }
                    val restoredText = restoredDraft?.takeIf { it.matches(sourceText) }?.text ?: sourceText
                    textState = TextFieldValue(restoredText, TextRange(cursorStart.coerceIn(0, restoredText.length), cursorEnd.coerceIn(0, restoredText.length)))
                    sessionInitialized = true
                }
                loadState = TextLoadState.Ready
            },
            onFailure = { error ->
                if (error is CancellationException) throw error
                loadState = TextLoadState.Failed(if (error is TextTooLargeException)
                    resources.getString(R.string.text_editor_too_large) else error.localizedMessage)
            }
        )
    }

    LaunchedEffect(textState.selection, sessionInitialized) {
        if (sessionInitialized) {
            cursorStart = textState.selection.start
            cursorEnd = textState.selection.end
        }
    }

    LaunchedEffect(reference, textState.text, originalText, sessionInitialized, isSaving, recoveryDraft) {
        if (!writable || !sessionInitialized || isSaving || recoveryDraft != null) return@LaunchedEffect
        delay(600)
        val textToPersist = textState.text
        val draftResult = withContext(Dispatchers.IO) {
            val draftContext = currentCoroutineContext()
            runCatching {
                if (textToPersist != originalText) {
                    writeDraft(context, reference, originalText, textToPersist) { draftContext.ensureActive() }
                } else {
                    clearMatchingDraft(context, reference, originalText) { draftContext.ensureActive() }
                }
            }
        }
        if (draftResult.isFailure && !draftFailureShown) {
            draftFailureShown = true
            coroutineScope.launch {
                snackbarHostState.showSnackbar(resources.getString(R.string.text_editor_draft_failed))
            }
        }
    }

    val attemptBack: () -> Unit = {
        when {
            infoVisible -> infoVisible = false
            isSaving -> coroutineScope.launch {
                snackbarHostState.showSnackbar(resources.getString(R.string.text_editor_wait_for_save))
            }
            isDirty -> showUnsavedDialog = true
            else -> onNavigateBack()
        }
    }

    PredictiveBackHandler(enabled = infoVisible) { progress ->
        try {
            progress.collect { }
            infoVisible = false
        } catch (error: CancellationException) {
            throw error
        }
    }
    BackHandler(enabled = !infoVisible, onBack = attemptBack)

    fun updateTextWithHistory(newValue: TextFieldValue) {
        if (!writable) return
        if (!textFitsEditor(newValue.text)) {
            coroutineScope.launch { snackbarHostState.showSnackbar(resources.getString(R.string.text_editor_edit_limit)) }
            return
        }
        if (newValue.text != textState.text) {
            undoStack = boundedEditorHistory(undoStack + textState, emptyList()).first
            redoStack = emptyList()
        }
        textState = newValue
    }

    fun handleUndo() {
        val previous = undoStack.lastOrNull() ?: return
        val history = boundedEditorHistory(undoStack.dropLast(1), redoStack + textState)
        undoStack = history.first
        redoStack = history.second
        textState = previous
    }

    fun handleRedo() {
        val next = redoStack.lastOrNull() ?: return
        val history = boundedEditorHistory(undoStack + textState, redoStack.dropLast(1))
        undoStack = history.first
        redoStack = history.second
        textState = next
    }

    fun insertFormatting(prefix: String, suffix: String) {
        if (!writable) return
        updateTextWithHistory(formatEditorSelection(textState, prefix, suffix))
    }

    fun performSave(onSuccess: () -> Unit = {}) {
        if (!writable || isSaving || !isDirty) return
        val snapshot = textState.text
        isSaving = true
        coroutineScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    // Preserve this snapshot durably before any provider can truncate its target.
                    writeDraft(context, reference, originalText, snapshot)
                    (persistContent?.invoke(snapshot)
                        ?: writeAndVerifyTextFile(context, reference, snapshot)).getOrThrow()
                }
            }
            isSaving = false
            result.fold(
                onSuccess = {
                    originalText = snapshot
                    withContext(Dispatchers.IO) {
                        // Keep newer edits even if they arrived while publication was running.
                        if (textState.text == snapshot) clearDraft(context, reference)
                    }
                    val noNewEdits = textState.text == snapshot
                    coroutineScope.launch {
                        val message = if (persistContent == null && reference.toUri().scheme == "content")
                            R.string.text_editor_provider_save_success else R.string.text_editor_save_success
                        snackbarHostState.showSnackbar(resources.getString(message))
                    }
                    if (noNewEdits) onSuccess()
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            resources.getString(
                                R.string.text_editor_save_failed_detail,
                                error.localizedMessage ?: resources.getString(R.string.text_editor_unknown_error)
                            )
                        )
                    }
                }
            )
        }
    }

    fun afterSavingIfNeeded(action: () -> Unit) {
        if (isDirty) performSave(onSuccess = action) else action()
    }

    recoveryDraft?.let { draft ->
        AlertDialog(
            onDismissRequest = { recoveryDraft = null },
            title = { Text(stringResource(R.string.text_editor_recover_draft)) },
            text = { Text(stringResource(R.string.text_editor_recover_draft_message)) },
            confirmButton = {
                TextButton(onClick = {
                    textState = TextFieldValue(draft.text)
                    recoveryDraft = null
                }) { Text(stringResource(R.string.text_editor_recover_draft)) }
            },
            dismissButton = {
                TextButton(onClick = { recoveryDraft = null }) {
                    Text(stringResource(R.string.text_editor_keep_current))
                }
            }
        )
    }

    Surface(
        modifier = modifier.fillMaxSize().imePadding(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(Modifier.fillMaxSize()) {
            when (val state = loadState) {
                TextLoadState.Loading -> CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
                is TextLoadState.Failed -> LoadFailure(
                    message = state.message,
                    onRetry = { loadRequest += 1 },
                    onOpenWith = onOpenWith,
                    modifier = Modifier.align(Alignment.Center)
                )
                TextLoadState.Ready -> {
                    val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 92.dp
                    val bottomPadding = if (
                        writable && supportsMarkdownPreview && mode == TextEditorMode.EDIT
                    ) {
                        168.dp
                    } else {
                        104.dp
                    } + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    AnimatedContent(
                        targetState = mode,
                        modifier = Modifier.fillMaxSize(),
                        transitionSpec = {
                            fadeIn(spring(stiffness = Spring.StiffnessLow)) togetherWith
                                fadeOut(spring(stiffness = Spring.StiffnessLow))
                        },
                        label = "editorModeTransition"
                    ) { targetMode ->
                        if (targetMode == TextEditorMode.PREVIEW && supportsMarkdownPreview) {
                            MarkdownRenderer(
                                content = textState.text,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(previewScrollState)
                                    .padding(top = topPadding, bottom = bottomPadding)
                            )
                        } else {
                            BasicTextField(
                                value = textState,
                                onValueChange = ::updateTextWithHistory,
                                readOnly = !writable,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(editorScrollState)
                                    .padding(
                                        start = 18.dp,
                                        top = topPadding + 12.dp,
                                        end = 18.dp,
                                        bottom = bottomPadding + 12.dp
                                    ),
                                textStyle = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 15.sp,
                                    lineHeight = 23.sp,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.92f)
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = !infoVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
            ) {
                TextEditorTopChrome(
                    title = title,
                    isDirty = isDirty,
                    isSaving = isSaving,
                    writable = writable,
                    onNavigateBack = attemptBack
                )
            }
            AnimatedVisibility(
                visible = loadState is TextLoadState.Ready && !infoVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
            ) {
                TextEditorBottomChrome(
                    text = textState.text,
                    mode = mode,
                    writable = writable,
                    supportsMarkdownPreview = supportsMarkdownPreview,
                    isDirty = isDirty,
                    isSaving = isSaving,
                    canUndo = undoStack.isNotEmpty(),
                    canRedo = redoStack.isNotEmpty(),
                    onModeSelected = { mode = it },
                    onUndo = ::handleUndo,
                    onRedo = ::handleRedo,
                    onSave = { performSave() },
                    onShare = { afterSavingIfNeeded(onShare) },
                    onInfo = { infoVisible = true },
                    onOpenWith = { afterSavingIfNeeded(onOpenWith) },
                    onFormat = ::insertFormatting
                )
            }
            if (infoVisible) {
                TextDocumentInfoSheet(
                    title = title,
                    reference = reference,
                    sizeBytes = if (writable) textState.text.toByteArray().size.toLong() else sizeBytes,
                    text = textState.text,
                    onDismiss = { infoVisible = false }
                )
            }
            dev.qtremors.arcile.core.ui.ArcileSnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
            )
        }
    }

    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            title = { Text(stringResource(R.string.text_editor_unsaved_changes)) },
            text = { Text(stringResource(R.string.text_editor_unsaved_changes_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showUnsavedDialog = false
                    performSave(onSuccess = onNavigateBack)
                }) { Text(stringResource(R.string.text_editor_save_and_exit)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showUnsavedDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                    TextButton(onClick = {
                        showUnsavedDialog = false
                        clearDraft(context, reference)
                        onNavigateBack()
                    }) { Text(stringResource(R.string.text_editor_discard)) }
                }
            }
        )
    }
}

@Composable
private fun LoadFailure(
    message: String?,
    onRetry: () -> Unit,
    onOpenWith: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(24.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.text_editor_load_failed),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        if (!message.isNullOrBlank()) {
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            TextButton(onClick = onOpenWith) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.open_app))
            }
        }
    }
}

internal fun readTextFileContent(context: Context, reference: String): String {
    val uri = reference.toUri()
    return when (uri.scheme) {
        "content" -> context.contentResolver.openInputStream(uri)?.use {
            readBoundedText(it)
        } ?: error("Unable to open the document for reading")
        "file", null -> {
            val file = if (uri.scheme == "file") File(uri.path.orEmpty()) else File(reference)
            require(file.isFile && ExternalFileAccessHelper.isAllowedUserFile(context, file)) {
                "Access denied or file does not exist"
            }
            file.inputStream().use { readBoundedText(it) }
        }
        else -> error("Unsupported URI scheme ${uri.scheme}")
    }
}

internal fun writeAndVerifyTextFile(
    context: Context,
    reference: String,
    content: String
): Result<Unit> = persistVerifiedText(
    content = content,
    write = { snapshot ->
        writeDraft(context, reference, readTextFileContent(context, reference), snapshot)
        val uri = reference.toUri()
        when (uri.scheme) {
            "content" -> context.contentResolver.openOutputStream(uri, "wt")?.use {
                it.bufferedWriter().use { writer -> writer.write(snapshot) }
            } ?: error("The document provider did not allow writing")
            "file", null -> {
                val file = if (uri.scheme == "file") File(uri.path.orEmpty()) else File(reference)
                require(
                    file.isFile &&
                        file.canWrite() &&
                        ExternalFileAccessHelper.isAllowedUserFile(context, file)
                ) {
                    "The file is read-only or no longer available"
                }
                persistAtomicText(file, snapshot).getOrThrow()
            }
            else -> error("Unsupported URI scheme ${uri.scheme}")
        }
    },
    read = { readTextFileContent(context, reference) }
)

internal fun persistVerifiedText(
    content: String,
    write: (String) -> Unit,
    read: () -> String
): Result<Unit> = runCatching {
    write(content)
    check(read() == content) { "The provider did not persist the complete document" }
}
