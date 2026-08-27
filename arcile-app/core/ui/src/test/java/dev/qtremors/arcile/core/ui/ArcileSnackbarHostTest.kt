package dev.qtremors.arcile.core.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ArcileSnackbarHostTest {

    @Test
    fun `severity maps to expected vector icons`() {
        assertEquals(Icons.Default.CheckCircle, ArcileFeedbackSeverity.Success.icon())
        assertEquals(Icons.Default.Error, ArcileFeedbackSeverity.Error.icon())
        assertEquals(Icons.Default.Warning, ArcileFeedbackSeverity.Warning.icon())
        assertEquals(Icons.Default.Info, ArcileFeedbackSeverity.Info.icon())
    }
}
