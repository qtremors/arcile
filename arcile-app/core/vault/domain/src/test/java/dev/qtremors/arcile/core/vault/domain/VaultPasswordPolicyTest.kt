package dev.qtremors.arcile.core.vault.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultPasswordPolicyTest {
    @Test
    fun `password strength warning never rejects confirmed nonempty input`() {
        assertTrue(VaultPasswordPolicy.isWeak("short".toCharArray()))
        assertTrue(VaultPasswordPolicy.isWeak("alllowercasebutlong".toCharArray()))
        assertFalse(VaultPasswordPolicy.isWeak("Long-Password-7".toCharArray()))
        VaultPasswordPolicy.requireAccepted("x".toCharArray(), weakPasswordConfirmed = true)
    }

    @Test(expected = VaultFailure.WeakPasswordConfirmationRequired::class)
    fun `weak password needs explicit confirmation`() {
        VaultPasswordPolicy.requireAccepted("short".toCharArray(), weakPasswordConfirmed = false)
    }
}
