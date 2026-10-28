package dev.qtremors.arcile.feature.onlyfiles

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.core.vault.domain.VaultBackgroundLockDelay
import org.junit.Assert.*
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
    fun `background lock picker exposes the persisted delay and dispatches the selected policy`() {
        val choices = mutableListOf<VaultBackgroundLockDelay>()
        rule.setContent {
            MaterialTheme {
                OnlyFilesLockSettings(OnlyFilesUiState(backgroundLockDelay = VaultBackgroundLockDelay.ONE_MINUTE), choices::add, {}, {})
            }
        }
        rule.onNodeWithText(text(R.string.onlyfiles_lock_one_minute)).assertExists()
        rule.onNodeWithText(text(R.string.onlyfiles_background_lock)).performClick()
        rule.onNodeWithText(text(R.string.onlyfiles_lock_five_minutes)).performClick()
        rule.runOnIdle { assertEquals(listOf(VaultBackgroundLockDelay.FIVE_MINUTES), choices) }
    }

    @Test
    fun `screen-off and keyboard choices are independent`() {
        val screen = mutableListOf<Boolean>()
        val numeric = mutableListOf<Boolean>()
        rule.setContent {
            MaterialTheme { OnlyFilesLockSettings(OnlyFilesUiState(), {}, screen::add, numeric::add) }
        }
        rule.onNodeWithText(text(R.string.onlyfiles_lock_screen_off)).performClick()
        rule.onNodeWithText(text(R.string.onlyfiles_numeric_keyboard)).performClick()
        rule.runOnIdle {
            assertEquals(listOf(false), screen)
            assertEquals(listOf(true), numeric)
        }
    }

    @Test
    fun `closing the picker preserves the existing lock policy`() {
        val choices = mutableListOf<VaultBackgroundLockDelay>()
        rule.setContent {
            MaterialTheme { OnlyFilesLockSettings(OnlyFilesUiState(), choices::add, {}, {}) }
        }
        rule.onNodeWithText(text(R.string.onlyfiles_background_lock)).performClick()
        rule.onNodeWithText(text(R.string.onlyfiles_cancel)).performClick()
        rule.runOnIdle { assertTrue(choices.isEmpty()) }
    }
}
