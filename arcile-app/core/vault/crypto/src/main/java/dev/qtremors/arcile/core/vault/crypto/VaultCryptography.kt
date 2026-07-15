package dev.qtremors.arcile.core.vault.crypto

import dev.qtremors.arcile.core.vault.domain.VaultFailure
import dev.qtremors.arcile.core.vault.domain.VaultId
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class VaultKdfParameters(
    val salt: ByteArray,
    val memoryKiB: Int,
    val iterations: Int,
    val parallelism: Int,
    val algorithm: String = VaultKdfPolicy.ARGON2_ID,
    val version: Int = VaultKdfPolicy.ARGON2_VERSION
) {
    override fun equals(other: Any?): Boolean = other is VaultKdfParameters &&
        salt.contentEquals(other.salt) && memoryKiB == other.memoryKiB &&
        iterations == other.iterations && parallelism == other.parallelism &&
        algorithm == other.algorithm && version == other.version

    override fun hashCode(): Int = arrayOf(
        salt.contentHashCode(), memoryKiB, iterations, parallelism, algorithm, version
    ).contentHashCode()

    fun copyDefensively(): VaultKdfParameters = copy(salt = salt.copyOf())
}

object VaultKdfPolicy {
    const val ARGON2_ID = "argon2id"
    const val ARGON2_VERSION = 0x13
    const val DEFAULT_MEMORY_KIB = 64 * 1024
    const val DEFAULT_ITERATIONS = 3
    const val DEFAULT_PARALLELISM = 1
    const val MIN_MEMORY_KIB = 32 * 1024
    const val MAX_MEMORY_KIB = 128 * 1024
    const val MIN_ITERATIONS = 1
    const val MAX_ITERATIONS = 10
    const val MIN_PARALLELISM = 1
    const val MAX_PARALLELISM = 4
    const val MIN_SALT_BYTES = 16
    const val MAX_SALT_BYTES = 64

    fun validate(parameters: VaultKdfParameters): VaultKdfParameters {
        fun reject(message: String): Nothing = throw VaultFailure.UnsafeKdfParameters(message)
        if (parameters.algorithm != ARGON2_ID) reject("Unsupported password derivation algorithm")
        if (parameters.version != ARGON2_VERSION) reject("Unsupported Argon2 version")
        if (parameters.salt.size !in MIN_SALT_BYTES..MAX_SALT_BYTES) reject("Invalid Argon2 salt size")
        if (parameters.memoryKiB !in MIN_MEMORY_KIB..MAX_MEMORY_KIB) reject("Unsafe Argon2 memory cost")
        if (parameters.iterations !in MIN_ITERATIONS..MAX_ITERATIONS) reject("Unsafe Argon2 iteration count")
        if (parameters.parallelism !in MIN_PARALLELISM..MAX_PARALLELISM) reject("Unsafe Argon2 parallelism")
        return parameters
    }
}

enum class VaultKeyDomain(val label: String) {
    ROOT_DIRECTORY("root-directory-key"),
    HEADER_AUTHENTICATION("public-header-authentication"),
    TRANSACTION("transaction-journal"),
    THUMBNAILS("encrypted-thumbnail-cache"),
    BIOMETRIC_ENVELOPE("biometric-convenience-envelope")
}

data class VaultSealedValue(val nonce: ByteArray, val ciphertext: ByteArray) {
    override fun equals(other: Any?): Boolean = other is VaultSealedValue &&
        nonce.contentEquals(other.nonce) && ciphertext.contentEquals(other.ciphertext)
    override fun hashCode(): Int = 31 * nonce.contentHashCode() + ciphertext.contentHashCode()
}

class VaultAuthenticationException(cause: Throwable? = null) : Exception("Authentication failed", cause)

object VaultCryptography {
    const val KEY_SIZE_BYTES = 32
    const val NONCE_SIZE_BYTES = 12
    const val GCM_TAG_SIZE_BYTES = 16
    private const val TAG_SIZE_BITS = GCM_TAG_SIZE_BYTES * 8
    private val random = SecureRandom()

    fun randomBytes(size: Int): ByteArray {
        require(size >= 0)
        return ByteArray(size).also(random::nextBytes)
    }

    fun defaultKdfParameters(): VaultKdfParameters = VaultKdfParameters(
        salt = randomBytes(16),
        memoryKiB = VaultKdfPolicy.DEFAULT_MEMORY_KIB,
        iterations = VaultKdfPolicy.DEFAULT_ITERATIONS,
        parallelism = VaultKdfPolicy.DEFAULT_PARALLELISM
    )

    fun derivePasswordKey(password: CharArray, parameters: VaultKdfParameters): ByteArray {
        require(password.isNotEmpty()) { "Vault password must not be empty" }
        VaultKdfPolicy.validate(parameters)
        val encoded = StandardCharsets.UTF_8.encode(CharBuffer.wrap(password))
        val passwordBytes = ByteArray(encoded.remaining()).also(encoded::get)
        if (encoded.hasArray()) encoded.array().fill(0)
        return try {
            val generator = Argon2BytesGenerator()
            generator.init(
                Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                    .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                    .withSalt(parameters.salt)
                    .withMemoryAsKB(parameters.memoryKiB)
                    .withIterations(parameters.iterations)
                    .withParallelism(parameters.parallelism)
                    .build()
            )
            ByteArray(KEY_SIZE_BYTES).also { generator.generateBytes(passwordBytes, it) }
        } finally {
            passwordBytes.fill(0)
        }
    }

    /** RFC 5869 HKDF-SHA-256 with a fixed-size output suitable for AES-256 keys. */
    fun deriveDomainKey(masterSecret: ByteArray, vaultId: VaultId, domain: VaultKeyDomain): ByteArray {
        require(masterSecret.size == KEY_SIZE_BYTES)
        val salt = MessageDigest.getInstance("SHA-256").digest(
            "Arcile OnlyFiles vault ${vaultId.value}".toByteArray(StandardCharsets.UTF_8)
        )
        val info = "Arcile/OnlyFiles/v1/${domain.label}".toByteArray(StandardCharsets.UTF_8)
        return hkdfSha256(masterSecret, salt, info, KEY_SIZE_BYTES).also { salt.fill(0) }
    }

    fun hkdfSha256(inputKey: ByteArray, salt: ByteArray, info: ByteArray, outputSize: Int): ByteArray {
        require(outputSize in 1..(255 * KEY_SIZE_BYTES))
        val mac = Mac.getInstance("HmacSHA256")
        val actualSalt = if (salt.isEmpty()) ByteArray(KEY_SIZE_BYTES) else salt
        mac.init(SecretKeySpec(actualSalt, "HmacSHA256"))
        val pseudoRandomKey = mac.doFinal(inputKey)
        val output = ByteArray(outputSize)
        var previous = ByteArray(0)
        var outputOffset = 0
        var counter = 1
        try {
            while (outputOffset < output.size) {
                mac.init(SecretKeySpec(pseudoRandomKey, "HmacSHA256"))
                mac.update(previous)
                mac.update(info)
                mac.update(counter.toByte())
                val block = mac.doFinal()
                previous.fill(0)
                previous = block
                val count = minOf(block.size, output.size - outputOffset)
                block.copyInto(output, outputOffset, 0, count)
                outputOffset += count
                counter++
            }
            return output
        } finally {
            pseudoRandomKey.fill(0)
            previous.fill(0)
            if (actualSalt !== salt) actualSalt.fill(0)
        }
    }

    fun seal(
        key: ByteArray,
        plaintext: ByteArray,
        associatedData: ByteArray,
        nonce: ByteArray = randomBytes(NONCE_SIZE_BYTES)
    ): VaultSealedValue {
        require(key.size == KEY_SIZE_BYTES) { "Vault encryption key must be 256 bits" }
        require(nonce.size == NONCE_SIZE_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_SIZE_BITS, nonce))
        cipher.updateAAD(associatedData)
        return VaultSealedValue(nonce.copyOf(), cipher.doFinal(plaintext))
    }

    fun open(key: ByteArray, sealed: VaultSealedValue, associatedData: ByteArray): ByteArray {
        require(key.size == KEY_SIZE_BYTES) { "Vault encryption key must be 256 bits" }
        require(sealed.nonce.size == NONCE_SIZE_BYTES) { "Vault nonce has an invalid size" }
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_SIZE_BITS, sealed.nonce))
            cipher.updateAAD(associatedData)
            cipher.doFinal(sealed.ciphertext)
        } catch (error: AEADBadTagException) {
            throw VaultAuthenticationException(error)
        }
    }

    fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
    fun constantTimeEquals(left: ByteArray, right: ByteArray): Boolean = MessageDigest.isEqual(left, right)
    fun longBytes(value: Long): ByteArray = ByteBuffer.allocate(Long.SIZE_BYTES).putLong(value).array()
    fun intBytes(value: Int): ByteArray = ByteBuffer.allocate(Int.SIZE_BYTES).putInt(value).array()
}

inline fun <T> ByteArray.useSecret(block: (ByteArray) -> T): T = try {
    block(this)
} finally {
    fill(0)
}
