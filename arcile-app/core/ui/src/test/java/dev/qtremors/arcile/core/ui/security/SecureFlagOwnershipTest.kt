package dev.qtremors.arcile.core.ui.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecureFlagOwnershipTest {
    @Test
    fun `protection remains until the final owner leaves`() {
        val target = Target()
        val ownership = ownership()
        val browser = Any()
        val viewer = Any()

        ownership.acquire(target, browser)
        ownership.acquire(target, viewer)
        ownership.release(target, browser)

        assertTrue(target.secure)

        ownership.release(target, viewer)

        assertFalse(target.secure)
    }

    @Test
    fun `preexisting protection is preserved after owners leave`() {
        val target = Target(secure = true)
        val ownership = ownership()
        val owner = Any()

        ownership.acquire(target, owner)
        ownership.release(target, owner)

        assertTrue(target.secure)
    }

    private fun ownership() = SecureFlagOwnership<Target>(
        isSecure = Target::secure,
        setSecure = { target, secure -> target.secure = secure }
    )

    private class Target(var secure: Boolean = false)
}
