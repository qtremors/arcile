package dev.qtremors.arcile.core.operation.android

fun sanitizeIncomingFileName(
    rawName: String?,
    fallbackExtension: String? = null,
    existingNames: Set<String> = emptySet()
): String {
    val cleaned = rawName.orEmpty()
        .filterNot { it.isISOControl() }
        .trim()
        .replace(Regex("""[/\\:*?"<>|]"""), "_")
        .replace(Regex("""\s+"""), " ")
    val withoutTraversal = cleaned
        .split('.', '/', '\\')
        .joinToString(".")
        .replace("..", ".")
        .trim('.', '_', ' ')
    val base = withoutTraversal.takeIf { it.isNotBlank() && it != "." && it != ".." } ?: "shared-file"
    val withReservedFallback = if (base.substringBefore('.').uppercase() in WINDOWS_RESERVED_NAMES) {
        "shared-file${base.substringAfter('.', missingDelimiterValue = "").takeIf { it.isNotBlank() }?.let { ".$it" }.orEmpty()}"
    } else {
        base
    }
    val withExtension = if (!fallbackExtension.isNullOrBlank() && !withReservedFallback.contains('.')) {
        "$withReservedFallback.$fallbackExtension"
    } else {
        withReservedFallback
    }
    val limited = limitFileNameLength(withExtension, MAX_FILE_NAME_CHARS)
    return uniqueName(limited, existingNames)
}

private fun limitFileNameLength(name: String, maxLength: Int): String {
    if (name.length <= maxLength) return name
    val extension = name.substringAfterLast('.', missingDelimiterValue = "")
        .takeIf { it.isNotBlank() && it.length < 32 }
        ?.let { ".$it" }
        .orEmpty()
    val baseLimit = (maxLength - extension.length).coerceAtLeast(1)
    return name.substringBeforeLast('.', missingDelimiterValue = name).take(baseLimit) + extension
}

private fun uniqueName(name: String, existingNames: Set<String>): String {
    if (existingNames.none { it.equals(name, ignoreCase = true) }) return name
    val extension = name.substringAfterLast('.', missingDelimiterValue = "")
        .takeIf { it.isNotBlank() }
        ?.let { ".$it" }
        .orEmpty()
    val base = name.substringBeforeLast('.', missingDelimiterValue = name)
    var index = 1
    while (true) {
        val suffix = " ($index)"
        val candidateBase = limitFileNameLength(base, MAX_FILE_NAME_CHARS - suffix.length - extension.length)
        val candidate = "$candidateBase$suffix$extension"
        if (existingNames.none { it.equals(candidate, ignoreCase = true) }) return candidate
        index += 1
    }
}

private val WINDOWS_RESERVED_NAMES = setOf(
    "CON",
    "PRN",
    "AUX",
    "NUL",
    "COM1",
    "COM2",
    "COM3",
    "COM4",
    "COM5",
    "COM6",
    "COM7",
    "COM8",
    "COM9",
    "LPT1",
    "LPT2",
    "LPT3",
    "LPT4",
    "LPT5",
    "LPT6",
    "LPT7",
    "LPT8",
    "LPT9"
)

private const val MAX_FILE_NAME_CHARS = 255
const val STREAM_BUFFER_SIZE = 128 * 1024
const val MAX_IMPORT_ITEMS = 200
const val MAX_IMPORT_BYTES = 10L * 1024L * 1024L * 1024L
const val FREE_SPACE_SAFETY_BUFFER_BYTES = 50L * 1024L * 1024L
