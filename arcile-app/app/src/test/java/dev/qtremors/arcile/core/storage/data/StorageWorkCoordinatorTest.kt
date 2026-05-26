package dev.qtremors.arcile.core.storage.data

import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageWorkCoordinatorTest {
    @Test
    fun `active state remains true until all mutations end`() {
        val coordinator = DefaultStorageWorkCoordinator()

        coordinator.beginMutation()
        coordinator.beginMutation()
        assertTrue(coordinator.isMutationActive.value)

        coordinator.endMutation()
        assertTrue(coordinator.isMutationActive.value)

        coordinator.endMutation()
        assertFalse(coordinator.isMutationActive.value)
    }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun `low priority work waits until mutation completes`() = runTest {
        val coordinator = DefaultStorageWorkCoordinator()
        coordinator.beginMutation()

        val waiter = async {
            coordinator.awaitLowPrioritySlot()
            true
        }
        advanceTimeBy(500)
        assertFalse(waiter.isCompleted)

        coordinator.endMutation()
        advanceTimeBy(250)

        assertTrue(waiter.await())
    }
}
