package dev.qtremors.arcile.presentation.ui

import dev.qtremors.arcile.core.ui.ArcileFeedbackEvent

internal data class OwnedFeedbackEvent(
    val ownerId: String?,
    val event: ArcileFeedbackEvent
)

internal fun OwnedFeedbackEvent.belongsTo(activeOwnerId: String?): Boolean =
    ownerId != null && ownerId == activeOwnerId
