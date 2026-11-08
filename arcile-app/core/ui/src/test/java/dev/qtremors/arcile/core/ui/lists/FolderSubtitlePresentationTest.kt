package dev.qtremors.arcile.core.ui.lists

import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.FolderStats
import dev.qtremors.arcile.core.storage.domain.FolderStatsStatus
import dev.qtremors.arcile.core.ui.theme.FileDetailVisibility
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FolderSubtitlePresentationTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `folder rows distinguish unknown and unavailable from cached empty folders`() {
        val stats = mutableStateOf<FolderStats?>(null)
        val folder = FileModel(name = "Docs", reference = "/storage/emulated/0/Docs", isDirectory = true)
        compose.setContent {
            Text(folder.toFileRowUiModel(SimpleDateFormat("yyyy", Locale.US), folderStats = stats.value).displaySubtitle())
        }
        compose.onNodeWithText("Calculating size…").assertExists()
        compose.onNodeWithText("Folder").assertDoesNotExist()
        compose.runOnIdle { stats.value = FolderStats(0, 0, 0, FolderStatsStatus.Ready) }
        compose.onNodeWithText("0 files", substring = true).assertExists()
        compose.onNodeWithText("Calculating size…").assertDoesNotExist()
        compose.runOnIdle { stats.value = FolderStats(0, 0, 0, FolderStatsStatus.Unavailable) }
        compose.onNodeWithText("Size unavailable", substring = true).assertExists()
        compose.onNodeWithText("Folder").assertDoesNotExist()
    }

    @Test
    fun `hidden details drop from folder and file subtitles`() {
        val visibility = mutableStateOf(FileDetailVisibility(size = false))
        val folder = FileModel(name = "Docs", reference = "/storage/emulated/0/Docs", isDirectory = true)
        val file = FileModel(name = "a.txt", reference = "/storage/emulated/0/a.txt", size = 2048, lastModified = 0)
        val stats = FolderStats(3, 4096, 0, FolderStatsStatus.Ready)
        val formatter = SimpleDateFormat("yyyy", Locale.US)
        compose.setContent {
            Text("folder:" + folder.toFileRowUiModel(formatter, folderStats = stats).displaySubtitle(visibility.value))
            Text("file:" + file.toFileRowUiModel(formatter).displaySubtitle(visibility.value))
            Text("stamp:" + file.toFileRowUiModel(formatter).displayTimestamp(visibility.value.copy(date = false, time = false)))
        }
        compose.onNodeWithText("folder:3 files").assertExists()
        compose.onNodeWithText("file:").assertExists()
        compose.onNodeWithText("stamp:").assertExists()
        compose.runOnIdle { visibility.value = FileDetailVisibility(itemCount = false) }
        compose.onNodeWithText("folder:4", substring = true).assertExists()
        compose.onNodeWithText("files", substring = true).assertDoesNotExist()
        compose.runOnIdle { visibility.value = FileDetailVisibility(itemCount = false, size = false) }
        compose.onNodeWithText("folder:").assertExists()
    }
}
