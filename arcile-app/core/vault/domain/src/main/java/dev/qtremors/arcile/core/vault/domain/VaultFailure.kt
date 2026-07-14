package dev.qtremors.arcile.core.vault.domain

sealed class VaultFailure(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NameConflict(val vaultName: String) : VaultFailure("A vault or folder named $vaultName already exists")
    class InvalidName(message: String) : VaultFailure(message)
    class NotFound(val vaultId: VaultId) : VaultFailure("Vault is unavailable: ${vaultId.value}")
    class Locked(val vaultId: VaultId) : VaultFailure("Vault is locked: ${vaultId.value}")
    class AuthenticationFailed : VaultFailure("The password is incorrect")
    class IntegrityFailed(message: String, cause: Throwable? = null) : VaultFailure(message, cause)
    class PathConflict(val path: VaultPath) : VaultFailure("An item already exists at ${path.value}")
    class InvalidPath(message: String) : VaultFailure(message)
    class FileTooLarge(val sizeBytes: Long, val maximumBytes: Long) :
        VaultFailure("File size $sizeBytes exceeds the in-memory viewing limit $maximumBytes")
    class ImportUnavailable(message: String) : VaultFailure(message)
}
