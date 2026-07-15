package dev.qtremors.arcile.core.vault.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.io.Closeable

interface VaultSessionLease : Closeable

interface VaultRepository {
    val vaults: StateFlow<List<VaultSummary>>
    val unlockedVaultIds: StateFlow<Set<VaultId>>

    suspend fun refreshVaults()
    suspend fun createAppPrivateVault(name: String, password: CharArray): Result<VaultId>
    suspend fun createUserFolderVault(path: String, name: String, password: CharArray): Result<VaultId>
    suspend fun attachExistingVault(path: String): Result<VaultId>
    suspend fun unlock(vaultId: VaultId, password: CharArray): Result<Unit>
    suspend fun lock(vaultId: VaultId)
    suspend fun lockAll()
    fun holdSession(vaultId: VaultId): Result<VaultSessionLease>

    suspend fun list(vaultId: VaultId, directory: VaultPath = VaultPath.Root): Result<List<VaultNode>>
    suspend fun createDirectory(vaultId: VaultId, parent: VaultPath, name: String): Result<VaultNode>
    suspend fun rename(vaultId: VaultId, path: VaultPath, newName: String): Result<VaultNode>
    suspend fun delete(vaultId: VaultId, path: VaultPath): Result<Unit>
    suspend fun readBytes(vaultId: VaultId, path: VaultPath, maximumBytes: Long): Result<ByteArray>
    fun openReader(vaultId: VaultId, path: VaultPath): Result<VaultSeekableReader>
}

interface VaultImportCoordinator {
    val activeImports: StateFlow<Map<VaultId, VaultImportState>>
    val events: Flow<VaultImportEvent>

    fun startImport(
        vaultId: VaultId,
        destination: VaultPath,
        sourceUris: List<String>,
        selectionLease: VaultSessionLease? = null
    ): Boolean
    fun cancelImport(vaultId: VaultId)
}
