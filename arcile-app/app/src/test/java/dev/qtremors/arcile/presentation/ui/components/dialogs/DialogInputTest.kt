package dev.qtremors.arcile.presentation.ui.components.dialogs

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import dev.qtremors.arcile.testutil.ArcileTestTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DialogInputTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `create file dialog shows duplicate error and disables create`() {
        composeRule.setContent {
            ArcileTestTheme {
                CreateFileDialog(
                    onDismiss = {},
                    onConfirm = {},
                    existingNames = setOf("report.txt"),
                    destinationPath = "/storage/emulated/0/Download"
                )
            }
        }

        composeRule.onNodeWithText("File Name (e.g., text.txt)").performTextInput("report.txt")

        composeRule.onNodeWithText("A file or folder with this name already exists").assertExists()
        composeRule.onNodeWithText("Create").assertIsNotEnabled()
    }

    @Test
    fun `create folder dialog shows destination preview`() {
        composeRule.setContent {
            ArcileTestTheme {
                CreateFolderDialog(
                    onDismiss = {},
                    onConfirm = {},
                    destinationPath = "/storage/emulated/0/Download"
                )
            }
        }

        composeRule.onNodeWithText("Folder Name").performTextInput("Docs")

        composeRule.onNodeWithText("Creates at /storage/emulated/0/Download/Docs").assertExists()
    }
}
