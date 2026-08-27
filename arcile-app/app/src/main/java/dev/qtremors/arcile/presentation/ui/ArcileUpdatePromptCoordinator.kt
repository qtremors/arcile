package dev.qtremors.arcile.presentation.ui

import android.content.Context
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dagger.hilt.android.EntryPointAccessors
import dev.qtremors.arcile.core.operation.android.apk.ApkUpdateCandidate
import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.core.ui.ArcileFeedbackEvent
import dev.qtremors.arcile.core.ui.ArcileFeedbackSeverity
import dev.qtremors.arcile.core.ui.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class ArcileUpdatePromptRecord(
    val lastPromptCandidateId: String? = null,
    val lastPromptAtMillis: Long = 0L,
    val dismissedCandidateId: String? = null
)

internal object ArcileUpdatePromptPolicy {
    const val PROMPT_COOLDOWN_MILLIS = 24L * 60L * 60L * 1_000L

    fun candidateId(candidate: ApkUpdateCandidate): String = buildString {
        append(candidate.metadata.packageName)
        append(':')
        append(candidate.metadata.versionCode)
        append(':')
        append(candidate.metadata.signingDigests.sorted().joinToString(","))
    }

    fun shouldPrompt(
        candidateId: String,
        record: ArcileUpdatePromptRecord,
        nowMillis: Long,
        cooldownMillis: Long = PROMPT_COOLDOWN_MILLIS
    ): Boolean {
        if (record.dismissedCandidateId == candidateId) return false
        if (record.lastPromptCandidateId != candidateId) return true
        return nowMillis - record.lastPromptAtMillis >= cooldownMillis
    }
}

private class ArcileUpdatePromptStore(private val context: Context) {
    private val preferences by lazy {
        context.applicationContext.getSharedPreferences(
            "arcile_update_prompts",
            Context.MODE_PRIVATE
        )
    }

    fun read(): ArcileUpdatePromptRecord = ArcileUpdatePromptRecord(
        lastPromptCandidateId = preferences.getString(KEY_LAST_PROMPT_CANDIDATE, null),
        lastPromptAtMillis = preferences.getLong(KEY_LAST_PROMPT_AT, 0L),
        dismissedCandidateId = preferences.getString(KEY_DISMISSED_CANDIDATE, null)
    )

    fun recordPrompt(candidateId: String, nowMillis: Long) {
        preferences.edit()
            .putString(KEY_LAST_PROMPT_CANDIDATE, candidateId)
            .putLong(KEY_LAST_PROMPT_AT, nowMillis)
            .apply()
    }

    fun dismiss(candidateId: String) {
        preferences.edit().putString(KEY_DISMISSED_CANDIDATE, candidateId).apply()
    }

    private companion object {
        const val KEY_LAST_PROMPT_CANDIDATE = "last_prompt_candidate"
        const val KEY_LAST_PROMPT_AT = "last_prompt_at"
        const val KEY_DISMISSED_CANDIDATE = "dismissed_candidate"
    }
}

@Composable
internal fun ArcileUpdatePromptCoordinator(
    enabled: Boolean,
    onInstallUpdate: (String) -> Unit,
    onFeedback: (ArcileFeedbackEvent) -> Unit
) {
    val context = LocalContext.current
    val applicationContext = context.applicationContext
    val discovery = remember(applicationContext) {
        runCatching {
            EntryPointAccessors.fromApplication(
                applicationContext,
                AboutScreenEntryPoint::class.java
            ).onDeviceApkDiscovery()
        }.getOrNull()
    }
    val promptStore = remember(applicationContext) { ArcileUpdatePromptStore(applicationContext) }
    val coroutineScope = rememberCoroutineScope()
    var candidate by remember { mutableStateOf<ApkUpdateCandidate?>(null) }

    LaunchedEffect(enabled, discovery) {
        if (!enabled || discovery == null || candidate != null) return@LaunchedEffect
        val discovered = discovery.discoverArcileUpdate() ?: return@LaunchedEffect
        val candidateId = ArcileUpdatePromptPolicy.candidateId(discovered)
        val shouldPrompt = withContext(Dispatchers.IO) {
            ArcileUpdatePromptPolicy.shouldPrompt(
                candidateId = candidateId,
                record = promptStore.read(),
                nowMillis = System.currentTimeMillis()
            ).also { allowed ->
                if (allowed) promptStore.recordPrompt(candidateId, System.currentTimeMillis())
            }
        }
        if (shouldPrompt) candidate = discovered
    }

    val visibleCandidate = candidate ?: return
    val dismissPrompt = {
        candidate = null
        coroutineScope.launch(Dispatchers.IO) {
            promptStore.dismiss(ArcileUpdatePromptPolicy.candidateId(visibleCandidate))
        }
        Unit
    }

    AlertDialog(
        onDismissRequest = dismissPrompt,
        title = { Text(stringResource(R.string.arcile_update_available_title)) },
        text = {
            Text(
                stringResource(
                    R.string.arcile_update_available_message,
                    visibleCandidate.metadata.versionName
                )
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    candidate = null
                    coroutineScope.launch {
                        val freshCandidate = discovery?.revalidateCandidate(visibleCandidate)
                        if (freshCandidate?.isValid == true) {
                            onInstallUpdate(freshCandidate.metadata.filePath)
                        } else {
                            onFeedback(
                                ArcileFeedbackEvent(
                                    message = UiText.StringResource(R.string.error_file_operation_failed),
                                    severity = ArcileFeedbackSeverity.Error
                                )
                            )
                        }
                    }
                }
            ) {
                Text(stringResource(R.string.arcile_update_action))
            }
        },
        dismissButton = {
            TextButton(onClick = dismissPrompt) {
                Text(stringResource(R.string.arcile_update_dismiss))
            }
        }
    )
}
