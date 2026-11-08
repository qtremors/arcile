package dev.qtremors.arcile.feature.onlyfiles

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.core.ui.asString
import dev.qtremors.arcile.core.vault.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OnlyFilesLocalizedMessagesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `authentication errors remain actionable without disclosing vault identifiers`() {
        val failures = listOf(
            VaultFailure.AuthenticationFailed(), VaultFailure.WeakPasswordConfirmationRequired(),
            VaultFailure.BiometricInvalidated(), VaultFailure.Locked(VaultId.of("secret-vault-id"))
        )
        val messages = failures.map { vaultOperationErrorText(it, R.string.onlyfiles_error_operation).asString(context) }
        assertEquals(4, messages.toSet().size)
        messages.forEach {
            assertTrue(it.isNotBlank())
            assertFalse(it.contains("secret-vault-id"))
        }
        assertEquals(context.getString(R.string.onlyfiles_error_password), messages.first())
    }

    @Test
    fun `storage format registration and boundary failures resolve to resources without raw diagnostics`() {
        val failures = listOf(
            VaultFailure.UnsupportedFormat(999), VaultFailure.UnsafeKdfParameters("secret parameters"),
            VaultFailure.StaleRegistration(), VaultFailure.DuplicateVault(VaultId.of("secret-vault-id")),
            VaultFailure.FolderNotEmpty(), VaultFailure.InvalidName("secret file name"),
            VaultFailure.SymlinkRejected("secret symlink"), VaultFailure.TransactionRecoveryFailed("secret storage path"),
            VaultFailure.FileTooLarge(9999, 10), VaultFailure.ExternalGrantExpired(),
            VaultFailure.ExternalGrantConsumerMismatch(), VaultFailure.DestructiveConfirmationRequired()
        )
        val fallback = context.getString(R.string.onlyfiles_error_operation)
        failures.forEach { failure ->
            val message = vaultOperationErrorText(failure, R.string.onlyfiles_error_operation).asString(context)
            assertNotEquals(fallback, message)
            assertTrue(message.isNotBlank())
            assertFalse(message.contains("secret"))
            assertFalse(message.contains("9999"))
        }
    }

    @Test
    fun `backup names are formatted as data and completion carries the current mount`() {
        val filename = "backup-%1s.arcile"
        val feedback = operationSuccess(R.string.onlyfiles_backup_created, 7, listOf(filename))
        assertTrue(feedback.message.asString(context).contains(filename))
        assertTrue(feedback.belongsTo(OnlyFilesUiState(mountGeneration = 7)))
        assertFalse(feedback.belongsTo(OnlyFilesUiState(mountGeneration = 8)))
        assertNull(feedback.destination)
    }

    @Test
    fun `an unknown backend exception never replaces the localized generic error with its text`() {
        val error = IllegalStateException("/private/secret/opaque-id cannot be read")
        val message = vaultOperationErrorText(error, R.string.onlyfiles_error_operation).asString(context)
        assertEquals(context.getString(R.string.onlyfiles_error_operation), message)
        assertFalse(message.contains("private"))
        assertFalse(message.contains("opaque-id"))
    }
}
