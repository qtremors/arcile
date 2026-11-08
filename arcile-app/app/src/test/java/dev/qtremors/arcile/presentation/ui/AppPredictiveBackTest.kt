package dev.qtremors.arcile.presentation.ui

import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.qtremors.arcile.navigation.AppRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppPredictiveBackTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var controller: NavHostController
    private lateinit var dispatcher: OnBackPressedDispatcher

    @Test fun `settings stays full width during back and cancellation restores the same page`() {
        openSettings()
        val initial = bounds()
        gesture(BackEventCompat.EDGE_LEFT, 0.65f)
        val preview = bounds()
        assertEquals(initial.width, preview.width, 1f)
        assertTrue(preview.top > initial.top)
        compose.runOnIdle { assertTrue(controller.currentDestination!!.hasRoute<AppRoutes.Settings>()) }
        compose.runOnIdle { dispatcher.dispatchOnBackCancelled() }
        settle()
        assertEquals(initial, bounds())
        compose.runOnIdle { assertTrue(controller.currentDestination!!.hasRoute<AppRoutes.Settings>()) }
    }

    @Test fun `right edge back commits one page and a following back remains usable`() {
        openSettings()
        gesture(BackEventCompat.EDGE_RIGHT, 0.7f)
        compose.runOnIdle { dispatcher.onBackPressed() }
        settle()
        compose.runOnIdle {
            assertTrue(controller.currentDestination!!.hasRoute<AppRoutes.Main>())
            controller.navigate(AppRoutes.Settings)
        }
        compose.waitForIdle()
        gesture(BackEventCompat.EDGE_LEFT, 0.5f)
        compose.runOnIdle { dispatcher.onBackPressed() }
        settle()
        compose.runOnIdle { assertTrue(controller.currentDestination!!.hasRoute<AppRoutes.Main>()) }
    }

    @Test fun `reduced motion does not acquire a shrinking predictive animation`() {
        openSettings(reducedMotion = true)
        val initial = bounds()
        gesture(BackEventCompat.EDGE_LEFT, 0.6f)
        assertEquals(initial, bounds())
        compose.runOnIdle { dispatcher.dispatchOnBackCancelled() }
        settle()
        compose.runOnIdle { assertTrue(controller.currentDestination!!.hasRoute<AppRoutes.Settings>()) }
    }

    private fun openSettings(reducedMotion: Boolean = false) {
        compose.setContent {
            val navController = rememberNavController()
            val backDispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            SideEffect { controller = navController; dispatcher = backDispatcher }
            val transitions = appNavigationTransitions(reducedMotion)
            AppNavHost(navController, transitions) {
                composable<AppRoutes.Main> { Box(Modifier.fillMaxSize().background(Color.Blue)) }
                composable<AppRoutes.Settings>(
                    enterTransition = transitions.utilityEnter,
                    exitTransition = transitions.utilityExit,
                    popEnterTransition = transitions.utilityPopEnter,
                    popExitTransition = transitions.utilityPopExit
                ) { Box(Modifier.fillMaxSize().background(Color.White).testTag("settings")) }
            }
        }
        compose.runOnIdle { controller.navigate(AppRoutes.Settings) }
        compose.waitForIdle()
    }

    private fun bounds() = compose.onNodeWithTag("settings").fetchSemanticsNode().boundsInRoot

    private fun gesture(edge: Int, progress: Float) {
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 100f, 0f, edge)) }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { dispatcher.dispatchOnBackProgressed(BackEventCompat(100f, 100f, progress, edge)) }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(100)
    }

    private fun settle() {
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
    }
}
