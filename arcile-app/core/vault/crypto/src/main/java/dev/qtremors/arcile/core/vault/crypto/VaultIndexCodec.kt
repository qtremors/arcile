package dev.qtremors.arcile.core.vault.crypto

import dev.qtremors.arcile.core.vault.domain.VaultFailure
import dev.qtremors.arcile.core.vault.domain.VaultId
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.charset.StandardCharsets

data class VaultIndexEntry(
    val id: String,
    val path: String,
    val objectName: String?,
    val sizeBytes: Long,
    val modifiedAtMillis: Long,
    val isDirectory: Boolean,
    val mimeType: String?
)

data class VaultIndex(
    val generation: Long,
    val vaultName: String,
    val entries: List<VaultIndexEntry>
)

class VaultIndexCodec(
    private val json: Json = Json { ignoreUnknownKeys = false; encodeDefaults = true }
) {
    fun create(vaultDirectory: File, vaultId: VaultId, vaultName: String, masterKey: ByteArray) {
        write(vaultDirectory, vaultId, masterKey, VaultIndex(0L, vaultName, emptyList()))
    }

    fun read(vaultDirectory: File, vaultId: VaultId, masterKey: ByteArray): VaultIndex {
        val valid = listOf(SLOT_A, SLOT_B).mapNotNull { name ->
            val file = File(vaultDirectory, name)
            if (!file.isFile) return@mapNotNull null
            runCatching { decode(file.readBytes(), vaultId, masterKey) }.getOrNull()
        }
        return valid.maxByOrNull(VaultIndex::generation)
            ?: throw VaultFailure.IntegrityFailed("Vault directory metadata is missing or damaged")
    }

    fun write(vaultDirectory: File, vaultId: VaultId, masterKey: ByteArray, index: VaultIndex) {
        require(index.generation >= 0L)
        require(index.vaultName.isNotBlank())
        val targetName = if (index.generation % 2L == 0L) SLOT_A else SLOT_B
        atomicWrite(File(vaultDirectory, targetName), encode(index, vaultId, masterKey))

        val verified = decode(File(vaultDirectory, targetName).readBytes(), vaultId, masterKey)
        if (verified != index) {
            throw VaultFailure.IntegrityFailed("Vault metadata verification failed after writing")
        }
    }

    private fun encode(index: VaultIndex, vaultId: VaultId, masterKey: ByteArray): ByteArray {
        val document = VaultIndexDocument(
            vaultName = index.vaultName,
            entries = index.entries.map(VaultIndexEntry::toDocument)
        )
        val plaintext = json.encodeToString(document).toByteArray(StandardCharsets.UTF_8)
        val sealed = VaultCryptography.seal(
            masterKey,
            plaintext,
            indexAssociatedData(vaultId, index.generation)
        )
        plaintext.fill(0)
        return ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.write(MAGIC)
                output.writeInt(FORMAT_VERSION)
                output.writeLong(index.generation)
                output.write(sealed.nonce)
                output.writeInt(sealed.ciphertext.size)
                output.write(sealed.ciphertext)
            }
            bytes.toByteArray()
        }
    }

    private fun decode(bytes: ByteArray, vaultId: VaultId, masterKey: ByteArray): VaultIndex {
        return try {
            DataInputStream(bytes.inputStream()).use { input ->
                val magic = ByteArray(MAGIC.size).also(input::readFully)
                require(magic.contentEquals(MAGIC)) { "Invalid vault metadata marker" }
                require(input.readInt() == FORMAT_VERSION) { "Unsupported vault metadata format" }
                val generation = input.readLong()
                require(generation >= 0L) { "Invalid vault metadata generation" }
                val nonce = ByteArray(VaultCryptography.NONCE_SIZE_BYTES).also(input::readFully)
                val ciphertextSize = input.readInt()
                require(ciphertextSize in 17..MAX_INDEX_BYTES) { "Invalid vault metadata size" }
                require(input.available() == ciphertextSize) { "Truncated or trailing vault metadata" }
                val ciphertext = ByteArray(ciphertextSize).also(input::readFully)
                val plaintext = VaultCryptography.open(
                    masterKey,
                    VaultSealedValue(nonce, ciphertext),
                    indexAssociatedData(vaultId, generation)
                )
                try {
                    val document = json.decodeFromString<VaultIndexDocument>(
                        plaintext.toString(StandardCharsets.UTF_8)
                    )
                    validate(document)
                    VaultIndex(
                        generation = generation,
                        vaultName = document.vaultName,
                        entries = document.entries.map(VaultIndexEntryDocument::toEntry)
                    )
                } finally {
                    plaintext.fill(0)
                }
            }
        } catch (error: VaultFailure) {
            throw error
        } catch (error: Throwable) {
            throw VaultFailure.IntegrityFailed("Vault directory metadata failed authentication", error)
        }
    }

    private fun validate(document: VaultIndexDocument) {
        require(document.vaultName.isNotBlank())
        val paths = mutableSetOf<String>()
        val ids = mutableSetOf<String>()
        document.entries.forEach { entry ->
            require(entry.id.isNotBlank())
            require(entry.path.isNotBlank())
            require(entry.sizeBytes >= 0L)
            require(entry.modifiedAtMillis >= 0L)
            require(ids.add(entry.id)) { "Duplicate vault entry id" }
            require(paths.add(entry.path)) { "Duplicate vault entry path" }
            if (entry.isDirectory) {
                require(entry.objectName == null)
                require(entry.sizeBytes == 0L)
            } else {
                require(!entry.objectName.isNullOrBlank())
            }
        }
    }

    private fun indexAssociatedData(vaultId: VaultId, generation: Long): ByteArray =
        "ONLYFILES|INDEX|$FORMAT_VERSION|${vaultId.value}|$generation".toByteArray(StandardCharsets.UTF_8)

    companion object {
        const val SLOT_A = "index.onlyfiles.a"
        const val SLOT_B = "index.onlyfiles.b"
        private val MAGIC = byteArrayOf('O'.code.toByte(), 'F'.code.toByte(), 'I'.code.toByte(), '1'.code.toByte())
        private const val FORMAT_VERSION = 1
        private const val MAX_INDEX_BYTES = 256 * 1024 * 1024
    }
}

@Serializable
private data class VaultIndexDocument(
    val vaultName: String,
    val entries: List<VaultIndexEntryDocument>
)

@Serializable
private data class VaultIndexEntryDocument(
    val id: String,
    val path: String,
    val objectName: String? = null,
    val sizeBytes: Long,
    val modifiedAtMillis: Long,
    val isDirectory: Boolean,
    val mimeType: String? = null
)

private fun VaultIndexEntry.toDocument() = VaultIndexEntryDocument(
    id = id,
    path = path,
    objectName = objectName,
    sizeBytes = sizeBytes,
    modifiedAtMillis = modifiedAtMillis,
    isDirectory = isDirectory,
    mimeType = mimeType
)

private fun VaultIndexEntryDocument.toEntry() = VaultIndexEntry(
    id = id,
    path = path,
    objectName = objectName,
    sizeBytes = sizeBytes,
    modifiedAtMillis = modifiedAtMillis,
    isDirectory = isDirectory,
    mimeType = mimeType
)
