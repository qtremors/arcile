package dev.qtremors.arcile.core.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun Modifier.keyboardInputField(): Modifier = composed {
    val keyboardController = LocalSoftwareKeyboardController.current
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()

    fun showKeyboardAndReveal() {
        keyboardController?.show()
        coroutineScope.launch {
            delay(100)
            bringIntoViewRequester.bringIntoView()
        }
    }

    this
        .bringIntoViewRequester(bringIntoViewRequester)
        .onFocusChanged { focusState ->
            if (focusState.isFocused) {
                showKeyboardAndReveal()
            }
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(pass = PointerEventPass.Initial)
                showKeyboardAndReveal()
            }
        }
}
