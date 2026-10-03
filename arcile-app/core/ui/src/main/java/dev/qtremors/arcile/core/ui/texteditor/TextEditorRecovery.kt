package dev.qtremors.arcile.core.ui.texteditor

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

internal data class TextEditorDraft(val sourceHash: String, val text: String) {
    fun matches(source: String): Boolean = sourceHash == textContentHash(source)
}

internal fun textContentHash(content: String): String = MessageDigest.getInstance("SHA-256")
    .digest(content.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

internal fun readTextDraft(file: File): TextEditorDraft? {
    if (!file.isFile) return null
    val saved = file.inputStream().use { readBoundedText(it, MAX_TEXT_BYTES + 65) }
    val separator = saved.indexOf('\n')
    if (separator != 64 || !saved.take(separator).matches(Regex("[0-9a-f]{64}"))) return null
    val text = saved.substring(separator + 1)
    if (!textFitsEditor(text)) throw TextTooLargeException()
    return TextEditorDraft(saved.take(separator), text)
}

internal fun writeTextDraft(file: File, source: String, draft: String) {
    check(file.parentFile?.let { it.mkdirs() || it.isDirectory } == true)
    persistAtomicText(file, "${textContentHash(source)}\n$draft").getOrThrow()
}

internal fun persistAtomicText(
    file: File,
    content: String,
    writeStaging: (File, ByteArray) -> Unit = { staged, bytes ->
        FileOutputStream(staged).use { output ->
            output.write(bytes)
            output.flush()
            output.fd.sync()
        }
    },
    publish: (File, File) -> Unit = { staged, target ->
        Files.move(staged.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }
): Result<Unit> = runCatching {
    require(!Files.isSymbolicLink(file.toPath())) { "Cannot replace a symbolic link" }
    val original = if (file.exists()) file.readBytes() else null
    val originalKey = if (file.exists()) Files.readAttributes(file.toPath(), java.nio.file.attribute.BasicFileAttributes::class.java) else null
    val staged = File.createTempFile(".arcile-editor-", ".tmp", file.parentFile)
    try {
        val bytes = content.toByteArray(Charsets.UTF_8)
        writeStaging(staged, bytes)
        check(staged.readBytes().contentEquals(bytes)) { "The staging file did not preserve the complete document" }
        if (original != null) {
            val currentKey = Files.readAttributes(file.toPath(), java.nio.file.attribute.BasicFileAttributes::class.java)
            check(!Files.isSymbolicLink(file.toPath()) && currentKey.fileKey() == originalKey?.fileKey() &&
                currentKey.creationTime() == originalKey?.creationTime() && file.readBytes().contentEquals(original)) {
                "The original document changed before saving"
            }
            runCatching { Files.setPosixFilePermissions(staged.toPath(), Files.getPosixFilePermissions(file.toPath())) }
        } else check(!file.exists()) { "The destination appeared before saving" }
        publish(staged, file)
        check(file.readBytes().contentEquals(bytes)) { "The saved document could not be verified" }
    } finally {
        staged.delete()
    }
}

private fun draftFile(context: Context, reference: String): File {
    return File(File(context.noBackupFilesDir, "text_editor_drafts"), "${textContentHash(reference)}.draft")
}

private val draftLock = Any()

internal fun readRecoveryDraft(context: Context, reference: String): TextEditorDraft? = synchronized(draftLock) {
    val file = draftFile(context, reference)
    val legacy = File(File(context.cacheDir, "text_editor_drafts"), file.name)
    runCatching { readTextDraft(if (file.isFile) file else legacy) }.getOrElse {
        if (it is TextTooLargeException) throw it
        null
    }
}

internal fun writeDraft(context: Context, reference: String, source: String, draft: String, checkCancellation: () -> Unit = {}) = synchronized(draftLock) {
    checkCancellation()
    val destination = draftFile(context, reference)
    writeTextDraft(destination, source, draft)
}

internal fun clearDraft(context: Context, reference: String) = synchronized(draftLock) {
    val file = draftFile(context, reference)
    file.delete()
    File(File(context.cacheDir, "text_editor_drafts"), file.name).delete()
}

internal fun clearMatchingDraft(context: Context, reference: String, source: String, checkCancellation: () -> Unit = {}) = synchronized(draftLock) {
    checkCancellation()
    val current = draftFile(context, reference)
    val legacy = File(File(context.cacheDir, "text_editor_drafts"), current.name)
    listOf(current, legacy).forEach { file ->
        if (runCatching { readTextDraft(file)?.matches(source) }.getOrNull() == true) file.delete()
    }
}
