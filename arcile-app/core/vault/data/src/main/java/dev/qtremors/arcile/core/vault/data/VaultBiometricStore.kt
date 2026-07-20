package dev.qtremors.arcile.core.vault.data

import android.content.Context
import android.hardware.biometrics.BiometricPrompt
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import dev.qtremors.arcile.core.vault.domain.VaultFailure
import dev.qtremors.arcile.core.vault.domain.VaultId
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import java.security.UnrecoverableKeyException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

internal class VaultBiometricStore(context: Context) {
    private val root = File(context.noBackupFilesDir, DIRECTORY).apply { mkdirs() }

    fun prepareEnrollment(vaultId: VaultId): Cipher = mapInvalidation {
        removeKey(vaultId)
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias(vaultId),
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true)
                .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                .build()
        )
        val key = generator.generateKey()
        cipher().apply { init(Cipher.ENCRYPT_MODE, key) }
    }

    fun finishEnrollment(vaultId: VaultId, cipher: Cipher, masterSecret: ByteArray) = mapInvalidation {
        require(masterSecret.size == 32)
        val ciphertext = cipher.doFinal(masterSecret)
        val payload = ByteArrayOutputStream().use { output ->
            DataOutputStream(output).use { data ->
                data.writeInt(RECORD_VERSION)
                data.writeInt(cipher.iv.size)
                data.write(cipher.iv)
                data.writeInt(ciphertext.size)
                data.write(ciphertext)
            }
            output.toByteArray()
        }
        val target = record(vaultId)
        val temporary = File(root, ".${target.name}.part")
        FileOutputStream(temporary).use { stream ->
            stream.write(payload)
            stream.flush()
            stream.fd.sync()
        }
        if (!temporary.renameTo(target)) {
            temporary.delete()
            throw VaultFailure.Unavailable("Biometric enrollment could not be saved")
        }
    }

    fun prepareUnlock(vaultId: VaultId): Cipher = mapInvalidation {
        val enrollment = read(vaultId)
        cipher().apply { init(Cipher.DECRYPT_MODE, key(vaultId), javax.crypto.spec.GCMParameterSpec(128, enrollment.iv)) }
    }

    fun finishUnlock(vaultId: VaultId, cipher: Cipher): ByteArray = mapInvalidation {
        val enrollment = read(vaultId)
        cipher.doFinal(enrollment.ciphertext).also { require(it.size == 32) }
    }

    fun remove(vaultId: VaultId) {
        record(vaultId).delete()
        removeKey(vaultId)
    }

    fun cryptoObject(cipher: Cipher): BiometricPrompt.CryptoObject = BiometricPrompt.CryptoObject(cipher)

    private fun read(vaultId: VaultId): EnrollmentRecord {
        val file = record(vaultId)
        if (!file.isFile || file.length() !in 1..MAX_RECORD_BYTES) throw VaultFailure.BiometricInvalidated()
        return try {
            DataInputStream(FileInputStream(file)).use { input ->
                require(input.readInt() == RECORD_VERSION)
                val ivSize = input.readInt()
                require(ivSize in 12..32)
                val iv = ByteArray(ivSize).also(input::readFully)
                val ciphertextSize = input.readInt()
                require(ciphertextSize in 48..128)
                val ciphertext = ByteArray(ciphertextSize).also(input::readFully)
                require(input.read() == -1)
                EnrollmentRecord(iv, ciphertext)
            }
        } catch (error: VaultFailure) {
            throw error
        } catch (error: Throwable) {
            remove(vaultId)
            throw VaultFailure.BiometricInvalidated()
        }
    }

    private fun key(vaultId: VaultId): SecretKey = (keyStore().getKey(alias(vaultId), null) as? SecretKey)
        ?: throw VaultFailure.BiometricInvalidated()

    private fun removeKey(vaultId: VaultId) {
        runCatching { keyStore().deleteEntry(alias(vaultId)) }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    private fun cipher(): Cipher = Cipher.getInstance("AES/GCM/NoPadding")
    private fun record(vaultId: VaultId) = File(root, "${vaultId.value}.bio")
    private fun alias(vaultId: VaultId) = "arcile.onlyfiles.${vaultId.value}"

    private inline fun <T> mapInvalidation(block: () -> T): T = try {
        block()
    } catch (error: KeyPermanentlyInvalidatedException) {
        throw VaultFailure.BiometricInvalidated()
    } catch (error: UnrecoverableKeyException) {
        throw VaultFailure.BiometricInvalidated()
    }

    private data class EnrollmentRecord(val iv: ByteArray, val ciphertext: ByteArray)

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val DIRECTORY = "onlyfiles-biometric"
        const val RECORD_VERSION = 1
        const val MAX_RECORD_BYTES = 1024L
    }
}
