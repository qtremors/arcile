package dev.qtremors.arcile.core.vault.crypto

import dev.qtremors.arcile.core.vault.domain.VaultFailure
import dev.qtremors.arcile.core.vault.domain.VaultId
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Base64

data class OpenedVaultManifest(
    val id: VaultId,
    val publicName: String,
    val createdAtMillis: Long,
    val masterKey: ByteArray
)

data class PublicVaultManifest(
    val id: VaultId,
    val publicName: String,
    val createdAtMillis: Long
)

class VaultManifestCodec(
    private val json: Json = Json { ignoreUnknownKeys = false; encodeDefaults = true }
) {
    fun create(
        vaultDirectory: File,
        id: VaultId,
        publicName: String,
        createdAtMillis: Long,
        password: CharArray,
        masterKey: ByteArray
    ) {
        require(masterKey.size == VaultCryptography.KEY_SIZE_BYTES)
        val kdf = VaultCryptography.defaultKdfParameters()
        val passwordKey = VaultCryptography.derivePasswordKey(password, kdf)
        val aad = manifestAssociatedData(
            id = id.value,
            format = FORMAT_VERSION,
            publicName = publicName,
            createdAtMillis = createdAtMillis,
            slotId = PRIMARY_PASSWORD_SLOT,
            kdf = kdf
        )
        val sealedMasterKey = try {
            VaultCryptography.seal(passwordKey, masterKey, aad)
        } finally {
            passwordKey.fill(0)
        }
        val document = VaultManifestDocument(
            format = FORMAT_VERSION,
            id = id.value,
            publicName = publicName,
            createdAtMillis = createdAtMillis,
            keySlots = listOf(
                VaultKeySlotDocument(
                    id = PRIMARY_PASSWORD_SLOT,
                    type = PASSWORD_SLOT_TYPE,
                    kdf = VaultKdfDocument(
                        salt = kdf.salt.toBase64(),
                        memoryKiB = kdf.memoryKiB,
                        iterations = kdf.iterations,
                        parallelism = kdf.parallelism
                    ),
                    envelope = VaultEnvelopeDocument(
                        nonce = sealedMasterKey.nonce.toBase64(),
                        ciphertext = sealedMasterKey.ciphertext.toBase64()
                    )
                )
            )
        )
        val bytes = json.encodeToString(document).toByteArray(StandardCharsets.UTF_8)
        vaultDirectory.mkdirs()
        atomicWrite(File(vaultDirectory, PRIMARY_FILE), bytes)
        atomicWrite(File(vaultDirectory, BACKUP_FILE), bytes)
    }

    fun readPublic(vaultDirectory: File): Result<PublicVaultManifest> = runCatching {
        val document = readDocument(vaultDirectory)
        PublicVaultManifest(
            id = VaultId.of(document.id),
            publicName = document.publicName,
            createdAtMillis = document.createdAtMillis
        )
    }

    fun open(vaultDirectory: File, password: CharArray): Result<OpenedVaultManifest> = runCatching {
        val document = readDocument(vaultDirectory)
        if (document.format != FORMAT_VERSION) {
            throw VaultFailure.IntegrityFailed("Unsupported OnlyFiles vault format ${document.format}")
        }
        val id = VaultId.of(document.id)
        val slot = document.keySlots.firstOrNull { it.type == PASSWORD_SLOT_TYPE }
            ?: throw VaultFailure.IntegrityFailed("Vault has no password key slot")
        val kdf = slot.kdf.toParameters()
        val passwordKey = VaultCryptography.derivePasswordKey(password, kdf)
        val masterKey = try {
            VaultCryptography.open(
                passwordKey,
                VaultSealedValue(
                    nonce = slot.envelope.nonce.fromBase64(),
                    ciphertext = slot.envelope.ciphertext.fromBase64()
                ),
                manifestAssociatedData(
                    id = document.id,
                    format = document.format,
                    publicName = document.publicName,
                    createdAtMillis = document.createdAtMillis,
                    slotId = slot.id,
                    kdf = kdf
                )
            )
        } catch (error: VaultAuthenticationException) {
            throw VaultFailure.AuthenticationFailed()
        } finally {
            passwordKey.fill(0)
        }
        OpenedVaultManifest(id, document.publicName, document.createdAtMillis, masterKey)
    }

    private fun readDocument(vaultDirectory: File): VaultManifestDocument {
        val candidates = listOf(PRIMARY_FILE, BACKUP_FILE).map { File(vaultDirectory, it) }
        var lastFailure: Throwable? = null
        candidates.forEach { file ->
            if (!file.isFile) return@forEach
            try {
                val document = json.decodeFromString<VaultManifestDocument>(file.readText(StandardCharsets.UTF_8))
                validateDocument(document)
                return document
            } catch (error: Throwable) {
                lastFailure = error
            }
        }
        throw VaultFailure.IntegrityFailed("Vault manifest is missing or damaged", lastFailure)
    }

    private fun validateDocument(document: VaultManifestDocument) {
        require(document.magic == MAGIC) { "Invalid vault manifest marker" }
        VaultId.of(document.id)
        require(document.publicName.isNotBlank()) { "Vault name is missing" }
        require(document.createdAtMillis >= 0L) { "Vault creation time is invalid" }
        require(document.keySlots.isNotEmpty() && document.keySlots.size <= MAX_KEY_SLOTS)
        require(document.keySlots.map { it.id }.toSet().size == document.keySlots.size)
        require(document.keySlots.count { it.type == PASSWORD_SLOT_TYPE } == 1)
        document.keySlots.forEach { slot ->
            require(slot.id.isNotBlank() && slot.type.isNotBlank())
            slot.kdf.toParameters()
            require(slot.envelope.nonce.fromBase64().size == VaultCryptography.NONCE_SIZE_BYTES)
            require(slot.envelope.ciphertext.fromBase64().size == VaultCryptography.KEY_SIZE_BYTES + 16)
        }
    }

    private fun VaultKdfDocument.toParameters(): VaultKdfParameters = VaultKdfParameters(
        salt = salt.fromBase64(),
        memoryKiB = memoryKiB,
        iterations = iterations,
        parallelism = parallelism
    )

    private fun manifestAssociatedData(
        id: String,
        format: Int,
        publicName: String,
        createdAtMillis: Long,
        slotId: String,
        kdf: VaultKdfParameters
    ): ByteArray =
        buildString {
            append(MAGIC).append('|')
            append(format).append('|')
            append(id).append('|')
            append(publicName).append('|')
            append(createdAtMillis).append('|')
            append(slotId).append('|')
            append(kdf.salt.toBase64()).append('|')
            append(kdf.memoryKiB).append('|')
            append(kdf.iterations).append('|')
            append(kdf.parallelism)
        }.toByteArray(StandardCharsets.UTF_8)

    companion object {
        const val PRIMARY_FILE = "vault.onlyfiles"
        const val BACKUP_FILE = "vault.onlyfiles.bak"
        const val FORMAT_VERSION = 1
        private const val MAGIC = "ONLYFILES"
        private const val PRIMARY_PASSWORD_SLOT = "password-primary"
        private const val PASSWORD_SLOT_TYPE = "password"
        private const val MAX_KEY_SLOTS = 8
    }
}

@Serializable
private data class VaultManifestDocument(
    val magic: String = "ONLYFILES",
    val format: Int,
    val id: String,
    val publicName: String,
    val createdAtMillis: Long,
    val keySlots: List<VaultKeySlotDocument>
)

@Serializable
private data class VaultKeySlotDocument(
    val id: String,
    val type: String,
    val kdf: VaultKdfDocument,
    val envelope: VaultEnvelopeDocument
)

@Serializable
private data class VaultKdfDocument(
    val salt: String,
    val memoryKiB: Int,
    val iterations: Int,
    val parallelism: Int
)

@Serializable
private data class VaultEnvelopeDocument(
    val nonce: String,
    val ciphertext: String
)

internal fun ByteArray.toBase64(): String = Base64.getEncoder().encodeToString(this)
internal fun String.fromBase64(): ByteArray = Base64.getDecoder().decode(this)

internal fun atomicWrite(target: File, bytes: ByteArray) {
    target.parentFile?.mkdirs()
    val temporary = File(target.parentFile, ".${target.name}.${System.nanoTime()}.tmp")
    try {
        java.io.FileOutputStream(temporary).use { fileOutput ->
            val output = fileOutput.buffered()
            output.write(bytes)
            output.flush()
            fileOutput.fd.sync()
        }
        try {
            Files.move(
                temporary.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    } finally {
        temporary.delete()
    }
}
