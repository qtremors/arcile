package dev.qtremors.arcile.core.vault.crypto

import dev.qtremors.arcile.core.vault.domain.VaultFailure
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

data class OpenedVaultManifest(
    val id: VaultId,
    val publicName: String,
    val createdAtMillis: Long,
    val masterKey: ByteArray,
    val headerFingerprint: String = ""
)

data class PublicVaultManifest(
    val id: VaultId,
    val publicName: String,
    val createdAtMillis: Long,
    val headerFingerprint: String = ""
)

/**
 * Arcile OnlyFiles public header codec. Both header files contain the same canonical bytes.
 * The password envelope authenticates the complete public identity. A durable commit record
 * makes password-envelope replacement recoverable and atomic across the two public copies.
 */
class VaultManifestCodec(
    private val json: Json = Json {
        ignoreUnknownKeys = false
        encodeDefaults = true
        explicitNulls = false
    }
) {
    fun create(
        vaultDirectory: java.io.File,
        id: VaultId,
        publicName: String,
        createdAtMillis: Long,
        password: CharArray,
        masterKey: ByteArray
    ) = create(FileVaultDirectory(vaultDirectory), id, publicName, createdAtMillis, password, masterKey)

    fun create(
        vaultDirectory: VaultDirectoryAccess,
        id: VaultId,
        publicName: String,
        createdAtMillis: Long,
        password: CharArray,
        masterKey: ByteArray
    ) {
        require(masterKey.size == VaultCryptography.KEY_SIZE_BYTES)
        require(password.isNotEmpty())
        val label = VaultName.of(publicName).value
        require(createdAtMillis >= 0L)
        require(!vaultDirectory.exists(PRIMARY_FILE) && !vaultDirectory.exists(BACKUP_FILE)) {
            "Vault header already exists"
        }
        val document = createDocument(id, label, createdAtMillis, password, masterKey)
        val canonical = encode(document)
        vaultDirectory.writeAtomic(PRIMARY_FILE, canonical)
        vaultDirectory.writeAtomic(BACKUP_FILE, canonical)
        verifyCopies(vaultDirectory, canonical)
    }

    fun readPublic(vaultDirectory: java.io.File): Result<PublicVaultManifest> =
        readPublic(FileVaultDirectory(vaultDirectory))

    fun readPublic(vaultDirectory: VaultDirectoryAccess): Result<PublicVaultManifest> = runCatching {
        recoverCommittedPasswordChange(vaultDirectory)
        val candidate = readBestDocument(vaultDirectory)
        PublicVaultManifest(
            id = VaultId.of(candidate.document.id),
            publicName = candidate.document.publicName,
            createdAtMillis = candidate.document.createdAtMillis,
            headerFingerprint = fingerprint(candidate.canonical)
        )
    }.mapFailureToIntegrity("Vault public header is missing or damaged")

    fun open(vaultDirectory: java.io.File, password: CharArray): Result<OpenedVaultManifest> =
        open(FileVaultDirectory(vaultDirectory), password)

    fun open(vaultDirectory: VaultDirectoryAccess, password: CharArray): Result<OpenedVaultManifest> = runCatching {
        require(password.isNotEmpty())
        recoverCommittedPasswordChange(vaultDirectory)
        val candidates = readCandidates(vaultDirectory)
        if (candidates.isEmpty()) throw VaultFailure.IntegrityFailed("Vault public headers are missing or damaged")

        var authenticationFailure: Throwable? = null
        candidates.forEach { candidate ->
            try {
                val opened = openDocument(candidate.document, password)
                return@runCatching OpenedVaultManifest(
                    id = VaultId.of(candidate.document.id),
                    publicName = candidate.document.publicName,
                    createdAtMillis = candidate.document.createdAtMillis,
                    masterKey = opened,
                    headerFingerprint = fingerprint(candidate.canonical)
                )
            } catch (error: VaultFailure.AuthenticationFailed) {
                authenticationFailure = error
            }
        }
        throw authenticationFailure ?: VaultFailure.AuthenticationFailed()
    }.mapFailureToIntegrity("Vault public header could not be opened", preserveAuthentication = true)

    fun changePassword(
        vaultDirectory: VaultDirectoryAccess,
        currentPassword: CharArray,
        newPassword: CharArray
    ): Result<String> = runCatching {
        require(newPassword.isNotEmpty())
        recoverCommittedPasswordChange(vaultDirectory)
        val current = readBestDocument(vaultDirectory)
        val masterSecret = openDocument(current.document, currentPassword)
        try {
            val replacement = createDocument(
                id = VaultId.of(current.document.id),
                publicName = current.document.publicName,
                createdAtMillis = current.document.createdAtMillis,
                password = newPassword,
                masterKey = masterSecret
            )
            val replacementBytes = encode(replacement)
            val commit = HeaderCommitDocument(
                magic = COMMIT_MAGIC,
                format = FORMAT_VERSION,
                headerSha256 = VaultCryptography.sha256(replacementBytes).toBase64(),
                header = replacementBytes.toBase64()
            )
            // The commit record is the linearization point. Recovery always publishes it.
            vaultDirectory.writeAtomic(COMMIT_FILE, json.encodeToString(commit).toByteArray(StandardCharsets.UTF_8))
            publishCommittedHeader(vaultDirectory, replacementBytes)
            fingerprint(replacementBytes)
        } finally {
            masterSecret.fill(0)
        }
    }.mapFailureToIntegrity("Vault password change failed", preserveAuthentication = true)

    fun recoverCommittedPasswordChange(vaultDirectory: VaultDirectoryAccess) {
        if (!vaultDirectory.exists(COMMIT_FILE)) return
        val commitBytes = vaultDirectory.readBytes(COMMIT_FILE)
        require(commitBytes.size in 1..MAX_COMMIT_BYTES) { "Invalid header commit size" }
        val commit = json.decodeFromString<HeaderCommitDocument>(commitBytes.toString(StandardCharsets.UTF_8))
        require(commit.magic == COMMIT_MAGIC && commit.format == FORMAT_VERSION)
        val header = commit.header.fromBase64(MAX_HEADER_BYTES)
        val expectedHash = commit.headerSha256.fromBase64(VaultCryptography.KEY_SIZE_BYTES)
        require(MessageDigest.isEqual(expectedHash, VaultCryptography.sha256(header))) {
            "Header commit authentication failed"
        }
        decode(header)
        publishCommittedHeader(vaultDirectory, header)
    }

    private fun publishCommittedHeader(directory: VaultDirectoryAccess, canonical: ByteArray) {
        directory.writeAtomic(PRIMARY_FILE, canonical)
        directory.writeAtomic(BACKUP_FILE, canonical)
        verifyCopies(directory, canonical)
        directory.delete(COMMIT_FILE)
    }

    private fun verifyCopies(directory: VaultDirectoryAccess, expected: ByteArray) {
        listOf(PRIMARY_FILE, BACKUP_FILE).forEach { name ->
            val actual = directory.readBytes(name)
            require(MessageDigest.isEqual(expected, actual)) { "Header copy verification failed" }
            decode(actual)
        }
    }

    private fun createDocument(
        id: VaultId,
        publicName: String,
        createdAtMillis: Long,
        password: CharArray,
        masterKey: ByteArray
    ): VaultManifestDocument {
        val kdf = VaultCryptography.defaultKdfParameters()
        val identity = PublicIdentity(FORMAT_VERSION, id.value, publicName, createdAtMillis)
        val passwordKey = VaultCryptography.derivePasswordKey(password, kdf)
        val envelope = try {
            VaultCryptography.seal(passwordKey, masterKey, envelopeAssociatedData(identity, PASSWORD_SLOT_ID, kdf))
        } finally {
            passwordKey.fill(0)
        }
        return VaultManifestDocument(
            format = identity.format,
            id = identity.id,
            publicName = identity.publicName,
            createdAtMillis = identity.createdAtMillis,
            keySlots = listOf(
                VaultKeySlotDocument(
                    id = PASSWORD_SLOT_ID,
                    type = PASSWORD_SLOT_TYPE,
                    kdf = VaultKdfDocument.from(kdf),
                    envelope = VaultEnvelopeDocument(envelope.nonce.toBase64(), envelope.ciphertext.toBase64())
                )
            )
        )
    }

    private fun openDocument(document: VaultManifestDocument, password: CharArray): ByteArray {
        val identity = document.identity()
        val passwordSlot = document.keySlots.single { it.type == PASSWORD_SLOT_TYPE }
        val parameters = passwordSlot.kdf.toParameters()
        VaultKdfPolicy.validate(parameters)
        val passwordKey = VaultCryptography.derivePasswordKey(password, parameters)
        return try {
            val masterSecret = VaultCryptography.open(
                passwordKey,
                VaultSealedValue(
                    passwordSlot.envelope.nonce.fromBase64(VaultCryptography.NONCE_SIZE_BYTES),
                    passwordSlot.envelope.ciphertext.fromBase64(VaultCryptography.KEY_SIZE_BYTES + VaultCryptography.GCM_TAG_SIZE_BYTES)
                ),
                envelopeAssociatedData(identity, passwordSlot.id, parameters)
            )
            require(masterSecret.size == VaultCryptography.KEY_SIZE_BYTES)
            masterSecret
        } catch (error: VaultAuthenticationException) {
            throw VaultFailure.AuthenticationFailed()
        } finally {
            passwordKey.fill(0)
        }
    }

    private fun readBestDocument(directory: VaultDirectoryAccess): HeaderCandidate =
        readCandidates(directory).firstOrNull()
            ?: throw VaultFailure.IntegrityFailed("Both public header copies are missing or damaged")

    private fun readCandidates(directory: VaultDirectoryAccess): List<HeaderCandidate> {
        val failures = mutableListOf<Throwable>()
        val candidates = listOf(PRIMARY_FILE, BACKUP_FILE).mapNotNull { name ->
            if (!directory.exists(name)) null else try {
                val bytes = directory.readBytes(name)
                val document = decode(bytes)
                HeaderCandidate(document, encode(document))
            } catch (error: Throwable) {
                failures += error
                null
            }
        }
        if (candidates.isEmpty()) {
            failures.firstOrNull { it is VaultFailure.UnsafeKdfParameters || it is VaultFailure.UnsupportedFormat }
                ?.let { throw it }
        }
        if (candidates.size == 2) {
            val firstIdentity = candidates[0].document.identity()
            val secondIdentity = candidates[1].document.identity()
            if (firstIdentity != secondIdentity) {
                throw VaultFailure.IntegrityFailed("Public header copies identify different vaults")
            }
        }
        return candidates.distinctBy { fingerprint(it.canonical) }
    }

    private fun encode(document: VaultManifestDocument): ByteArray =
        json.encodeToString(document).toByteArray(StandardCharsets.UTF_8).also {
            require(it.size in 1..MAX_HEADER_BYTES)
        }

    private fun decode(bytes: ByteArray): VaultManifestDocument {
        require(bytes.size in 1..MAX_HEADER_BYTES) { "Invalid vault header size" }
        val document = json.decodeFromString<VaultManifestDocument>(bytes.toString(StandardCharsets.UTF_8))
        validate(document)
        return document
    }

    private fun validate(document: VaultManifestDocument) {
        require(document.magic == MAGIC)
        if (document.format != FORMAT_VERSION) throw VaultFailure.UnsupportedFormat(document.format)
        VaultId.of(document.id)
        require(VaultName.of(document.publicName).value == document.publicName) { "Vault label is not NFC" }
        require(document.createdAtMillis >= 0L)
        require(document.keySlots.size in 1..MAX_KEY_SLOTS)
        require(document.keySlots.map { it.id }.toSet().size == document.keySlots.size)
        require(document.keySlots.count { it.type == PASSWORD_SLOT_TYPE } == 1)
        document.keySlots.forEach { slot ->
            require(slot.id.isNotBlank() && slot.id.length <= 64)
            require(slot.type == PASSWORD_SLOT_TYPE || slot.type == BIOMETRIC_SLOT_TYPE)
            val parameters = slot.kdf.toParameters()
            VaultKdfPolicy.validate(parameters)
            slot.envelope.nonce.fromBase64(VaultCryptography.NONCE_SIZE_BYTES)
            slot.envelope.ciphertext.fromBase64(VaultCryptography.KEY_SIZE_BYTES + VaultCryptography.GCM_TAG_SIZE_BYTES)
        }
    }

    private fun envelopeAssociatedData(
        identity: PublicIdentity,
        slotId: String,
        kdf: VaultKdfParameters
    ): ByteArray {
        val label = identity.publicName.toByteArray(StandardCharsets.UTF_8)
        val id = identity.id.toByteArray(StandardCharsets.UTF_8)
        val slot = slotId.toByteArray(StandardCharsets.UTF_8)
        return ByteBuffer.allocate(
            MAGIC_BYTES.size + Int.SIZE_BYTES * 9 + Long.SIZE_BYTES + id.size + label.size + slot.size +
                kdf.salt.size + kdf.algorithm.length
        ).apply {
            put(MAGIC_BYTES)
            putInt(identity.format)
            putInt(id.size); put(id)
            putInt(label.size); put(label)
            putLong(identity.createdAtMillis)
            putInt(slot.size); put(slot)
            putInt(kdf.salt.size); put(kdf.salt)
            putInt(kdf.memoryKiB)
            putInt(kdf.iterations)
            putInt(kdf.parallelism)
            putInt(kdf.version)
            put(kdf.algorithm.toByteArray(StandardCharsets.US_ASCII))
        }.array()
    }

    private fun fingerprint(bytes: ByteArray): String = VaultCryptography.sha256(bytes).toBase64()

    companion object {
        const val PRIMARY_FILE = "vault.onlyfiles"
        const val BACKUP_FILE = "vault.onlyfiles.bak"
        const val COMMIT_FILE = ".header.commit"
        const val FORMAT_VERSION = 1
        private const val MAGIC = "ARCILE_ONLYFILES"
        private val MAGIC_BYTES = MAGIC.toByteArray(StandardCharsets.US_ASCII)
        private const val COMMIT_MAGIC = "ARCILE_ONLYFILES_HEADER_COMMIT"
        private const val PASSWORD_SLOT_ID = "password-primary"
        private const val PASSWORD_SLOT_TYPE = "password"
        private const val BIOMETRIC_SLOT_TYPE = "biometric"
        private const val MAX_KEY_SLOTS = 4
        private const val MAX_HEADER_BYTES = 64 * 1024
        private const val MAX_COMMIT_BYTES = 128 * 1024
    }
}

private data class HeaderCandidate(val document: VaultManifestDocument, val canonical: ByteArray)
private data class PublicIdentity(val format: Int, val id: String, val publicName: String, val createdAtMillis: Long)

@Serializable
private data class VaultManifestDocument(
    val magic: String = "ARCILE_ONLYFILES",
    val format: Int,
    val id: String,
    val publicName: String,
    val createdAtMillis: Long,
    val keySlots: List<VaultKeySlotDocument>
) {
    fun identity() = PublicIdentity(format, id, publicName, createdAtMillis)
}

@Serializable
private data class VaultKeySlotDocument(
    val id: String,
    val type: String,
    val kdf: VaultKdfDocument,
    val envelope: VaultEnvelopeDocument
)

@Serializable
private data class VaultKdfDocument(
    val algorithm: String,
    val version: Int,
    val salt: String,
    val memoryKiB: Int,
    val iterations: Int,
    val parallelism: Int
) {
    fun toParameters() = VaultKdfParameters(
        salt = salt.fromBase64(VaultKdfPolicy.MAX_SALT_BYTES, minimumSize = VaultKdfPolicy.MIN_SALT_BYTES),
        memoryKiB = memoryKiB,
        iterations = iterations,
        parallelism = parallelism,
        algorithm = algorithm,
        version = version
    )

    companion object {
        fun from(value: VaultKdfParameters) = VaultKdfDocument(
            algorithm = value.algorithm,
            version = value.version,
            salt = value.salt.toBase64(),
            memoryKiB = value.memoryKiB,
            iterations = value.iterations,
            parallelism = value.parallelism
        )
    }
}

@Serializable
private data class VaultEnvelopeDocument(val nonce: String, val ciphertext: String)

@Serializable
private data class HeaderCommitDocument(
    val magic: String,
    val format: Int,
    val headerSha256: String,
    val header: String
)

fun ByteArray.toBase64(): String = Base64.getEncoder().encodeToString(this)

fun String.fromBase64(maximumSize: Int, minimumSize: Int = maximumSize): ByteArray {
    require(length <= ((maximumSize + 2) / 3) * 4 + 4) { "Encoded value is too large" }
    return try {
        Base64.getDecoder().decode(this).also { require(it.size in minimumSize..maximumSize) }
    } catch (error: IllegalArgumentException) {
        throw VaultFailure.IntegrityFailed("Invalid base64 data in vault header", error)
    }
}

private fun <T> Result<T>.mapFailureToIntegrity(
    message: String,
    preserveAuthentication: Boolean = false
): Result<T> = fold(
    onSuccess = { Result.success(it) },
    onFailure = { error ->
        when {
            error is VaultFailure.IntegrityFailed -> Result.failure(error)
            error is VaultFailure.UnsupportedFormat -> Result.failure(error)
            error is VaultFailure.UnsafeKdfParameters -> Result.failure(error)
            preserveAuthentication && error is VaultFailure.AuthenticationFailed -> Result.failure(error)
            else -> Result.failure(VaultFailure.IntegrityFailed(message, error))
        }
    }
)
