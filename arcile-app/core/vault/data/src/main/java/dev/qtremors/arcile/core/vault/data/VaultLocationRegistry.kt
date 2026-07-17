package dev.qtremors.arcile.core.vault.data

import android.content.Context
import dev.qtremors.arcile.core.vault.crypto.VaultDirectoryAccess
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultLocation
import dev.qtremors.arcile.core.vault.domain.VaultLocationKind
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal data class VaultLocationRecord(
    val access: VaultDirectoryAccess,
    val kind: VaultLocationKind,
    val location: VaultLocation
)

@Serializable
internal data class ExternalVaultPointer(
    val vaultId: String,
    val volumeId: String = "",
    val relativePath: String = "",
    val cachedName: String,
    val cachedCreatedAtMillis: Long,
    val headerFingerprint: String = "",
    /** Read only for migrating pre-1.5.4 registrations. Never written by current code. */
    val path: String? = null
)

internal class VaultLocationRegistry(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun load(): List<ExternalVaultPointer> = preferences.getStringSet(KEY_POINTERS, emptySet())
        .orEmpty()
        .mapNotNull { runCatching { json.decodeFromString<ExternalVaultPointer>(it) }.getOrNull() }
        .distinctBy(ExternalVaultPointer::vaultId)

    fun put(pointer: ExternalVaultPointer) {
        val next = load().filterNot { it.vaultId == pointer.vaultId } + pointer
        preferences.edit().putStringSet(KEY_POINTERS, next.mapTo(mutableSetOf()) { json.encodeToString(it) }).apply()
    }

    fun find(vaultId: VaultId): ExternalVaultPointer? = load().firstOrNull { it.vaultId == vaultId.value }

    fun remove(vaultId: VaultId): Boolean {
        val current = load()
        val next = current.filterNot { it.vaultId == vaultId.value }
        if (next.size == current.size) return false
        preferences.edit().putStringSet(KEY_POINTERS, next.mapTo(mutableSetOf()) { json.encodeToString(it) }).commit()
        return true
    }

    private companion object {
        const val PREFERENCES = "onlyfiles_vault_locations"
        const val KEY_POINTERS = "external_vault_pointers"
    }
}
