package dev.qtremors.arcile.feature.onlyfiles

import android.content.Context
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.compose.material3.MaterialTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OnlyFilesImportActionsTest {
    @get:Rule val rule = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun text(id: Int) = context.getString(id)

    @Test
    fun `destination sheet exposes separate copy and move intents`() {
        val actions = mutableListOf<String>()
        rule.setContent {
            MaterialTheme {
                OnlyFilesImportDestinationActions(true, { actions += "copy" }, { actions += "move" })
            }
        }
        rule.onNodeWithText(text(R.string.onlyfiles_copy_here)).performClick()
        rule.onNodeWithText(text(R.string.onlyfiles_move_here)).performClick()
        rule.runOnIdle { assertEquals(listOf("copy", "move"), actions) }
    }

    @Test
    fun `unavailable destination disables both copy and move`() {
        rule.setContent {
            MaterialTheme { OnlyFilesImportDestinationActions(false, {}, {}) }
        }
        rule.onNodeWithText(text(R.string.onlyfiles_copy_here)).assertIsNotEnabled()
        rule.onNodeWithText(text(R.string.onlyfiles_move_here)).assertIsNotEnabled()
    }

    @Test
    fun `move confirmation explains verified removal and supports cancellation`() {
        var confirmed = 0
        var cancelled = 0
        rule.setContent {
            MaterialTheme { OnlyFilesMoveIntoVaultDialog({ confirmed++ }, { cancelled++ }) }
        }
        rule.onNodeWithText(text(R.string.onlyfiles_move_into_vault_confirm)).assertExists()
        rule.onNodeWithText(text(R.string.onlyfiles_cancel)).performClick()
        rule.runOnIdle {
            assertEquals(0, confirmed)
            assertEquals(1, cancelled)
        }
    }
}
