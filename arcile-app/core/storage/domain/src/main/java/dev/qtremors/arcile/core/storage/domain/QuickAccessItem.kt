package dev.qtremors.arcile.core.storage.domain

import kotlinx.serialization.Serializable

enum class QuickAccessType {
    STANDARD,  // Hardcoded paths like Downloads, DCIM
    CUSTOM,    // User-selected internal storage paths via Arcile picker
    SAF_TREE,  // Selected via ACTION_OPEN_DOCUMENT_TREE
    EXTERNAL_HANDOFF,
    FILES_APP
}

@Serializable
data class QuickAccessItem(
    val id: String,
    val label: String,
    val path: String, // Can be an absolute path or a URI string
    val type: QuickAccessType,
    val handoffDescription: String? = null,
    val isPinned: Boolean = true,
    val isEnabled: Boolean = true
) {
    companion object {
        const val FILES_APP_PATH = "arcile://quick-access/files-app"
    }
}
