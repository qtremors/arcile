package dev.qtremors.arcile.core.ui.security

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.R

/** Keeps navigation mounted while background/expired private content is inaccessible. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VaultPrivacyCurtain(blocked: Boolean, failed: Boolean, onRetry: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Box(if (blocked) Modifier.clearAndSetSemantics {} else Modifier) { content() }
        if (blocked) {
            val focus = remember { FocusRequester() }
            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier.fillMaxSize().clickable(onClick = {})
                    .focusRequester(focus).focusable()
            ) {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
                ) {
                    if (failed) {
                        Text(stringResource(R.string.onlyfiles_lock_failed))
                        Button(onClick = onRetry) { Text(stringResource(R.string.onlyfiles_retry_lock)) }
                    } else {
                        LoadingIndicator()
                        Text(stringResource(R.string.onlyfiles_locking))
                    }
                }
            }
            LaunchedEffect(focus) { focus.requestFocus() }
        }
    }
}
