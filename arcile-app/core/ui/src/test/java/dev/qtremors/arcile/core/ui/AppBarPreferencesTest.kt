package dev.qtremors.arcile.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppBarPreferencesTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `locked bar matches natural collapse and cannot be dragged open`() {
        val locked = mutableStateOf(true)
        compose.setContent {
            MaterialTheme {
                val range = with(LocalDensity.current) {
                    -(TopAppBarDefaults.LargeAppBarExpandedHeight - TopAppBarDefaults.TopAppBarExpandedHeight).toPx()
                }
                val normal = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
                    state = remember { TopAppBarState(range, range, 0f) })
                val expandable = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
                Column {
                    LargeTopAppBar(title = { Text("Natural") }, modifier = Modifier.testTag("natural"),
                        scrollBehavior = normal, windowInsets = WindowInsets(0, 0, 0, 0))
                    CompositionLocalProvider(LocalKeepAppBarsCollapsed provides locked.value) {
                        LargeTopAppBar(title = { Text("Locked") }, modifier = Modifier.testTag("locked"),
                            scrollBehavior = arcileTopAppBarScrollBehavior(expandable),
                            windowInsets = WindowInsets(0, 0, 0, 0))
                    }
                }
            }
        }
        val natural = compose.onNodeWithTag("natural").fetchSemanticsNode().boundsInRoot
        val compact = compose.onNodeWithTag("locked").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText("Locked").assertIsDisplayed()
        val naturalTitle = compose.onNodeWithText("Natural").fetchSemanticsNode().boundsInRoot
        val lockedTitle = compose.onNodeWithText("Locked").fetchSemanticsNode().boundsInRoot
        assertEquals(natural.height, compact.height, 1f)
        assertEquals(naturalTitle.top - natural.top, lockedTitle.top - compact.top, 1f)
        compose.onNodeWithTag("locked").performTouchInput { swipeDown() }
        assertEquals(compact.height, compose.onNodeWithTag("locked").fetchSemanticsNode().boundsInRoot.height, 1f)
        compose.runOnIdle { locked.value = false }
        assertTrue(compose.onNodeWithTag("locked").fetchSemanticsNode().boundsInRoot.height > compact.height)
    }

    @Test
    fun `locked app bar does not consume content scroll`() {
        val locked = mutableStateOf(true)
        var scrollValue = 0
        compose.setContent {
            MaterialTheme {
                val behavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
                val scrollState = rememberScrollState()
                CompositionLocalProvider(LocalKeepAppBarsCollapsed provides locked.value) {
                    Scaffold(
                        modifier = Modifier.arcileTopAppBarNestedScroll(behavior),
                        topBar = {
                            LargeTopAppBar(
                                title = { Text("Locked") },
                                scrollBehavior = arcileTopAppBarScrollBehavior(behavior)
                            )
                        }
                    ) { padding ->
                        Column(
                            Modifier.fillMaxSize().padding(padding)
                                .verticalScroll(scrollState).testTag("content")
                        ) {
                            repeat(40) { Text("Item $it") }
                        }
                    }
                }
                scrollValue = scrollState.value
            }
        }
        compose.onNodeWithTag("content").performTouchInput { swipeUp() }
        compose.runOnIdle { assertTrue(scrollValue > 0) }
    }
}
