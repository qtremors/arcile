package dev.qtremors.arcile.presentation.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.qtremors.arcile.navigation.AppRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A quick reopen has the start page on the first frame, so the reset can start before the NavHost
 * (inside the Scaffold content) has set the graph. Reading `graph` then threw
 * "You must call setGraph() before calling getGraph()" and crashed the app on launch.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ColdLaunchResetTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `reset started before the nav host exists waits for the graph instead of crashing`() {
        lateinit var controller: NavHostController
        var showHost by mutableStateOf(false)
        var resetFinished by mutableStateOf(false)

        compose.setContent {
            val navController = rememberNavController()
            SideEffect { controller = navController }
            LaunchedEffect(Unit) {
                navController.resetToColdLaunchPage(BROWSER_PAGE)
                resetFinished = true
            }
            if (showHost) {
                AppNavHost(navController, appNavigationTransitions(reducedMotion = true)) {
                    composable<AppRoutes.Main> { Box {} }
                }
            }
        }

        compose.waitForIdle()
        assertFalse("the reset must wait while there is no graph", resetFinished)

        showHost = true
        compose.waitForIdle()

        assertTrue(resetFinished)
        compose.runOnIdle {
            val entry = controller.currentBackStackEntry!!
            assertTrue(entry.destination.hasRoute<AppRoutes.Main>())
            assertEquals(BROWSER_PAGE, entry.toRoute<AppRoutes.Main>().initialPage)
            // The start destination was replaced, not stacked underneath
            assertEquals(1, controller.currentBackStack.value.count { it.destination.hasRoute<AppRoutes.Main>() })
        }
    }
}
