package dev.qtremors.arcile.feature.onlyfiles

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OnlyFilesPasswordInputTest {
    @get:Rule val rule = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun text(id: Int) = context.getString(id)

    @Test
    fun `both layouts request password input without autocorrection or capitalization`() {
        for ((numeric, type) in listOf(false to KeyboardType.Password, true to KeyboardType.NumberPassword)) {
            val options = vaultPasswordKeyboardOptions(numeric)
            assertEquals(type, options.keyboardType)
            assertEquals(false, options.autoCorrectEnabled)
            assertEquals(KeyboardCapitalization.None, options.capitalization)
        }
    }

    @Test
    fun `numeric layout never filters letters symbols unicode or leading zeroes`() {
        var entered = ""
        rule.setContent {
            MaterialTheme {
                VaultPasswordKeyboardProvider(true, {}) {
                    var password by remember { mutableStateOf("") }
                    PasswordField(password, { password = it; entered = it }, false, {})
                }
            }
        }
        val password = "00a!é"
        rule.onNodeWithText(text(R.string.onlyfiles_password)).performTextInput(password)
        rule.runOnIdle { assertEquals(password, entered) }
        rule.onNodeWithContentDescription(text(R.string.onlyfiles_use_text_keyboard)).performClick()
        rule.runOnIdle { assertEquals(password, entered) }
    }

    @Test
    fun `switching keyboard updates the shared preference without changing confirmation contents`() {
        val preferences = mutableListOf<Boolean>()
        var confirmation = ""
        rule.setContent {
            MaterialTheme {
                VaultPasswordKeyboardProvider(true, preferences::add) {
                    var password by remember { mutableStateOf("") }
                    var repeated by remember { mutableStateOf("") }
                    Column {
                        PasswordField(password, { password = it }, false, {})
                        PasswordField(repeated, { repeated = it; confirmation = it }, false, {},
                            label = R.string.onlyfiles_confirm_password, showKeyboardSwitch = false)
                    }
                }
            }
        }
        rule.onNodeWithText(text(R.string.onlyfiles_confirm_password)).performTextInput("0042")
        rule.onNodeWithContentDescription(text(R.string.onlyfiles_use_text_keyboard)).performClick()
        rule.onNodeWithContentDescription(text(R.string.onlyfiles_use_numeric_keyboard)).performClick()
        rule.runOnIdle {
            assertEquals(listOf(false, true), preferences)
            assertEquals("0042", confirmation)
        }
    }

    @Test
    fun `archive passwords keep their ordinary text keyboard without changing vault preferences`() {
        val preferences = mutableListOf<Boolean>()
        rule.setContent {
            MaterialTheme {
                VaultPasswordKeyboardProvider(true, preferences::add) {
                    PasswordField("", {}, false, {}, allowKeyboardSwitch = false)
                }
            }
        }
        rule.onNodeWithContentDescription(text(R.string.onlyfiles_use_text_keyboard)).assertDoesNotExist()
        rule.onNodeWithContentDescription(text(R.string.onlyfiles_use_numeric_keyboard)).assertDoesNotExist()
        rule.runOnIdle { assertTrue(preferences.isEmpty()) }
    }
}
