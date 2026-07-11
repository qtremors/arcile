package dev.qtremors.arcile.core.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.qtremors.arcile.core.runtime.NativeStorageAuthorizationGateway
import dev.qtremors.arcile.core.storage.domain.StorageAuthorizationRequirement

/**
 * Resolves the Android object associated with a platform-neutral authorization requirement and
 * launches it at the route boundary. A saved request id prevents configuration recreation from
 * launching a second system confirmation while ActivityResultRegistry restores the first one.
 */
@Composable
fun NativeStorageAuthorizationEffect(
    requirement: StorageAuthorizationRequirement?,
    onResult: (requestId: String, confirmed: Boolean) -> Unit,
    onUnavailable: (requestId: String) -> Unit
) {
    val applicationContext = LocalContext.current.applicationContext
    val gateway = remember(applicationContext) {
        EntryPointAccessors.fromApplication(
            applicationContext,
            NativeStorageAuthorizationEntryPoint::class.java
        ).authorizationGateway()
    }
    val currentOnResult by rememberUpdatedState(onResult)
    val currentOnUnavailable by rememberUpdatedState(onUnavailable)
    var launchedRequestId by rememberSaveable { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val completedRequestId = launchedRequestId ?: return@rememberLauncherForActivityResult
        gateway.complete(completedRequestId)
        launchedRequestId = null
        currentOnResult(completedRequestId, result.resultCode == Activity.RESULT_OK)
    }

    LaunchedEffect(requirement?.requestId, launchedRequestId) {
        val current = requirement ?: return@LaunchedEffect
        if (launchedRequestId != null) return@LaunchedEffect

        val sender = gateway.consume(current)
        if (sender == null) {
            gateway.complete(current.requestId)
            currentOnUnavailable(current.requestId)
            return@LaunchedEffect
        }

        launchedRequestId = current.requestId
        runCatching {
            launcher.launch(IntentSenderRequest.Builder(sender).build())
        }.onFailure {
            gateway.complete(current.requestId)
            currentOnUnavailable(current.requestId)
            launchedRequestId = null
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface NativeStorageAuthorizationEntryPoint {
    fun authorizationGateway(): NativeStorageAuthorizationGateway
}
