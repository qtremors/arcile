package dev.qtremors.arcile.core.vault.data

import dev.qtremors.arcile.core.vault.crypto.PreparedVaultManifest
import dev.qtremors.arcile.core.vault.crypto.VaultCryptography
import dev.qtremors.arcile.core.vault.crypto.VaultDirectoryAccess
import dev.qtremors.arcile.core.vault.crypto.VaultDirectoryManifestCodec
import dev.qtremors.arcile.core.vault.crypto.VaultKeyDomain
import dev.qtremors.arcile.core.vault.crypto.VaultSealedValue
import dev.qtremors.arcile.core.vault.crypto.fromBase64
import dev.qtremors.arcile.core.vault.crypto.sha256
import dev.qtremors.arcile.core.vault.crypto.toBase64
import dev.qtremors.arcile.core.vault.domain.VaultFailure
import dev.qtremors.arcile.core.vault.domain.VaultId
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID

internal data class VaultPreparedDirectory(
    val manifest: PreparedVaultManifest,
    val directoryKey: ByteArray
)

internal enum class VaultTransactionStage {
    OBJECTS_SYNCED,
    STAGED_RESULT_AUTHENTICATED,
    COMMIT_MARKER_SYNCED,
    MANIFESTS_PUBLISHED,
    COMMIT_MARKER_REMOVED,
    OBSOLETE_OBJECTS_CLEANED
}

/** Serial transaction coordinator. A durable marker is the mutation linearization point. */
internal class VaultTransactionManager(
    private val manifestCodec: VaultDirectoryManifestCodec = VaultDirectoryManifestCodec(),
    private val json: Json = Json { ignoreUnknownKeys = false; encodeDefaults = true },
    private val stageObserver: (VaultTransactionStage) -> Unit = {}
) {
    fun commit(
        directory: VaultDirectoryAccess,
        vaultId: VaultId,
        masterSecret: ByteArray,
        preparedDirectories: List<VaultPreparedDirectory>,
        newObjectPaths: Set<String>,
        obsoletePaths: Set<String>
    ) {
        require(preparedDirectories.isNotEmpty())
        require(preparedDirectories.map { it.manifest.directoryId }.distinct().size == preparedDirectories.size)
        require(!directory.exists(COMMIT_MARKER)) { "A committed transaction must be recovered first" }

        var commitMarkerWritten = false
        try {
            // 1-2: metadata is prepared by the caller; immutable pages and content are synced first.
            preparedDirectories.forEach { prepared ->
                manifestCodec.stagePages(directory, prepared.manifest)
            }
            val allNewPaths = newObjectPaths + preparedDirectories.flatMap { prepared ->
                prepared.manifest.pages.map { it.relativePath }
            }
            stageObserver(VaultTransactionStage.OBJECTS_SYNCED)

            // 3: authenticate every staged manifest and fingerprint every immutable object.
            preparedDirectories.forEach { prepared ->
                manifestCodec.verifyPrepared(directory, vaultId, prepared.manifest, prepared.directoryKey)
            }
            val objectRecords = allNewPaths.sorted().map { path -> fingerprint(directory, path) }
            stageObserver(VaultTransactionStage.STAGED_RESULT_AUTHENTICATED)
            val marker = VaultCommitDocument(
                transactionId = UUID.randomUUID().toString(),
                vaultId = vaultId.value,
                publications = preparedDirectories.map { prepared ->
                    VaultPublicationDocument(
                        relativePath = prepared.manifest.rootRelativePath,
                        bytes = prepared.manifest.rootBytes.toBase64()
                    )
                },
                newObjects = objectRecords,
                obsoletePaths = obsoletePaths.sorted()
            )

            // 4: writing this marker commits the replacement generation.
            writeMarker(directory, vaultId, masterSecret, marker)
            commitMarkerWritten = true
            stageObserver(VaultTransactionStage.COMMIT_MARKER_SYNCED)
            val committed = readMarker(directory, vaultId, masterSecret)
            publishCommitted(directory, committed)
        } catch (error: Throwable) {
            if (!commitMarkerWritten) {
                preparedDirectories.flatMap { it.manifest.pages }.forEach { directory.delete(it.relativePath) }
                newObjectPaths.forEach(directory::delete)
            }
            throw error
        } finally {
            preparedDirectories.forEach { it.directoryKey.fill(0) }
        }
    }

    fun recover(directory: VaultDirectoryAccess, vaultId: VaultId, masterSecret: ByteArray): Boolean {
        if (!directory.exists(COMMIT_MARKER)) return false
        try {
            publishCommitted(directory, readMarker(directory, vaultId, masterSecret))
            return true
        } catch (error: Throwable) {
            throw VaultFailure.TransactionRecoveryFailed("Committed vault transaction could not be recovered", error)
        }
    }

    fun hasPendingCommit(directory: VaultDirectoryAccess): Boolean = directory.exists(COMMIT_MARKER)

    private fun publishCommitted(directory: VaultDirectoryAccess, marker: VaultCommitDocument) {
        marker.newObjects.forEach { expected ->
            val actual = fingerprint(directory, expected.relativePath)
            if (actual.length != expected.length || actual.sha256 != expected.sha256) {
                throw VaultFailure.TransactionRecoveryFailed("A staged encrypted object changed before publication")
            }
        }
        // 5: readers can now discover only complete authenticated roots.
        marker.publications.forEach { publication ->
            val bytes = publication.bytes.fromBase64(MAX_ROOT_BYTES, minimumSize = 1)
            directory.writeAtomic(publication.relativePath, bytes)
            require(MessageDigest.isEqual(bytes, directory.readBytes(publication.relativePath)))
        }
        stageObserver(VaultTransactionStage.MANIFESTS_PUBLISHED)
        // 6: the commit is fully published before cleanup becomes eligible.
        check(directory.delete(COMMIT_MARKER) || !directory.exists(COMMIT_MARKER)) {
            "Unable to clear the transaction marker"
        }
        stageObserver(VaultTransactionStage.COMMIT_MARKER_REMOVED)
        marker.obsoletePaths.forEach { path -> runCatching { directory.delete(path) } }
        stageObserver(VaultTransactionStage.OBSOLETE_OBJECTS_CLEANED)
    }

    private fun writeMarker(
        directory: VaultDirectoryAccess,
        vaultId: VaultId,
        masterSecret: ByteArray,
        document: VaultCommitDocument
    ) {
        val plaintext = json.encodeToString(document).toByteArray(StandardCharsets.UTF_8)
        require(plaintext.size in 1..MAX_MARKER_PLAINTEXT_BYTES)
        val transactionKey = VaultCryptography.deriveDomainKey(masterSecret, vaultId, VaultKeyDomain.TRANSACTION)
        val sealed = try {
            VaultCryptography.seal(transactionKey, plaintext, markerAssociatedData(vaultId))
        } finally {
            plaintext.fill(0)
            transactionKey.fill(0)
        }
        val encoded = ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.write(MAGIC)
                output.writeInt(FORMAT_VERSION)
                output.write(sealed.nonce)
                output.writeInt(sealed.ciphertext.size)
                output.write(sealed.ciphertext)
            }
            bytes.toByteArray()
        }
        directory.writeAtomic(COMMIT_MARKER, encoded)
    }

    private fun readMarker(
        directory: VaultDirectoryAccess,
        vaultId: VaultId,
        masterSecret: ByteArray
    ): VaultCommitDocument {
        val bytes = directory.readBytes(COMMIT_MARKER)
        require(bytes.size in MIN_MARKER_BYTES..MAX_MARKER_BYTES)
        try {
            DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                require(ByteArray(MAGIC.size).also(input::readFully).contentEquals(MAGIC))
                val format = input.readInt()
                if (format != FORMAT_VERSION) throw VaultFailure.UnsupportedFormat(format)
                val nonce = ByteArray(VaultCryptography.NONCE_SIZE_BYTES).also(input::readFully)
                val ciphertextSize = input.readInt()
                require(ciphertextSize in VaultCryptography.GCM_TAG_SIZE_BYTES..MAX_MARKER_BYTES)
                require(input.available() == ciphertextSize)
                val ciphertext = ByteArray(ciphertextSize).also(input::readFully)
                val transactionKey = VaultCryptography.deriveDomainKey(masterSecret, vaultId, VaultKeyDomain.TRANSACTION)
                val plaintext = try {
                    VaultCryptography.open(
                        transactionKey,
                        VaultSealedValue(nonce, ciphertext),
                        markerAssociatedData(vaultId)
                    )
                } finally {
                    transactionKey.fill(0)
                }
                try {
                    val document = json.decodeFromString<VaultCommitDocument>(plaintext.toString(StandardCharsets.UTF_8))
                    validate(document, vaultId)
                    return document
                } finally {
                    plaintext.fill(0)
                }
            }
        } catch (error: Throwable) {
            if (error is VaultFailure) throw error
            throw VaultFailure.IntegrityFailed("Transaction marker failed authentication", error)
        }
    }

    private fun validate(document: VaultCommitDocument, vaultId: VaultId) {
        require(document.transactionId.isNotBlank())
        require(document.vaultId == vaultId.value)
        require(document.publications.isNotEmpty() && document.publications.size <= MAX_PUBLICATIONS)
        require(document.publications.map { it.relativePath }.distinct().size == document.publications.size)
        document.publications.forEach { publication ->
            validateRelativePath(publication.relativePath)
            publication.bytes.fromBase64(MAX_ROOT_BYTES, minimumSize = 1)
        }
        require(document.newObjects.size <= MAX_OBJECTS_PER_TRANSACTION)
        require(document.newObjects.map { it.relativePath }.distinct().size == document.newObjects.size)
        document.newObjects.forEach { record ->
            validateRelativePath(record.relativePath)
            require(record.length >= 0L)
            record.sha256.fromBase64(32)
        }
        require(document.obsoletePaths.size <= MAX_OBJECTS_PER_TRANSACTION)
        document.obsoletePaths.forEach(::validateRelativePath)
    }

    private fun fingerprint(directory: VaultDirectoryAccess, relativePath: String): VaultObjectFingerprint {
        validateRelativePath(relativePath)
        val length = directory.openRandom(relativePath, writable = false).use { it.length }
        return VaultObjectFingerprint(relativePath, length, directory.sha256(relativePath).toBase64())
    }

    private fun validateRelativePath(path: String) {
        require(path.isNotBlank() && path.length <= 512 && '\u0000' !in path)
        require(path.replace('\\', '/').split('/').none { it.isBlank() || it == "." || it == ".." })
    }

    private fun markerAssociatedData(vaultId: VaultId) =
        "Arcile/OnlyFiles/v1/transaction/${vaultId.value}".toByteArray(StandardCharsets.UTF_8)

    companion object {
        const val COMMIT_MARKER = "transactions/commit.onlyfiles"
        private const val FORMAT_VERSION = 1
        private val MAGIC = "AOTXN001".toByteArray(StandardCharsets.US_ASCII)
        private const val MAX_MARKER_PLAINTEXT_BYTES = 32 * 1024 * 1024
        private const val MAX_MARKER_BYTES = MAX_MARKER_PLAINTEXT_BYTES + 1024
        private const val MIN_MARKER_BYTES = 8 + 4 + 12 + 4 + 16
        private const val MAX_ROOT_BYTES = 8 * 1024 * 1024
        private const val MAX_PUBLICATIONS = 32
        private const val MAX_OBJECTS_PER_TRANSACTION = 100_000
    }
}

@Serializable
private data class VaultCommitDocument(
    val transactionId: String,
    val vaultId: String,
    val publications: List<VaultPublicationDocument>,
    val newObjects: List<VaultObjectFingerprint>,
    val obsoletePaths: List<String>
)

@Serializable
private data class VaultPublicationDocument(val relativePath: String, val bytes: String)

@Serializable
private data class VaultObjectFingerprint(val relativePath: String, val length: Long, val sha256: String)
