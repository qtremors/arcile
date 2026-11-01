package dev.qtremors.arcile.core.ui.viewer

import dev.qtremors.arcile.core.storage.domain.StorageNodeCapabilities

/**
 * A shared viewer can render private content without sending its file actions to native storage.
 * The owner supplies vault callbacks and capabilities for this exact unlocked source. Keep this
 * binding in memory and replace it whenever the source or its capabilities change.
 */
class PrivateViewerActions(
    capabilities: StorageNodeCapabilities,
    private val perform: (ViewerFileAction) -> Unit
) {
    val allowedActions: Set<ViewerFileAction> = ViewerActionPolicy.resolveAllowedActions(
        ViewerSourceScope.Vault, capabilities
    )

    /** Check again at dispatch so stale menu events cannot bypass the bound capability policy. */
    fun dispatch(action: ViewerFileAction): Boolean {
        if (action !in allowedActions) return false
        perform(action)
        return true
    }
}
