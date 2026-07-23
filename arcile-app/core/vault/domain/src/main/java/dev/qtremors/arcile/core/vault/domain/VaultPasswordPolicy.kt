package dev.qtremors.arcile.core.vault.domain

object VaultPasswordPolicy {
    const val RECOMMENDED_MINIMUM_LENGTH = 12

    fun isWeak(password: CharArray): Boolean {
        if (password.size < RECOMMENDED_MINIMUM_LENGTH) return true
        var classes = 0
        if (password.any(Char::isLowerCase)) classes++
        if (password.any(Char::isUpperCase)) classes++
        if (password.any(Char::isDigit)) classes++
        if (password.any { !it.isLetterOrDigit() }) classes++
        return classes < 3
    }

    fun requireAccepted(password: CharArray, weakPasswordConfirmed: Boolean) {
        if (password.isEmpty()) throw VaultFailure.AuthenticationFailed()
        if (isWeak(password) && !weakPasswordConfirmed) {
            throw VaultFailure.WeakPasswordConfirmationRequired()
        }
    }
}
