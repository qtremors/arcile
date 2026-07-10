package dev.qtremors.arcile.presentation.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import dev.qtremors.arcile.core.ui.EmptyState
import dev.qtremors.arcile.core.ui.EmptyStateVariant
import dev.qtremors.arcile.testutil.ArcileTestTheme
import dev.qtremors.arcile.core.ui.theme.LocalReducedMotionEnabled
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VisualQaMatrixTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `core empty states render across visual qa matrix`() {
        val matrix = listOf(
            VisualCase(fontScale = 1.0f, layoutDirection = LayoutDirection.Ltr),
            VisualCase(fontScale = 1.5f, layoutDirection = LayoutDirection.Ltr),
            VisualCase(fontScale = 2.0f, layoutDirection = LayoutDirection.Ltr),
            VisualCase(fontScale = 1.5f, layoutDirection = LayoutDirection.Rtl)
        )

        composeRule.setContent {
            Column {
                matrix.forEachIndexed { index, case ->
                CompositionLocalProvider(
                    LocalDensity provides Density(density = 1f, fontScale = case.fontScale),
                    LocalLayoutDirection provides case.layoutDirection
                ) {
                    ArcileTestTheme {
                        CompositionLocalProvider(LocalReducedMotionEnabled provides true) {
                            EmptyState(
                                variant = EmptyStateVariant.Folder,
                                title = "Visual QA $index",
                                description = "Matrix case ${case.fontScale} ${case.layoutDirection}"
                            )
                        }
                    }
                }
            }
            }
        }

        matrix.forEachIndexed { index, case ->
            composeRule.onNodeWithText("Visual QA $index").assertExists()
            composeRule.onNodeWithText("Matrix case ${case.fontScale} ${case.layoutDirection}").assertExists()
        }
    }

    private data class VisualCase(
        val fontScale: Float,
        val layoutDirection: LayoutDirection
    )
}
