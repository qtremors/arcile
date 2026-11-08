package dev.qtremors.arcile.core.vault.data

import dev.qtremors.arcile.core.vault.crypto.VaultEncryptedScratchChannel
import java.io.File

/** One repository owns this dedicated cache directory. Cleanup precedes its first operation. */
internal class VaultProtectedScratchSpace(private val directory: File) {
    private var initialized = false

    @Synchronized
    fun open(maximumBytes: Long, checkActive: () -> Unit): VaultEncryptedScratchChannel {
        if (!initialized) {
            VaultEncryptedScratchChannel.discardAbandoned(directory)
            initialized = true
        }
        return VaultEncryptedScratchChannel.create(directory, maximumBytes, checkActive)
    }
}
