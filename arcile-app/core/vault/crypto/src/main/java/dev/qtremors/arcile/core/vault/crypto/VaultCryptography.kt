package dev.qtremors.arcile.core.vault.crypto

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class VaultKdfParameters(
    val salt: ByteArray,
    val memoryKiB: Int,
    val iterations: Int,
    val parallelism: Int
)

data class VaultSealedValue(
    val nonce: ByteArray,
    val ciphertext: ByteArray
)

class VaultAuthenticationException(cause: Throwable? = null) : Exception("Authentication failed", cause)

object VaultCryptography {
    const val KEY_SIZE_BYTES = 32
    const val NONCE_SIZE_BYTES = 12
    private const val TAG_SIZE_BITS = 128

    private val random = SecureRandom()

    fun randomBytes(size: Int): ByteArray = ByteArray(size).also(random::nextBytes)

    fun defaultKdfParameters(): VaultKdfParameters = VaultKdfParameters(
        salt = randomBytes(16),
        memoryKiB = 64 * 1024,
        iterations = 3,
        parallelism = minOf(2, Runtime.getRuntime().availableProcessors().coerceAtLeast(1))
    )

    fun derivePasswordKey(password: CharArray, parameters: VaultKdfParameters): ByteArray {
        require(password.isNotEmpty()) { "Vault password must not be empty" }
        require(parameters.salt.size >= 16) { "Vault KDF salt is too short" }
        require(parameters.memoryKiB >= 32 * 1024) { "Vault KDF memory cost is too low" }
        require(parameters.iterations >= 2) { "Vault KDF iteration count is too low" }
        require(parameters.parallelism >= 1) { "Vault KDF parallelism is invalid" }

        val passwordBytes = StandardCharsets.UTF_8.encode(CharBuffer.wrap(password)).let { encoded ->
            ByteArray(encoded.remaining()).also(encoded::get)
        }
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

    fun seal(key: ByteArray, plaintext: ByteArray, associatedData: ByteArray): VaultSealedValue {
        require(key.size == KEY_SIZE_BYTES) { "Vault encryption key must be 256 bits" }
        val nonce = randomBytes(NONCE_SIZE_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_SIZE_BITS, nonce))
        cipher.updateAAD(associatedData)
        return VaultSealedValue(nonce, cipher.doFinal(plaintext))
    }

    fun open(key: ByteArray, sealed: VaultSealedValue, associatedData: ByteArray): ByteArray {
        require(key.size == KEY_SIZE_BYTES) { "Vault encryption key must be 256 bits" }
        require(sealed.nonce.size == NONCE_SIZE_BYTES) { "Vault nonce has an invalid size" }
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key, "AES"),
                GCMParameterSpec(TAG_SIZE_BITS, sealed.nonce)
            )
            cipher.updateAAD(associatedData)
            cipher.doFinal(sealed.ciphertext)
        } catch (error: AEADBadTagException) {
            throw VaultAuthenticationException(error)
        }
    }

    fun longBytes(value: Long): ByteArray = ByteBuffer.allocate(Long.SIZE_BYTES).putLong(value).array()
    fun intBytes(value: Int): ByteArray = ByteBuffer.allocate(Int.SIZE_BYTES).putInt(value).array()
}
