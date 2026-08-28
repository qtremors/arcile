package dev.qtremors.arcile.feature.browser.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.advanceEventTime
import androidx.compose.ui.test.click
import androidx.compose.ui.test.down
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.moveTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.testing.ArcileTestTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalFoundationApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BrowserWorkspaceSwipeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `tap remains a tap without switching workspace`() {
        var taps = 0
        val swipes = mutableListOf<Int>()
        composeRule.setContent {
            ArcileTestTheme {
                gestureSurface(
                    onClick = { taps += 1 },
                    onLongClick = {},
                    onSwipe = swipes::add
                )
            }
        }

        composeRule.onNodeWithTag(GestureSurfaceTag).performTouchInput { click(center) }

        assertEquals(1, taps)
        assertTrue(swipes.isEmpty())
    }

    @Test
    fun `horizontal swipe switches once and does not click`() {
        var taps = 0
        val swipes = mutableListOf<Int>()
        composeRule.setContent {
            ArcileTestTheme {
                gestureSurface(
                    onClick = { taps += 1 },
                    onLongClick = {},
                    onSwipe = swipes::add
                )
            }
        }

        composeRule.onNodeWithTag(GestureSurfaceTag).performTouchInput {
            swipe(
                start = center + Offset(100f, 0f),
                end = center - Offset(100f, 0f),
                durationMillis = 240L
            )
        }

        assertEquals(0, taps)
        assertEquals(listOf(1), swipes)
    }

    @Test
    fun `vertical and ambiguous diagonal movement never switches workspace`() {
        val swipes = mutableListOf<Int>()
        composeRule.setContent {
            ArcileTestTheme {
                gestureSurface(onClick = {}, onLongClick = {}, onSwipe = swipes::add)
            }
        }

        composeRule.onNodeWithTag(GestureSurfaceTag).performTouchInput {
            swipe(
                start = center - Offset(0f, 80f),
                end = center + Offset(0f, 80f),
                durationMillis = 240L
            )
        }
        composeRule.onNodeWithTag(GestureSurfaceTag).performTouchInput {
            swipe(
                start = center - Offset(80f, 70f),
                end = center + Offset(80f, 70f),
                durationMillis = 240L
            )
        }

        assertTrue(swipes.isEmpty())
    }

    @Test
    fun `long press owns the gesture and cannot become a workspace swipe`() {
        var longPresses = 0
        val swipes = mutableListOf<Int>()
        composeRule.setContent {
            ArcileTestTheme {
                gestureSurface(
                    onClick = {},
                    onLongClick = { longPresses += 1 },
                    onSwipe = swipes::add
                )
            }
        }

        composeRule.onNodeWithTag(GestureSurfaceTag).performTouchInput {
            down(center)
            advanceEventTime(700L)
            moveTo(center - Offset(100f, 0f))
            up()
        }

        assertEquals(1, longPresses)
        assertTrue(swipes.isEmpty())
    }

    @Test
    fun `multi touch and system edge movement never switch workspace`() {
        val swipes = mutableListOf<Int>()
        composeRule.setContent {
            ArcileTestTheme {
                gestureSurface(onClick = {}, onLongClick = {}, onSwipe = swipes::add)
            }
        }

        composeRule.onNodeWithTag(GestureSurfaceTag).performTouchInput {
            down(0, center)
            down(1, center + Offset(0f, 20f))
            moveTo(0, center - Offset(100f, 0f))
            up(1)
            up(0)
        }
        composeRule.onNodeWithTag(GestureSurfaceTag).performTouchInput {
            swipe(
                start = Offset(1f, center.y),
                end = Offset(180f, center.y),
                durationMillis = 240L
            )
        }

        assertTrue(swipes.isEmpty())
    }

    @Test
    fun `nested horizontal scroller keeps ownership from workspace swipe`() {
        val swipes = mutableListOf<Int>()
        var nestedScrollValue = 0
        composeRule.setContent {
            ArcileTestTheme {
                val nestedScroll = rememberScrollState()
                nestedScrollValue = nestedScroll.value
                Box(
                    modifier = Modifier
                        .size(width = 320.dp, height = 240.dp)
                        .testTag(GestureSurfaceTag)
                        .browserWorkspaceSwipe(swipes::add)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .horizontalScroll(nestedScroll)
                    ) {
                        Spacer(Modifier.width(900.dp).fillMaxHeight())
                    }
                }
            }
        }

        composeRule.onNodeWithTag(GestureSurfaceTag).performTouchInput {
            swipe(
                start = center + Offset(100f, 0f),
                end = center - Offset(100f, 0f),
                durationMillis = 240L
            )
        }
        composeRule.waitForIdle()

        assertTrue(nestedScrollValue > 0)
        assertTrue(swipes.isEmpty())
    }
}

@Composable
private fun gestureSurface(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSwipe: (Int) -> Unit
) {
    Box(
        modifier = Modifier
            .size(width = 320.dp, height = 240.dp)
            .testTag(GestureSurfaceTag)
            .browserWorkspaceSwipe(onSwipe)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    )
}

private const val GestureSurfaceTag = "browser_workspace_gesture_surface"
