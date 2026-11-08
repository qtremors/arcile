package dev.qtremors.arcile.feature.onlyfiles

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
    private fun tag(delay: VaultBackgroundLockDelay) = "lock_delay_${delay.name}"

    @Test
    fun `lock timer marks the saved delay and dispatches a new choice`() {
        val choices = mutableListOf<VaultBackgroundLockDelay>()
        rule.setContent {
            MaterialTheme { BackgroundLockDelaySelector(VaultBackgroundLockDelay.ONE_MINUTE, choices::add) }
        }
        rule.onNodeWithTag(tag(VaultBackgroundLockDelay.ONE_MINUTE)).assertIsOn()
        rule.onNodeWithTag(tag(VaultBackgroundLockDelay.IMMEDIATE)).assertIsOff()
        rule.onNodeWithTag(tag(VaultBackgroundLockDelay.FIVE_MINUTES)).performClick()
        rule.runOnIdle { assertEquals(listOf(VaultBackgroundLockDelay.FIVE_MINUTES), choices) }
    }

    @Test
    fun `tapping the selected delay again changes nothing`() {
        val choices = mutableListOf<VaultBackgroundLockDelay>()
        rule.setContent {
            MaterialTheme { BackgroundLockDelaySelector(VaultBackgroundLockDelay.IMMEDIATE, choices::add) }
        }
        rule.onNodeWithTag(tag(VaultBackgroundLockDelay.IMMEDIATE)).performClick()
        rule.runOnIdle { assertTrue(choices.isEmpty()) }
    }

    @Test
    fun `every delay is offered with a short label and the selection follows the setting`() {
        var current by mutableStateOf(VaultBackgroundLockDelay.IMMEDIATE)
        rule.setContent {
            MaterialTheme { BackgroundLockDelaySelector(current) { current = it } }
        }
        VaultBackgroundLockDelay.entries.forEach { delay ->
            rule.onNodeWithText(text(delay.label())).assertExists()
        }
        rule.onNodeWithTag(tag(VaultBackgroundLockDelay.THIRTY_SECONDS)).performClick()
        rule.onNodeWithTag(tag(VaultBackgroundLockDelay.THIRTY_SECONDS)).assertIsOn()
        rule.onNodeWithTag(tag(VaultBackgroundLockDelay.IMMEDIATE)).assertIsOff()
    }
}
