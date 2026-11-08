package dev.qtremors.arcile.feature.onlyfiles

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
import dev.qtremors.arcile.core.vault.domain.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import dev.qtremors.arcile.core.ui.R as UiR

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OnlyFilesExtractionDialogTest {
    @get:Rule val rule = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val archive = VaultNodeMetadata(
        VaultNodeRef(VaultId.of("vault"), NodeId.of("archive"), DirectoryId.Root, VaultNodeCapabilities()),
        "Archive.zip", VaultNodeKind.FILE, 100L, 0L, 1L
    )
    private val selection = VaultZipSelection(emptyList(), emptySet())

    @Test fun `extract here uses Browser choice and dispatches direct extraction`() {
        val requests = mutableListOf<Pair<VaultZipExtraction, Boolean>>()
        rule.setContent { MaterialTheme { VaultExtractZipDialog(archive, selection, {}) { request, choose -> requests += request to choose } } }
        rule.onNodeWithText(text(UiR.string.archive_extract_here)).performScrollTo().performClick()
        rule.onNodeWithText(text(R.string.onlyfiles_new_folder)).assertDoesNotExist()
        confirm().performClick()
        rule.runOnIdle {
            assertTrue(requests.single().first.intoExisting)
            assertFalse(requests.single().second)
        }
    }

    @Test fun `choose folder requests the protected picker instead of a native path field`() {
        val requests = mutableListOf<Pair<VaultZipExtraction, Boolean>>()
        rule.setContent { MaterialTheme { VaultExtractZipDialog(archive, selection, {}) { request, choose -> requests += request to choose } } }
        rule.onNodeWithText(text(UiR.string.archive_choose_folder)).performScrollTo().performClick()
        rule.onNodeWithText(text(UiR.string.archive_destination_folder)).assertDoesNotExist()
        confirm().performClick()
        rule.runOnIdle { assertTrue(requests.single().first.intoExisting); assertTrue(requests.single().second) }
    }

    @Test fun `named folder uses Browser naming without extra vault naming controls`() {
        val requests = mutableListOf<VaultZipExtraction>()
        rule.setContent { MaterialTheme { VaultExtractZipDialog(archive, selection, {}) { request, _ -> requests += request } } }
        rule.onNodeWithText(text(R.string.onlyfiles_new_folder)).assertDoesNotExist()
        confirm().performClick()
        rule.runOnIdle {
            assertEquals("Archive", requests.single().name)
            assertFalse(requests.single().intoExisting)
        }
    }

    @Test fun `selecting an archive subfolder keeps the Browser archive-based output name`() {
        val requests = mutableListOf<VaultZipExtraction>()
        rule.setContent { MaterialTheme { VaultExtractZipDialog(archive, selection.copy(folder = listOf("Docs")), {}) { request, _ -> requests += request } } }
        confirm().performClick()
        rule.runOnIdle { assertEquals("Archive", requests.single().name); assertFalse(requests.single().intoExisting) }
    }

    @Test fun `private extraction choices are not restored from saved state`() {
        val restoration = StateRestorationTester(rule)
        val requests = mutableListOf<VaultZipExtraction>()
        restoration.setContent { MaterialTheme { VaultExtractZipDialog(archive, selection, {}) { request, _ -> requests += request } } }
        rule.onNodeWithText(text(UiR.string.archive_extract_here)).performScrollTo().performClick()
        restoration.emulateSavedInstanceStateRestore()
        confirm().performClick()
        rule.runOnIdle {
            assertFalse(requests.single().intoExisting)
            assertEquals("Archive", requests.single().name)
        }
    }

    @Test fun `archive passwords must be entered again after saved state restoration`() {
        val restoration = StateRestorationTester(rule)
        restoration.setContent { MaterialTheme { VaultExtractZipDialog(archive, selection.copy(requiresPassword = true), {}) { _, _ -> } } }
        confirm().assertIsNotEnabled()
        rule.onNodeWithText(text(R.string.onlyfiles_password)).performScrollTo().performTextReplacement("secret")
        restoration.emulateSavedInstanceStateRestore()
        confirm().assertIsNotEnabled()
    }

    private fun text(id: Int) = context.getString(id)
    private fun confirm() = rule.onNode(hasText(text(UiR.string.archive_extract_archive)) and hasClickAction())
}
