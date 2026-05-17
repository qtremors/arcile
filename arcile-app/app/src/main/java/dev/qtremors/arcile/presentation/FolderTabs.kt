package dev.qtremors.arcile.presentation

import dev.qtremors.arcile.domain.FileModel

data class FolderTab(
    val path: String?,
    val label: String,
    val count: Int,
    val totalSizeBytes: Long
)

fun buildFolderTabs(
    files: List<FileModel>,
    allLabel: String
): List<FolderTab> {
    val folderTabs = files
        .groupBy { containingFolderPath(it.absolutePath) }
        .mapNotNull { (path, folderFiles) ->
            if (path == null) return@mapNotNull null
            FolderTab(
                path = path,
                label = folderLabel(path),
                count = folderFiles.size,
                totalSizeBytes = folderFiles.sumOf { it.size }
            )
        }

    return listOf(FolderTab(path = null, label = allLabel, count = files.size, totalSizeBytes = files.sumOf { it.size })) + folderTabs
}

fun filterFilesByFolderTab(
    files: List<FileModel>,
    selectedFolderTabPath: String?
): List<FileModel> {
    if (selectedFolderTabPath == null) return files
    return files.filter { containingFolderPath(it.absolutePath) == selectedFolderTabPath }
}

fun hasFolderTabPath(files: List<FileModel>, selectedFolderTabPath: String?): Boolean {
    if (selectedFolderTabPath == null) return true
    return files.any { containingFolderPath(it.absolutePath) == selectedFolderTabPath }
}

fun containingFolderPath(path: String): String? {
    val normalized = path.trimEnd('/', '\\')
    if (normalized.isBlank()) return null
    val lastSeparator = maxOf(normalized.lastIndexOf('/'), normalized.lastIndexOf('\\'))
    if (lastSeparator <= 0) return null
    return normalized.substring(0, lastSeparator).takeIf { it.isNotBlank() }
}

private fun folderLabel(path: String): String {
    val normalized = path.trimEnd('/', '\\')
    val lastSeparator = maxOf(normalized.lastIndexOf('/'), normalized.lastIndexOf('\\'))
    val name = if (lastSeparator >= 0 && lastSeparator < normalized.lastIndex) {
        normalized.substring(lastSeparator + 1)
    } else {
        normalized
    }
    return name.ifBlank { normalized }
}
