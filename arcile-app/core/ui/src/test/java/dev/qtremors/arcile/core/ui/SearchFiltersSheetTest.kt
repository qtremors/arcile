package dev.qtremors.arcile.core.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.qtremors.arcile.core.storage.domain.SearchFilters
import dev.qtremors.arcile.core.ui.testing.ArcileTestTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SearchFiltersSheetTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `filter changes wait for apply and apply dismisses sheet`() {
        var applied: SearchFilters? = null
        var dismissed = false
        composeRule.setContent {
            ArcileTestTheme {
                SearchFiltersSheet(
                    currentFilters = SearchFilters(),
                    onApplyFilters = { applied = it },
                    onDismiss = { dismissed = true },
                    showCategoryFilter = false
                )
            }
        }

        composeRule.onNodeWithText("Files").performClick()
        composeRule.runOnIdle {
            assertNull(applied)
            assertFalse(dismissed)
        }

        composeRule.onNodeWithText("Apply").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals("Files", applied?.itemType)
            assertTrue(dismissed)
        }
    }
}
