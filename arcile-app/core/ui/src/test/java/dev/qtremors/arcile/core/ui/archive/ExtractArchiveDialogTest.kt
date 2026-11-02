package dev.qtremors.arcile.core.ui.archive

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.core.ui.R
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExtractArchiveDialogTest {
    @get:Rule val rule = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun `Browser custom destinations retain editable path validation and dispatch`() {
        val requests = mutableListOf<Pair<ArchiveExtractionChoice, String?>>()
        rule.setContent { MaterialTheme { ExtractArchiveDialog("Archive.zip", "/Download", {}, { choice, path -> requests += choice to path }) } }
        rule.onNodeWithText(text(R.string.archive_choose_folder)).performScrollTo().performClick()
        rule.onNodeWithText("/Download").performScrollTo().performTextReplacement("")
        confirm().assertIsNotEnabled()
        rule.onNodeWithText(text(R.string.archive_destination_folder)).performTextReplacement("/Documents")
        confirm().performClick()
        rule.runOnIdle { assertEquals(listOf(ArchiveExtractionChoice.CUSTOM_FOLDER to "/Documents"), requests) }
    }

    @Test fun `ordinary Browser choices survive saved state while direct extraction has no custom path`() {
        val restoration = StateRestorationTester(rule)
        val requests = mutableListOf<Pair<ArchiveExtractionChoice, String?>>()
        restoration.setContent { MaterialTheme { ExtractArchiveDialog("Archive.zip", "/Download", {}, { choice, path -> requests += choice to path }) } }
        rule.onNodeWithText(text(R.string.archive_extract_here)).performScrollTo().performClick()
        restoration.emulateSavedInstanceStateRestore()
        confirm().performClick()
        rule.runOnIdle { assertEquals(listOf(ArchiveExtractionChoice.SAME_FOLDER to null), requests) }
    }

    private fun text(id: Int) = context.getString(id)
    private fun confirm() = rule.onNode(hasText(text(R.string.archive_extract_archive)) and hasClickAction())
}
