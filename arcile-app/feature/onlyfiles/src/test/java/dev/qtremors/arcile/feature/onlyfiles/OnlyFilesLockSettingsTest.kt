package dev.qtremors.arcile.feature.onlyfiles

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.core.vault.domain.VaultBackgroundLockDelay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OnlyFilesLockSettingsTest {
    @get:Rule val rule = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun text(id: Int) = context.getString(id)

    @Test
    fun `background lock picker marks the persisted delay and dispatches the selected policy`() {
        val choices = mutableListOf<VaultBackgroundLockDelay>()
        rule.setContent {
            MaterialTheme {
                BackgroundLockDelayDialog(VaultBackgroundLockDelay.ONE_MINUTE, choices::add, onDismiss = {})
            }
        }
        rule.onNodeWithText(text(R.string.onlyfiles_lock_one_minute)).assertIsSelected()
        rule.onNodeWithText(text(R.string.onlyfiles_lock_five_minutes)).performClick()
        rule.runOnIdle { assertEquals(listOf(VaultBackgroundLockDelay.FIVE_MINUTES), choices) }
    }

    @Test
    fun `cancelling the picker keeps the existing lock policy`() {
        val choices = mutableListOf<VaultBackgroundLockDelay>()
        var dismissed = false
        rule.setContent {
            MaterialTheme {
                BackgroundLockDelayDialog(VaultBackgroundLockDelay.IMMEDIATE, choices::add) { dismissed = true }
            }
        }
        rule.onNodeWithText(text(R.string.onlyfiles_cancel)).performClick()
        rule.runOnIdle {
            assertTrue(choices.isEmpty())
            assertTrue(dismissed)
        }
    }

    @Test
    fun `every delay is offered as a choice`() {
        rule.setContent {
            MaterialTheme { BackgroundLockDelayDialog(VaultBackgroundLockDelay.IMMEDIATE, {}, onDismiss = {}) }
        }
        VaultBackgroundLockDelay.entries.forEach { delay ->
            rule.onNodeWithText(text(delay.label())).assertExists()
        }
    }
}
