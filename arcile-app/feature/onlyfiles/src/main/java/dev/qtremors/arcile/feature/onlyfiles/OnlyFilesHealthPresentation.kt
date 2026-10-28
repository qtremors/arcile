package dev.qtremors.arcile.feature.onlyfiles

import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.core.vault.domain.VaultHealthIssue
import dev.qtremors.arcile.core.vault.domain.VaultHealthSeverity

internal fun vaultHealthSeverityLabel(severity: VaultHealthSeverity): Int = when (severity) {
    VaultHealthSeverity.INFO -> R.string.onlyfiles_health_info
    VaultHealthSeverity.WARNING -> R.string.onlyfiles_health_warning
    VaultHealthSeverity.ERROR -> R.string.onlyfiles_health_error
}

internal fun vaultHealthIssueText(issue: VaultHealthIssue, orphanCount: Int): UiText = when (issue.code) {
    "orphan_objects" -> UiText.PluralResource(R.plurals.onlyfiles_health_orphans, orphanCount, listOf(orphanCount))
    else -> UiText.StringResource(when (issue.code) {
        "header_identity" -> R.string.onlyfiles_health_header_identity
        "header_damage" -> R.string.onlyfiles_health_header_damage
        "pending_transaction" -> R.string.onlyfiles_health_pending_transaction
        "directory_cycle" -> R.string.onlyfiles_health_directory_cycle
        "manifest_damage" -> R.string.onlyfiles_health_manifest_damage
        "object_damage" -> R.string.onlyfiles_health_object_damage
        "missing_object" -> R.string.onlyfiles_health_missing_object
        else -> R.string.onlyfiles_health_unknown_issue
    })
}
