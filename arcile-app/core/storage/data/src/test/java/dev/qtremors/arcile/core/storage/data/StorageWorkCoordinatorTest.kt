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
    fun `concurrent mutations settle to idle only when all have ended`() {
        val coordinator = DefaultStorageWorkCoordinator()
        val pool = java.util.concurrent.Executors.newFixedThreadPool(8)
        try {
            repeat(20) {
                val start = java.util.concurrent.CountDownLatch(1)
                val tasks = List(8) {
                    pool.submit {
                        start.await()
                        repeat(500) {
                            coordinator.beginMutation()
                            coordinator.endMutation()
                        }
                    }
                }
                start.countDown()
                tasks.forEach { it.get() }
                assertFalse(coordinator.isMutationActive.value)
            }
        } finally {
            pool.shutdownNow()
        }
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
