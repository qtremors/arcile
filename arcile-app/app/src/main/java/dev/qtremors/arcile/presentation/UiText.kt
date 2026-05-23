package dev.qtremors.arcile.presentation

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource

sealed interface UiText {
    data class StringResource(
        @StringRes val resId: Int,
        val args: List<Any> = emptyList()
    ) : UiText

    data class PluralResource(
        @PluralsRes val resId: Int,
        val quantity: Int,
        val args: List<Any> = emptyList()
    ) : UiText

    data class Dynamic(val value: String) : UiText
}

@Composable
fun UiText.asString(): String =
    when (this) {
        is UiText.StringResource -> stringResource(resId, *args.toTypedArray())
        is UiText.PluralResource -> pluralStringResource(resId, quantity, *args.toTypedArray())
        is UiText.Dynamic -> value
    }

fun UiText.asString(context: Context): String =
    when (this) {
        is UiText.StringResource -> context.getString(resId, *args.toTypedArray())
        is UiText.PluralResource -> context.resources.getQuantityString(resId, quantity, *args.toTypedArray())
        is UiText.Dynamic -> value
    }
