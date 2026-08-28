package dev.qtremors.arcile.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.FileConflict
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.ui.testing.ArcileTestTheme
import dev.qtremors.arcile.core.ui.theme.LocalReducedMotionEnabled
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ConflictCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val formatter = SimpleDateFormat("MMM dd, yyyy · HH:mm", Locale.US)

    private val sourceFile = FileModel(
        name = "report_incoming.pdf",
        absolutePath = "/storage/emulated/0/Download/report_incoming.pdf",
        size = 2048,
        lastModified = 2000L,
        isDirectory = false,
        extension = "pdf"
    )

    private val existingFile = FileModel(
        name = "report_existing.pdf",
        absolutePath = "/storage/emulated/0/Documents/report_existing.pdf",
        size = 1024,
        lastModified = 1000L,
        isDirectory = false,
        extension = "pdf"
    )

    @Test
    fun `conflict card renders incoming and existing panels and thumbnails`() {
        val conflict = FileConflict(
            sourcePath = sourceFile.absolutePath,
            sourceFile = sourceFile,
            existingFile = existingFile
        )

        composeRule.setContent {
            ArcileTestTheme {
                CompositionLocalProvider(LocalReducedMotionEnabled provides true) {
                    ConflictCard(
                        conflict = conflict,
                        resolution = null,
                        formatter = formatter,
                        onResolutionChange = {}
                    )
                }
            }
        }

        composeRule.onNodeWithTag("conflict_incoming").assertExists()
        composeRule.onNodeWithTag("conflict_existing").assertExists()
        composeRule.onNodeWithTag("conflict_thumbnail_incoming").assertExists()
        composeRule.onNodeWithTag("conflict_thumbnail_existing").assertExists()
        composeRule.onNodeWithText("report_existing.pdf").assertExists()
    }

    @Test
    fun `conflict card renders identical banner when files match size and timestamp`() {
        val matchingExisting = sourceFile.copy(absolutePath = "/target/report_incoming.pdf")
        val conflict = FileConflict(
            sourcePath = sourceFile.absolutePath,
            sourceFile = sourceFile,
            existingFile = matchingExisting
        )

        composeRule.setContent {
            ArcileTestTheme {
                CompositionLocalProvider(LocalReducedMotionEnabled provides true) {
                    ConflictCard(
                        conflict = conflict,
                        resolution = null,
                        formatter = formatter,
                        onResolutionChange = {}
                    )
                }
            }
        }

        composeRule.onNodeWithTag("conflict_identical_banner").assertExists()
    }

    @Test
    fun `conflict card responsive orientation changes with width`() {
        val conflict = FileConflict(
            sourcePath = sourceFile.absolutePath,
            sourceFile = sourceFile,
            existingFile = existingFile
        )

        composeRule.setContent {
            ArcileTestTheme {
                CompositionLocalProvider(LocalReducedMotionEnabled provides true) {
                    Box(modifier = Modifier.width(360.dp)) {
                        ConflictCard(
                            conflict = conflict,
                            resolution = null,
                            formatter = formatter,
                            onResolutionChange = {}
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("conflict_orientation_stacked").assertExists()
    }

    @Test
    fun `conflict card uses side by side orientation at wide breakpoint`() {
        val conflict = FileConflict(
            sourcePath = sourceFile.absolutePath,
            sourceFile = sourceFile,
            existingFile = existingFile
        )

        composeRule.setContent {
            ArcileTestTheme {
                CompositionLocalProvider(LocalReducedMotionEnabled provides true) {
                    Box(modifier = Modifier.requiredWidth(ConflictWideLayoutMinWidth)) {
                        ConflictCard(
                            conflict = conflict,
                            resolution = null,
                            formatter = formatter,
                            onResolutionChange = {}
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("conflict_orientation_side_by_side").assertExists()
    }

    @Test
    fun `conflict card displays resolution label when resolution is selected`() {
        val conflict = FileConflict(
            sourcePath = sourceFile.absolutePath,
            sourceFile = sourceFile,
            existingFile = existingFile
        )

        composeRule.setContent {
            ArcileTestTheme {
                CompositionLocalProvider(LocalReducedMotionEnabled provides true) {
                    ConflictCard(
                        conflict = conflict,
                        resolution = ConflictResolution.KEEP_BOTH,
                        formatter = formatter,
                        onResolutionChange = {}
                    )
                }
            }
        }

        composeRule.onNodeWithTag("conflict_resolution_status").assertExists()
    }
}
