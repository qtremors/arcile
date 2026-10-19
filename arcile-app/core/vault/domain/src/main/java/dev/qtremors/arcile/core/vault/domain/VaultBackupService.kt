package dev.qtremors.arcile.core.vault.domain

interface VaultBackupService {
    suspend fun createBackup(
        vaultId: VaultId,
        destinationPath: String,
        label: String,
        password: CharArray,
        weakPasswordConfirmed: Boolean
    ): Result<String>

    suspend fun restoreBackup(sourcePath: String, label: String, password: CharArray): Result<VaultId>
}
