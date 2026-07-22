package dev.qtremors.arcile.core.vault.data

import android.content.Context
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import dev.qtremors.arcile.core.vault.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal abstract class VaultSessionLayer(
    context: Context,
    dispatchers: ArcileDispatchers,
    applicationScope: CoroutineScope,
    portableLocationResolver: VaultPortableLocationResolver
) : VaultCatalogLayer(context, dispatchers, applicationScope, portableLocationResolver),
    VaultSessionManager {
    override suspend fun unlock(vaultId: VaultId, options: VaultUnlockOptions): Result<Unit> {
        val password = options.password ?: return Result.failure(VaultFailure.AuthenticationFailed())
        return unlock(vaultId, password)
    }

    override suspend fun lockInteractive(vaultId: VaultId) = lock(vaultId)
    override suspend fun lockAllInteractive() = lockAll()

    override suspend fun hasBiometricEnrollment(vaultId: VaultId): Boolean =
        withContext(dispatchers.io) { runCatching { biometricStore.hasEnrollment(vaultId) }.getOrDefault(false) }

    override fun acquireLease(vaultId: VaultId, purpose: VaultLeasePurpose): Result<VaultKeyLease> =
        holdSession(vaultId).map { VaultKeyLeaseImpl(vaultId, purpose, it) }

    override suspend fun changePassword(
        vaultId: VaultId,
        currentPassword: CharArray,
        newPassword: CharArray,
        weakPasswordConfirmed: Boolean
    ): Result<Unit> = withContext(dispatchers.io) {
        lifecycleMutex.withLock {
            try {
                VaultPasswordPolicy.requireAccepted(newPassword, weakPasswordConfirmed)
                val location = locations[vaultId.value] ?: throw VaultFailure.NotFound(vaultId)
                val fingerprint = headerCodec.changePassword(
                    location.access,
                    currentPassword,
                    newPassword
                ).getOrThrow()
                locationRegistry.find(vaultId)?.let { pointer ->
                    locationRegistry.put(pointer.copy(headerFingerprint = fingerprint, path = null))
                }
                refreshVaults()
                Result.success(Unit)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                Result.failure(error)
            } finally {
                currentPassword.fill('\u0000')
                newPassword.fill('\u0000')
            }
        }
    }

    override suspend fun prepareBiometricEnrollment(
        vaultId: VaultId,
        password: CharArray
    ): Result<VaultBiometricChallenge> {
        var masterSecret: ByteArray? = null
        return try {
            val location = locations[vaultId.value] ?: throw VaultFailure.NotFound(vaultId)
            val opened = headerCodec.open(location.access, password).getOrThrow()
            if (opened.id != vaultId) throw VaultFailure.StaleRegistration()
            masterSecret = opened.masterKey
            val cipher = biometricStore.prepareEnrollment(vaultId)
            val ownedSecret = masterSecret
            masterSecret = null
            Result.success(
                VaultBiometricChallengeImpl(
                    vaultId,
                    VaultBiometricPurpose.ENROLL,
                    biometricStore.cryptoObject(cipher),
                    ownedSecret
                ) {
                    biometricStore.finishEnrollment(vaultId, cipher, ownedSecret)
                }
            )
        } catch (error: Throwable) {
            Result.failure(error)
        } finally {
            masterSecret?.fill(0)
            password.fill('\u0000')
        }
    }

    override suspend fun prepareBiometricUnlock(vaultId: VaultId): Result<VaultBiometricChallenge> =
        try {
            if (sessions.containsKey(vaultId.value)) {
                throw VaultFailure.Unavailable("Vault is already unlocked")
            }
            val location = locations[vaultId.value] ?: throw VaultFailure.NotFound(vaultId)
            val cipher = biometricStore.prepareUnlock(vaultId)
            Result.success(
                VaultBiometricChallengeImpl(
                    vaultId,
                    VaultBiometricPurpose.UNLOCK,
                    biometricStore.cryptoObject(cipher)
                ) {
                    var secret: ByteArray? = null
                    try {
                        secret = biometricStore.finishUnlock(vaultId, cipher)
                        transactionManager.recover(location.access, vaultId, secret)
                        val candidate = VaultSessionRecord(vaultId, location.access, secret)
                        candidate.root().let { root ->
                            try {
                                candidate.readDirectory(root).clearProtectedKeys()
                            } finally {
                                root.key.fill(0)
                            }
                        }
                        synchronized(sessions) {
                            sessions.put(vaultId.value, candidate)?.destroy()
                        }
                        secret = null
                        publishUnlockedIds()
                        mutableVaults.value = mutableVaults.value.map {
                            if (it.id == vaultId) it.copy(isUnlocked = true) else it
                        }
                    } catch (error: Throwable) {
                        if (error is VaultFailure.BiometricInvalidated) biometricStore.remove(vaultId)
                        throw error
                    } finally {
                        secret?.fill(0)
                    }
                }
            )
        } catch (error: Throwable) {
            Result.failure(error)
        }

    override suspend fun removeBiometric(vaultId: VaultId): Result<Unit> =
        withContext(dispatchers.io) {
            runCatching {
                biometricStore.remove(vaultId)
                Unit
            }
        }

    override suspend fun unlock(vaultId: VaultId, password: CharArray): Result<Unit> =
        withContext(dispatchers.io) {
            lifecycleMutex.withLock {
                if (sessions.containsKey(vaultId.value)) {
                    password.fill('\u0000')
                    return@withLock Result.success(Unit)
                }
                val location = locations[vaultId.value] ?: run {
                    refreshVaults()
                    locations[vaultId.value]
                }
                if (location == null) {
                    password.fill('\u0000')
                    return@withLock Result.failure(VaultFailure.NotFound(vaultId))
                }
                var secret: ByteArray? = null
                try {
                    val opened = headerCodec.open(location.access, password).getOrThrow()
                    if (opened.id != vaultId) throw VaultFailure.StaleRegistration()
                    secret = opened.masterKey
                    transactionManager.recover(location.access, vaultId, secret)
                    val candidate = VaultSessionRecord(vaultId, location.access, secret)
                    candidate.root().let { root ->
                        try {
                            candidate.readDirectory(root).clearProtectedKeys()
                        } finally {
                            root.key.fill(0)
                        }
                    }
                    sessions[vaultId.value] = candidate
                    secret = null
                    refreshVaults()
                    Result.success(Unit)
                } catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    Result.failure(error)
                } finally {
                    secret?.fill(0)
                    password.fill('\u0000')
                }
            }
        }

    override suspend fun lock(vaultId: VaultId) = withContext(dispatchers.io) {
        lifecycleMutex.withLock {
            requestLock(vaultId)
            refreshVaults()
        }
    }

    override suspend fun lockAll() = withContext(dispatchers.io) {
        lifecycleMutex.withLock {
            sessions.values.toList().forEach { requestLock(it.id) }
            refreshVaults()
        }
    }

    override fun holdSession(vaultId: VaultId): Result<VaultSessionLease> = holdSessionRecord(vaultId)
}
