package dev.qtremors.arcile.feature.trash.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.StorageKind
import dev.qtremors.arcile.core.storage.domain.TrashMetadata
import dev.qtremors.arcile.core.ui.testing.ArcileTestTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TrashListInteractionTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `preview opens file while row body requests item actions`() {
        val item = testTrashItem()
        var openedFile: FileModel? = null
        var actionItemId: String? = null
        var restoreItemId: String? = null

        composeRule.setContent {
            ArcileTestTheme {
                TrashList(
                    files = listOf(item),
                    selectedFiles = emptySet(),
                    onToggleSelection = { actionItemId = it },
                    onOpenFile = { openedFile = it },
                    onRequestRestore = { restoreItemId = it }
                )
            }
        }

        composeRule
            .onNodeWithContentDescription("Open holiday.jpg")
            .performClick()
        assertEquals(item.fileModel, openedFile)
        assertEquals(null, actionItemId)

        composeRule
            .onNodeWithText("holiday.jpg")
            .performClick()
        assertEquals(item.id, actionItemId)

        composeRule
            .onNodeWithContentDescription("Restore holiday.jpg")
            .performClick()
        assertEquals(item.id, restoreItemId)
    }
}

private fun testTrashItem() = TrashMetadata(
    id = "trash-item-1",
    originalPath = "/storage/emulated/0/DCIM/holiday.jpg",
    deletionTime = System.currentTimeMillis(),
    fileModel = FileModel(
        name = "holiday.jpg",
        absolutePath = "/storage/emulated/0/.arcile/.trash/trash-item-1",
        size = 1_024L,
        lastModified = 1_700_000_000_000L,
        extension = "jpg",
        mimeType = "image/jpeg"
    ),
    sourceVolumeId = "primary",
    sourceStorageKind = StorageKind.INTERNAL
)
