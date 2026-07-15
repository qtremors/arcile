package dev.qtremors.arcile.core.vault.data

import dev.qtremors.arcile.core.vault.crypto.VaultIndex
import dev.qtremors.arcile.core.vault.domain.VaultId
import kotlinx.coroutines.sync.Mutex
import dev.qtremors.arcile.core.vault.crypto.VaultDirectoryAccess

internal class VaultSessionRecord(
    val id: VaultId,
    val directory: VaultDirectoryAccess,
    val masterKey: ByteArray,
    var index: VaultIndex
) {
    val mutationMutex = Mutex()
    var holdCount: Int = 0
    var lockRequested: Boolean = false

    fun destroy() {
        masterKey.fill(0)
    }
}
