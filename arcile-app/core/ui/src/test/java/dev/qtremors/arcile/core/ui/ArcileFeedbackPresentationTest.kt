package dev.qtremors.arcile.core.ui

import android.content.Context
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.core.presentation.UiText
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ArcileFeedbackPresentationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `shared presenter uses Browser appearance and dispatches only the selected action`() = runTest {
        val host = SnackbarHostState()
        var actions = 0
        var dismissals = 0
        val event = ArcileFeedbackEvent(
            UiText.PluralResource(R.plurals.file_operation_copied_items, 2, listOf(2)),
            actionLabel = UiText.Dynamic("Show destination"),
            onAction = { actions++ }, onDismiss = { dismissals++ }
        )
        val job = launch(start = CoroutineStart.UNDISPATCHED) { host.showArcileFeedback(context, event) }
        val data = requireNotNull(host.currentSnackbarData)
        assertEquals(event.message.asString(context), data.visuals.message)
        assertEquals("Show destination", data.visuals.actionLabel)
        assertEquals(SnackbarDuration.Short, data.visuals.duration)
        assertFalse(data.visuals.withDismissAction)
        data.performAction()
        job.join()
        assertEquals(1, actions)
        assertEquals(0, dismissals)
    }

    @Test
    fun `dismissal clears only the presented event`() = runTest {
        val host = SnackbarHostState()
        var actions = 0
        var dismissals = 0
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            host.showArcileFeedback(context, ArcileFeedbackEvent(
                UiText.Dynamic("Completed"), onAction = { actions++ }, onDismiss = { dismissals++ }
            ))
        }
        requireNotNull(host.currentSnackbarData).dismiss()
        job.join()
        assertEquals(0, actions)
        assertEquals(1, dismissals)
    }

    @Test
    fun `cancelling a protected presentation removes its action without invoking stale callbacks`() = runTest {
        val host = SnackbarHostState()
        var callbacks = 0
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            host.showArcileFeedback(context, ArcileFeedbackEvent(
                UiText.Dynamic("Completed"), actionLabel = UiText.Dynamic("Show destination"),
                onAction = { callbacks++ }, onDismiss = { callbacks++ }
            ))
        }
        job.cancel()
        runCurrent()
        assertNull(host.currentSnackbarData)
        assertEquals(0, callbacks)
    }
}
