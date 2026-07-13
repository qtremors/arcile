package dev.qtremors.arcile.core.runtime

import android.content.IntentSender
import dev.qtremors.arcile.core.storage.domain.StorageAuthorizationOperation
import dev.qtremors.arcile.core.storage.domain.StorageAuthorizationRequirement
import io.mockk.mockk
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class NativeStorageAuthorizationGatewayTest {
    private val gateway = NativeStorageAuthorizationGateway()

    @Test
    fun `registered sender resolves only for matching request`() {
        val requirement = requirement("request-1")
        val sender = mockk<IntentSender>()

        gateway.register(requirement, sender)

        assertSame(sender, gateway.resolve(requirement))
        assertNull(gateway.resolve(requirement("request-2")))
    }

    @Test
    fun `completion by request id removes sender`() {
        val requirement = requirement("request-1")
        gateway.register(requirement, mockk())

        gateway.complete(requirement.requestId)

        assertNull(gateway.resolve(requirement))
    }

    @Test
    fun `consuming sender is atomic and one shot`() {
        val requirement = requirement("request-1")
        val sender = mockk<IntentSender>()
        gateway.register(requirement, sender)

        assertSame(sender, gateway.consume(requirement))
        assertNull(gateway.consume(requirement))
        assertNull(gateway.resolve(requirement))
    }

    @Test
    fun `new sender replaces stale sender for same request`() {
        val requirement = requirement("request-1")
        val stale = mockk<IntentSender>()
        val current = mockk<IntentSender>()

        gateway.register(requirement, stale)
        gateway.register(requirement, current)

        assertSame(current, gateway.resolve(requirement))
    }

    private fun requirement(requestId: String) = StorageAuthorizationRequirement(
        requestId = requestId,
        operation = StorageAuthorizationOperation.RESTORE_TRASH
    )
}
