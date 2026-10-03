package dev.qtremors.arcile.core.ui.texteditor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import java.io.ByteArrayOutputStream
import java.io.InputStream

internal const val MAX_TEXT_BYTES = 4 * 1024 * 1024
internal const val MAX_HISTORY_BYTES = 8L * 1024 * 1024
internal class TextTooLargeException : IllegalArgumentException("Text exceeds the editor limit")

internal fun readBoundedText(input: InputStream, byteLimit: Int = MAX_TEXT_BYTES): String {
    val output = ByteArrayOutputStream(minOf(byteLimit, 8192))
    val buffer = ByteArray(8192)
    while (true) {
        val read = input.read(buffer, 0, minOf(buffer.size, byteLimit - output.size() + 1))
        if (read < 0) return output.toString(Charsets.UTF_8.name())
        if (read == 0) continue
        if (output.size() + read > byteLimit) throw TextTooLargeException()
        output.write(buffer, 0, read)
    }
}

internal fun textFitsEditor(text: String): Boolean {
    if (text.length > MAX_TEXT_BYTES) return false
    var bytes = 0
    var index = 0
    while (index < text.length) {
        val char = text[index++]
        bytes += when {
            char.code < 0x80 -> 1
            char.code < 0x800 -> 2
            char.isHighSurrogate() && index < text.length && text[index].isLowSurrogate() -> { index++; 4 }
            char.isSurrogate() -> 1 // UTF-8's replacement for an unpaired surrogate.
            else -> 3
        }
        if (bytes > MAX_TEXT_BYTES) return false
    }
    return true
}

internal fun boundedEditorHistory(
    undo: List<TextFieldValue>, redo: List<TextFieldValue>
): Pair<List<TextFieldValue>, List<TextFieldValue>> {
    var keptUndo = undo.takeLast(50)
    var keptRedo = redo.takeLast(50)
    var bytes = (keptUndo + keptRedo).sumOf { it.text.length.toLong() * 2 }
    while (bytes > MAX_HISTORY_BYTES || keptUndo.size + keptRedo.size > 50) {
        val trimUndo = keptUndo.isNotEmpty() && keptUndo.size >= keptRedo.size
        val removed = if (trimUndo) keptUndo.first() else keptRedo.first()
        bytes -= removed.text.length.toLong() * 2
        if (trimUndo) keptUndo = keptUndo.drop(1) else keptRedo = keptRedo.drop(1)
    }
    return keptUndo to keptRedo
}

internal fun formatEditorSelection(value: TextFieldValue, prefix: String, suffix: String): TextFieldValue {
    val start = value.selection.min.coerceIn(0, value.text.length)
    val end = value.selection.max.coerceIn(start, value.text.length)
    val replacement = prefix + value.text.substring(start, end) + suffix
    val text = value.text.replaceRange(start, end, replacement)
    val cursor = if (start == end) start + prefix.length else start + replacement.length
    return TextFieldValue(text, TextRange(cursor))
}
