package dev.qtremors.arcile.feature.trash

import android.app.Activity
import android.content.IntentSender
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.SharedFlow

@Composable
internal fun TrashNativeAuthorizationEffect(
    state: TrashState,
    requests: SharedFlow<IntentSender>?,
    onRestoreSelected: () -> Unit,
    onRestoreToDestination: (List<String>, String) -> Unit,
    onEmptyTrash: () -> Unit,
    onPermanentlyDeleteSelected: () -> Unit
) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        when (state.pendingNativeAction) {
            NativeAction.RESTORE -> onRestoreSelected()
            NativeAction.RESTORE_TO_DESTINATION -> {
                val destination = state.pendingDestinationPath
                if (destination != null && state.pendingRestoreIds.isNotEmpty()) {
                    onRestoreToDestination(state.pendingRestoreIds, destination)
                }
            }
            NativeAction.EMPTY -> onEmptyTrash()
            NativeAction.DELETE -> onPermanentlyDeleteSelected()
            null -> Unit
        }
    }

    LaunchedEffect(requests) {
        requests?.collect { sender ->
            launcher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }
}
