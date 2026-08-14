package dev.qtremors.arcile.presentation.ui

import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import dev.qtremors.arcile.navigation.AppRoutes

internal fun StorageNodeRef.toStorageDashboardRoute() = AppRoutes.StorageDashboard(
    scopePath = displayPath.absolutePath,
    scopeBackendId = backendId,
    scopeBackendIdentity = backendIdentity
)

internal fun StorageNodeRef.toStorageCleanerRoute() = AppRoutes.StorageCleaner(
    scopePath = displayPath.absolutePath,
    scopeBackendId = backendId,
    scopeBackendIdentity = backendIdentity
)
