package dev.qtremors.arcile.feature.browser.ui

import android.content.IntentSender
import androidx.compose.runtime.Stable
import dev.qtremors.arcile.core.ui.ArcileFeedbackEvent
import kotlinx.coroutines.flow.SharedFlow

@Stable
internal data class BrowserEffects(
    val onFeedback: (ArcileFeedbackEvent) -> Unit,
    val nativeRequestFlow: SharedFlow<IntentSender>
)
