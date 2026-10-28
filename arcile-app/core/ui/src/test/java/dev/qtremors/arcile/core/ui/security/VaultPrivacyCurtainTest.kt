package dev.qtremors.arcile.core.ui.security

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.core.ui.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VaultPrivacyCurtainTest {
    @get:Rule val rule = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `private semantics disappear while navigation content stays mounted behind the curtain`() {
        val blocked = mutableStateOf(false)
        var mounts = 0
        var disposals = 0
        rule.setContent {
            MaterialTheme {
                VaultPrivacyCurtain(blocked.value, false, {}) {
                    DisposableEffect(Unit) { mounts++; onDispose { disposals++ } }
                    Text("Private filename")
                }
            }
        }
        rule.onNodeWithText("Private filename").assertExists()
        rule.runOnIdle { blocked.value = true }
        rule.onNodeWithText("Private filename").assertDoesNotExist()
        rule.onNodeWithText(context.getString(R.string.onlyfiles_locking)).assertExists()
        rule.runOnIdle { blocked.value = false }
        rule.onNodeWithText("Private filename").assertExists()
        rule.runOnIdle {
            assertEquals(1, mounts)
            assertEquals(0, disposals)
        }
    }

    @Test
    fun `failed lock keeps content inaccessible and offers retry`() {
        var retries = 0
        rule.setContent {
            MaterialTheme {
                VaultPrivacyCurtain(true, true, { retries++ }) { Text("Private filename") }
            }
        }
        rule.onNodeWithText("Private filename").assertDoesNotExist()
        rule.onNodeWithText(context.getString(R.string.onlyfiles_lock_failed)).assertExists()
        rule.onNodeWithText(context.getString(R.string.onlyfiles_retry_lock)).performClick()
        rule.runOnIdle { assertEquals(1, retries) }
    }
}
