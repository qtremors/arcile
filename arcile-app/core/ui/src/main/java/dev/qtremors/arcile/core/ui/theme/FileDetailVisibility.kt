package dev.qtremors.arcile.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/** Which details file and folder rows show under the name; with everything off, only the name remains. */
@Immutable
data class FileDetailVisibility(
    /** Number of files inside a folder. */
    val itemCount: Boolean = true,
    /** File size, or a folder's total size. */
    val size: Boolean = true,
    /** Modified date. */
    val date: Boolean = true,
    /** Modified time of day. */
    val time: Boolean = true
) {
    val showsStats: Boolean get() = itemCount || size
    val showsTimestamp: Boolean get() = date || time
}

val LocalFileDetailVisibility = staticCompositionLocalOf { FileDetailVisibility() }
