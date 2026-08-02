package dev.qtremors.arcile.core.ui.texteditor

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
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
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.qtremors.arcile.core.presentation.formatFileSize
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.externalfile.ExternalFileAccessHelper
import dev.qtremors.arcile.core.ui.theme.bounceClickable
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var loadState by remember(reference) { mutableStateOf<TextLoadState>(TextLoadState.Loading) }
    var loadRequest by remember { mutableIntStateOf(0) }
    var textState by rememberSaveable(reference, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    var sessionInitialized by rememberSaveable(reference) { mutableStateOf(false) }
    var originalText by remember(reference) { mutableStateOf("") }
    var mode by rememberSaveable(reference) {
        mutableStateOf(
            if (supportsMarkdownPreview && !writable) TextEditorMode.PREVIEW else TextEditorMode.EDIT
        )
    }
    var isSaving by remember { mutableStateOf(false) }
    var showUnsavedDialog by rememberSaveable { mutableStateOf(false) }
    var infoVisible by rememberSaveable { mutableStateOf(false) }
    var draftFailureShown by remember { mutableStateOf(false) }
    var undoStack by remember { mutableStateOf(listOf<TextFieldValue>()) }
    var redoStack by remember { mutableStateOf(listOf<TextFieldValue>()) }
    val editorScrollState = rememberScrollState()
    val previewScrollState = rememberScrollState()

    val isDirty = writable && textState.text != originalText

    LaunchedEffect(reference, loadRequest) {
        loadState = TextLoadState.Loading
        val loaded = withContext(Dispatchers.IO) {
            runCatching { readTextFileContent(context, reference) }
        }
        loaded.fold(
            onSuccess = { sourceText ->
                originalText = sourceText
                if (!sessionInitialized) {
                    val restoredDraft = if (writable) {
                        withContext(Dispatchers.IO) {
                            readMatchingDraft(context, reference, sourceText)
                        }
                    } else {
                        null
                    }
                    textState = TextFieldValue(restoredDraft ?: sourceText)
                    sessionInitialized = true
                }
                loadState = TextLoadState.Ready
            },
            onFailure = { error ->
                if (error is CancellationException) throw error
                loadState = TextLoadState.Failed(error.localizedMessage)
            }
        )
    }

    LaunchedEffect(reference, textState.text, originalText, isDirty) {
        if (loadState !is TextLoadState.Ready || !writable) return@LaunchedEffect
        delay(250)
        val draftResult = withContext(Dispatchers.IO) {
            runCatching {
            if (isDirty) {
                writeDraft(context, reference, originalText, textState.text)
            } else {
                clearDraft(context, reference)
            }
            }
        }
        if (draftResult.isFailure && !draftFailureShown) {
            draftFailureShown = true
            Toast.makeText(
                context,
                context.getString(R.string.text_editor_draft_failed),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val attemptBack: () -> Unit = {
        when {
            infoVisible -> infoVisible = false
            isSaving -> Toast.makeText(
                context,
                context.getString(R.string.text_editor_wait_for_save),
                Toast.LENGTH_SHORT
            ).show()
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
        if (newValue.text != textState.text) {
            undoStack = (undoStack + textState).takeLast(50)
            redoStack = emptyList()
        }
        textState = newValue
    }

    fun handleUndo() {
        val previous = undoStack.lastOrNull() ?: return
        undoStack = undoStack.dropLast(1)
        redoStack = (redoStack + textState).takeLast(50)
        textState = previous
    }

    fun handleRedo() {
        val next = redoStack.lastOrNull() ?: return
        redoStack = redoStack.dropLast(1)
        undoStack = (undoStack + textState).takeLast(50)
        textState = next
    }

    fun insertFormatting(prefix: String, suffix: String) {
        if (!writable) return
        val selection = textState.selection
        val selectedText = textState.text.substring(selection.start, selection.end)
        val replacement = "$prefix$selectedText$suffix"
        val newText = textState.text.replaceRange(selection.start, selection.end, replacement)
        val cursor = if (selection.collapsed) {
            selection.start + prefix.length
        } else {
            selection.start + replacement.length
        }
        updateTextWithHistory(TextFieldValue(newText, TextRange(cursor)))
    }

    fun performSave(onSuccess: () -> Unit = {}) {
        if (!writable || isSaving || !isDirty) return
        val snapshot = textState.text
        isSaving = true
        coroutineScope.launch {
            val result = withContext(Dispatchers.IO) {
                writeAndVerifyTextFile(context, reference, snapshot)
            }
            isSaving = false
            result.fold(
                onSuccess = {
                    originalText = snapshot
                    val noNewEdits = textState.text == snapshot
                    withContext(Dispatchers.IO) {
                        if (noNewEdits) clearDraft(context, reference)
                    }
                    Toast.makeText(
                        context,
                        context.getString(R.string.text_editor_save_success),
                        Toast.LENGTH_SHORT
                    ).show()
                    if (noNewEdits) onSuccess()
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    Toast.makeText(
                        context,
                        context.getString(
                            R.string.text_editor_save_failed_detail,
                            error.localizedMessage ?: context.getString(R.string.text_editor_unknown_error)
                        ),
                        Toast.LENGTH_LONG
                    ).show()
                }
            )
        }
    }

    fun afterSavingIfNeeded(action: () -> Unit) {
        if (isDirty) performSave(onSuccess = action) else action()
    }

    Surface(
        modifier = modifier.fillMaxSize().imePadding(),
        color = Color(0xFF202124)
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
                    val bottomPadding = if (
                        writable && supportsMarkdownPreview && mode == TextEditorMode.EDIT
                    ) {
                        150.dp
                    } else {
                        94.dp
                    }
                    AnimatedContent(
                        targetState = mode,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 92.dp, bottom = bottomPadding),
                        transitionSpec = {
                            fadeIn(spring(stiffness = Spring.StiffnessLow)) togetherWith
                                fadeOut(spring(stiffness = Spring.StiffnessLow))
                        },
                        label = "editorModeTransition"
                    ) { targetMode ->
                        if (targetMode == TextEditorMode.PREVIEW && supportsMarkdownPreview) {
                            MarkdownRenderer(
                                content = textState.text,
                                modifier = Modifier.fillMaxSize().verticalScroll(previewScrollState)
                            )
                        } else {
                            BasicTextField(
                                value = textState,
                                onValueChange = ::updateTextWithHistory,
                                readOnly = !writable,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 18.dp, vertical = 12.dp)
                                    .verticalScroll(editorScrollState),
                                textStyle = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 15.sp,
                                    lineHeight = 23.sp,
                                    color = Color.White.copy(alpha = 0.92f)
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
            AnimatedVisibility(
                visible = infoVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                TextDocumentInfoSheet(
                    title = title,
                    reference = reference,
                    sizeBytes = if (writable) textState.text.toByteArray().size.toLong() else sizeBytes,
                    text = textState.text,
                    onDismiss = { infoVisible = false }
                )
            }
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
            .background(Color.Black.copy(alpha = 0.68f))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.text_editor_load_failed),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        if (!message.isNullOrBlank()) {
            Text(message, color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.bodySmall)
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

@Composable
private fun TextDocumentInfoSheet(
    title: String,
    reference: String,
    sizeBytes: Long,
    text: String,
    onDismiss: () -> Unit
) {
    val lines = remember(text) { text.lines().size }
    val words = remember(text) { text.wordCount() }
    val chars = remember(text) { text.length }
    Surface(Modifier.fillMaxSize(), color = Color.Black.copy(alpha = 0.92f)) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.15f),
                    modifier = Modifier.size(48.dp).bounceClickable(onClick = onDismiss)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = Color.White
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Text(
                    stringResource(R.string.text_editor_file_info),
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(24.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    InfoCard {
                        Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            stringResource(R.string.text_editor_path_format, reference),
                            color = Color.White.copy(alpha = 0.72f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                item {
                    InfoCard {
                        InfoRow(stringResource(R.string.text_editor_lines), lines.toString())
                        InfoRow(stringResource(R.string.text_editor_words), words.toString())
                        InfoRow(stringResource(R.string.text_editor_characters), chars.toString())
                        if (sizeBytes > 0) {
                            InfoRow(stringResource(R.string.text_editor_file_size), formatFileSize(sizeBytes))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.12f)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Text(label, color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.bodyMedium)
        Text(value, color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

internal fun readTextFileContent(context: Context, reference: String): String {
    val uri = Uri.parse(reference)
    return when (uri.scheme) {
        "content" -> context.contentResolver.openInputStream(uri)?.use {
            it.bufferedReader().readText()
        } ?: error("Unable to open the document for reading")
        "file", null -> {
            val file = if (uri.scheme == "file") File(uri.path.orEmpty()) else File(reference)
            require(file.isFile && ExternalFileAccessHelper.isAllowedUserFile(context, file)) {
                "Access denied or file does not exist"
            }
            file.readText()
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
        val uri = Uri.parse(reference)
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
                file.writeText(snapshot)
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

private fun draftFile(context: Context, reference: String): File {
    val key = MessageDigest.getInstance("SHA-256")
        .digest(reference.toByteArray())
        .joinToString("") { "%02x".format(it) }
    return File(File(context.cacheDir, "text_editor_drafts"), "$key.draft")
}

private fun contentHash(content: String): String = MessageDigest.getInstance("SHA-256")
    .digest(content.toByteArray())
    .joinToString("") { "%02x".format(it) }

private fun readMatchingDraft(context: Context, reference: String, source: String): String? {
    val file = draftFile(context, reference)
    if (!file.isFile) return null
    val saved = runCatching { file.readText() }.getOrNull() ?: return null
    val separator = saved.indexOf('\n')
    if (separator < 0 || saved.substring(0, separator) != contentHash(source)) {
        file.delete()
        return null
    }
    return saved.substring(separator + 1)
}

private fun writeDraft(context: Context, reference: String, source: String, draft: String) {
    val destination = draftFile(context, reference)
    destination.parentFile?.mkdirs()
    val temporary = File(destination.parentFile, "${destination.name}.tmp")
    temporary.writeText("${contentHash(source)}\n$draft")
    if (destination.exists() && !destination.delete()) error("Unable to replace editor draft")
    if (!temporary.renameTo(destination)) {
        temporary.copyTo(destination, overwrite = true)
        temporary.delete()
    }
}

private fun clearDraft(context: Context, reference: String) {
    draftFile(context, reference).delete()
}
