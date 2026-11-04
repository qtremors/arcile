package dev.qtremors.arcile.core.ui.lists

import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FileRowDetailLayoutTest {
    @get:Rule val compose = createComposeRule()

    private fun setRow(subtitle: String, timestamp: String) {
        compose.setContent {
            FileRowText(
                name = "Camera",
                subtitle = subtitle,
                timestamp = timestamp,
                titleStyle = MaterialTheme.typography.titleMedium,
                supportStyle = MaterialTheme.typography.bodySmall,
                nameMaxLines = 1,
                marquee = false,
                modifier = Modifier.width(320.dp)
            )
        }
    }

    @Test
    fun `timestamp alone sits beside the name on one centered line`() {
        setRow(subtitle = "", timestamp = "10:42 AM")

        val name = compose.onNodeWithText("Camera").getBoundsInRoot()
        val time = compose.onNodeWithTag(FILE_ROW_INLINE_TIMESTAMP_TAG).getBoundsInRoot()
        assertEquals((name.top + name.bottom) / 2, (time.top + time.bottom) / 2)
        assertTrue(time.left > name.left)
        assertEquals(320.dp, time.right)
    }

    @Test
    fun `subtitle goes under the name with the timestamp at its end`() {
        setRow(subtitle = "3 files", timestamp = "10:42 AM")

        val name = compose.onNodeWithText("Camera").getBoundsInRoot()
        val subtitle = compose.onNodeWithText("3 files").getBoundsInRoot()
        val time = compose.onNodeWithText("10:42 AM").getBoundsInRoot()
        compose.onNodeWithTag(FILE_ROW_INLINE_TIMESTAMP_TAG).assertDoesNotExist()
        assertTrue(subtitle.top >= name.bottom)
        assertEquals(subtitle.top, time.top)
        assertEquals(320.dp, time.right)
    }

    @Test
    fun `name stands alone when no details are visible`() {
        setRow(subtitle = "", timestamp = "")

        compose.onNodeWithText("Camera").assertExists()
        compose.onNodeWithTag(FILE_ROW_INLINE_TIMESTAMP_TAG).assertDoesNotExist()
    }

    @Test
    fun `grid cards pad empty slots after visible details`() {
        assertEquals(listOf("10:42", ""), gridDetailLines("", "10:42", subtitleSlot = true, timestampSlot = true))
        assertEquals(listOf("3 files", "10:42"), gridDetailLines("3 files", "10:42", subtitleSlot = true, timestampSlot = true))
        assertEquals(listOf("10:42"), gridDetailLines("", "10:42", subtitleSlot = false, timestampSlot = true))
        assertEquals(emptyList<String>(), gridDetailLines("", "", subtitleSlot = false, timestampSlot = false))
    }
}
