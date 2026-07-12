package dev.qtremors.arcile.core.storage.domain

fun normalizeStoragePath(path: String): String = path.replace('\\', '/')

fun storagePathName(path: String): String = normalizeStoragePath(path)
    .trimEnd('/')
    .substringAfterLast('/')

fun storagePathNameWithoutExtension(path: String): String {
    val name = storagePathName(path)
    return name.substringBeforeLast('.', missingDelimiterValue = name)
}

fun storageParentPath(path: String): String? {
    val normalized = normalizeStoragePath(path).withoutTrailingStorageSeparators()
    if (normalized.isEmpty() || normalized == "/" || normalized.isDriveRoot()) return null
    val separator = normalized.lastIndexOf('/')
    return when {
        separator < 0 -> null
        separator == 0 -> "/"
        separator == 2 && normalized[1] == ':' -> normalized.take(3)
        else -> normalized.take(separator)
    }
}

fun joinStoragePath(parent: String, child: String): String {
    val normalizedParent = normalizeStoragePath(parent).withoutTrailingStorageSeparators()
    val normalizedChild = normalizeStoragePath(child).trimStart('/')
    return when {
        normalizedParent.isEmpty() -> normalizedChild
        normalizedChild.isEmpty() -> normalizedParent
        normalizedParent.endsWith('/') -> "$normalizedParent$normalizedChild"
        else -> "$normalizedParent/$normalizedChild"
    }
}

fun isStorageDescendantOrSelf(path: String, parent: String): Boolean {
    val normalizedPath = normalizeStoragePath(path).withoutTrailingStorageSeparators()
    val normalizedParent = normalizeStoragePath(parent).withoutTrailingStorageSeparators()
    if (normalizedPath == normalizedParent) return true
    if (normalizedPath.isEmpty() || normalizedParent.isEmpty()) return false
    val parentPrefix = if (normalizedParent.endsWith('/')) normalizedParent else "$normalizedParent/"
    return normalizedPath.startsWith(parentPrefix)
}

private fun String.withoutTrailingStorageSeparators(): String = when {
    this == "/" -> this
    isDriveRoot() -> take(3)
    else -> trimEnd('/')
}

private fun String.isDriveRoot(): Boolean =
    length >= 2 && this[1] == ':' && drop(2).all { it == '/' }
