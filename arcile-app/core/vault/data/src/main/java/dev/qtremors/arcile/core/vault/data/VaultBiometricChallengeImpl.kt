package dev.qtremors.arcile.core.vault.data

import dev.qtremors.arcile.core.vault.domain.VaultBiometricChallenge
import dev.qtremors.arcile.core.vault.domain.VaultBiometricPurpose
import dev.qtremors.arcile.core.vault.domain.VaultFailure
import dev.qtremors.arcile.core.vault.domain.VaultId
import java.util.concurrent.atomic.AtomicBoolean

internal class VaultBiometricChallengeImpl(
    override val vaultId: VaultId,
    override val purpose: VaultBiometricPurpose,
    override val platformCryptoObject: Any,
    private val secretToClear: ByteArray? = null,
    private val completion: suspend () -> Unit
) : VaultBiometricChallenge {
    private val closed = AtomicBoolean(false)
    override val isClosed: Boolean get() = closed.get()

    override suspend fun completeAfterAuthentication(): Result<Unit> {
        if (!closed.compareAndSet(false, true)) return Result.failure(VaultFailure.BiometricInvalidated())
        return try {
            completion()
            Result.success(Unit)
        } catch (error: Throwable) {
            Result.failure(error)
        } finally {
            secretToClear?.fill(0)
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) secretToClear?.fill(0)
    }
}
