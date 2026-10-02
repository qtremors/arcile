package dev.qtremors.arcile.feature.audio

import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.provider.MediaStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AudioMediaObserverTest {
    @Test
    fun `changes stop observing on cancellation and can be observed again`() = runTest {
        val resolver = mockk<ContentResolver>(relaxed = true)
        val context = mockk<Context>()
        every { context.contentResolver } returns resolver
        val observers = mutableListOf<ContentObserver>()
        every { resolver.registerContentObserver(any(), true, capture(observers)) } returns Unit
        val source = AudioMediaObserver(context)
        var changes = 0

        val first = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            source.changes.collect { changes++ }
        }
        runCurrent()
        verify(exactly = 1) {
            resolver.registerContentObserver(
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL), true, observers[0]
            )
        }
        observers[0].onChange(false)
        runCurrent()
        assertEquals(1, changes)
        verify(exactly = 0) { resolver.unregisterContentObserver(any()) }
        first.cancelAndJoin()
        verify(exactly = 1) { resolver.unregisterContentObserver(observers[0]) }

        val second = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            source.changes.collect { changes++ }
        }
        runCurrent()
        assertEquals(2, observers.size)
        assertNotSame(observers[0], observers[1])
        observers[1].onChange(false)
        runCurrent()
        assertEquals(2, changes)
        second.cancelAndJoin()
        verify(exactly = 1) { resolver.unregisterContentObserver(observers[1]) }
    }
}
