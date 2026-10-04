package dev.qtremors.arcile.core.ui.lists

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.ui.testing.ArcileTestTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FileItemSemanticsTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `hidden folders announce their details and keep open selection and options actions`() {
        var opened = false
        var toggled = false
        var optionsShown = false
        composeRule.setContent {
            ArcileTestTheme {
                Box(Modifier.size(48.dp).testTag("item").fileItemSemantics(
                    file = FileModel(".notes", "/.notes", isDirectory = true),
                    isSelected = false,
                    formattedDate = "today",
                    folderStatsText = "3 items",
                    isInSelectionMode = false,
                    onClick = { opened = true },
                    onLongClick = { optionsShown = true },
                    onOpenDirectly = { opened = true },
                    onToggleSelectionDirectly = { toggled = true }
                ))
            }
        }
        val item = composeRule.onNodeWithTag("item")
        val semantics = item.fetchSemanticsNode().config
        assertEquals(listOf(".notes (Hidden), Folder, 3 items, Modified today"), semantics[SemanticsProperties.ContentDescription])
        assertEquals("Open folder", semantics[SemanticsActions.OnClick].label)
        assertEquals("Show selection options", semantics[SemanticsActions.OnLongClick].label)
        item.performSemanticsAction(SemanticsActions.OnClick) { it() }
        item.performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        composeRule.runOnIdle {
            val action = semantics[SemanticsActions.CustomActions].single()
            assertEquals("Select item", action.label)
            assertTrue(action.action())
        }
        assertTrue(opened && toggled && optionsShown)
    }

    @Test
    fun `selection changes refresh spoken actions without changing their behavior`() {
        val selected = mutableStateOf(true)
        var opened = false
        var toggled = false
        composeRule.setContent {
            ArcileTestTheme {
                Box(Modifier.size(48.dp).testTag("item").fileItemSemantics(
                    file = FileModel("report.txt", "/report.txt"),
                    isSelected = selected.value,
                    formattedDate = "today",
                    folderStatsText = null,
                    isInSelectionMode = true,
                    fileSizeText = "1 KB",
                    onClick = { toggled = true },
                    onLongClick = {},
                    onOpenDirectly = { opened = true },
                    onToggleSelectionDirectly = { selected.value = !selected.value }
                ))
            }
        }
        val item = composeRule.onNodeWithTag("item")
        val semantics = item.fetchSemanticsNode().config
        assertEquals(listOf("report.txt, File, 1 KB, Modified today"), semantics[SemanticsProperties.ContentDescription])
        assertEquals("Toggle selection", semantics[SemanticsActions.OnClick].label)
        assertTrue(semantics[SemanticsProperties.Selected])
        item.performSemanticsAction(SemanticsActions.OnClick) { it() }
        composeRule.runOnIdle {
            val actions = semantics[SemanticsActions.CustomActions]
            assertEquals(listOf("Unselect item", "Open file"), actions.map { it.label })
            assertTrue(actions[1].action())
            assertTrue(actions[0].action())
        }
        assertEquals("Select item", item.fetchSemanticsNode().config[SemanticsActions.CustomActions].first().label)
        assertTrue(opened && toggled)
    }
}
