package dev.qtremors.arcile.core.ui.viewer

import dev.qtremors.arcile.core.storage.domain.StorageNodeCapabilities

object ViewerActionPolicy {
    fun resolveAllowedActions(
        scope: ViewerSourceScope,
        capabilities: StorageNodeCapabilities,
        isMultiFileArchiveAvailable: Boolean = true
    ): Set<ViewerFileAction> {
        val actions = mutableSetOf<ViewerFileAction>()

        when (scope) {
            ViewerSourceScope.ArchiveEntry -> {
                if (capabilities.canShare) actions += ViewerFileAction.Share
                if (capabilities.canOpenWith) actions += ViewerFileAction.OpenWith
                if (capabilities.canRead) actions += ViewerFileAction.Properties
            }
            ViewerSourceScope.ManagedTrash -> {
                if (capabilities.canDelete) {
                    actions += ViewerFileAction.Delete
                }
                if (capabilities.canShare) actions += ViewerFileAction.Share
                if (capabilities.canOpenWith) actions += ViewerFileAction.OpenWith
                if (capabilities.canRead) actions += ViewerFileAction.Properties
            }
            ViewerSourceScope.External -> {
                if (capabilities.canShare) actions += ViewerFileAction.Share
                if (capabilities.canOpenWith) actions += ViewerFileAction.OpenWith
                if (capabilities.canRead) actions += ViewerFileAction.Properties
            }
            ViewerSourceScope.Vault -> {
                if (capabilities.canDelete) actions += ViewerFileAction.Delete
                if (capabilities.canRename) actions += ViewerFileAction.Rename
                if (capabilities.canCopy) actions += ViewerFileAction.Copy
                if (capabilities.canMove) actions += ViewerFileAction.Cut
                if (capabilities.canShare) actions += ViewerFileAction.Share
                if (capabilities.canOpenWith) actions += ViewerFileAction.OpenWith
                if (capabilities.canRead) actions += ViewerFileAction.Properties
            }
            ViewerSourceScope.Normal -> {
                if (capabilities.canRename) actions += ViewerFileAction.Rename
                if (capabilities.canCopy) actions += ViewerFileAction.Copy
                if (capabilities.canMove) actions += ViewerFileAction.Cut
                if (capabilities.canDelete || capabilities.canTrash) actions += ViewerFileAction.Delete
                if (capabilities.canShare) actions += ViewerFileAction.Share
                if (capabilities.canOpenWith) actions += ViewerFileAction.OpenWith
                if (capabilities.canArchive && isMultiFileArchiveAvailable) actions += ViewerFileAction.CreateArchive
                if (capabilities.canRead) actions += ViewerFileAction.Properties
            }
        }

        return actions
    }
}
