package dev.qtremors.arcile.core.ui.texteditor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel

/** Document bytes live in memory across recreation, never in an activity Bundle. */
internal class TextEditorSession : ViewModel() {
    var text by mutableStateOf(TextFieldValue(""))
    var original by mutableStateOf("")
    var initialized by mutableStateOf(false)
    var recoveryDraft by mutableStateOf<TextEditorDraft?>(null)
    var undo by mutableStateOf(listOf<TextFieldValue>())
    var redo by mutableStateOf(listOf<TextFieldValue>())
}
