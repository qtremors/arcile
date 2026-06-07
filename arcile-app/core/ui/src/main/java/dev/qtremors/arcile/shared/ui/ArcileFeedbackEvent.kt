package dev.qtremors.arcile.shared.ui

import dev.qtremors.arcile.core.ui.UiText
import java.util.UUID

data class ArcileFeedbackEvent(
    val message: UiText,
    val severity: ArcileFeedbackSeverity = ArcileFeedbackSeverity.Info,
    val actionLabel: UiText? = null,
    val onAction: (() -> Unit)? = null,
    val onDismiss: (() -> Unit)? = null,
    val id: String = UUID.randomUUID().toString()
)
