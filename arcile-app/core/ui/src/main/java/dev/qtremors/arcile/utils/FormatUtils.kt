package dev.qtremors.arcile.utils

import java.util.Locale

fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = size.toDouble()
    var unitIndex = 0

    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }

    if (String.format(Locale.US, "%.1f", value).toDouble() >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }

    return String.format(Locale.US, "%.1f %s", value, units[unitIndex])
}
