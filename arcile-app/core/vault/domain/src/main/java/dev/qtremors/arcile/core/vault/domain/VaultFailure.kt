package dev.qtremors.arcile.core.vault.domain

sealed class VaultFailure(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NameConflict(val conflictingName: String) :
        VaultFailure("An item named $conflictingName already exists")
    class InvalidName(message: String, cause: Throwable? = null) : VaultFailure(message, cause)
    class NotFound(val vaultId: VaultId) : VaultFailure("Vault is unavailable: ${vaultId.value}")
    class NodeNotFound(val nodeId: NodeId) : VaultFailure("Vault item is unavailable: ${nodeId.value}")
    class Unavailable(message: String = "Vault storage is unavailable", cause: Throwable? = null) :
        VaultFailure(message, cause)
    class RemovableStorageMissing(val volumeId: String) :
        VaultFailure("The storage volume $volumeId is not connected")
    class Locked(val vaultId: VaultId) : VaultFailure("Vault is locked: ${vaultId.value}")
    class AuthenticationFailed : VaultFailure("The password is incorrect")
    class WeakPasswordConfirmationRequired :
        VaultFailure("This password is weak and requires explicit confirmation")
    class BiometricInvalidated : VaultFailure("Biometric unlock was invalidated; use the password")
    class IntegrityFailed(message: String, cause: Throwable? = null) : VaultFailure(message, cause)
    class UnsupportedFormat(val version: Int) : VaultFailure("Unsupported OnlyFiles format $version")
    class UnsafeKdfParameters(message: String) : VaultFailure(message)
    class PathConflict(val path: VaultPath) : VaultFailure("An item already exists at ${path.value}")
    class InvalidPath(message: String, cause: Throwable? = null) : VaultFailure(message, cause)
    class StaleRegistration : VaultFailure("The registered folder belongs to a different vault")
    class DuplicateVault(val vaultId: VaultId) : VaultFailure("Vault ${vaultId.value} is already registered")
    class FolderNotEmpty : VaultFailure("A portable vault can only be created in an empty folder")
    class SymlinkRejected(val displayName: String) : VaultFailure("Symbolic link $displayName was skipped")
    class InsufficientSpace(val requiredBytes: Long, val availableBytes: Long) :
        VaultFailure("Not enough space: $requiredBytes bytes required, $availableBytes available")
    class Cancelled : VaultFailure("The operation was cancelled")
    class SourceChanged : VaultFailure("A source changed while it was being read")
    class ObjectReplaced(val objectId: VaultObjectId) :
        VaultFailure("Encrypted object ${objectId.value} changed unexpectedly")
    class ConcurrentMutation : VaultFailure("The vault changed while the operation was being prepared")
    class TransactionRecoveryFailed(message: String, cause: Throwable? = null) :
        VaultFailure(message, cause)
    class FileTooLarge(val sizeBytes: Long, val maximumBytes: Long) :
        VaultFailure("File size $sizeBytes exceeds the in-memory viewing limit $maximumBytes")
    class ImportUnavailable(message: String, cause: Throwable? = null) : VaultFailure(message, cause)
    class ExternalGrantExpired : VaultFailure("External access has expired")
    class DestructiveConfirmationRequired : VaultFailure("Vault deletion confirmation does not match")
    class OperationInProgress : VaultFailure("A vault operation is still in progress")
}
